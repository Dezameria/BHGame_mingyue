package net.deizyn.bhgame.core;

/**
 * Fixed values that are not worth a config option. Everything an admin may tune is in
 * {@link net.deizyn.bhgame.core.config.BhGameConfig}.
 */
public final class Tuning {
    /** Calm-down period before the first cue (no countdown on screen). */
    public static final int FOCUS_TICKS = 40;
    /** World input (attack/use) stays blocked this long after a session ends. */
    public static final int POST_SESSION_COOLDOWN_TICKS = 10;
    public static final int SWEEP_INTERVAL_TICKS = 100;
    /** Client-side brightening before a cue window opens (purely visual). */
    public static final int CUE_RAMP_TICKS = 6;
    /** Roles per session; cue role masks are one byte. */
    public static final int MAX_PARTICIPANTS = 4;

    private Tuning() {
    }
}
