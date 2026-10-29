package net.deizyn.bhgame.core.cue;

public enum CueResult {
    NONE, HIT, MISS;

    private static final CueResult[] VALUES = values();

    public static CueResult byId(int id) {
        return VALUES[id];
    }
}
