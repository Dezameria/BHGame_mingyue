package net.deizyn.bhgame.client;

import net.deizyn.bhgame.core.Tuning;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.cue.CueResult;
import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.session.TrainingPins;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's copy of its own session (at most one). Time runs on {@link #clock}, a counter of client ticks,
 * so cue windows stay smooth even when the server's world time resyncs.
 */
public final class ClientSession {
    private static long clock;
    private static int cooldownTicks;
    @Nullable
    private static ClientSession current;

    /** A cue placed on the local clock. */
    public record LocalCue(CueData data, long open, long close) {
    }

    /** How bright a partner's cue shows compared with your own (it helps you keep time together, D31). */
    private static final float PARTNER_CUE_BRIGHTNESS = 0.35F;
    private static final float IDLE_GLOW = 0.12F;
    private static final int GAUGES = 4;
    /** A sync that disagrees with the predicted needle by less than this blends in over {@link #BLEND_TICKS}. */
    private static final float SNAP = 0.12F;
    private static final int BLEND_TICKS = 5;

    public final BlockPos pos;
    public final String gameId;
    public final int maxHp;
    public final long startTick;
    public final float startYaw;
    public final float startPitch;
    public InputProfile profile;
    /** This client's role (join order) and how many players the game has. */
    public int role;
    public int players;
    /** Camera target above the block's base. */
    public float lookHeight = 0.35F;
    /** FREE_LOOK (D66): how far the mouse turned the camera away from the body, in degrees. */
    float lookYawOffset;
    float lookPitchOffset;
    public CueChannel channel = CueChannel.BLOCK;
    private boolean waiting;
    private long focusEndTick;
    private final List<LocalCue> cues = new ArrayList<>();
    private int hp;
    private int lastSoundCueId = -1;
    /** Visual of the brightest cue found by the last {@link #glow} call. */
    private CueVisual glowVisual = CueVisual.GLOW;

    // gauges: value + rate from the last sync, animated on the local clock
    private final float[] gaugeValue = new float[GAUGES];
    private final float[] gaugeRate = new float[GAUGES];
    private final long[] gaugeSetAt = new long[GAUGES];
    private final int[] gaugeFlags = new int[GAUGES];
    private final boolean[] gaugeKnown = new boolean[GAUGES];
    // balance gauges move for our own held keys at once (D57); a sync that disagrees a little fades in
    private final float[] gaugeKeyRate = new float[GAUGES];
    private final float[] gaugeCorrection = new float[GAUGES];
    private final long[] gaugeCorrectedAt = new long[GAUGES];
    private int heldKeys;

    // the SEQUENCE being played, predicted locally with the server's rules (SequenceTrack); the server decides
    @Nullable
    private LocalCue sequence;
    @Nullable
    private LocalCue finisher;
    private int sequenceProgress;
    private boolean sequenceComplete;
    private boolean finisherUsed;
    private boolean sequenceResolved;
    /** Strict row (ARROWS_STRICT) after a wrong press: frozen until the server ends the session. */
    private boolean sequenceBroken;
    private long sequenceShakeUntil;

    @Nullable
    private Component banner;
    private long bannerStart;

    private ClientSession(BlockPos pos, String gameId, InputProfile profile, int maxHp, float startYaw,
                          float startPitch) {
        this.pos = pos;
        this.gameId = gameId;
        this.profile = profile;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.startTick = clock;
        this.startYaw = startYaw;
        this.startPitch = startPitch;
    }

    /**
     * A start packet: a new session, or (same block, after waiting) the partner arrived and the game begins.
     * The camera keeps turning from where it started.
     */
    static void start(BlockPos pos, String gameId, InputProfile profile, int maxHp, int focusTicks, int role,
                      int players, boolean waiting, float lookHeight, CueChannel channel, float yaw, float pitch) {
        ClientSession session = current;
        if (session == null || !session.pos.equals(pos)) {
            session = new ClientSession(pos, gameId, profile, maxHp, yaw, pitch);
            current = session;
        }
        session.profile = profile;
        session.role = role;
        session.players = players;
        session.waiting = waiting;
        session.lookHeight = lookHeight;
        session.channel = channel;
        session.focusEndTick = waiting ? Long.MAX_VALUE : clock + focusTicks;
    }

