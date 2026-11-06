package net.deizyn.bhgame.game.station.qi_station;

import net.deizyn.bhgame.core.block.StationBlock;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A stone pedestal with a qi orb above it; two players stand on opposite sides and push the orb at each other (PvP
 * only, D58). Spec: docs/design/minigames/qi_station.md
 */
public class QiStationBlock extends StationBlock {
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 16.0, 13.0);
    private static final InputProfile INPUT = InputProfile.locked(InputKey.LMB, InputKey.RMB, InputKey.SPACE, InputKey.SNEAK)
            .hold(InputKey.LMB, InputKey.SNEAK).withIdlePresses();
    /** A burst you failed to counter. */
    private static final Penalty SMALL = Penalties.knockback(0.3);
    /** The orb reached you: you lost the duel. */
    private static final Penalty BIG = Penalties.all(
            Penalties.knockback(1.0),
            Penalties.effect(MobEffects.WEAKNESS, 200, 0));
    private static final Penalty BRUTE_FORCE = Penalties.all(
            Penalties.knockback(0.6),
            Penalties.effect(MobEffects.WEAKNESS, 100, 0));

    public QiStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public InputProfile inputProfile() {
        return INPUT;
    }

    @Override
    public int requiredPlayers() {
        return 2;
    }

    @Override
    public boolean facesOff() {
        return true;
    }

    @Override
    public Pattern createPattern(PatternContext ctx) {
        return new QiStationPattern(ctx);
    }

    /** Not used: the duel is decided by the tug gauge. */
    @Override
    public int defaultMaxHp() {
        return 1;
    }

    /** Mistakes are shared by both duellists, so they must never end the session; the orb decides. */
    @Override
    public int defaultMaxMistakes() {
        return 1000;
    }

    @Override
    public Penalty smallPenalty() {
        return SMALL;
    }

    @Override
    public Penalty bigPenalty() {
        return BIG;
    }

    @Override
    public Penalty bruteForcePenalty() {
        return BRUTE_FORCE;
    }

    @Override
    public PlayerPose pose() {
        return PlayerPose.PALM_PUSH;
    }

    @Override
    public String outcomeKey(Outcome outcome) {
        return switch (outcome) {
            case PERFECT, SUCCESS -> "bhgame.qi_station.outcome.win";
            case FAIL -> "bhgame.qi_station.outcome.lose";
            default -> super.outcomeKey(outcome);
        };
    }

    /** Eye level with the orb. */
    @Override
    public float lookHeight() {
        return 1.25F;
    }

    /** Palms an arm's length from the orb, one duellist on each side (D58). */
    @Override
    public double standDistance() {
        return 1.4;
    }

    @Override
    public double startReach() {
        return 2.5;
    }
}
