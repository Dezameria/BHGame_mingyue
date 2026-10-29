package net.deizyn.bhgame.game.wooden_dummy;

import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pattern.Phases;
import net.deizyn.bhgame.core.pattern.common.SequenceTrack;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.session.Participant;

/**
 * Endless (D40): sets keep coming until the player makes a mistake. Every {@link #POINTS_PER_STAGE} points the stage
 * goes up (a passed set is 1 point, a PERFECT finisher 2) and its number shows at the top of the screen; each stage
 * adds an arrow, takes time off each arrow and, from stage 3, mixes in reversed (red) arrows. A wrong arrow or an
 * early SPACE swats the player away and ends the session (D39); running out of time on a set is a mistake, and
 * MaxMistakes of them also end it. The stage reached is the result.
 * Spec: docs/design/minigames/wooden_dummy.md
 */
final class WoodenDummyPattern implements Pattern, SequenceTrack.Listener {
    static final String BEST_OBJECTIVE = "bhg_dummy_stage";
    private static final InputKey[] ARROWS = {InputKey.UP, InputKey.LEFT, InputKey.DOWN, InputKey.RIGHT};
    private static final int POINTS_PER_STAGE = 3;
    /** Per Difficulty 1 / 2 / 3. */
    private static final int[] BASE_LENGTH = {3, 4, 4};
    private static final int[] BASE_TICKS_PER_ARROW = {15, 12, 10};
    private static final int[] MIN_TICKS_PER_ARROW = {6, 5, 4};
    private static final int MAX_LENGTH = 12;
    /** Reading time on top of the per-arrow time. */
    private static final int READ_TICKS = 16;
    private static final int PERFECT_TICKS = 2;
    private static final int GOOD_TICKS = 5;
    private static final int GAP_TICKS = 20;

    private final SequenceTrack track = new SequenceTrack(PERFECT_TICKS, GOOD_TICKS, true, this);
    private int points;
    private int stage;
    private boolean started;

    WoodenDummyPattern(PatternContext ctx) {
        // ending is the normal way out of an endless run, so it costs a swat, not the heavy fail penalty
        ctx.setFailPenalty(WoodenDummyPenalties.SWAT);
    }

    @Override
    public void tick(PatternContext ctx, long now) {
        track.tick(ctx, now);
        if (ctx.isFinished() || track.busy()) {
            return;
        }
        int delay = GAP_TICKS;
        if (!started) {
            started = true;
            delay = (int) Math.max(GAP_TICKS, ctx.focusEndTick() - now);
        }
        enterStage(ctx, 1 + points / POINTS_PER_STAGE);
        int length = Math.min(MAX_LENGTH, Phases.byDifficulty(ctx, BASE_LENGTH) + stage - 1);
        int perArrow = Math.max(Phases.byDifficulty(ctx, MIN_TICKS_PER_ARROW),
                Phases.byDifficulty(ctx, BASE_TICKS_PER_ARROW) - (stage - 1));
        track.start(ctx, now, delay, steps(ctx, length), READ_TICKS + length * perArrow);
    }

    @Override
    public void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now) {
        if (press) {
            track.onPress(ctx, who, key, now);
        }
    }

    /** No HP in an endless run: a passed set is feedback plus points. */
    @Override
    public void onPassed(PatternContext ctx, SequenceTrack.Set set, int damage) {
        points += damage;
        ctx.hit(set.id(), 0);
    }

    /** Bystanders see a punch for each correct arrow, alternating hands (D41). */
    @Override
    public void onStep(PatternContext ctx, Participant who, SequenceTrack.Set set, int index, InputKey key) {
        ctx.action(who, index % 2 == 0 ? PlayerAction.PUNCH_RIGHT : PlayerAction.PUNCH_LEFT, key);
    }

    @Override
    public void onFinisher(PatternContext ctx, Participant who, SequenceTrack.Set set, boolean perfect) {
        ctx.action(who, PlayerAction.PUNCH_FINISH, InputKey.SPACE);
    }

    @Override
    public void onWrongPress(PatternContext ctx, Participant who, SequenceTrack.Set set) {
        ctx.action(who, PlayerAction.KNOCKED_BACK, null);
        ctx.fail();
    }

    private void enterStage(PatternContext ctx, int next) {
        if (next == stage) {
            return;
        }
        stage = next;
        ctx.banner("bhgame.wooden_dummy.stage", stage);
        ctx.setOutcomeMessage("bhgame.wooden_dummy.outcome.reached", stage);
        ctx.recordBest(BEST_OBJECTIVE, stage);
    }

    private byte[] steps(PatternContext ctx, int length) {
        byte[] steps = new byte[length];
        for (int i = 0; i < length; i++) {
            steps[i] = CueData.step(ARROWS[ctx.random().nextInt(ARROWS.length)].ordinal(), false);
        }
        int maxReversed = stage < 3 ? 0 : Math.min(stage - 2, length / 3);
        int reversed = maxReversed == 0 ? 0 : ctx.random().nextInt(maxReversed + 1);
        for (int i = 0; i < reversed; i++) {
            // never the first arrow, so the row always starts readable
            int at = 1 + ctx.random().nextInt(length - 1);
            steps[at] = CueData.step(CueData.stepKey(steps[at]), true);
        }
        return steps;
    }
}
