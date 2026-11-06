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

    /**
     * About how many ticks ago this participant's key really went down (half the ping, capped like the grace), so a
     * gauge the client already moved can be moved from then too (D57).
     */
    default float inputLagTicks(Participant participant) {
        return 0.0F;
    }

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
     * Ends a duel now (D58): {@code winner} wins (PERFECT without their own mistakes), every other participant gets
     * FAIL with the fail penalty. For PvP games such as qi_station.
     */
    void winBy(Participant winner);

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

    /**
     * A one-shot cosmetic event everyone near the block sees, participants included (D60), sent with the public gauge
     * ({@link Gauge#FLAG_PUBLIC}) and held keys. The game defines {@code event} (never 0, which is a plain state
     * sync), {@code role} and {@code data}. Must not tell a player anything their own screen does not, e.g. a feint
     * and a real burst send the same event. The state alone is synced by the session whenever it changes.
     */
    void showNearby(int event, int role, int data);

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

    /**
     * How the carrier moves now, sampled on the server every few ticks (carried games, D48). Always STILL for other
     * games and for stress bots.
     */
    default Motion motion() {
        return Motion.STILL;
    }

    /**
     * The byte bystanders get with the player's pose (water_yoke: the water levels). Starts from what the carried
     * block held.
     */
    default int poseData() {
        return 0;
    }

    /** Changes the pose byte and sends it to the players around (only when it changes, never per tick). */
    default void setPoseData(int data) {
    }

    /**
     * Slows the carrier by this share of their speed (0 = none) until changed or the session ends (carried games,
     * D70). Only a change reaches the player, so calling it every sample costs nothing.
     */
    default void setCarrySlow(float share) {
    }
}