    static void end() {
        TrainingPins.setClientPinned(null);
        if (current != null) {
            current = null;
            cooldownTicks = Tuning.POST_SESSION_COOLDOWN_TICKS;
        }
    }

    static void tickClock() {
        clock++;
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
    }

    @Nullable
    public static ClientSession get() {
        return current;
    }

    public static boolean isActive() {
        return current != null;
    }

    /** True while a session runs or just ended, so a held click can't hit the world (D27). */
    public static boolean blocksWorldInput() {
        return current != null || cooldownTicks > 0;
    }

    public static long clock() {
        return clock;
    }

    public static boolean isActiveAt(BlockPos pos) {
        return current != null && current.pos.equals(pos);
    }

    void addCues(List<CueData> incoming) {
        long now = clock;
        cues.removeIf(cue -> cue.close < now - 40);
        for (CueData data : incoming) {
            long open = now + data.opensIn();
            LocalCue cue = new LocalCue(data, open, open + data.window());
            cues.add(cue);
            if (data.type() == CueType.SEQUENCE) {
                sequence = cue;
                finisher = null;
                sequenceProgress = 0;
                sequenceComplete = false;
                finisherUsed = false;
                sequenceResolved = false;
                sequenceBroken = false;
            } else if (data.visual() == CueVisual.FINISHER) {
                finisher = cue;
            }
        }
    }

    void setHp(int hp) {
        this.hp = hp;
    }

    /** The server's verdict on a cue; ends the shown SEQUENCE when it is the one judged. */
    void onResult(int cueId, CueResult result) {
        if (sequence != null && sequence.data().id() == cueId) {
            sequenceResolved = true;
        }
    }

    /**
     * A gauge sync. {@code ratePerTick} leaves out the held keys; a gauge with a {@code keyRate} adds our own keys
     * here, and since the sync is {@code leadTicks} old (half a ping) it is moved forward to now first (D57).
     */
    void setGauge(int index, int flags, float value, float ratePerTick, float keyRate, float leadTicks) {
        if (index < 0 || index >= GAUGES) {
            return;
        }
        boolean predicted = keyRate > 0.0F && (flags & Gauge.FLAG_BALANCE) != 0;
        float shown = gaugeKnown[index] ? gauge(index, clock) : value;
        gaugeRate[index] = ratePerTick;
        gaugeKeyRate[index] = predicted ? keyRate : 0.0F;
        gaugeValue[index] = predicted ? clamp(value + (ratePerTick + keyPart(index)) * leadTicks) : value;
        gaugeSetAt[index] = clock;
        gaugeFlags[index] = flags;
        float error = shown - gaugeValue[index];
        // small differences glide over; jolts, spills and the first sync jump straight there
        gaugeCorrection[index] = predicted && gaugeKnown[index] && Math.abs(error) < SNAP ? error : 0.0F;
        gaugeCorrectedAt[index] = clock;
        gaugeKnown[index] = true;
    }

    public float gauge(int index, double time) {
        float value = raw(index, time);
        float fade = 1.0F - (float) (time - gaugeCorrectedAt[index]) / BLEND_TICKS;
        if (fade > 0.0F) {
            value += gaugeCorrection[index] * fade;
        }
        return clamp(value);
    }

    /** Where a gauge is from its last sync and our own keys, without the blend. */
    private float raw(int index, double time) {
        return clamp(gaugeValue[index] + (gaugeRate[index] + keyPart(index)) * (float) (time - gaugeSetAt[index]));
    }

    /** Whether we hold {@code key} right now (hold keys only), without waiting for the server. */
    public boolean holdsLocally(InputKey key) {
        return (heldKeys & key.bit()) != 0;
    }

