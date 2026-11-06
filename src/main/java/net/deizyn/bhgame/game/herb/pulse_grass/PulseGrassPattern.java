package net.deizyn.bhgame.game.herb.pulse_grass;

import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pattern.Phases;
import net.deizyn.bhgame.core.pattern.common.BeatTrack;
import net.deizyn.bhgame.core.session.Participant;

/**
 * The herb breathes: each exhale is a TAP LMB window, the dim inhale between them punishes presses.
 * Beats are generated a few ahead and sent in batches, so the client animates them on its own clock.
 * Numbers and rules: docs/design/minigames/pulse_grass.md
 */
final class PulseGrassPattern implements Pattern {
    /** Beats opening within this many ticks are sent; one packet usually carries 2-4 beats. */
    private static final int SEND_AHEAD_TICKS = 60;
    private static final int GENERATED_AHEAD = 4;
    private static final int[] WINDOW_PHASE_1 = {10, 8, 6};
    private static final int[] WINDOW_PHASE_2 = {8, 6, 5};
    private static final int DOUBLE_BEAT_GAP = 6;
    private static final float DOUBLE_BEAT_CHANCE = 0.3F;

    private final BeatTrack track;
    private boolean nextIsDouble;

    PulseGrassPattern(PatternContext ctx) {
        this.track = new BeatTrack(SEND_AHEAD_TICKS, true, ctx.focusEndTick());
    }

    @Override
    public void tick(PatternContext ctx, long now) {
        track.tick(ctx, now);
        if (ctx.isFinished()) {
            return;
        }
        while (track.queued() < GENERATED_AHEAD) {
            boolean phase2 = Phases.byHp(ctx, 50) > 0;
            int window = Phases.byDifficulty(ctx, phase2 ? WINDOW_PHASE_2 : WINDOW_PHASE_1);
            int inhale;
            if (nextIsDouble) {
                inhale = DOUBLE_BEAT_GAP;
                nextIsDouble = false;
            } else {
                inhale = phase2 ? 12 + ctx.random().nextInt(17) : 20 + ctx.random().nextInt(9);
                nextIsDouble = phase2 && ctx.random().nextFloat() < DOUBLE_BEAT_CHANCE;
            }
            track.addAfter(ctx, inhale, CueType.TAP, InputKey.LMB.bit(), 0, CueVisual.GLOW, window);
        }
        track.flush(ctx, now);
    }

    @Override
    public void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now) {
        if (press) {
            track.onPress(ctx, who, key, now);
        }
    }
}
