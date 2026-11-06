package net.deizyn.bhgame.game.mob.sparring_dummy;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * หุ่นฝึก: a humanoid mob that attacks its target with a list of Epic Fight animations set by command (D73). It is a
 * plain mob, not a minigame session: Epic Fight plays the moves on the server, so they hit for real.
 * <p>
 * Config lives in NBT ({@code Sparring}), so {@code /summon} and {@code /data} work as well as {@code /bhgame sparring}
 * and the sparring baton (D74). Conditional patterns, play speed and chase modes: D75. Stopping at low target HP and
 * event commands: D79.
 */
public class SparringDummy extends PathfinderMob {
    /** Used while no move is set: Epic Fight's three fist attacks. */
    public static final List<SparringMove> DEFAULT_MOVES = List.of(
            new SparringMove(new ResourceLocation("epicfight", "biped/combat/fist_auto1"), 0F),
            new SparringMove(new ResourceLocation("epicfight", "biped/combat/fist_auto2"), 0F),
            new SparringMove(new ResourceLocation("epicfight", "biped/combat/fist_auto3"), 0F));
    public static final int MAX_MOVES = 32;
    public static final int MAX_PAUSE = 200;
    public static final float MAX_RANGE = 16F;
    public static final float MIN_RUN_SPEED = 1F;
    public static final float MAX_RUN_SPEED = 3F;
    /** AUTO starts running when the target is farther than this, and walks again once it is closer than the next. */
    public static final double AUTO_RUN_DISTANCE = 6D;
    public static final double AUTO_WALK_DISTANCE = 4.5D;
    /** "Stop below" goes up to this % of the target's max HP; 0 = never stop. */
    public static final float MAX_STOP_BELOW = 95F;
    /** Longest name the baton screen or {@code name} command sets. */
    public static final int MAX_NAME = 48;
    /** Event commands run at this level, like a command block. */
    public static final int COMMAND_PERMISSION = 2;
    /** A dummy's command may set off one more dummy's command, no deeper (no loops between dummies). */
    private static final int MAX_COMMAND_DEPTH = 2;
    /** An invulnerable dummy still counts a hit this often at most (half of vanilla's hurt cooldown). */
    private static final int FELT_HIT_INTERVAL = 10;
    private static int commandDepth;

    /** Play speed of the move being played, for the Epic Fight mixin on both sides. */
    private static final EntityDataAccessor<Float> DATA_MOVE_SPEED =
            SynchedEntityData.defineId(SparringDummy.class, EntityDataSerializers.FLOAT);

    private static final String TAG_ROOT = "Sparring";
    private static final int FIXED_TARGET_INTERVAL = 10;

    public enum Order {SEQUENCE, RANDOM}

    /** How it gets to a target out of range: not at all, walking, running, or running only while far. */
    public enum ChaseMode {STAY, WALK, RUN, AUTO}

    /** Who it goes after: the nearest player, one entity set by command, or only whoever hits it. */
    public enum TargetMode {NEAREST_PLAYER, FIXED, NONE}

    private final List<SparringMove> moves = new ArrayList<>();
    private Order order = Order.SEQUENCE;
    private int pauseTicks = 10;
    private float attackRange = 2.5F;
    private ChaseMode chaseMode = ChaseMode.WALK;
    private float runSpeed = 1.5F;
    private float animationSpeed = 1F;
    private final List<SparringPattern> patterns = new ArrayList<>();
    /** Next move of each pattern in order; the last slot is the default moves. */
    private final int[] nextMoves = new int[SparringPattern.MAX_PATTERNS + 1];
    /** One roll per pattern, drawn again after every move (CHANCE). */
    private final float[] rolls = new float[SparringPattern.MAX_PATTERNS];
    private int lastPattern = -1;
    private TargetMode targetMode = TargetMode.NEAREST_PLAYER;
    @Nullable
    private UUID fixedTarget;
    private float stopBelow;
    private SparringCommands commands = SparringCommands.NONE;
    /** The target it stopped attacking because its HP fell under {@link #stopBelow}, until it heals to full. */
    @Nullable
    private UUID heldBackFrom;
    private int lastFeltHit = Integer.MIN_VALUE / 2;

