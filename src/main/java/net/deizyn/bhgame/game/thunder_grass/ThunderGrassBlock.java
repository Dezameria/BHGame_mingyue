package net.deizyn.bhgame.game.thunder_grass;

import net.deizyn.bhgame.core.block.HerbBlock;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.minecraft.world.effect.MobEffects;

/** Hold LMB to pull, tap SPACE to ground each spark. Spec: docs/design/minigames/thunder_grass.md */
public class ThunderGrassBlock extends HerbBlock {
    private static final InputProfile INPUT = InputProfile.locked(InputKey.LMB, InputKey.SPACE).hold(InputKey.LMB);
    private static final Penalty SMALL = Penalties.all(
            Penalties.strikeVisual(3.0F),
            Penalties.effect(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
    private static final Penalty BIG = Penalties.all(
            Penalties.strikeVisual(6.0F),
            Penalties.effect(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));

    public ThunderGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InputProfile inputProfile() {
        return INPUT;
    }

    @Override
    public Pattern createPattern(PatternContext ctx) {
        return new ThunderGrassPattern(ctx);
    }

    /** Not used: this herb is won by the pull gauge, not by HP. */
    @Override
    public int defaultMaxHp() {
        return 1;
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
