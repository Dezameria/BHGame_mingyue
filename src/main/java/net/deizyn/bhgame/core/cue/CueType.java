package net.deizyn.bhgame.core.cue;

public enum CueType {
    TAP, HOLD, FEINT, AIM, SEQUENCE;

    private static final CueType[] VALUES = values();

    public static CueType byId(int id) {
        return VALUES[id];
    }
}
