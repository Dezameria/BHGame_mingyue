package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.common.BeatTrack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Judging rules of {@link BeatTrack} (docs/design/core-mechanics.md section 7), without Minecraft running. */
class BeatTrackTest {
    private static final int LMB = InputKey.LMB.bit();
    private static final int RMB = InputKey.RMB.bit();

    private static void run(BeatTrack track, FakePatternContext ctx, long now) {
        track.tick(ctx, now);
        track.flush(ctx, now);
    }

    @Test
    void soloPressInsideWindowHitsOnce() {
        FakePatternContext ctx = new FakePatternContext(1);
        BeatTrack track = new BeatTrack(60, true, 0);
        track.add(ctx, 20, CueType.TAP, LMB, 0, CueVisual.GLOW, 8);
        run(track, ctx, 0);
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.LMB, 22);
        track.onPress(ctx, p, InputKey.LMB, 23); // repeat in the same window: ignored
        run(track, ctx, 40);
        assertEquals(List.of("hit 0"), ctx.log);
    }

    @Test
    void soloGapPressIsOneMissAndUnpressedWindowIsAMiss() {
        FakePatternContext ctx = new FakePatternContext(1);
        BeatTrack track = new BeatTrack(60, true, 0);
        track.add(ctx, 20, CueType.TAP, LMB, 0, CueVisual.GLOW, 8);
        run(track, ctx, 0);
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.LMB, 5);
        track.onPress(ctx, p, InputKey.LMB, 10); // same gap: no second miss
        run(track, ctx, 28);
        assertEquals(List.of("miss r0 -1", "miss r0 0"), ctx.log);
    }

    @Test
    void togetherBeatNeedsBothRoles() {
        FakePatternContext ctx = new FakePatternContext(2);
        BeatTrack track = new BeatTrack(60, true, 0);
        track.add(ctx, 20, CueType.TAP, LMB | RMB, 0b11, CueVisual.GLOW, 8);
        track.add(ctx, 40, CueType.TAP, LMB | RMB, 0b11, CueVisual.GLOW, 8);
        run(track, ctx, 0);
        Participant yang = ctx.participants.get(0);
        Participant yin = ctx.participants.get(1);
        // first beat: only yang pulls -> yin is blamed when it closes, no hit
        track.onPress(ctx, yang, InputKey.LMB, 21);
        run(track, ctx, 28);
        // second beat: both pull -> one hit
        track.onPress(ctx, yin, InputKey.RMB, 41);
        track.onPress(ctx, yang, InputKey.LMB, 43);
        run(track, ctx, 48);
        assertEquals(List.of("miss r1 0", "hit 1"), ctx.log);
    }

    @Test
    void pressingDuringPartnersBeatIsYourGapMiss() {
        FakePatternContext ctx = new FakePatternContext(2);
        BeatTrack track = new BeatTrack(60, true, 0);
        track.add(ctx, 20, CueType.TAP, LMB, 0b01, CueVisual.GLOW, 8);
        track.add(ctx, 40, CueType.TAP, RMB, 0b10, CueVisual.GLOW, 8);
        run(track, ctx, 0);
        Participant yang = ctx.participants.get(0);
        Participant yin = ctx.participants.get(1);
        track.onPress(ctx, yang, InputKey.LMB, 21);
        track.onPress(ctx, yin, InputKey.RMB, 22); // yang's root is lit, yin's is dim
        track.onPress(ctx, yin, InputKey.RMB, 42);
        run(track, ctx, 60);
        assertEquals(List.of("hit 0", "miss r1 -1", "hit 1"), ctx.log);
    }

    @Test
    void feintPunishesPressAndPassesQuietly() {
        FakePatternContext ctx = new FakePatternContext(2);
        BeatTrack track = new BeatTrack(60, true, 0);
        track.add(ctx, 20, CueType.FEINT, RMB, 0b10, CueVisual.GLOW, 8);
        track.add(ctx, 40, CueType.FEINT, LMB, 0b01, CueVisual.GLOW, 8);
        run(track, ctx, 0);
        track.onPress(ctx, ctx.participants.get(1), InputKey.RMB, 22);
        track.onPress(ctx, ctx.participants.get(1), InputKey.RMB, 23);
        run(track, ctx, 60);
        assertEquals(List.of("miss r1 0"), ctx.log);
    }

    @Test
    void wrongKeyIsOneMissAndNoExtraMissAtClose() {
        FakePatternContext ctx = new FakePatternContext(1);
        BeatTrack track = new BeatTrack(60, true, 0);
        track.add(ctx, 20, CueType.TAP, LMB, 0, CueVisual.GLOW, 8);
        run(track, ctx, 0);
        Participant p = ctx.participants.get(0);
        track.onPress(ctx, p, InputKey.RMB, 21);
        track.onPress(ctx, p, InputKey.RMB, 22);
        run(track, ctx, 40);
        assertEquals(List.of("miss r0 0"), ctx.log);
    }
}
