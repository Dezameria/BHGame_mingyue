package net.deizyn.bhgame.game.station.qi_station;

/**
 * The qi_station events everyone near the pedestal sees (D60), shared by the pattern and the client's particles.
 * A feint and a real burst both start as {@link #SEND}; only the verdict tells them apart.
 */
public final class QiFx {
    /** A burst or feint starts gathering. role = sender ({@link #ORB} for the frenzied orb), data = gather ticks. */
    public static final int SEND = 1;
    /** A real burst was countered. role = who countered, data = who sent it (or {@link #ORB}: it just breaks up). */
    public static final int REFLECT = 2;
    /** A real burst hit. role = who was hit. */
    public static final int STRUCK = 3;
    /** Someone countered a feint. role = who pressed. */
    public static final int FEINT_CAUGHT = 4;
    /** A feint passed with nobody biting. role = its target. */
    public static final int FIZZLE = 5;
    /** SPACE with nothing coming. role = who pressed. */
    public static final int WHIFF = 6;
    /** A draw (Shift) has taken hold (D76). role = who draws. */
    public static final int GRIP = 7;
    /** A draw that has taken hold starts turning a push back on the pusher. role = who draws, data = who pushes. */
    public static final int YIELD = 8;
    public static final int ORB = 2;

    private QiFx() {
    }
}
