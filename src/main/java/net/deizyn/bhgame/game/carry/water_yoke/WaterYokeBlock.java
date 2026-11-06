package net.deizyn.bhgame.game.carry.water_yoke;

import net.deizyn.bhgame.core.block.CarryBlock;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.input.InputProfile;
import net.deizyn.bhgame.core.outcome.Penalties;
import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.pattern.Pattern;
import net.deizyn.bhgame.core.pattern.PatternContext;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * A yoke with two wooden buckets (D48-D52). Placed, it is filled and emptied one level at a time with vanilla buckets
 * on the side you click; Shift + right-click with empty hands lifts it onto your shoulders and you walk anywhere,
 * keeping it level with LMB / RMB. Water levels live in the blockstate and ride in the pose byte while carried
 * (left in bits 0-1, right in bits 2-3). Spec: docs/design/minigames/water_yoke.md
 */
public class WaterYokeBlock extends CarryBlock {
    public static final int MAX_WATER = 3;
    public static final IntegerProperty WATER_LEFT = IntegerProperty.create("water_left", 0, MAX_WATER);
    public static final IntegerProperty WATER_RIGHT = IntegerProperty.create("water_right", 0, MAX_WATER);

    private static final InputProfile INPUT = InputProfile.locked(InputKey.LMB, InputKey.RMB)
            .hold(InputKey.LMB, InputKey.RMB).carried();
    /** Both buckets ran dry: "aching shoulders", once. */
    static final Penalty EMPTY = Penalties.effect(MobEffects.MOVEMENT_SLOWDOWN, 100, 0);
    private static final VoxelShape SHAPE_NS = Block.box(0.0, 0.0, 4.0, 16.0, 9.0, 12.0);
    private static final VoxelShape SHAPE_EW = Block.box(4.0, 0.0, 0.0, 12.0, 9.0, 16.0);

    public WaterYokeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATER_LEFT, 0)
                .setValue(WATER_RIGHT, 0));
    }

    public static int data(int left, int right) {
        return left | right << 2;
    }

    public static int left(int data) {
        return data & 3;
    }

    public static int right(int data) {
        return data >> 2 & 3;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATER_LEFT, WATER_RIGHT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public int poseData(BlockState state) {
        return data(state.getValue(WATER_LEFT), state.getValue(WATER_RIGHT));
    }

    @Override
    public BlockState withPoseData(BlockState state, int data, Direction facing) {
        return state.setValue(FACING, facing).setValue(WATER_LEFT, left(data)).setValue(WATER_RIGHT, right(data));
    }

    @Override
    public Outcome carryOutcome(int startData, int endData) {
        int start = left(startData) + right(startData);
        int end = left(endData) + right(endData);
        if (start == 0) {
            return Outcome.ABORTED;
        }
        return end == start ? Outcome.PERFECT : end > 0 ? Outcome.SUCCESS : Outcome.FAIL;
    }

    /** A water bucket pours one level into the clicked side, an empty bucket scoops one out (D52). */
    @Override
    protected InteractionResult usePlaced(BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean pour = held.is(Items.WATER_BUCKET);
        if (!pour && !held.is(Items.BUCKET)) {
            return super.usePlaced(state, level, pos, player, hand, hit);
        }
        boolean leftFirst = clickedLeft(state, pos, hit.getLocation());
        IntegerProperty first = leftFirst ? WATER_LEFT : WATER_RIGHT;
        IntegerProperty second = leftFirst ? WATER_RIGHT : WATER_LEFT;
        IntegerProperty side = canChange(state, first, pour) ? first : canChange(state, second, pour) ? second : null;
        if (side == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(side, state.getValue(side) + (pour ? 1 : -1)), 3);
            ItemStack result = new ItemStack(pour ? Items.BUCKET : Items.WATER_BUCKET);
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, result));
            level.playSound(null, pos, pour ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS,
                    1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean canChange(BlockState state, IntegerProperty side, boolean pour) {
        int level = state.getValue(side);
        return pour ? level < MAX_WATER : level > 0;
    }

    /** The left bucket is on the left of whoever placed it, i.e. counter-clockwise from FACING. */
    private static boolean clickedLeft(BlockState state, BlockPos pos, Vec3 hit) {
        Direction left = state.getValue(FACING).getCounterClockWise();
        Vec3 offset = hit.subtract(Vec3.atCenterOf(pos));
        return offset.x * left.getStepX() + offset.z * left.getStepZ() >= 0.0;
    }

    @Override
    public InputProfile inputProfile() {
        return INPUT;
    }

    @Override
    public Pattern createPattern(PatternContext ctx) {
        return new WaterYokePattern(ctx);
    }

    /** Never reached: the session ends when the yoke is put down. */
    @Override
    public int defaultMaxHp() {
        return 1000;
    }

    /** The water level is the life, not a mistake count. */
    @Override
    public int defaultMaxMistakes() {
        return Integer.MAX_VALUE;
    }

    /** A spill is its own punishment. */
    @Override
    public Penalty smallPenalty() {
        return Penalty.NONE;
    }

    @Override
    public Penalty bigPenalty() {
        return EMPTY;
    }

    @Override
    public PlayerPose pose() {
        return PlayerPose.CARRY_YOKE;
    }

    @Override
    public String outcomeKey(Outcome outcome) {
        return "bhgame.water_yoke.outcome." + outcome.name().toLowerCase(Locale.ROOT);
    }
}
