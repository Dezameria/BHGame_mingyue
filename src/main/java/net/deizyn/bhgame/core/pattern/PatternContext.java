package net.deizyn.bhgame.core.pattern;

import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.session.Participant;
import net.minecraft.util.RandomSource;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Everything a pattern may read or do. Implemented by the session; patterns never see players, levels or packets,
 * which also lets /bhgame stress run them without real players.
 */
public interface PatternContext {

    int difficulty();

    int hp();

    int maxHp();

    /** Mistakes of all participants together. */
    int mistakes();

    RandomSource random();

    /** First tick of RUNNING; patterns place their first cue after it. */
    long focusEndTick();

    /** In role order; never empty. */
    List<Participant> participants();

    int newCueId();

    /** Asks for {@link Pattern#tick} to run at {@code tick} (the earliest request wins). */
    void scheduleAt(long tick);

    /** Extra ticks a window stays open for this participant's ping. */
    int graceTicks(Participant participant);

    /** The largest grace of all participants, for expiring cues shared by everyone. */
    int maxGraceTicks();

    /** Sends upcoming cues to every participant (each client sees its partners' cues dimmed). */
    void sendCues(List<CueData> cues);

    /** A cue was done right: shared HP goes down by one. */
    default void hit(int cueId) {
        hit(cueId, 1);
    }

    /** A cue was done right: HP goes down by {@code damage} (0 = feedback only, for games that win by a gauge). */
    void hit(int cueId, int damage);

    /** {@code who} got a cue wrong: their mistake, the game's small penalty. {@code cueId} -1 = a press in a gap. */
    void miss(Participant who, int cueId);

    /** Like {@link #miss(Participant, int)} with a penalty chosen by the pattern (e.g. by which key was wrong). */
    void miss(Participant who, int cueId, Penalty penalty);

    /** Ends the session as won now (PERFECT without mistakes), for games that win by a gauge instead of HP. */
    void win();

    /** Ends the session as lost now (FAIL with the fail penalty), e.g. the dummy swatting you away (D39). */
    void fail();

    /**
     * A short line in large letters at the top of the players' screens, e.g. "Stage 4" in the endless wooden dummy
     * (D40). {@code value} fills {@code %s} in the translation.
     */
    void banner(String translationKey, int value);

    /**
     * A one-shot movement of {@code who} that bystanders see, e.g. a punch on the dummy (D41), with the arrow key that
     * caused it so the hit effect lands on that side (D42). Throttled per player; extra calls are dropped. Sent to the
     * players around only: the player's own client already played it the moment the key was pressed.
     */
    void action(Participant who, PlayerAction action, @Nullable InputKey key);

    /** Replaces the action-bar outcome line for the session's end, e.g. "you reached stage 7" (D40). */
    void setOutcomeMessage(String translationKey, int value);

    /** At the end, raises each participant's score on scoreboard {@code objective} to {@code value} if higher. */
    void recordBest(String objective, int value);

    /** Replaces the game's big penalty if this session ends in FAIL (e.g. "the element of the last mistake"). */
    void setFailPenalty(Penalty penalty);

    /**
     * Gauge {@code index} (0-3), created on first use with {@code flags} ({@link Gauge#FLAG_PROGRESS}). Changes are
     * sent to the players after the pattern call returns.
     */
    Gauge gauge(int index, int flags);

    boolean isFinished();
}
