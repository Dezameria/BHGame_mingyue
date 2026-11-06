package net.deizyn.bhgame.game.station.wooden_dummy;

/** The wooden dummy's events everyone near it sees (D60, D66), shared by the pattern and the client's particles. */
public final class DummyFx {
    /** A new stage was reached. data = the stage. */
    public static final int STAGE_UP = 1;
    /** Every this many stages the burst is a big one. */
    public static final int MILESTONE = 5;

    private DummyFx() {
    }
}
