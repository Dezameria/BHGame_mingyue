package net.deizyn.bhgame.core.pattern;

/** Things a carrier's body does that a carried game reacts to at once (D49). */
public enum CarryEvent {
    /** Landed after a jump. */
    LAND,
    /** Fell more than 2 blocks. */
    HARD_LAND,
    /** Stepped down a block without jumping. */
    STEP_DOWN,
    /** Took damage. */
    HURT
}
