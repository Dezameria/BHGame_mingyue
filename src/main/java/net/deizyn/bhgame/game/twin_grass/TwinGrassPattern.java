package net.deizyn.bhgame.game.twin_grass;

import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pattern.Phases;
import net.deizyn.bhgame.core.pattern.common.BeatTrack;
import net.deizyn.bhgame.core.session.Participant;

/**
 * Phase 1: the roots light up one side at a time, alternating. Phase 2: random sides, "both" beats that need both
 * pullers inside the same window, and quick doubles on one side. Phase 3 adds feints: one side flickers and its
 * puller must hold still. Pressing while your side is dim is your miss.
 * Numbers and rules: docs/design/minigames/twin_grass.md
 */
final class TwinGrassPattern implements Pattern {
    private static final int SEND_AHEAD_TICKS = 60;
    private static final int GENERATED_AHEAD = 4;
    private static final int[] WINDOW_PHASE_1 = {10, 8, 6};
    private static final int[] WINDOW_LATER = {8, 6, 5};
    private static final int DOUBLE_GAP = 6;
    private static final int YANG_BIT = 1 << TwinGrassBlock.YANG;
    private static final int YIN_BIT = 1 << TwinGrassBlock.YIN;
    private static final int BOTH = YANG_BIT | YIN_BIT;

    private final BeatTrack track;
    private int lastSide = TwinGrassBlock.YIN;
    private int pendingDoubleSide = -1;

    TwinGrassPattern(PatternContext ctx) {
        this.track = new BeatTrack(SEND_AHEAD_TICKS, true, ctx.focusEndTick());
    }

    @Override
    public void tick(PatternContext ctx, long now) {
        track.tick(ctx, now);
        if (ctx.isFinished()) {
            return;
        }
        while (track.queued() < GENERATED_AHEAD) {
            generate(ctx);
        }
        track.flush(ctx, now);
    }

    @Override
    public void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now) {
        if (press) {
            track.onPress(ctx, who, key, now);
        }
    }

    private void generate(PatternContext ctx) {
        int phase = Phases.byHp(ctx, 60, 25);
        int window = Phases.byDifficulty(ctx, phase == 0 ? WINDOW_PHASE_1 : WINDOW_LATER);
        if (pendingDoubleSide >= 0) {
            single(ctx, DOUBLE_GAP, pendingDoubleSide, CueType.TAP, window);
            pendingDoubleSide = -1;
            return;
        }
        if (phase == 0) {
            lastSide ^= 1;
            single(ctx, 16 + ctx.random().nextInt(9), lastSide, CueType.TAP, window);
            return;
        }
        int gap = 12 + ctx.random().nextInt(13);
        float roll = ctx.random().nextFloat();
        if (phase == 2 && roll < 0.2F) {
            single(ctx, gap, ctx.random().nextInt(2), CueType.FEINT, window);
        } else if (roll < 0.5F) {
            // both roots at once: each puller presses their own key inside the same window
            track.addAfter(ctx, gap, CueType.TAP, InputKey.LMB.bit() | InputKey.RMB.bit(), BOTH, CueVisual.GLOW,
                    window + 2);
        } else {
            int side = ctx.random().nextInt(2);
            single(ctx, gap, side, CueType.TAP, window);
            if (roll > 0.8F) {
                pendingDoubleSide = side;
            }
        }
    }

    private void single(PatternContext ctx, int gap, int side, CueType type, int window) {
        int key = side == TwinGrassBlock.YANG ? InputKey.LMB.bit() : InputKey.RMB.bit();
        track.addAfter(ctx, gap, type, key, 1 << side, CueVisual.GLOW, window);
    }
}
