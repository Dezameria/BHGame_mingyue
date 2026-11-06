package net.deizyn.bhgame.core.pattern;

import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.session.Participant;

/**
 * Server-side cue logic of one minigame. One instance per session, created when every participant has joined.
 * {@link #tick} runs only when the server tick reaches the session's next scheduled event; the pattern must call
 * {@link PatternContext#scheduleAt} for its next one.
 */
public interface Pattern {

    void tick(PatternContext ctx, long now);

    /** A key edge that passed the profile filter, rate limit and distance check. */
    void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now);

    /** Something the carrier's body did, for carried games (D48, D49). Sent at once, between ticks. */
    default void onCarryEvent(PatternContext ctx, CarryEvent event, long now) {
    }
}
