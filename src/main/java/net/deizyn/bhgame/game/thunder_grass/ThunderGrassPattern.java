package net.deizyn.bhgame.game.thunder_grass;

import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pattern.Phases;
import net.deizyn.bhgame.core.pattern.common.BeatTrack;
import net.deizyn.bhgame.core.session.Participant;
import org.jetbrains.annotations.Nullable;

/**
 * Holding LMB fills the {@code pull} gauge (about 12 s of steady pulling); letting go drains it slowly. Sparks run up
 * the stem as TAP SPACE cues; from half way they come faster, sometimes twice, and purple decoys must be left alone.
 * A missed spark shocks the player and costs a quarter of the pull. Full pull wins.
 * Numbers and rules: docs/design/minigames/thunder_grass.md
 */
final class ThunderGrassPattern implements Pattern, BeatTrack.Listener {
    private static final int PULL = 0;
    private static final float PULL_RATE = 1.0F / 240.0F;
    private static final float DRAIN_RATE = -1.0F / 600.0F;
    private static final float MISS_LOSS = 0.25F;
    private static final int SEND_AHEAD_TICKS = 60;
    private static final int GENERATED_AHEAD = 3;
    private static final int[] WINDOW_PHASE_1 = {10, 8, 6};
    private static final int[] WINDOW_PHASE_2 = {8, 7, 5};
    private static final int DOUBLE_GAP = 6;
    private static final float DOUBLE_CHANCE = 0.2F;
    private static final float DECOY_CHANCE = 0.2F;

    private final BeatTrack track;
    /** The tick of the call in progress, for listener callbacks that change the gauge. */
    private long now;
    private boolean nextIsDouble;

    ThunderGrassPattern(PatternContext ctx) {
        // SPACE is sent only while a spark is lit (sendIdlePresses=false), so there are no gap misses
        this.track = new BeatTrack(SEND_AHEAD_TICKS, false, ctx.focusEndTick(), this);
    }

    @Override
    public void tick(PatternContext ctx, long now) {
        this.now = now;
        Gauge pull = ctx.gauge(PULL, Gauge.FLAG_PROGRESS);
        if (pull.value(now) >= 1.0F) {
            ctx.win();
            return;
        }
        track.tick(ctx, now);
        if (ctx.isFinished()) {
            return;
        }
        while (track.queued() < GENERATED_AHEAD) {
            boolean phase2 = pull.value(now) >= 0.5F;
            int window = Phases.byDifficulty(ctx, phase2 ? WINDOW_PHASE_2 : WINDOW_PHASE_1);
            int gap;
            if (nextIsDouble) {
                gap = DOUBLE_GAP;
                nextIsDouble = false;
            } else {
                gap = phase2 ? 16 + ctx.random().nextInt(25) : 30 + ctx.random().nextInt(31);
                nextIsDouble = phase2 && ctx.random().nextFloat() < DOUBLE_CHANCE;
            }
            boolean decoy = phase2 && !nextIsDouble && ctx.random().nextFloat() < DECOY_CHANCE;
            track.addAfter(ctx, gap, decoy ? CueType.FEINT : CueType.TAP, InputKey.SPACE.bit(), 0,
                    decoy ? CueVisual.SPARK_FAKE : CueVisual.SPARK, window);
        }
        track.flush(ctx, now);
        scheduleFull(ctx, pull, now);
    }

    @Override
    public void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now) {
        this.now = now;
        Gauge pull = ctx.gauge(PULL, Gauge.FLAG_PROGRESS);
        if (key == InputKey.LMB) {
            pull.setRate(now, press ? PULL_RATE : DRAIN_RATE);
            scheduleFull(ctx, pull, now);
        } else if (key == InputKey.SPACE && press) {
            track.onPress(ctx, who, key, now);
        }
    }

    /** A grounded spark: feedback only, the gauge is the progress. */
    @Override
    public void onHit(PatternContext ctx, BeatTrack.Beat beat) {
        ctx.hit(beat.id(), 0);
    }

    @Override
    public void onMiss(PatternContext ctx, Participant who, @Nullable BeatTrack.Beat beat, @Nullable InputKey pressed) {
        Gauge pull = ctx.gauge(PULL, Gauge.FLAG_PROGRESS);
        pull.add(now, -MISS_LOSS);
        ctx.miss(who, beat == null ? -1 : beat.id());
        if (!ctx.isFinished()) {
            scheduleFull(ctx, pull, now);
        }
    }

    /** Wakes the session when a steady pull would reach the top, to declare the win. */
    private static void scheduleFull(PatternContext ctx, Gauge pull, long now) {
        if (pull.ratePerTick() > 0.0F) {
            ctx.scheduleAt(pull.tickReaching(now, 1.0F));
        }
    }
}
