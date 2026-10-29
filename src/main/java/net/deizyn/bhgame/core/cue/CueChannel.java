package net.deizyn.bhgame.core.cue;

/** Where a cue is shown. Every channel is drawn only on the player's own client. */
public enum CueChannel {
    BLOCK, WORLD_ARROW, WORLD_TARGET, SPATIAL_AUDIO;

    private static final CueChannel[] VALUES = values();

    public static CueChannel byId(int id) {
        return VALUES[id];
    }
}
