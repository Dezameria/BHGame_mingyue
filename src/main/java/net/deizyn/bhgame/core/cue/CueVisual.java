package net.deizyn.bhgame.core.cue;

/** Hints the client knows how to draw. Add new ones at the end so ids stay stable. */
public enum CueVisual {
    /** Neutral breathing light (pulse_grass, twin_grass). */
    GLOW,
    /** Cold blue aura: yin (polarity_grass, answered with LMB). */
    YIN,
    /** Warm gold aura: yang (polarity_grass, answered with RMB). */
    YANG,
    /** White-blue spark running up the stem (thunder_grass). */
    SPARK,
    /** Purple decoy spark: do not press (thunder_grass FEINT). */
    SPARK_FAKE,
    /** A row of brush arrows in front of the object (wooden_dummy SEQUENCE). */
    ARROWS,
    /** The strike point of a SEQUENCE, answered with SPACE (wooden_dummy finisher). */
    FINISHER,
    /** Like ARROWS, but a wrong press ends the set instead of restarting the row (wooden_dummy, D39). */
    ARROWS_STRICT;

    private static final CueVisual[] VALUES = values();

    public static CueVisual byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : GLOW;
    }
}