    public SparringDummy(EntityType<? extends SparringDummy> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0F); // weapons given by command must not turn into free items
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MOVE_SPEED, 1F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.ATTACK_DAMAGE, 4D)
                .add(Attributes.FOLLOW_RANGE, 24D);
    }

    @Override
    protected void registerGoals() {
        // runs inside the Mob constructor, before this class's fields are set: the lambdas read them later
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SparringAttackGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return targetMode != TargetMode.FIXED && super.canUse();
            }
        });
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, null) {
            @Override
            public boolean canUse() {
                // other modes never search for players (no scan, no line-of-sight raycast)
                return targetMode == TargetMode.NEAREST_PLAYER && super.canUse();
            }
        });
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (targetMode == TargetMode.FIXED && fixedTarget != null && tickCount % FIXED_TARGET_INTERVAL == 0
                && level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(fixedTarget);
            LivingEntity wanted = entity instanceof LivingEntity living && living.isAlive() && living != this
                    ? living : null;
            if (getTarget() != wanted) {
                setTarget(wanted);
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (level().isClientSide || !(source.getEntity() instanceof LivingEntity attacker) || attacker == this) {
            return hurt;
        }
        if (!hurt && isInvulnerable() && tickCount - lastFeltHit >= FELT_HIT_INTERVAL) {
            // vanilla stops before noting the attacker: still let "got hit", "hit back only" and On hurt see it
            lastFeltHit = tickCount;
            setLastHurtByMob(attacker);
            runCommand(SparringCommands.Event.HURT, attacker);
        } else if (hurt) {
            runCommand(SparringCommands.Event.HURT, attacker);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && !isRemoved() && !dead) {
            LivingEntity killer = source.getEntity() instanceof LivingEntity living ? living : getKillCredit();
            runCommand(SparringCommands.Event.DEATH, killer);
        }
        super.die(source);
    }

    // ---- name -----------------------------------------------------------------------------------------------------

    /** The custom name as the baton screen edits it: {@code &} colour codes instead of {@code §}; "" for none. */
    public String editableName() {
        Component name = getCustomName();
        return name == null ? "" : name.getString().replace('\u00a7', '&');
    }

    /**
     * Sets the name shown over the dummy ({@code &6}, {@code &l} ... colour and style codes work), or removes it when
     * blank. {@code always}: shown all the time, else only while looked at (vanilla name tag behaviour).
     */
    public void setEditableName(String text, boolean always) {
        String clean = cleanName(text);
        if (clean.isEmpty()) {
            setCustomName(null);
            setCustomNameVisible(false);
            return;
        }
        setCustomName(Component.literal(clean.replaceAll("(?i)&([0-9a-fk-or])", "\u00a7$1")));
        setCustomNameVisible(always);
    }

    public static String cleanName(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace('\n', ' ').replace('\r', ' ').replace('\u00a7', '&').strip();
        return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME).strip() : clean;
    }

    // ---- stop at low HP, commands (D79) ----------------------------------------------------------------------------

    /**
     * True while it should leave {@code target} alone: its HP fell under {@link #stopBelow} % (runs On target low HP
     * once), until it heals back to full. Checked by the attack goal, so the dummy then stands still and only looks.
     */
    public boolean holdsBack(LivingEntity target) {
        if (stopBelow <= 0F) {
            heldBackFrom = null;
            return false;
        }
        if (target.getUUID().equals(heldBackFrom)) {
            if (target.getHealth() < target.getMaxHealth()) {
                return true;
            }
            heldBackFrom = null;
            return false;
        }
        if (target.getHealth() < target.getMaxHealth() * stopBelow / 100F) {
            heldBackFrom = target.getUUID();
            runCommand(SparringCommands.Event.TARGET_LOW, target);
            return true;
        }
        return false;
    }

    /** Runs this event's command, if any, as the dummy (see {@link SparringCommands}). */
    public void runCommand(SparringCommands.Event event, @Nullable LivingEntity about) {
        String line = commands.get(event);
        if (line.isEmpty() || commandDepth >= MAX_COMMAND_DEPTH || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (line.contains(SparringCommands.TARGET)) {
            if (about == null) {
                return;
            }
            line = line.replace(SparringCommands.TARGET, about.getStringUUID());
        }
        CommandSourceStack source = createCommandSourceStack().withPermission(COMMAND_PERMISSION)
                .withSuppressedOutput();
        commandDepth++;
        try {
            serverLevel.getServer().getCommands().performPrefixedCommand(source, line);
        } finally {
            commandDepth--;
        }
    }

    /** % of the target's max HP under which it stops attacking; 0 = never. */
    public float stopBelow() {
        return stopBelow;
    }

    public void setStopBelow(float stopBelow) {
        this.stopBelow = clampStopBelow(stopBelow);
        heldBackFrom = null;
    }

    public static float clampStopBelow(float percent) {
        return Float.isFinite(percent) ? Math.max(0F, Math.min(MAX_STOP_BELOW, percent)) : 0F;
    }

    public SparringCommands commands() {
        return commands;
    }

    public void setCommands(SparringCommands commands) {
        this.commands = commands;
    }

    // ---- moves -----------------------------------------------------------------------------------------------------

    /** The configured moves, or {@link #DEFAULT_MOVES} while none is set. */
    public List<SparringMove> activeMoves() {
        return moves.isEmpty() ? DEFAULT_MOVES : Collections.unmodifiableList(moves);
    }

    public List<SparringMove> moves() {
        return Collections.unmodifiableList(moves);
    }

    public boolean addMove(SparringMove move) {
        if (moves.size() >= MAX_MOVES) {
            return false;
        }
        moves.add(move);
        return true;
    }

    /** {@code index} counts from 0; returns the removed move or null. */
    @Nullable
    public SparringMove removeMove(int index) {
        if (index < 0 || index >= moves.size()) {
            return null;
        }
        SparringMove removed = moves.remove(index);
        resetCombo();
        return removed;
    }

    public void setMoves(List<SparringMove> newMoves) {
        moves.clear();
        moves.addAll(newMoves.subList(0, Math.min(newMoves.size(), MAX_MOVES)));
        resetCombo();
    }

    // ---- patterns --------------------------------------------------------------------------------------------------

    public List<SparringPattern> patterns() {
        return Collections.unmodifiableList(patterns);
    }

    public void setPatterns(List<SparringPattern> newPatterns) {
        patterns.clear();
        patterns.addAll(newPatterns.subList(0, Math.min(newPatterns.size(), SparringPattern.MAX_PATTERNS)));
        resetCombo();
    }

    /** Index used by {@link #nextMove} for the default moves. */
    public int defaultPattern() {
        return patterns.size();
    }

    /**
     * Which move set to use against {@code target} now: the first pattern with moves whose condition holds and whose
     * range reaches, else the default moves if they reach, else -1 (get closer).
     */
    public int choosePattern(LivingEntity target) {
        double distance = Math.sqrt(distanceToSqr(target));
        for (int i = 0; i < patterns.size(); i++) {
            SparringPattern pattern = patterns.get(i);
            if (!pattern.moves().isEmpty() && distance <= pattern.range()
                    && pattern.test(this, target, distance, rolls[i])) {
                return i;
            }
        }
        return distance <= attackRange ? defaultPattern() : -1;
    }

    /** The move to play now from that move set: in order, or at random. A new move set starts from its first move. */
    public SparringMove nextMove(int pattern) {
        List<SparringMove> list;
        Order listOrder;
        if (pattern >= 0 && pattern < patterns.size() && !patterns.get(pattern).moves().isEmpty()) {
            list = patterns.get(pattern).moves();
            listOrder = patterns.get(pattern).order();
        } else {
            pattern = defaultPattern();
            list = activeMoves();
            listOrder = order;
        }
        if (pattern != lastPattern) {
            nextMoves[pattern] = 0;
            lastPattern = pattern;
        }
        for (int i = 0; i < rolls.length; i++) {
            rolls[i] = getRandom().nextFloat();
        }
        if (listOrder == Order.RANDOM) {
            return list.get(getRandom().nextInt(list.size()));
        }
        if (nextMoves[pattern] >= list.size()) {
            nextMoves[pattern] = 0;
        }
        return list.get(nextMoves[pattern]++);
    }

    /** Every move set starts from its first move again (target lost, list changed). */
    public void resetCombo() {
        Arrays.fill(nextMoves, 0);
        lastPattern = -1;
    }

    /** Set on the server one tick before the move plays, so clients have it when the animation starts. */
    public void setMoveSpeed(float speed) {
        entityData.set(DATA_MOVE_SPEED, speed);
    }

    /** Play speed of the current move (dummy speed x move speed); read by the Epic Fight mixin on both sides. */
    public float moveSpeed() {
        return entityData.get(DATA_MOVE_SPEED);
    }

    // ---- settings --------------------------------------------------------------------------------------------------

    public Order order() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
        resetCombo();
    }

    /** Ticks to wait after a move ends before the next one. */
    public int pauseTicks() {
        return pauseTicks;
    }

    public void setPauseTicks(int pauseTicks) {
        this.pauseTicks = Math.max(0, Math.min(MAX_PAUSE, pauseTicks));
    }

    /** The default moves start once the target is this close (blocks, centre to centre). */
    public float attackRange() {
        return attackRange;
    }

    public void setAttackRange(float attackRange) {
        this.attackRange = clampRange(attackRange);
    }

    public static float clampRange(float range) {
        return Float.isFinite(range) ? Math.max(0.5F, Math.min(MAX_RANGE, range)) : 2.5F;
    }

    public ChaseMode chaseMode() {
        return chaseMode;
    }

    public void setChaseMode(ChaseMode chaseMode) {
        this.chaseMode = chaseMode;
        if (chaseMode == ChaseMode.STAY) {
            getNavigation().stop();
            setSprinting(false);
        }
    }

    /** Command shorthand: true = walk, false = stay. */
    public void setChase(boolean chase) {
        setChaseMode(chase ? ChaseMode.WALK : ChaseMode.STAY);
    }

    /** Multiplier on the walk speed while running. */
    public float runSpeed() {
        return runSpeed;
    }

    public void setRunSpeed(float runSpeed) {
        this.runSpeed = Float.isFinite(runSpeed) ? Math.max(MIN_RUN_SPEED, Math.min(MAX_RUN_SPEED, runSpeed)) : 1.5F;
    }

    /** Play speed of every move, multiplied by the move's own. */
    public float animationSpeed() {
        return animationSpeed;
    }

    public void setAnimationSpeed(float animationSpeed) {
        this.animationSpeed = SparringMove.clampSpeed(animationSpeed);
    }

    public TargetMode targetMode() {
        return targetMode;
    }

    @Nullable
    public UUID fixedTarget() {
        return fixedTarget;
    }

    public void targetNearestPlayer() {
        targetMode = TargetMode.NEAREST_PLAYER;
        fixedTarget = null;
        setTarget(null);
    }

    public void targetNobody() {
        targetMode = TargetMode.NONE;
        fixedTarget = null;
        setTarget(null);
    }

    public void targetFixed(LivingEntity target) {
        targetMode = TargetMode.FIXED;
        fixedTarget = target.getUUID();
        setTarget(target);
    }

    // ---- NBT -------------------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        moves.forEach(move -> list.add(move.save()));
        root.put("Moves", list);
        root.putString("Order", order.name().toLowerCase(Locale.ROOT));
        root.putInt("Pause", pauseTicks);
        root.putFloat("Range", attackRange);
        root.putString("ChaseMode", chaseMode.name().toLowerCase(Locale.ROOT));
        root.putFloat("RunSpeed", runSpeed);
        root.putFloat("AnimSpeed", animationSpeed);
        if (!patterns.isEmpty()) {
            ListTag patternList = new ListTag();
            patterns.forEach(pattern -> patternList.add(pattern.save()));
            root.put("Patterns", patternList);
        }
        root.putString("TargetMode", targetMode.name().toLowerCase(Locale.ROOT));
        if (fixedTarget != null) {
            root.putUUID("Target", fixedTarget);
        }
        if (stopBelow > 0F) {
            root.putFloat("StopBelow", stopBelow);
        }
        if (!commands.isEmpty()) {
            root.put("Commands", commands.save());
        }
        tag.put(TAG_ROOT, root);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag root = tag.getCompound(TAG_ROOT);
        if (root.contains("Moves", Tag.TAG_LIST)) {
            List<SparringMove> loaded = new ArrayList<>();
            ListTag list = root.getList("Moves", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                SparringMove move = SparringMove.load(list.getCompound(i));
                if (move != null) {
                    loaded.add(move);
                }
            }
            setMoves(loaded);
        }
        if (root.contains("Order")) {
            order = parse(Order.class, root.getString("Order"), Order.SEQUENCE);
        }
        if (root.contains("Pause")) {
            setPauseTicks(root.getInt("Pause"));
        }
        if (root.contains("Range")) {
            setAttackRange(root.getFloat("Range"));
        }
        if (root.contains("ChaseMode")) {
            chaseMode = parse(ChaseMode.class, root.getString("ChaseMode"), ChaseMode.WALK);
        } else if (root.contains("Chase")) {
            chaseMode = root.getBoolean("Chase") ? ChaseMode.WALK : ChaseMode.STAY; // saved before D75
        }
        if (root.contains("RunSpeed")) {
            setRunSpeed(root.getFloat("RunSpeed"));
        }
        if (root.contains("AnimSpeed")) {
            setAnimationSpeed(root.getFloat("AnimSpeed"));
        }
        if (root.contains("Patterns", Tag.TAG_LIST)) {
            List<SparringPattern> loaded = new ArrayList<>();
            ListTag list = root.getList("Patterns", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                loaded.add(SparringPattern.load(list.getCompound(i)));
            }
            setPatterns(loaded);
        }
        if (root.contains("TargetMode")) {
            targetMode = parse(TargetMode.class, root.getString("TargetMode"), TargetMode.NEAREST_PLAYER);
        }
        fixedTarget = root.hasUUID("Target") ? root.getUUID("Target") : null;
        setStopBelow(root.getFloat("StopBelow"));
        commands = SparringCommands.load(root.getCompound("Commands"));
        if (targetMode == TargetMode.FIXED && fixedTarget == null) {
            targetMode = TargetMode.NONE;
        }
    }

    static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return fallback;
        }
    }
}
