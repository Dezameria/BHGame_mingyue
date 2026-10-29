package net.deizyn.bhgame.core.pattern;

/** Picks a pattern phase from how much HP is left. */
public final class Phases {

    /**
     * {@code byHp(ctx, 50)} is 0 while HP is above 50%, then 1. {@code byHp(ctx, 60, 25)} adds phase 2 at 25% and
     * below. Thresholds go from high to low.
     */
    public static int byHp(PatternContext ctx, int... percents) {
        int phase = 0;
        for (int percent : percents) {
            if (ctx.hp() * 100 <= ctx.maxHp() * percent) {
                phase++;
            }
        }
        return phase;
    }

    /** {@code values[difficulty - 1]}, for per-difficulty tables in the specs. */
    public static int byDifficulty(PatternContext ctx, int[] values) {
        return values[Math.max(0, Math.min(values.length - 1, ctx.difficulty() - 1))];
    }

    private Phases() {
    }
}