    /** Our held keys' push on a balance gauge: LMB toward 0, RMB toward 1. */
    private float keyPart(int index) {
        float rate = gaugeKeyRate[index];
        if (rate == 0.0F) {
            return 0.0F;
        }
        return ((heldKeys & InputKey.RMB.bit()) != 0 ? rate : 0.0F) - ((heldKeys & InputKey.LMB.bit()) != 0 ? rate : 0.0F);
    }

    /** A hold key of ours went down or up: predicted gauges change speed from this tick on. */
    void onLocalHold(InputKey key, boolean down) {
        for (int i = 0; i < GAUGES; i++) {
            if (gaugeKeyRate[i] != 0.0F) {
                gaugeValue[i] = raw(i, clock);
                gaugeSetAt[i] = clock;
            }
        }
        heldKeys = down ? heldKeys | key.bit() : heldKeys & ~key.bit();
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    /** Index of the first gauge carrying {@code flag} that has been synced, or -1. */
    public int gaugeWithFlag(int flag) {
        for (int i = 0; i < GAUGES; i++) {
            if (gaugeKnown[i] && (gaugeFlags[i] & flag) != 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 0 at the start, 1 at the end; drives "the herb rises out of the soil". From the progress gauge when the game
     * has one (thunder_grass), otherwise from HP.
     */
    public float progress(double time) {
        for (int i = 0; i < GAUGES; i++) {
            if (gaugeKnown[i] && (gaugeFlags[i] & Gauge.FLAG_PROGRESS) != 0) {
                return gauge(i, time);
            }
        }
        return maxHp <= 0 ? 0.0F : 1.0F - (float) hp / maxHp;
    }

    /** Also true while waiting for a partner: no cues, no input. */
    public boolean isFocusing() {
        return clock < focusEndTick;
    }

    public boolean isWaiting() {
        return waiting;
    }

    /** Every cue this client still remembers (drawn by game-specific renderers, e.g. the yoke's tilt). */
    public List<LocalCue> cues() {
        return cues;
    }

    public boolean isOwn(LocalCue cue) {
        return cue.data().isFor(role);
    }

    /** This client's own cue whose window is open at {@code time}, or null. */
    @Nullable
    public LocalCue openCue(double time) {
        for (LocalCue cue : cues) {
            if (isOwn(cue) && time >= cue.open && time < cue.close) {
                return cue;
            }
        }
        return null;
    }

    /**
     * Brightness of the block-channel cues at {@code time}: a faint idle glow, a ramp before the window, full while
     * it is open, then a short fade. Partners' cues show dimmed. {@link #glowVisual()} then tells which cue won.
     */
    public float glow(double time) {
        float best = IDLE_GLOW;
        CueVisual bestVisual = CueVisual.GLOW;
        for (LocalCue cue : cues) {
            if (cue.data().channel() != CueChannel.BLOCK) {
                continue;
            }
            float value;
            if (time >= cue.open - Tuning.CUE_RAMP_TICKS && time < cue.open) {
                value = IDLE_GLOW + 0.6F * (float) ((time - (cue.open - Tuning.CUE_RAMP_TICKS)) / Tuning.CUE_RAMP_TICKS);
            } else if (time >= cue.open && time < cue.close) {
                value = 1.0F;
            } else if (time >= cue.close && time < cue.close + 6) {
                value = 1.0F - (1.0F - IDLE_GLOW) * (float) ((time - cue.close) / 6.0);
            } else {
                continue;
            }
            if (!isOwn(cue)) {
                value = IDLE_GLOW + (value - IDLE_GLOW) * PARTNER_CUE_BRIGHTNESS;
            }
            if (value > best) {
                best = value;
                bestVisual = cue.data().visual();
            }
        }
        glowVisual = bestVisual;
        return best;
    }

    public CueVisual glowVisual() {
        return glowVisual;
    }

    /**
     * A block-channel cue of this player that just started and has not played its sound yet, or null. Sparks sound
     * when they start running up the stem (the ramp), everything else when the window opens.
     */
    @Nullable
    LocalCue takeOpeningSoundCue() {
        for (LocalCue cue : cues) {
            if (!isOwn(cue) || cue.data().channel() != CueChannel.BLOCK || cue.data().id() <= lastSoundCueId) {
                continue;
            }
            CueVisual visual = cue.data().visual();
            long start = visual == CueVisual.SPARK || visual == CueVisual.SPARK_FAKE
                    ? cue.open - Tuning.CUE_RAMP_TICKS
                    // the orb hums as it starts to gather (as soon as the cue arrives), not when it bursts
                    : visual == CueVisual.QI_BURST || visual == CueVisual.QI_FEINT ? arrival(cue) : cue.open;
            if (clock >= start && clock < cue.close) {
                lastSoundCueId = cue.data().id();
                return cue;
            }
        }
        return null;
    }

    /** The local tick this cue arrived on (its window opened {@code opensIn} ticks later). */
    public static long arrival(LocalCue cue) {
        return cue.open - cue.data().opensIn();
    }

    void showBanner(Component text) {
        banner = text;
        bannerStart = clock;
    }

    @Nullable
    public Component banner() {
        return banner;
    }

    /** Ticks since the banner appeared. */
    public double bannerAge(double time) {
        return time - bannerStart;
    }

    // ---- SEQUENCE (wooden_dummy) ----

    /** The arrow row on show, or null between sets. */
    @Nullable
    public LocalCue shownSequence(double time) {
        LocalCue set = sequence;
        if (set == null || sequenceResolved || time < set.open || (time >= set.close && !sequenceComplete)) {
            return null;
        }
        return set;
    }

    @Nullable
    public LocalCue finisher() {
        return finisher;
    }

    public int sequenceProgress() {
        return sequenceProgress;
    }

    public boolean sequenceComplete() {
        return sequenceComplete;
    }

    public boolean finisherUsed() {
        return finisherUsed;
    }

    /** A strict row that was broken by a wrong press; shown red until the session ends. */
    public boolean sequenceBroken() {
        return sequenceBroken;
    }

    public boolean sequenceShaking(double time) {
        return time < sequenceShakeUntil;
    }

    /** What a SEQUENCE press did on this client, for the instant punch, sound and effect. */
    public enum LocalPress {
        /** Nothing to show (no row, or a press that doesn't count). */
        NONE,
        /** A correct arrow. */
        STEP,
        /** SPACE on a complete row. */
        FINISHER,
        /** A wrong press: the row restarts, or in a strict row the dummy swats you. */
        WRONG
    }

    /**
     * Instant feedback for a SEQUENCE press, with the same rules as the server's SequenceTrack: right arrow →
     * next, wrong arrow or early SPACE → back to the first arrow (or broken, in a strict row), SPACE after a full
     * row → finisher.
     */
    LocalPress onLocalPress(InputKey key) {
        LocalCue set = shownSequence(clock);
        if (set == null || sequenceBroken) {
            return LocalPress.NONE;
        }
        byte[] steps = set.data().steps();
        if (key == InputKey.SPACE) {
            if (!sequenceComplete) {
                restartSequence();
                return LocalPress.WRONG;
            }
            if (!finisherUsed) {
                finisherUsed = true;
                return LocalPress.FINISHER;
            }
            return LocalPress.NONE;
        }
        if (sequenceComplete || !key.isArrow() || sequenceProgress >= steps.length) {
            return LocalPress.NONE;
        }
        byte step = steps[sequenceProgress];
        InputKey shown = InputKey.byId(CueData.stepKey(step));
        InputKey wanted = shown == null ? null : CueData.stepReversed(step) ? shown.opposite() : shown;
        if (key != wanted) {
            restartSequence();
            return LocalPress.WRONG;
        }
        sequenceProgress++;
        if (sequenceProgress == steps.length) {
            sequenceComplete = true;
        }
        return LocalPress.STEP;
    }

    /** A wrong press: back to the first arrow, or, in a strict row, broken for good (D39). */
    private void restartSequence() {
        LocalCue set = sequence;
        if (set != null && set.data().visual() == CueVisual.ARROWS_STRICT) {
            sequenceBroken = true;
        } else {
            sequenceProgress = 0;
        }
        sequenceShakeUntil = clock + 6;
    }
}
