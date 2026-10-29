package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.cue.CueData;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.common.SequenceTrack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Rules of {@link SequenceTrack} (wooden_dummy spec). Every set here opens at tick 10, closes at 60 and has its
 * strike point at 10 + round(50 * 0.85) = 53; perfect is ±2, good ±5.
 */
class SequenceTrackTest {
    private static final long OPEN = 10;
    private static final long STRIKE = 53;

    private static byte[] row(InputKey... keys) {
        byte[] steps = new byte[keys.length];
        for (int i = 0; i < keys.length; i++) {
            steps[i] = CueData.step(keys[i].ordinal(), false);
        }
        return steps;
    }

    private static FakePatternContext ctx() {
        return new FakePatternContext(1, InputKey.UP, InputKey.LEFT, InputKey.DOWN, InputKey.RIGHT,
                InputKey.SPACE);
    }

    private static SequenceTrack started(FakePatternContext ctx, byte[] steps) {
        SequenceTrack track = new SequenceTrack(2, 5);
        track.start(ctx, 0, 10, steps, 50);
        return track;
    }

    @Test
    void fullRowAndPerfectFinisherIsTwoDamage() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = started(ctx, row(InputKey.UP, InputKey.LEFT, InputKey.DOWN));
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.UP, OPEN + 2);
        track.onPress(ctx, p, InputKey.LEFT, OPEN + 4);
        track.onPress(ctx, p, InputKey.DOWN, OPEN + 6);
        track.onPress(ctx, p, InputKey.SPACE, STRIKE + 1);
        assertEquals(List.of("hit 0 x2"), ctx.log);
        assertFalse(track.busy());
    }

    @Test
    void wrongArrowRestartsAndNoFinisherStillPasses() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = started(ctx, row(InputKey.UP, InputKey.RIGHT));
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.UP, OPEN + 1);
        track.onPress(ctx, p, InputKey.LEFT, OPEN + 2); // wrong: back to the first arrow
        track.onPress(ctx, p, InputKey.RIGHT, OPEN + 3); // wrong again (row restarted at UP)
        track.onPress(ctx, p, InputKey.UP, OPEN + 4);
        track.onPress(ctx, p, InputKey.RIGHT, OPEN + 5);
        track.tick(ctx, STRIKE + 6); // past the good window without SPACE
        assertEquals(List.of("hit 0"), ctx.log);
    }

    @Test
    void earlySpaceRestartsTheRow() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = started(ctx, row(InputKey.UP, InputKey.DOWN));
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.UP, OPEN + 1);
        track.onPress(ctx, p, InputKey.SPACE, OPEN + 2);
        track.onPress(ctx, p, InputKey.DOWN, OPEN + 3); // row restarted: DOWN is wrong now
        track.tick(ctx, 60);
        assertEquals(List.of("miss r0 0"), ctx.log);
    }

    @Test
    void goodFinisherIsOneDamage() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = started(ctx, row(InputKey.LEFT));
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.LEFT, OPEN + 1);
        track.onPress(ctx, p, InputKey.SPACE, STRIKE - 4);
        assertEquals(List.of("hit 0"), ctx.log);
    }

    @Test
    void reversedArrowWantsTheOppositeKey() {
        FakePatternContext ctx = ctx();
        byte[] steps = {CueData.step(InputKey.UP.ordinal(), false), CueData.step(InputKey.LEFT.ordinal(), true)};
        SequenceTrack track = started(ctx, steps);
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.UP, OPEN + 1);
        track.onPress(ctx, p, InputKey.RIGHT, OPEN + 2);
        track.onPress(ctx, p, InputKey.SPACE, STRIKE);
        assertEquals(List.of("hit 0 x2"), ctx.log);
    }

    @Test
    void finishingAfterTheStrikePointPassesWithoutFinisher() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = started(ctx, row(InputKey.DOWN));
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.DOWN, STRIKE + 6);
        track.onPress(ctx, p, InputKey.SPACE, STRIKE + 7);
        assertEquals(List.of("hit 0"), ctx.log);
    }

    @Test
    void pressesBeforeTheRowShowAreIgnored() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = started(ctx, row(InputKey.UP));
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.UP, OPEN - 1);
        track.tick(ctx, 60);
        assertEquals(List.of("miss r0 0"), ctx.log);
    }

    @Test
    void strictWrongArrowEndsTheSet() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = new SequenceTrack(2, 5, true, new SequenceTrack.Listener() {
        });
        track.start(ctx, 0, 10, row(InputKey.UP, InputKey.DOWN), 50);
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.UP, OPEN + 1);
        track.onPress(ctx, p, InputKey.LEFT, OPEN + 2);
        track.onPress(ctx, p, InputKey.DOWN, OPEN + 3); // ignored: the set is over
        assertEquals(List.of("miss r0 0"), ctx.log);
        assertFalse(track.busy());
    }

    @Test
    void strictEarlySpaceEndsTheSet() {
        FakePatternContext ctx = ctx();
        SequenceTrack track = new SequenceTrack(2, 5, true, new SequenceTrack.Listener() {
        });
        track.start(ctx, 0, 10, row(InputKey.UP, InputKey.DOWN), 50);
        track.onPress(ctx, ctx.participants.get(0), InputKey.SPACE, OPEN + 1);
        assertEquals(List.of("miss r0 0"), ctx.log);
        assertFalse(track.busy());
    }
}
