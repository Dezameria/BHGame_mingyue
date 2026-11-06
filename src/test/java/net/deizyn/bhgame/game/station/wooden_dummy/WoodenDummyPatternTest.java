package net.deizyn.bhgame.game.station.wooden_dummy;

import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.common.SequenceTrack;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.session.FakePatternContext;
import net.deizyn.bhgame.core.session.Participant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The endless run (D40), played by a bot through the same PatternContext a real session uses. */
class WoodenDummyPatternTest {

    /** Plays until {@code stages} banners have shown; {@code wrongAtSet} >= 0 presses a wrong arrow in that set. */
    private static Run play(int stages, int wrongAtSet) {
        FakePatternContext ctx = new FakePatternContext(1, InputKey.UP, InputKey.LEFT, InputKey.DOWN, InputKey.RIGHT,
                InputKey.SPACE);
        ctx.focusEnd = 40;
        WoodenDummyPattern pattern = new WoodenDummyPattern(ctx);
        Participant player = ctx.participants.get(0);
        Run run = new Run(ctx);
        int handled = 0;
        byte[] steps = null;
        long open = -1;
        long strike = -1;
        int set = -1;
        for (long now = 1; now < 100_000 && !ctx.finished && ctx.banners.size() < stages + 1; now++) {
            if (now == 1 || now >= ctx.nextEvent) {
                ctx.clearSchedule();
                pattern.tick(ctx, now);
            }
            while (handled < ctx.cues.size()) {
                CueData cue = ctx.cues.get(handled++);
                if (cue.type() == CueType.SEQUENCE) {
                    steps = cue.steps();
                    open = now + cue.opensIn();
                    strike = open + Math.round(cue.window() * SequenceTrack.STRIKE_AT);
                    set++;
                    run.lengths.add(steps.length);
                }
            }
            if (steps != null && now == open + 1) {
                for (byte step : steps) {
                    InputKey shown = InputKey.byId(CueData.stepKey(step));
                    InputKey key = CueData.stepReversed(step) ? shown.opposite() : shown;
                    if (set == wrongAtSet) {
                        key = key.opposite();
                    }
                    pattern.onInput(ctx, player, key, true, now);
                    if (ctx.finished) {
                        break;
                    }
                }
            }
            if (steps != null && now == strike && !ctx.finished) {
                pattern.onInput(ctx, player, InputKey.SPACE, true, now);
                steps = null;
            }
        }
        return run;
    }

    private record Run(FakePatternContext ctx, List<Integer> lengths) {
        Run(FakePatternContext ctx) {
            this(ctx, new ArrayList<>());
        }
    }

    @Test
    void perfectPlayKeepsGoingAndStagesClimb() {
        Run run = play(6, -1);
        assertFalse(run.ctx().finished, "an endless run never ends on its own");
        assertEquals(List.of("bhgame.wooden_dummy.stage 1", "bhgame.wooden_dummy.stage 2",
                "bhgame.wooden_dummy.stage 3", "bhgame.wooden_dummy.stage 4", "bhgame.wooden_dummy.stage 5",
                "bhgame.wooden_dummy.stage 6", "bhgame.wooden_dummy.stage 7"), run.ctx().banners);
        assertEquals("bhg_dummy_stage=7", run.ctx().best);
        List<Integer> lengths = run.lengths();
        for (int i = 1; i < lengths.size(); i++) {
            assertTrue(lengths.get(i) >= lengths.get(i - 1), "rows never get shorter: " + lengths);
        }
        assertTrue(lengths.get(lengths.size() - 1) > lengths.get(0), "rows get longer: " + lengths);
        // bystanders see punches: both hands and a finisher (D41)
        List<PlayerAction> actions = run.ctx().actions;
        assertTrue(actions.contains(PlayerAction.PUNCH_RIGHT) && actions.contains(PlayerAction.PUNCH_LEFT)
                && actions.contains(PlayerAction.PUNCH_FINISH), actions.toString());
        assertFalse(actions.contains(PlayerAction.KNOCKED_BACK));
    }

    @Test
    void wrongArrowEndsTheRun() {
        Run run = play(10, 3);
        assertTrue(run.ctx().finished);
        assertEquals("fail", run.ctx().log.get(run.ctx().log.size() - 1));
        assertEquals(PlayerAction.KNOCKED_BACK, run.ctx().actions.get(run.ctx().actions.size() - 1));
    }

    /** D77: running out of time is a swat that ends the run at once, never a mistake that lets you keep punching. */
    @Test
    void timeoutEndsTheRun() {
        FakePatternContext ctx = new FakePatternContext(1, InputKey.UP, InputKey.LEFT, InputKey.DOWN, InputKey.RIGHT,
                InputKey.SPACE);
        ctx.focusEnd = 40;
        WoodenDummyPattern pattern = new WoodenDummyPattern(ctx);
        for (long now = 1; now < 10_000 && !ctx.finished; now++) {
            if (now == 1 || now >= ctx.nextEvent) {
                ctx.clearSchedule();
                pattern.tick(ctx, now);
            }
        }
        assertTrue(ctx.finished);
        assertEquals("fail", ctx.log.get(ctx.log.size() - 1));
        assertFalse(ctx.log.stream().anyMatch(line -> line.startsWith("miss")));
        assertEquals(List.of(PlayerAction.KNOCKED_BACK), ctx.actions);
    }
}
