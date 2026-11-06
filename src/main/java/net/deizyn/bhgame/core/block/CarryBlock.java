package net.deizyn.bhgame.core.block;

import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A training object you pick up and carry around (D48, D51): Shift + right-click with empty hands lifts it out of the
 * world and starts the session, putting it down anywhere ends it. While carried the session has no anchor block, so
 * it is not range-checked and the block's removal does not abort it; every way the session ends puts the block back
 * (or drops it as an item). The game's state rides in the blockstate and the pose byte ({@link #poseData}).
 */
public abstract class CarryBlock extends TrainingBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    protected CarryBlock(Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties carryProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .noOcclusion()
                // the thin shape alone would let flowing water wash it away, and its empty loot table would lose it
                .forceSolidOn()
                // unbreakable outside creative (BruteForce=KEEP); creative players still remove it instantly
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.WOOD)
                .pushReaction(PushReaction.BLOCK);
    }

    /** The pose byte bystanders get while this block is carried, read from its state. */
    public abstract int poseData(BlockState state);

    /** The block to put back with {@code data} from the session, facing {@code facing}. */
    public abstract BlockState withPoseData(BlockState state, int data, Direction facing);

    /** The result of putting it down normally, from the pose byte when lifted and now. */
    public abstract Outcome carryOutcome(int startData, int endData);

    /** Right-click on the placed block without lifting it (Shift + empty hands lifts). */
    protected InteractionResult usePlaced(BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("bhgame.carry.how_to_lift"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public double startReach() {
        return 1.75;
    }

    @Override
    public double startHeightTolerance() {
        return 1.0;
    }

    @Override
    protected boolean abortsSessionOnRemove() {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("bhgame.carry.hands_full"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return super.use(state, level, pos, player, hand, hit);
        }
        return usePlaced(state, level, pos, player, hand, hit);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Never used: the session puts the block back itself. */
    @Override
    public void onWin(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players, Outcome outcome,
                      TrainingBlockEntity blockEntity) {
    }

    @Override
    public void onFail(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players, Penalty penalty,
                       TrainingBlockEntity blockEntity) {
    }
}
