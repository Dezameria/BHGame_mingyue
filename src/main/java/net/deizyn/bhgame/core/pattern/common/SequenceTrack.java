package net.deizyn.bhgame.core.pattern.common;

import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.session.Participant;

import java.util.List;

/**
 * The judge for Audition-style SEQUENCE cues with a SPACE finisher (wooden_dummy, D23, D29). One set at a time:
 * <ul>
 *   <li>Arrows must be pressed in order; a reversed arrow wants the opposite direction. A wrong arrow restarts the
 *       set from the first arrow while its time keeps running. No mistake for that.</li>
 *   <li>SPACE before the row is complete also restarts the set.</li>
 *   <li>Once complete, SPACE near the strike point passes the set with a bonus: within {@code perfectTicks} = 2
 *       damage, otherwise 1. No SPACE (or completing after the strike point) passes for 1.</li>
 *   <li>Time runs out before the row is complete: a miss.</li>
 *   <li>Strict mode (wooden_dummy, D39): a wrong arrow or an early SPACE ends the set at once and goes to
 *       {@link Listener#onWrongPress} instead of restarting the row.</li>
 * </ul>
 * The client predicts the same rules for instant feedback; this class is the authority. The finisher is judged at
 * arrival time minus half the player's round trip, since the press left the client about then.
 */
public final class SequenceTrack {
    /** Strike point as a share of the set's time (wooden_dummy spec). */
    public static final float STRIKE_AT = 0.85F;

    public interface Listener {
        default void onTimeout(PatternContext ctx, Participant who, Set set) {
            ctx.miss(who, set.id);
        }

        default void onPassed(PatternContext ctx, Set set, int damage) {
            ctx.hit(set.id, damage);
        }

        /** A correct arrow: {@code index} is its place in the row, {@code key} the key pressed (after reversing). */
        default void onStep(PatternContext ctx, Participant who, Set set, int index, InputKey key) {
        }

        /** SPACE on a complete row, before the set resolves. */
        default void onFinisher(PatternContext ctx, Participant who, Set set, boolean perfect) {
        }

        /** Strict mode only: a wrong arrow or an early SPACE. */
        default void onWrongPress(PatternContext ctx, Participant who, Set set) {
            ctx.miss(who, set.id);
        }
    }

    private static final Listener DEFAULT_LISTENER = new Listener() {
    };

    public static final class Set {
        private int id;
        private byte[] steps;
        private long open;
        private long close;
        private long strike;
        private int progress;
        private boolean complete;
        private boolean finisherUsed;

        public int id() {
            return id;
        }

        public int length() {
            return steps.length;
        }
    }

    private final int perfectTicks;
    private final int goodTicks;
    private final boolean strict;
    private final Listener listener;
    private Set current;

    public SequenceTrack(int perfectTicks, int goodTicks) {
        this(perfectTicks, goodTicks, false, DEFAULT_LISTENER);
    }

    /** @param strict a wrong press ends the set (and goes to the listener) instead of restarting the row */
    public SequenceTrack(int perfectTicks, int goodTicks, boolean strict, Listener listener) {
        this.perfectTicks = perfectTicks;
        this.goodTicks = goodTicks;
        this.strict = strict;
        this.listener = listener;
    }

    /** A set is shown and not yet resolved; the pattern starts the next one only when this is false. */
    public boolean busy() {
        return current != null;
    }

    /**
     * Shows a new set {@code delay} ticks from now (at least leadTicks) lasting {@code duration} ticks, and sends it
     * with its finisher cue in one packet.
     */
    public Set start(PatternContext ctx, long now, int delay, byte[] steps, int duration) {
        Set set = new Set();
        set.id = ctx.newCueId();
        set.steps = steps;
        set.open = now + Math.max(delay, BhGameConfig.leadTicks);
        set.close = set.open + duration;
        set.strike = set.open + Math.round(duration * STRIKE_AT);
        current = set;
        int arrowKeys = 0;
        for (byte step : steps) {
            InputKey shown = InputKey.byId(CueData.stepKey(step));
            if (shown != null) {
                arrowKeys |= (CueData.stepReversed(step) ? shown.opposite() : shown).bit();
            }
        }
        int finisherId = ctx.newCueId();
        ctx.sendCues(List.of(
                new CueData(set.id, CueType.SEQUENCE, CueChannel.WORLD_ARROW, arrowKeys, 0, (int) (set.open - now),
                        duration, strict ? CueVisual.ARROWS_STRICT : CueVisual.ARROWS, -1, steps),
                new CueData(finisherId, CueType.TAP, CueChannel.WORLD_ARROW, InputKey.SPACE.bit(), 0,
                        (int) (set.strike - goodTicks - now), goodTicks * 2 + 1, CueVisual.FINISHER, -1)));
        schedule(ctx);
        return set;
    }

    public void tick(PatternContext ctx, long now) {
        Set set = current;
        if (set == null) {
            return;
        }
        int grace = ctx.maxGraceTicks();
        if (set.complete && !set.finisherUsed && now > set.strike + goodTicks + grace) {
            pass(ctx, set, 1, now);
        } else if (!set.complete && now >= set.close + grace) {
            current = null;
            listener.onTimeout(ctx, ctx.participants().get(0), set);
        } else {
            schedule(ctx);
        }
    }

    public void onPress(PatternContext ctx, Participant who, InputKey key, long now) {
        Set set = current;
        if (set == null || now < set.open) {
            return;
        }
        int grace = ctx.graceTicks(who);
        if (!set.complete && now >= set.close + grace) {
            return;
        }
        long sentAt = now - grace / 2;
        if (key == InputKey.SPACE) {
            if (!set.complete) {
                wrongPress(ctx, who, set, now);
            } else if (!set.finisherUsed) {
                set.finisherUsed = true;
                boolean perfect = Math.abs(sentAt - set.strike) <= perfectTicks;
                listener.onFinisher(ctx, who, set, perfect);
                pass(ctx, set, perfect ? 2 : 1, now);
            }
            return;
        }
        if (set.complete || !key.isArrow()) {
            return;
        }
        byte step = set.steps[set.progress];
        InputKey shown = InputKey.byId(CueData.stepKey(step));
        InputKey wanted = shown == null ? null : CueData.stepReversed(step) ? shown.opposite() : shown;
        if (key != wanted) {
            wrongPress(ctx, who, set, now);
            return;
        }
        listener.onStep(ctx, who, set, set.progress, key);
        set.progress++;
        if (set.progress == set.steps.length) {
            set.complete = true;
            if (sentAt > set.strike + goodTicks) {
                // finished after the strike point: passes without a finisher
                pass(ctx, set, 1, now);
            } else {
                // now waiting for the finisher, which ends sooner than the set's time
                schedule(ctx);
            }
        }
    }

    private void wrongPress(PatternContext ctx, Participant who, Set set, long now) {
        if (!strict) {
            set.progress = 0;
            return;
        }
        current = null;
        listener.onWrongPress(ctx, who, set);
        ctx.scheduleAt(now + 1);
    }

    private void pass(PatternContext ctx, Set set, int damage, long now) {
        current = null;
        listener.onPassed(ctx, set, damage);
        ctx.scheduleAt(now + 1);
    }

    private void schedule(PatternContext ctx) {
        Set set = current;
        if (set == null) {
            return;
        }
        int grace = ctx.maxGraceTicks();
        ctx.scheduleAt(set.complete ? set.strike + goodTicks + grace + 1 : set.close + grace);
    }
}
