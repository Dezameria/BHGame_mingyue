package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.Tuning;
import net.deizyn.bhgame.core.block.TrainingBlock;
import net.deizyn.bhgame.core.block.TrainingBlockEntity;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.outcome.Stats;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.S2CPlayerPose;
import net.deizyn.bhgame.network.S2CSessionEnd;
import net.deizyn.bhgame.network.S2CSessionStart;
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
        long now = level.getServer().getTickCount();
        UUID lock = blockEntity.user();
        if (lock != null) {
            TrainingSession existing = BY_PLAYER.get(lock);
            if (existing == null || !existing.pos().equals(pos)) {
                // a stale lock can only come from a bug; never let it block the herb forever
                blockEntity.setUser(null);
            } else {
                if (existing.state() == SessionState.WAITING && !existing.isFull()) {
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
            session.startWaiting(now);
            BhGameNetwork.sendTo(player, startMessage(session, session.participants().get(0), true));
            BhGameNetwork.sendToTrackingAndSelf(player, new S2CPlayerPose(player.getId(), game.pose()));
            player.displayClientMessage(Component.translatable(game.waitingHintKey()), true);
        }
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
            snapToSide(player, session);
            BhGameNetwork.sendTo(player, startMessage(session, participant, false));
            BhGameNetwork.sendToTrackingAndSelf(player, new S2CPlayerPose(player.getId(), session.game().pose()));
            player.displayClientMessage(Component.translatable(session.game().hintKey()), true);
        }
    }

    /**
     * D42: stand the player {@code standDistance} from the block on the nearest free side of the four, facing it.
     * A side is free when the player's standing box fits there and there is ground under it.
     */
    private static void snapToSide(ServerPlayer player, TrainingSession session) {
        double distance = session.game().standDistance();
        ServerLevel level = player.serverLevel();
        if (distance <= 0.0 || level.dimension() != session.dimension()) {
            return;
        }
        Vec3 centre = Vec3.atBottomCenterOf(session.pos());
        Direction best = null;
        double bestSqr = Double.MAX_VALUE;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Vec3 spot = centre.add(side.getStepX() * distance, 0.0, side.getStepZ() * distance);
            double sqr = player.position().distanceToSqr(spot);
            if (sqr < bestSqr && fits(level, player, spot)) {
                best = side;
                bestSqr = sqr;
            }
        }
        if (best == null) {
            return;
        }
        Vec3 spot = centre.add(best.getStepX() * distance, 0.0, best.getStepZ() * distance);
        float yaw = best.getOpposite().toYRot();
        player.connection.teleport(spot.x, spot.y, spot.z, yaw, 0.0F);
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
            if (session.pos().equals(pos) && session.dimension() == level.dimension()) {
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
        if (level != null && level.isLoaded(session.pos())
                && level.getBlockEntity(session.pos()) instanceof TrainingBlockEntity found) {
            blockEntity = found;
            if (session.hostId().equals(found.user())) {
                found.setUser(null);
            }
        }

        long ticks = server != null ? server.getTickCount() - session.startTick() : 0;
        List<ServerPlayer> stayed = new ArrayList<>(session.participants().size());
        ServerPlayer culpritPlayer = null;
        for (Participant participant : session.participants()) {
            ServerPlayer player = session.player(participant);
            if (player == null) {
                continue;
            }
            boolean isCulprit = participant.id().equals(culprit);
            Outcome own = isCulprit ? Outcome.FAIL : outcome;
            Stats.record(player, session.game().gameId(), own, participant.mistakes(), ticks, session.pos(), false);
            if (session.bestObjective() != null) {
                Stats.recordBest(player, session.bestObjective(), session.bestValue());
            }
            if (participant.id().equals(disconnecting)) {
                continue;
            }
            BhGameNetwork.sendTo(player, new S2CSessionEnd(own));
            BhGameNetwork.sendToTrackingAndSelf(player, new S2CPlayerPose(player.getId(), PlayerPose.NONE));
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
            }
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
        } else if (outcome.isWin()) {
            session.game().onWin(level, session.pos(), state, stayed, outcome, blockEntity);
        } else if (outcome == Outcome.FAIL) {
            session.game().onFail(level, session.pos(), state, stayed, session.failPenalty(), blockEntity);
        }
    }

    private static void endNoPartner(TrainingSession session) {
        finish(session, Outcome.ABORTED, null, null);
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
        if (level == null || !level.isLoaded(session.pos())
                || !(level.getBlockEntity(session.pos()) instanceof TrainingBlockEntity blockEntity)
                || !session.hostId().equals(blockEntity.user())) {
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
        double max = BhGameConfig.maxDistance;
        return player.level().dimension() == session.dimension()
                && player.distanceToSqr(Vec3.atCenterOf(session.pos())) <= max * max;
    }

    private SessionManager() {
    }
}
