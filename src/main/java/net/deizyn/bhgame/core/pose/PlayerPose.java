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
    QI_FLICK(false);

    private static final PlayerPose[] VALUES = values();

    /** The vanilla fallback shows this pose crouched (vanilla has no kneel or sit pose). */
    public final boolean crouched;

    PlayerPose(boolean crouched) {
        this.crouched = crouched;
    }

    public static PlayerPose byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
