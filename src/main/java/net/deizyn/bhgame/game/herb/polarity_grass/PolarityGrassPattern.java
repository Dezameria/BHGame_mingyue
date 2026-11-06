package net.deizyn.bhgame.game.herb.polarity_grass;

import net.deizyn.bhgame.core.cue.CueType;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pattern.Phases;
import net.deizyn.bhgame.core.pattern.common.BeatTrack;
import net.deizyn.bhgame.core.session.Participant;
import org.jetbrains.annotations.Nullable;

/**
 * The aura flips between yin (blue) and yang (gold); push the opposite qi: blue → LMB, gold → RMB. Phase 2 is faster
 * and adds a brief flash of the other colour (FEINT) right before a real cue. The penalty follows the qi you pushed:
 * extra yin (RMB) freezes, extra yang (LMB) burns, no press hurts. A FAIL repeats the last element, doubled.
 * Numbers and rules: docs/design/minigames/polarity_grass.md
 */
final class PolarityGrassPattern implements Pattern, BeatTrack.Listener {
    private static final int SEND_AHEAD_TICKS = 60;
    private static final int GENERATED_AHEAD = 4;
    private static final int[] WINDOW_PHASE_1 = {12, 10, 8};
    private static final int[] WINDOW_PHASE_2 = {10, 8, 6};
    private static final int FEINT_WINDOW = 4;
    private static final int AFTER_FEINT_GAP = 4;
    private static final float FEINT_CHANCE = 0.25F;
    private static final Penalty FAIL_FROZEN = Penalties.all(Penalties.damage(4.0F), Penalties.freeze(120));
    private static final Penalty FAIL_BURNT = Penalties.all(Penalties.damage(4.0F), Penalties.fire(4));

    private final BeatTrack track;

    PolarityGrassPattern(PatternContext ctx) {
        // idle presses are not sent (sendIdlePresses=false), so there are no gap misses
        this.track = new BeatTrack(SEND_AHEAD_TICKS, false, ctx.focusEndTick(), this);
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
            int gap = phase2 ? 10 + ctx.random().nextInt(9) : 16 + ctx.random().nextInt(9);
            boolean yin = ctx.random().nextBoolean();
            if (phase2 && ctx.random().nextFloat() < FEINT_CHANCE) {
                // the opposite colour flashes, then the real one shows
                track.addAfter(ctx, gap, CueType.FEINT, answer(!yin), 0, colour(!yin), FEINT_WINDOW);
                gap = AFTER_FEINT_GAP;
            }
            track.addAfter(ctx, gap, CueType.TAP, answer(yin), 0, colour(yin), window);
        }
        track.flush(ctx, now);
    }

    @Override
    public void onInput(PatternContext ctx, Participant who, InputKey key, boolean press, long now) {
        if (press) {
            track.onPress(ctx, who, key, now);
        }
    }

    @Override
    public void onMiss(PatternContext ctx, Participant who, @Nullable BeatTrack.Beat beat, @Nullable InputKey pressed) {
        int cueId = beat == null ? -1 : beat.id();
        if (pressed == InputKey.RMB) {
            ctx.setFailPenalty(FAIL_FROZEN);
            ctx.miss(who, cueId, PolarityGrassBlock.WRONG_YIN);
        } else if (pressed == InputKey.LMB) {
            ctx.setFailPenalty(FAIL_BURNT);
            ctx.miss(who, cueId, PolarityGrassBlock.WRONG_YANG);
        } else {
            ctx.miss(who, cueId, PolarityGrassBlock.NO_PRESS);
        }
    }

    /** Blue (yin) is answered with LMB (yang), gold (yang) with RMB (yin). */
    private static int answer(boolean yin) {
        return yin ? InputKey.LMB.bit() : InputKey.RMB.bit();
    }

    private static CueVisual colour(boolean yin) {
        return yin ? CueVisual.YIN : CueVisual.YANG;
    }
}
