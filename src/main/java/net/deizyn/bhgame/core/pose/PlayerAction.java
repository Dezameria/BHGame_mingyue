package net.deizyn.bhgame.core.pose;

/**
 * A one-shot movement that bystanders see on top of the looping {@link PlayerPose}, played on each client only
 * (D41). With Epic Fight these are its unarmed animations, played client-side so no attack ever hits anyone;
 * without it, a vanilla arm swing.
 */
public enum PlayerAction {
    /** A correct arrow on the wooden dummy (alternating sides). */
    PUNCH_RIGHT,
    PUNCH_LEFT,
    /** The finisher (SPACE) on the wooden dummy. */
    PUNCH_FINISH,
    /** Swatted by the dummy. */
    KNOCKED_BACK;

    private static final PlayerAction[] VALUES = values();

    public static PlayerAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PUNCH_RIGHT;
    }
}
