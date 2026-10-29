package net.deizyn.bhgame.game.wooden_dummy;

import net.deizyn.bhgame.core.block.StationBlock;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Wing Chun wooden dummy, played like Audition: WASD arrow rows, then SPACE on the strike point.
 * P3 is a single block whose model reaches above it; the two-block version comes with the real model (P6).
 * Spec: docs/design/minigames/wooden_dummy.md
 */
public class WoodenDummyBlock extends StationBlock {
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);
    private static final InputProfile INPUT = InputProfile.locked(InputKey.UP, InputKey.LEFT, InputKey.DOWN,
            InputKey.RIGHT, InputKey.SPACE);

    public WoodenDummyBlock(Properties properties) {
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
    public Pattern createPattern(PatternContext ctx) {
        return new WoodenDummyPattern(ctx);
    }

    /** Not used: the run is endless and never ends by HP (D40). */
    @Override
    public int defaultMaxHp() {
        return 1;
    }

    @Override
    public Penalty smallPenalty() {
        return WoodenDummyPenalties.TIMEOUT;
    }

    /** Every end of an endless run is a swat (the pattern sets it too); see D40. */
    @Override
    public Penalty bigPenalty() {
        return WoodenDummyPenalties.SWAT;
    }

    @Override
    public Penalty bruteForcePenalty() {
        return WoodenDummyPenalties.BRUTE_FORCE;
    }

    @Override
    public PlayerPose pose() {
        return PlayerPose.WING_CHUN;
    }

    @Override
    public CueChannel channel() {
        return CueChannel.WORLD_ARROW;
    }

    /** Used only before stage 1 starts; after that the pattern shows the stage reached. */
    @Override
    public String outcomeKey(Outcome outcome) {
        return outcome == Outcome.FAIL ? "bhgame.wooden_dummy.outcome.fail" : super.outcomeKey(outcome);
    }

    /** Face the dummy's arms, not its foot. */
    @Override
    public float lookHeight() {
        return 1.3F;
    }

    /** Snapped to an arm's length on the nearest side when you start (D42). */
    @Override
    public double standDistance() {
        return 1.3;
    }

    /** Stand in front of it, an arm's length away. */
    @Override
    public double startReach() {
        return 2.5;
    }
}
