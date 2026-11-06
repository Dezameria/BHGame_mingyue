package net.deizyn.bhgame.core.pose;

/**
 * What bystanders see the player doing during a session: a slow loop that never mirrors the real key presses (D18).
 * Ids are sent over the network, so add new poses at the end. See docs/integrations/playeranim.md.
 */
public enum PlayerPose {
    NONE(false),
    PULL_HERB(true),
    WING_CHUN(false),
    MEDITATE(true),
    QI_FLICK(false),
    /** water_yoke: yoke on the shoulders, drawn by the vanilla model only (D51). Data byte = water levels. */
    CARRY_YOKE(false),
    /** qi_station: both palms pushed at the orb. */
    PALM_PUSH(false);

    private static final PlayerPose[] VALUES = values();

    /** The vanilla fallback shows this pose crouched (vanilla has no kneel or sit pose). */
    public final boolean crouched;

    PlayerPose(boolean crouched) {
        this.crouched = crouched;
    }

    /**
     * Whether a player in this pose is kept in Epic Fight's vanilla mode and drawn by the vanilla model (D51, D78):
     * every session pose except the wooden dummy's, which uses Epic Fight's fist attacks (D66).
     */
    public static boolean usesVanillaModel(PlayerPose pose) {
        return pose != NONE && pose != WING_CHUN;
    }

    public static PlayerPose byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
