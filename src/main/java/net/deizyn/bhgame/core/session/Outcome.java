package net.deizyn.bhgame.core.session;

public enum Outcome {
    PERFECT, SUCCESS, FAIL, ABORTED;

    private static final Outcome[] VALUES = values();

    public static Outcome byId(int id) {
        return VALUES[id];
    }

    public boolean isWin() {
        return this == PERFECT || this == SUCCESS;
    }
}
