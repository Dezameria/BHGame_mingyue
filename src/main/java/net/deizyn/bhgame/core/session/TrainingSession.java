package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.Tuning;
import net.deizyn.bhgame.core.block.TrainingBlock;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.cue.CueResult;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.S2CCue;
import net.deizyn.bhgame.network.S2CGaugeSync;
import net.deizyn.bhgame.network.S2CPlayerAction;
import net.deizyn.bhgame.network.S2CSessionBanner;
import net.deizyn.bhgame.network.S2CSessionUpdate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One round on one block, for one or more players (D31). Holds ids and positions only, never a ServerPlayer or
 * BlockEntity reference (docs/multiplayer-performance.md section 6). HP is shared, mistakes are counted per participant
 * and in total; none of it is ever shown as numbers.
 */
public final class TrainingSession implements PatternContext {
    private static int nextSerial;

    private final int serial = nextSerial++;
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private final TrainingBlock game;
    private final int difficulty;
    private final int maxHp;
    private final int maxMistakes;
    private final long startTick;
    private final boolean simulated;
    private final RandomSource random = RandomSource.create();
    private final List<Participant> participants = new ArrayList<>(2);
    private final List<Participant> participantsView = Collections.unmodifiableList(participants);

    private final Gauge[] gauges = new Gauge[4];
    @Nullable
    private Penalty failPenalty;
    @Nullable
    private String outcomeMessageKey;
    private int outcomeMessageValue;
    @Nullable
    private String bestObjective;
    private int bestValue;
    private Pattern pattern;
    private SessionState state;
    private long focusEndTick = Long.MAX_VALUE;
    private long waitDeadline;
    private int hp;
    private int mistakes;
    private long nextEventTick;
    private int nextCueId;
    /** Stress bots only: next tick a bot presses something. */
    private long botNextPress;

    TrainingSession(ResourceKey<Level> dimension, BlockPos pos, TrainingBlock game, int difficulty, int maxHp,
                    int maxMistakes, long startTick, boolean simulated) {
        this.dimension = dimension;
        this.pos = pos;
        this.game = game;
        this.difficulty = difficulty;
        this.maxHp = maxHp;
        this.maxMistakes = maxMistakes;
        this.startTick = startTick;
        this.simulated = simulated;
        this.hp = maxHp;
        this.state = SessionState.WAITING;
        this.nextEventTick = startTick + 1;
    }

    Participant join(UUID playerId) {
        int role = participants.size();
        Participant participant = new Participant(playerId, role, game.inputProfile(role), simulated);
        participants.add(participant);
        return participant;
    }

    boolean isFull() {
        return participants.size() >= game.requiredPlayers();
    }

    /** Everyone is here: start FOCUS and create the pattern. */
    void begin(long now) {
        state = SessionState.FOCUS;
        focusEndTick = now + Tuning.FOCUS_TICKS;
        pattern = game.createPattern(this);
        nextEventTick = now + 1;
        botNextPress = focusEndTick;
    }

    void startWaiting(long now) {
        waitDeadline = now + BhGameConfig.partnerWaitTicks;
        nextEventTick = waitDeadline;
    }

    @Nullable
    Participant participant(UUID playerId) {
        for (Participant participant : participants) {
            if (participant.id().equals(playerId)) {
                return participant;
            }
        }
        return null;
    }

    /** The player who clicked first; the block entity is locked with this id. */
    public UUID hostId() {
        return participants.get(0).id();
    }

