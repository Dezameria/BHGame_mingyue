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
    ARROWS_STRICT,
    /** The yoke dips on the carrier's left; answered with RMB on the other side (water_yoke, D52). */
    TILT_LEFT,
    /** The yoke dips on the carrier's right; answered with LMB. */
    TILT_RIGHT,
    /** A gust ripples the water, the yoke stays level: do not press (water_yoke FEINT). */
    RIPPLE,
    /** The orb gathers and bursts at you; answered with SPACE (qi_station). */
    QI_BURST,
    /** The orb gathers, then lets go: do not press (qi_station FEINT, the frenzied orb only). */
    QI_FEINT;

    private static final CueVisual[] VALUES = values();

    public static CueVisual byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : GLOW;
    }
}
