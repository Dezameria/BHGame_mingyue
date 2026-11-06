package net.deizyn.bhgame.game.station.wooden_dummy;

import net.deizyn.bhgame.core.block.StationBlock;
import net.deizyn.bhgame.core.block.TrainingBlockEntity;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Wing Chun wooden dummy, played like Audition: WASD arrow rows, then SPACE on the strike point.
 * Two blocks tall like its model (D66): the lower half holds the block entity and draws the dummy up to y 16, the
 * upper half draws the rest of it (D71: one model reaching into the block above was lit black at the top) and passes clicks
 * and hits down. It turns in 16 steps to face whoever placed it, like a sign (D71).
 * Spec: docs/design/minigames/wooden_dummy.md
 */
public class WoodenDummyBlock extends StationBlock {
    /** 22.5 degree steps clockwise seen from above; 0 = the model's front looks north. */
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 16.0, 12.0);
    /** The post and its cap end 15 pixels into the upper block. */
    private static final VoxelShape TOP_SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 15.0, 12.0);
    private static final InputProfile INPUT = InputProfile.locked(InputKey.UP, InputKey.LEFT, InputKey.DOWN,
            InputKey.RIGHT, InputKey.SPACE).freeLook();

    public WoodenDummyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROTATION, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, HALF);
    }

    /** Needs room for the upper half; the front turns to the player placing it, to the nearest 22.5 degrees. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        // the player looks along their yaw; a front turned clockwise by that much looks back at them
        int rotation = Mth.floor(context.getRotation() * 16.0F / 360.0F + 0.5F) & 15;
        return defaultBlockState().setValue(ROTATION, rotation);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * The upper half goes when the lower one does. The lower half never removes itself: dummies placed before D66
     * have no upper half and must stay.
     */
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER && direction == Direction.DOWN
                && !(neighbor.is(this) && neighbor.getValue(HALF) == DoubleBlockHalf.LOWER)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** Breaking the upper half (creative) takes the lower one too, without a drop, like a door. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? super.newBlockEntity(pos, state) : null;
    }

    /** Sessions live on the lower half, so a click on the upper one starts there. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(pos.below());
            return lower.is(this) ? super.use(lower, level, pos.below(), player, hand, hit) : InteractionResult.PASS;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(pos.below());
            if (lower.is(this)) {
                super.attack(lower, level, pos.below(), player);
            }
            return;
        }
        super.attack(state, level, pos, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? TOP_SHAPE : SHAPE;
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

    /** Not used: every swat ends the run (D77), so nothing is a plain mistake any more. */
    @Override
    public Penalty smallPenalty() {
        return WoodenDummyPenalties.SWAT;
    }

    /**
     * The swat that ends a run lands with a bang everyone around sees and hears (D77): a burst where the dummy's arm
     * meets the player, dust kicked up at their feet, a wooden crack. Played before the knockback moves them.
     */
    @Override
    public void onFail(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players, Penalty penalty,
                       TrainingBlockEntity blockEntity) {
        Vec3 centre = Vec3.atBottomCenterOf(pos);
        for (ServerPlayer player : players) {
            Vec3 feet = player.position();
            Vec3 toDummy = new Vec3(centre.x - feet.x, 0.0, centre.z - feet.z);
            Vec3 hit = feet.add(toDummy.lengthSqr() > 1.0E-4 ? toDummy.normalize().scale(0.45) : Vec3.ZERO)
                    .add(0.0, 1.1, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 30, 0.25, 0.25, 0.25, 0.6);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, hit.x, hit.y, hit.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.POOF, feet.x, feet.y + 0.1, feet.z, 16, 0.4, 0.05, 0.4, 0.08);
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.9F,
                    0.9F + level.random.nextFloat() * 0.2F);
            level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F,
                    0.8F);
        }
        super.onFail(level, pos, state, players, penalty, blockEntity);
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

    /**
     * Everyone around hears each punch land on the dummy (D66), the same thud and crack the puncher's client plays
     * (PunchEffects), a little louder so it carries.
     */
    @Override
    public void playActionSound(ServerLevel level, ServerPlayer player, PlayerAction action) {
        if (action == PlayerAction.KNOCKED_BACK) {
            return;
        }
        Vec3 at = player.getEyePosition().add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(0.95))
                .add(0.0, -0.3, 0.0);
        if (action == PlayerAction.PUNCH_FINISH) {
            level.playSound(player, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.9F);
        } else {
            level.playSound(player, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F,
                    1.1F + level.random.nextFloat() * 0.2F);
        }
    }

    /** The punches are Epic Fight's fist attacks, so the player trains in its battle mode (D66). */
    @Override
    public boolean forcesEpicFightMode() {
        return true;
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
