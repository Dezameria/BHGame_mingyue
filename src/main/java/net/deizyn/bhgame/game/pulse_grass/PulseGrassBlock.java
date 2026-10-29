package net.deizyn.bhgame.game.pulse_grass;

import net.deizyn.bhgame.core.block.HerbBlock;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.minecraft.world.effect.MobEffects;

/** Spec: docs/design/minigames/pulse_grass.md */
public class PulseGrassBlock extends HerbBlock {
    // the dim phase is a FEINT, so presses there must reach the server to count as misses
    private static final InputProfile INPUT = InputProfile.locked(InputKey.LMB).withIdlePresses();
    private static final Penalty SMALL = Penalties.effect(MobEffects.POISON, 60, 0);
    private static final Penalty BIG = Penalties.all(
            Penalties.effect(MobEffects.POISON, 160, 1),
            Penalties.effect(MobEffects.CONFUSION, 100, 0));

    public PulseGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InputProfile inputProfile() {
        return INPUT;
    }

    @Override
    public Pattern createPattern(PatternContext ctx) {
        return new PulseGrassPattern(ctx);
    }

    @Override
    public int defaultMaxHp() {
        return 8;
    }

    @Override
    public Penalty smallPenalty() {
        return SMALL;
    }

    @Override
    public Penalty bigPenalty() {
        return BIG;
    }
}