    public int serial() {
        return serial;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos pos() {
        return pos;
    }

    public TrainingBlock game() {
        return game;
    }

    Pattern pattern() {
        return pattern;
    }

    public boolean simulated() {
        return simulated;
    }

    @Override
    public int difficulty() {
        return difficulty;
    }

    @Override
    public int hp() {
        return hp;
    }

    @Override
    public int maxHp() {
        return maxHp;
    }

    @Override
    public int mistakes() {
        return mistakes;
    }

    public long startTick() {
        return startTick;
    }

    @Override
    public long focusEndTick() {
        return focusEndTick;
    }

    long waitDeadline() {
        return waitDeadline;
    }

    long botNextPress() {
        return botNextPress;
    }

    void setBotNextPress(long tick) {
        botNextPress = tick;
    }

    @Override
    public RandomSource random() {
        return random;
    }

    @Override
    public List<Participant> participants() {
        return participantsView;
    }

    public SessionState state() {
        return state;
    }

    void setState(SessionState state) {
        this.state = state;
    }

    @Override
    public boolean isFinished() {
        return state == SessionState.FINISHED;
    }

    public long nextEventTick() {
        return nextEventTick;
    }

    void clearSchedule() {
        nextEventTick = Long.MAX_VALUE;
    }

    @Override
    public void scheduleAt(long tick) {
        if (tick < nextEventTick) {
            nextEventTick = tick;
        }
    }

    @Override
    public int newCueId() {
        return nextCueId++;
    }

    /** The online player behind {@code participant}, or null (left, or a stress bot). */
    @Nullable
    public ServerPlayer player(Participant participant) {
        if (participant.simulated()) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.getPlayerList().getPlayer(participant.id()) : null;
    }

    @Override
    public int graceTicks(Participant participant) {
        ServerPlayer player = player(participant);
        if (player == null) {
            return 0;
        }
        int ms = Math.min(Math.max(player.latency, 0), BhGameConfig.maxPingGraceMs);
        return (ms + 49) / 50;
    }

    @Override
    public int maxGraceTicks() {
        int grace = 0;
        for (Participant participant : participants) {
            grace = Math.max(grace, graceTicks(participant));
        }
        return grace;
    }

    /** Sends to every participant; stress bots only count as simulated traffic. */
    void sendToAll(Object message) {
        for (Participant participant : participants) {
            if (participant.simulated()) {
                BhGameNetwork.countSimulated();
                continue;
            }
            ServerPlayer player = player(participant);
            if (player != null) {
                BhGameNetwork.sendTo(player, message);
            }
        }
    }

    @Override
    public void sendCues(List<CueData> cues) {
        sendToAll(new S2CCue(cues));
    }

    @Override
    public void hit(int cueId, int damage) {
        if (isFinished()) {
            return;
        }
        hp = Math.max(0, hp - damage);
        sendToAll(new S2CSessionUpdate(cueId, CueResult.HIT, hp, mistakes));
        if (hp <= 0) {
            win();
        }
    }

    @Override
    public void win() {
        SessionManager.finish(this, mistakes == 0 ? Outcome.PERFECT : Outcome.SUCCESS, null, null);
    }

    /** A punch every 3 ticks at most still reads as a flurry, and caps the traffic to bystanders. */
    private static final int ACTION_MIN_GAP_TICKS = 3;

    @Override
    public void action(Participant who, PlayerAction action, @Nullable InputKey key) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !who.allowAction(server.getTickCount(), ACTION_MIN_GAP_TICKS)) {
            return;
        }
        if (who.simulated()) {
            BhGameNetwork.countSimulated();
            return;
        }
        ServerPlayer player = player(who);
        if (player != null) {
            BhGameNetwork.sendToTracking(player, new S2CPlayerAction(player.getId(), action, key));
        }
    }

    @Override
    public void banner(String translationKey, int value) {
        sendToAll(new S2CSessionBanner(translationKey, value));
    }

    @Override
    public void setOutcomeMessage(String translationKey, int value) {
        outcomeMessageKey = translationKey;
        outcomeMessageValue = value;
    }

    /** The pattern's outcome line, or null to use the game's. */
    @Nullable
    Component outcomeMessage() {
        return outcomeMessageKey == null ? null : Component.translatable(outcomeMessageKey, outcomeMessageValue);
    }

    @Override
    public void recordBest(String objective, int value) {
        bestObjective = objective;
        bestValue = value;
    }

    @Nullable
    String bestObjective() {
        return bestObjective;
    }

    int bestValue() {
        return bestValue;
    }

    @Override
    public void fail() {
        SessionManager.finish(this, Outcome.FAIL, null, null);
    }

    @Override
    public void setFailPenalty(Penalty penalty) {
        failPenalty = penalty;
    }

    Penalty failPenalty() {
        return failPenalty != null ? failPenalty : game.bigPenalty();
    }

    @Override
    public Gauge gauge(int index, int flags) {
        Gauge gauge = gauges[index];
        if (gauge == null) {
            gauge = new Gauge(index, flags);
            gauges[index] = gauge;
        }
        return gauge;
    }

    /** Sends changed gauges and schedules the next resync of moving ones. */
    void flushGauges(long now) {
        for (Gauge gauge : gauges) {
            if (gauge == null) {
                continue;
            }
            if (gauge.needsSync(now, BhGameConfig.gaugeResyncTicks)) {
                sendToAll(new S2CGaugeSync(gauge.index(), gauge.flags(), gauge.value(now), gauge.ratePerTick()));
                gauge.markSynced(now);
            }
            if (gauge.ratePerTick() != 0.0F) {
                scheduleAt(now + BhGameConfig.gaugeResyncTicks);
            }
        }
    }

    @Override
    public void miss(Participant who, int cueId) {
        miss(who, cueId, game.smallPenalty());
    }

    @Override
    public void miss(Participant who, int cueId, Penalty penalty) {
        if (isFinished()) {
            return;
        }
        who.addMistake();
        mistakes++;
        ServerPlayer player = player(who);
        if (player != null) {
            penalty.apply(player, pos);
        }
        sendToAll(new S2CSessionUpdate(cueId, CueResult.MISS, hp, mistakes));
        if (mistakes >= maxMistakes) {
            SessionManager.finish(this, Outcome.FAIL, null, null);
        }
    }
}
