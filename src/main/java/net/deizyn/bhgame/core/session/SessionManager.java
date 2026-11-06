package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.Tuning;
import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.core.block.CarryBlock;
import net.deizyn.bhgame.core.block.TrainingBlock;
import net.deizyn.bhgame.core.block.TrainingBlockEntity;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.outcome.Stats;
import net.deizyn.bhgame.core.pattern.CarryEvent;
import net.deizyn.bhgame.core.pattern.Motion;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.S2CNearbyState;
import net.deizyn.bhgame.network.S2CPlayerPose;
import net.deizyn.bhgame.network.S2CSessionEnd;
import net.deizyn.bhgame.network.S2CSessionStart;
import net.deizyn.bhgame.registry.ModBlockEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Containers;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every running session. Ticks once per server tick, but a session only does work when the tick reaches its
 * {@code nextEventTick}. Every exit path goes through {@link #finish}.
 *
 * <p>Leaving a multi-player session after it began (abort key, death, logout, walking off) is a FAIL with the big
 * penalty for the one who left and ABORTED for the others (D32). Leaving a one-player session, or while still
 * WAITING for a partner, is a plain ABORTED.
 */
public final class SessionManager {
    private static final Map<UUID, TrainingSession> BY_PLAYER = new HashMap<>();
    private static final List<TrainingSession> ACTIVE = new ArrayList<>();

    public static void tryStart(ServerPlayer player, ServerLevel level, BlockPos pos, TrainingBlock game,
                                TrainingBlockEntity blockEntity) {
        if (BY_PLAYER.containsKey(player.getUUID())) {
            return;
        }
        if (!withinStartReach(player, pos, game)) {
            player.displayClientMessage(Component.translatable("bhgame.session.too_far"), true);
            return;
        }
        if (game instanceof CarryBlock carryBlock) {
            startCarry(player, level, pos, carryBlock, blockEntity);
            return;
        }
        long now = level.getServer().getTickCount();
        UUID lock = blockEntity.user();
        if (lock != null) {
            TrainingSession existing = BY_PLAYER.get(lock);
            if (existing == null || !existing.pos().equals(pos)) {
                // a stale lock can only come from a bug; never let it block the herb forever
                blockEntity.setUser(null);
            } else {
                if (existing.state() == SessionState.WAITING && !existing.isFull()) {
                    if (!roomOpposite(player, existing)) {
                        player.displayClientMessage(Component.translatable("bhgame.session.no_room_opposite"), true);
                        return;
                    }
                    join(existing, player, now);
                } else {
                    player.displayClientMessage(Component.translatable("bhgame.session.busy"), true);
                }
                return;
            }
        }
        if (BhGameConfig.maxConcurrentSessions > 0 && ACTIVE.size() >= BhGameConfig.maxConcurrentSessions) {
            player.displayClientMessage(Component.translatable("bhgame.session.server_busy"), true);
            return;
        }
        TrainingSession session = new TrainingSession(level.dimension(), pos.immutable(), game,
                blockEntity.difficulty(), blockEntity.maxHp(game.defaultMaxHp()),
                blockEntity.maxMistakes(game.defaultMaxMistakes()), now, false);
        blockEntity.setUser(player.getUUID());
        ACTIVE.add(session);
        join(session, player, now);
        if (session.state() == SessionState.WAITING) {
            if (game.facesOff()) {
                // the host's side is fixed now, so whoever joins knows where to stand: opposite (D62)
                Direction side = snapToSide(player, session);
                session.setHostSide(side != null ? side : nearestSide(player, session));
            }
            session.startWaiting(now);
            BhGameNetwork.sendTo(player, startMessage(session, session.participants().get(0), true));
            BhGameNetwork.sendToTrackingAndSelf(player, new S2CPlayerPose(player.getId(), game.pose()));
            player.displayClientMessage(Component.translatable(game.waitingHintKey()), true);
        }
    }

    /**
     * D48: lift the block out of the world and start at once (carried games are one-player). The block's state and
     * NBT ride in the session until it is put back.
     */
    private static void startCarry(ServerPlayer player, ServerLevel level, BlockPos pos, CarryBlock game,
                                   TrainingBlockEntity blockEntity) {
        if (BhGameConfig.maxConcurrentSessions > 0 && ACTIVE.size() >= BhGameConfig.maxConcurrentSessions) {
            player.displayClientMessage(Component.translatable("bhgame.session.server_busy"), true);
            return;
        }
        long now = level.getServer().getTickCount();
        BlockState state = level.getBlockState(pos);
        TrainingSession session = new TrainingSession(level.dimension(), pos.immutable(), game,
                blockEntity.difficulty(), blockEntity.maxHp(game.defaultMaxHp()),
                blockEntity.maxMistakes(game.defaultMaxMistakes()), now, false);
        session.setCarry(new CarryState(state, blockEntity.saveWithoutMetadata(), game.poseData(state),
                player.position()));
        ACTIVE.add(session);
        level.removeBlock(pos, false);
        join(session, player, now);
    }

    private static void join(TrainingSession session, ServerPlayer player, long now) {
        session.join(player.getUUID());
        BY_PLAYER.put(player.getUUID(), session);
        if (session.isFull()) {
            begin(session, now);
        }
    }

    private static void begin(TrainingSession session, long now) {
        session.begin(now);
        for (Participant participant : session.participants()) {
            ServerPlayer player = session.player(participant);
            if (player == null) {
                continue;
            }
            // move first, so the start packet's camera turn begins from the snapped spot
            Direction hostSide = session.hostSide();
            if (hostSide != null) {
                // face-off (D62): the host keeps the side taken on clicking, everyone else stands opposite
                placeAt(player, session, participant.role() == 0 ? hostSide : hostSide.getOpposite());
            } else {
                snapToSide(player, session);
            }
            if (session.carry() != null) {
                // drawn by the vanilla model only (D51)
                session.carry().restoreEpicFight = EpicFightCompat.forceVanillaMode(player);
            } else if (session.game().forcesEpicFightMode()) {
                // the dummy's punches are Epic Fight fist attacks (D66)
                participant.restoreVanillaMode = EpicFightCompat.forceEpicFightMode(player);
            } else {
                // every other game poses the vanilla model (D78)
                participant.restoreEpicFightMode = EpicFightCompat.forceVanillaMode(player);
            }
            BhGameNetwork.sendTo(player, startMessage(session, participant, false));
            BhGameNetwork.sendToTrackingAndSelf(player,
                    new S2CPlayerPose(player.getId(), session.game().pose(), session.poseData()));
            player.displayClientMessage(Component.translatable(session.game().hintKey()), true);
        }
    }

    /**
     * D42: stand the player {@code standDistance} from the block on the nearest free side of the four, facing it.
     * A side is free when the player's standing box fits there and there is ground under it. Returns the side used,
     * or null when the player was not moved. Face-off games (D58) snap only the host, on clicking; the side is then
     * fixed and the others go opposite through {@link #placeAt} (D62).
     */
    @Nullable
    private static Direction snapToSide(ServerPlayer player, TrainingSession session) {
        double distance = session.game().standDistance();
        ServerLevel level = player.serverLevel();
        if (distance <= 0.0 || level.dimension() != session.dimension()) {
            return null;
        }
        Vec3 centre = Vec3.atBottomCenterOf(session.pos());
        Direction best = null;
        double bestSqr = Double.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 spot = sideSpot(centre, side, distance);
            double sqr = player.position().distanceToSqr(spot);
            if (sqr < bestSqr && fits(level, player, spot)) {
                best = side;
                bestSqr = sqr;
            }
        }
        if (best != null) {
            placeAt(player, session, best);
        }
        return best;
    }

    /** Stands the player on {@code side} of the block facing it, if their box fits there; otherwise leaves them. */
    private static void placeAt(ServerPlayer player, TrainingSession session, Direction side) {
        double distance = session.game().standDistance();
        ServerLevel level = player.serverLevel();
        if (distance <= 0.0 || level.dimension() != session.dimension()) {
            return;
        }
        Vec3 spot = sideSpot(Vec3.atBottomCenterOf(session.pos()), side, distance);
        if (fits(level, player, spot)) {
            player.connection.teleport(spot.x, spot.y, spot.z, side.getOpposite().toYRot(), 0.0F);
        }
    }

    /** Face-off (D62): whether {@code player} can stand opposite the waiting host. Other games always have room. */
    private static boolean roomOpposite(ServerPlayer player, TrainingSession session) {
        Direction hostSide = session.hostSide();
        double distance = session.game().standDistance();
        if (hostSide == null || distance <= 0.0) {
            return true;
        }
        Vec3 spot = sideSpot(Vec3.atBottomCenterOf(session.pos()), hostSide.getOpposite(), distance);
        return fits(player.serverLevel(), player, spot);
    }

    /** The side of the block {@code player} is on, for a host who could not be moved to a free side. */
    private static Direction nearestSide(ServerPlayer player, TrainingSession session) {
        Vec3 centre = Vec3.atBottomCenterOf(session.pos());
        return Direction.getNearest(player.getX() - centre.x, 0.0, player.getZ() - centre.z);
    }

    private static Vec3 sideSpot(Vec3 centre, Direction side, double distance) {
        return centre.add(side.getStepX() * distance, 0.0, side.getStepZ() * distance);
    }

    private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 spot) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(spot);
        BlockPos below = BlockPos.containing(spot.x, spot.y - 0.5, spot.z);
        return level.noCollision(player, box) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private static S2CSessionStart startMessage(TrainingSession session, Participant participant, boolean waiting) {
        return new S2CSessionStart(session.pos(), session.game().gameId(), participant.profile(), session.maxHp(),
                Tuning.FOCUS_TICKS, List.of(), participant.role(), session.game().requiredPlayers(), waiting,
                session.game().lookHeight(), session.game().channel());
    }

    private static boolean withinStartReach(ServerPlayer player, BlockPos pos, TrainingBlock game) {
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double reach = game.startReach();
        return dx * dx + dz * dz <= reach * reach
                && Math.abs(player.getY() - pos.getY()) <= game.startHeightTolerance();
    }

    /** A stress-test session with bot participants and no block (see {@link StressTest}). */
    static TrainingSession startSimulated(MinecraftServer server, TrainingBlock game, BlockPos virtualPos) {
        long now = server.getTickCount();
        TrainingSession session = new TrainingSession(Level.OVERWORLD, virtualPos, game, 1 + server.overworld().random
                .nextInt(3), game.defaultMaxHp(), game.defaultMaxMistakes(), now, true);
        ACTIVE.add(session);
        while (!session.isFull()) {
            session.join(UUID.randomUUID());
        }
        session.begin(now);
        return session;
    }

    /** Whether a player is in any session (WAITING included); see {@link TrainingPins}. */
    public static boolean isInSession(UUID playerId) {
        return BY_PLAYER.containsKey(playerId);
    }

    /** The pose {@code player} currently shows, for a client that starts tracking them mid-session. */
    public static PlayerPose poseOf(ServerPlayer player) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        return session != null ? session.game().pose() : PlayerPose.NONE;
    }

    /** The public session state around {@code player} (D60), for a client that starts tracking them; or null. */
    @Nullable
    public static S2CNearbyState nearbyStateOf(ServerPlayer player) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.pattern() == null || session.isFinished()) {
            return null;
        }
        return session.nearbySnapshot(player.getServer().getTickCount());
    }

    /** The pose byte {@code player} currently shows (water levels while carrying), 0 when none. */
    public static int poseDataOf(ServerPlayer player) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        return session != null ? session.poseData() : 0;
    }

    /** Read-only view for /bhgame debug. */
    public static List<TrainingSession> sessions() {
        return Collections.unmodifiableList(ACTIVE);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        boolean sweep = now % Tuning.SWEEP_INTERVAL_TICKS == 0;
        // backwards, because finish() removes sessions from the list
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            if (i >= ACTIVE.size()) {
                continue;
            }
            TrainingSession session = ACTIVE.get(i);
            if (sweep && !session.simulated() && !sweepCheck(server, session)) {
                continue;
            }
            if (session.carry() != null && !session.simulated() && (now + session.serial()) % CARRY_SAMPLE_TICKS == 0
                    && !carryTick(session)) {
                continue;
            }
            if (now < session.nextEventTick()) {
                continue;
            }
            session.clearSchedule();
            if (session.state() == SessionState.WAITING) {
                if (now >= session.waitDeadline()) {
                    endNoPartner(session);
                } else {
                    session.scheduleAt(session.waitDeadline());
                }
                continue;
            }
            if (!session.simulated() && !everyoneOnline(session)) {
                continue;
            }
            if (session.state() == SessionState.FOCUS && now >= session.focusEndTick()) {
                session.setState(SessionState.RUNNING);
            }
            if (session.simulated() && session.state() == SessionState.RUNNING) {
                StressTest.botTick(session, now);
                if (session.isFinished()) {
                    continue;
                }
            }
            session.pattern().tick(session, now);
            if (session.isFinished()) {
                continue;
            }
            session.flushGauges(now);
            if (session.state() == SessionState.FOCUS) {
                session.scheduleAt(session.focusEndTick());
            }
            if (session.nextEventTick() == Long.MAX_VALUE) {
                session.scheduleAt(now + 20);
            }
        }
    }

    private static final int CARRY_SAMPLE_TICKS = 5;
    /**
     * Below this many blocks of horizontal travel per sample the carrier counts as standing still. Low enough that a
     * slowed carrier sneaking (D70) still counts as walking, so the yoke keeps swaying however slow they go.
     */
    private static final double STILL_DISTANCE = 0.08;
    private static final double PUT_DOWN_REACH = 4.5;
    private static final double MIN_CARRY_DISTANCE = 3.0;

    /**
     * Every few ticks for a carried session: samples how the carrier moves for {@link PatternContext#motion} and
     * ends the session if they can no longer carry (hands not empty, swimming, riding, gliding). Returns false if it
     * ended.
     */
    private static boolean carryTick(TrainingSession session) {
        CarryState carry = session.carry();
        ServerPlayer player = session.player(session.participants().get(0));
        if (player == null) {
            leave(session.hostId(), true);
            return false;
        }
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty() || player.isSwimming()
                || player.isEyeInFluid(FluidTags.WATER) || player.isPassenger() || player.isFallFlying()
                || player.level().dimension() != session.dimension()) {
            finish(session, Outcome.ABORTED, null, null);
            return false;
        }
        Vec3 pos = player.position();
        double dx = pos.x - carry.lastPos.x;
        double dz = pos.z - carry.lastPos.z;
        carry.lastPos = pos;
        if (!player.onGround()) {
            carry.motion = Motion.AIRBORNE;
        } else if (dx * dx + dz * dz < STILL_DISTANCE * STILL_DISTANCE) {
            carry.motion = Motion.STILL;
        } else {
            carry.motion = player.isSprinting() ? Motion.SPRINT : Motion.WALK;
        }
        return true;
    }

    /** The carrier jumped (the server sees it in the movement packets); a landing soon after is a jump landing. */
    public static void onCarrierJump(ServerPlayer player) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        if (session != null && session.carry() != null) {
            session.carry().lastJumpTick = player.getServer().getTickCount();
        }
    }

    /** The carrier landed after falling {@code distance} blocks. */
    public static void onCarrierFall(ServerPlayer player, float distance) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.carry() == null) {
            return;
        }
        long now = player.getServer().getTickCount();
        boolean jumped = now - session.carry().lastJumpTick < 40;
        CarryEvent event;
        if (distance > (jumped ? 3.25F : 2.0F)) {
            event = CarryEvent.HARD_LAND;
        } else if (jumped) {
            event = CarryEvent.LAND;
        } else if (distance >= 0.5F) {
            event = CarryEvent.STEP_DOWN;
        } else {
            return;
        }
        session.carry().lastJumpTick = Long.MIN_VALUE / 2;
        carryEvent(session, event, now);
    }

    public static void onCarrierHurt(ServerPlayer player) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        if (session != null && session.carry() != null) {
            carryEvent(session, CarryEvent.HURT, player.getServer().getTickCount());
        }
    }

    private static void carryEvent(TrainingSession session, CarryEvent event, long now) {
        if (session.state() != SessionState.RUNNING) {
            return;
        }
        session.pattern().onCarryEvent(session, event, now);
        if (!session.isFinished()) {
            session.flushGauges(now);
        }
    }

    /**
     * The abort key: a carried block is put down in front of the player (or at their feet), anything else ends as
     * ABORTED.
     */
    public static void abortKey(ServerPlayer player) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        if (session != null && session.carry() != null) {
            BlockPos front = player.blockPosition().relative(player.getDirection());
            if (!putDown(player, session, front) && !putDown(player, session, player.blockPosition())) {
                player.displayClientMessage(Component.translatable("bhgame.carry.cannot_place"), true);
            }
            return;
        }
        leave(player.getUUID(), false);
    }

    /** The drop key while carrying, looking at a block: put it down there (D56). */
    public static void putDownAt(ServerPlayer player, BlockPos clicked, Direction face) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.carry() == null
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(clicked)) > PUT_DOWN_REACH * PUT_DOWN_REACH) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
        if (!putDown(player, session, target)) {
            player.displayClientMessage(Component.translatable("bhgame.carry.cannot_place"), true);
        }
    }

    /**
     * Puts the carried block at {@code target} and ends the session with the game's verdict. Set down within
     * {@link #MIN_CARRY_DISTANCE} of where it was lifted, it was not really carried anywhere: ABORTED.
     */
    private static boolean putDown(ServerPlayer player, TrainingSession session, BlockPos target) {
        if ((session.state() != SessionState.RUNNING && session.state() != SessionState.FOCUS)
                || !canPlaceCarried(player.serverLevel(), target)) {
            return false;
        }
        CarryState carry = session.carry();
        CarryBlock game = (CarryBlock) session.game();
        placeCarried(player.serverLevel(), target, session, player.getDirection());
        boolean carried = session.state() == SessionState.RUNNING
                && target.distSqr(session.pos()) >= MIN_CARRY_DISTANCE * MIN_CARRY_DISTANCE;
        finish(session, carried ? game.carryOutcome(carry.startData, carry.data) : Outcome.ABORTED, null, null);
        return true;
    }

    private static boolean canPlaceCarried(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static void placeCarried(ServerLevel level, BlockPos pos, TrainingSession session, Direction facing) {
        CarryState carry = session.carry();
        CarryBlock game = (CarryBlock) session.game();
        level.setBlock(pos, game.withPoseData(carry.state, carry.data, facing), 3);
        if (level.getBlockEntity(pos) instanceof TrainingBlockEntity blockEntity) {
            blockEntity.load(carry.blockEntityTag);
            blockEntity.setChanged();
        }
        carry.placed = true;
    }

    /**
     * Any end of a carried session that did not put the block down: put it at the carrier's feet, or drop it as an
     * item that keeps its water and NBT. Never lost (D48).
     */
    private static void returnCarried(TrainingSession session, @Nullable ServerPlayer player, ServerLevel level) {
        CarryState carry = session.carry();
        Vec3 at = player != null && player.level() == level ? player.position() : carry.lastPos;
        BlockPos feet = BlockPos.containing(at);
        Direction facing = player != null ? player.getDirection() : carry.state.getValue(CarryBlock.FACING);
        for (BlockPos pos : new BlockPos[]{feet, feet.above()}) {
            if (canPlaceCarried(level, pos)) {
                placeCarried(level, pos, session, facing);
                return;
            }
        }
        CarryBlock game = (CarryBlock) session.game();
        ItemStack stack = new ItemStack(game);
        BlockState state = game.withPoseData(carry.state, carry.data, facing);
        CompoundTag stateTag = new CompoundTag();
        for (Property<?> property : state.getProperties()) {
            stateTag.putString(property.getName(), propertyValue(state, property));
        }
        stack.getOrCreateTag().put(BlockItem.BLOCK_STATE_TAG, stateTag);
        BlockItem.setBlockEntityData(stack, ModBlockEntities.TRAINING.get(), carry.blockEntityTag);
        Containers.dropItemStack(level, at.x, at.y, at.z, stack);
        carry.placed = true;
    }

    private static <T extends Comparable<T>> String propertyValue(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public static void onInput(ServerPlayer player, InputKey key, boolean press) {
        TrainingSession session = BY_PLAYER.get(player.getUUID());
        if (session == null || session.state() != SessionState.RUNNING) {
            return;
        }
        Participant participant = session.participant(player.getUUID());
        if (participant == null || !participant.profile().captures(key)) {
            return;
        }
        long now = player.getServer().getTickCount();
        if (!participant.allowInput(now)) {
            return;
        }
        if (!inRange(player, session)) {
            leave(player.getUUID(), false);
            return;
        }
        if (participant.profile().isHold(key)) {
            participant.setHeld(key, press);
            session.markNearbyDirty();
        }
        session.pattern().onInput(session, participant, key, press, now);
        if (!session.isFinished()) {
            session.flushGauges(now);
        }
    }

    /** Used by the stress bots, which skip the network and the player checks. */
    static void botInput(TrainingSession session, Participant participant, InputKey key, long now) {
        session.pattern().onInput(session, participant, key, true, now);
    }

    /**
     * {@code playerId} leaves whatever session they are in: abort key, death, dimension change, walking off, or
     * logout ({@code disconnecting}, so nothing is sent to them).
     */
    public static void leave(UUID playerId, boolean disconnecting) {
        TrainingSession session = BY_PLAYER.get(playerId);
        if (session == null) {
            return;
        }
        boolean abandons = session.participants().size() > 1 && session.state() != SessionState.WAITING;
        finish(session, Outcome.ABORTED, abandons ? playerId : null, disconnecting ? playerId : null);
    }

    /** The block was removed: nobody is to blame. */
    public static void abortAt(ServerLevel level, BlockPos pos) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            TrainingSession session = ACTIVE.get(i);
            if (session.carry() == null && session.pos().equals(pos) && session.dimension() == level.dimension()) {
                finish(session, Outcome.ABORTED, null, null);
                return;
            }
        }
    }

    /**
     * Ends a session exactly once: unlocks the block, tells every participant, applies the result to the block and
     * records it.
     *
     * @param culprit       a participant who abandoned a multi-player session: FAIL + big penalty for them, the
     *                      block is ruined, everyone else gets {@code outcome} (ABORTED)
     * @param disconnecting a participant whose connection is closing: no packets or penalties, stats only
     */
    static void finish(TrainingSession session, Outcome outcome, @Nullable UUID culprit, @Nullable UUID disconnecting) {
        finish(session, outcome, culprit, disconnecting, null);
    }

    /**
     * {@code winner}: a duel (D58). That participant wins (PERFECT without their own mistakes, else SUCCESS) and every
     * other one gets FAIL with the fail penalty; {@code outcome} is then ignored.
     */
    static void finish(TrainingSession session, Outcome outcome, @Nullable UUID culprit, @Nullable UUID disconnecting,
                       @Nullable UUID winner) {
        if (session.isFinished()) {
            return;
        }
        boolean wasWaiting = session.state() == SessionState.WAITING;
        session.setState(SessionState.FINISHED);
        for (Participant participant : session.participants()) {
            BY_PLAYER.remove(participant.id(), session);
        }
        ACTIVE.remove(session);
        if (session.simulated()) {
            StressTest.onFinished(session);
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server != null ? server.getLevel(session.dimension()) : null;
        TrainingBlockEntity blockEntity = null;
        if (session.carry() == null && level != null && level.isLoaded(session.pos())
                && level.getBlockEntity(session.pos()) instanceof TrainingBlockEntity found) {
            blockEntity = found;
            if (session.hostId().equals(found.user())) {
                found.setUser(null);
            }
        }

        long ticks = server != null ? server.getTickCount() - session.startTick() : 0;
        List<ServerPlayer> stayed = new ArrayList<>(session.participants().size());
        List<ServerPlayer> losers = winner != null ? new ArrayList<>(session.participants().size()) : null;
        ServerPlayer winnerPlayer = null;
        Outcome winnerOutcome = Outcome.SUCCESS;
        ServerPlayer culpritPlayer = null;
        for (Participant participant : session.participants()) {
            ServerPlayer player = session.player(participant);
            if (player == null) {
                continue;
            }
            if (session.carry() != null) {
                CarryState.applySlow(player, 0.0F);
            }
            boolean isCulprit = participant.id().equals(culprit);
            Outcome own;
            if (isCulprit) {
                own = Outcome.FAIL;
            } else if (winner != null) {
                own = !participant.id().equals(winner) ? Outcome.FAIL
                        : participant.mistakes() == 0 ? Outcome.PERFECT : Outcome.SUCCESS;
            } else {
                own = outcome;
            }
            Stats.record(player, session.game().gameId(), own, participant.mistakes(), ticks, session.pos(), false);
            if (session.bestObjective() != null) {
                Stats.recordBest(player, session.bestObjective(), session.bestValue());
            }
            if (participant.id().equals(disconnecting)) {
                continue;
            }
            BhGameNetwork.sendTo(player, new S2CSessionEnd(own));
            BhGameNetwork.sendToTrackingAndSelf(player, new S2CPlayerPose(player.getId(), PlayerPose.NONE));
            if (participant.restoreVanillaMode) {
                // after the pose packet: clients refuse vanilla mode for a player they still see training (D66)
                EpicFightCompat.restoreVanillaMode(player);
            } else if (participant.restoreEpicFightMode) {
                // the same the other way round (D78)
                EpicFightCompat.restoreEpicFightMode(player);
            }
            Component message;
            if (culprit != null && !isCulprit) {
                message = Component.translatable("bhgame.session.partner_left");
            } else if (wasWaiting && outcome == Outcome.ABORTED && disconnecting == null && culprit == null
                    && server != null && server.getTickCount() >= session.waitDeadline()) {
                message = Component.translatable("bhgame.session.no_partner");
            } else if (session.outcomeMessage() != null && culprit == null) {
                message = session.outcomeMessage();
            } else {
                message = Component.translatable(session.game().outcomeKey(own));
            }
            player.displayClientMessage(message, true);
            if (isCulprit) {
                culpritPlayer = player;
            } else {
                stayed.add(player);
                if (winner != null && own.isWin()) {
                    winnerPlayer = player;
                    winnerOutcome = own;
                } else if (winner != null) {
                    losers.add(player);
                }
            }
        }

        if (session.carry() != null) {
            endCarry(session, level, disconnecting);
            return;
        }
        if (blockEntity == null) {
            return;
        }
        BlockState state = level.getBlockState(session.pos());
        if (culprit != null) {
            if (culpritPlayer != null) {
                session.game().bigPenalty().apply(culpritPlayer, session.pos());
            }
            session.game().onAbandoned(level, session.pos(), state, blockEntity);
        } else if (winner != null) {
            if (winnerPlayer != null) {
                session.game().onWin(level, session.pos(), state, List.of(winnerPlayer), winnerOutcome, blockEntity);
            }
            session.game().onFail(level, session.pos(), state, losers, session.failPenalty(), blockEntity);
        } else if (outcome.isWin()) {
            session.game().onWin(level, session.pos(), state, stayed, outcome, blockEntity);
        } else if (outcome == Outcome.FAIL) {
            session.game().onFail(level, session.pos(), state, stayed, session.failPenalty(), blockEntity);
        }
    }

    /** Puts a carried block back if it is still out, and gives the carrier their Epic Fight mode back. */
    private static void endCarry(TrainingSession session, @Nullable ServerLevel level, @Nullable UUID disconnecting) {
        CarryState carry = session.carry();
        ServerPlayer player = session.player(session.participants().get(0));
        if (!carry.placed) {
            ServerLevel target = player != null ? player.serverLevel() : level;
            if (target != null) {
                returnCarried(session, player, target);
            }
        }
        if (player != null && carry.restoreEpicFight && !player.getUUID().equals(disconnecting)) {
            // after the pose packet above: clients refuse Epic Fight mode for a player they still see carrying
            EpicFightCompat.restoreEpicFightMode(player);
        }
    }

    private static void endNoPartner(TrainingSession session) {
        finish(session, Outcome.ABORTED, null, null);
    }

    /**
     * The server is stopping: carried blocks go back into the world before it is saved (never lost, D48). Nothing
     * is sent; the players are about to be disconnected.
     */
    public static void shutdown() {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            TrainingSession session = ACTIVE.get(i);
            if (session.carry() != null && !session.simulated()) {
                finish(session, Outcome.ABORTED, null, session.hostId());
            }
        }
        clear();
    }

    public static void clear() {
        BY_PLAYER.clear();
        ACTIVE.clear();
        StressTest.reset();
    }

    public static int activeCount() {
        return ACTIVE.size();
    }

    private static boolean everyoneOnline(TrainingSession session) {
        for (Participant participant : session.participants()) {
            if (session.player(participant) == null) {
                leave(participant.id(), true);
                return false;
            }
        }
        return true;
    }

    /** Returns false if the session was ended. */
    private static boolean sweepCheck(MinecraftServer server, TrainingSession session) {
        ServerLevel level = server.getLevel(session.dimension());
        if (session.carry() == null && (level == null || !level.isLoaded(session.pos())
                || !(level.getBlockEntity(session.pos()) instanceof TrainingBlockEntity blockEntity)
                || !session.hostId().equals(blockEntity.user()))) {
            finish(session, Outcome.ABORTED, null, null);
            return false;
        }
        for (Participant participant : session.participants()) {
            ServerPlayer player = session.player(participant);
            if (player == null) {
                leave(participant.id(), true);
                return false;
            }
            if (!inRange(player, session)) {
                leave(participant.id(), false);
                return false;
            }
        }
        return true;
    }

    private static boolean inRange(ServerPlayer player, TrainingSession session) {
        if (session.carry() != null) {
            return true;
        }
        double max = BhGameConfig.maxDistance;
        return player.level().dimension() == session.dimension()
                && player.distanceToSqr(Vec3.atCenterOf(session.pos())) <= max * max;
    }

    private SessionManager() {
    }
}
