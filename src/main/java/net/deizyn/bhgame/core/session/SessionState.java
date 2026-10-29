package net.deizyn.bhgame.core.session;

public enum SessionState {
    /** A multi-player game waiting for its partner (D31). The first player already kneels; cancelling is free. */
    WAITING,
    /** Camera turns to the object and the hint shows; inputs are ignored. */
    FOCUS,
    RUNNING,
    FINISHED
}
