package net.deizyn.bhgame.game.herb.polarity_grass;

import net.deizyn.bhgame.core.block.HerbBlock;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;

/** Blue (yin) aura → answer with LMB (yang); gold (yang) → RMB (yin). Spec: docs/design/minigames/polarity_grass.md */
public class PolarityGrassBlock extends HerbBlock {
    private static final InputProfile INPUT = InputProfile.locked(InputKey.LMB, InputKey.RMB);
    /** A window that passed without a press. Wrong-key misses pick freeze or fire in the pattern. */
    static final Penalty NO_PRESS = Penalties.damage(2.0F);
    static final Penalty WRONG_YIN = Penalties.freeze(60);
    static final Penalty WRONG_YANG = Penalties.fire(2);
    /** Brute force; a FAIL replaces this with the element of the last mistake (see the pattern). */
    private static final Penalty BIG = Penalties.all(Penalties.damage(4.0F), Penalties.fire(4));

    public PolarityGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InputProfile inputProfile() {
        return INPUT;
    }

    @Override
    public Pattern createPattern(PatternContext ctx) {
        return new PolarityGrassPattern(ctx);
    }

    @Override
    public int defaultMaxHp() {
        return 10;
    }

    @Override
    public Penalty smallPenalty() {
        return NO_PRESS;
    }

    @Override
    public Penalty bigPenalty() {
        return BIG;
    }
}
