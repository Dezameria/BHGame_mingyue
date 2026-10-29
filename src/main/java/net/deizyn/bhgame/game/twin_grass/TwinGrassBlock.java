package net.deizyn.bhgame.game.twin_grass;

import net.deizyn.bhgame.core.block.HerbBlock;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.minecraft.world.effect.MobEffects;

/**
 * Two players, one herb: the first to kneel holds the yang root (LMB), the second the yin root (RMB).
 * Spec: docs/design/minigames/twin_grass.md
 */
public class TwinGrassBlock extends HerbBlock {
    static final int YANG = 0;
    static final int YIN = 1;
    private static final InputProfile YANG_INPUT = InputProfile.locked(InputKey.LMB).withIdlePresses();
    private static final InputProfile YIN_INPUT = InputProfile.locked(InputKey.RMB).withIdlePresses();
    private static final Penalty SMALL = Penalties.effect(MobEffects.POISON, 60, 0);
    private static final Penalty BIG = Penalties.all(
            Penalties.effect(MobEffects.POISON, 160, 1),
            Penalties.effect(MobEffects.CONFUSION, 100, 0));

    public TwinGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InputProfile inputProfile() {
        return YANG_INPUT;
    }

    @Override
    public InputProfile inputProfile(int role) {
        return role == YIN ? YIN_INPUT : YANG_INPUT;
    }

    @Override
    public int requiredPlayers() {
        return 2;
    }

    @Override
    public Pattern createPattern(PatternContext ctx) {
        return new TwinGrassPattern(ctx);
    }

    @Override
    public int defaultMaxHp() {
        return 10;
    }

    @Override
    public int defaultMaxMistakes() {
        return 4;
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
