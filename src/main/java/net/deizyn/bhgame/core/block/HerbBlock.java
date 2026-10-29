package net.deizyn.bhgame.core.block;

import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.outcome.Rewards;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.core.outcome.Stats;
import net.deizyn.bhgame.core.session.Outcome;
import net.deizyn.bhgame.core.session.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * A spirit herb: used once. Winning drops loot and removes it; failing or breaking it by hand ruins it.
 * Breaking by hand takes ~1.5 s (hardness 1.0) so a stray left click can't ruin it (D27).
 */
public abstract class HerbBlock extends TrainingBlock {
    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    protected HerbBlock(Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties herbProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.PLANT)
                .noCollission()
                .noOcclusion()
                // hand-break time = 30 ticks per hardness point -> 1.5 s; huge blast resistance
                .strength(1.0F, 1200.0F)
                .sound(SoundType.GRASS)
                .pushReaction(PushReaction.BLOCK)
                // counts as solid for fluids, so water can't wash the herb away
                .forceSolidOn();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("bhgame.herb.brute_warning"), true);
        }
        super.attack(state, level, pos, player);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && !serverPlayer.isCreative()) {
            SessionManager.abortAt(serverLevel, pos);
            bigPenalty().apply(serverPlayer, pos);
            serverPlayer.displayClientMessage(Component.translatable("bhgame.herb.brute_force"), true);
            Stats.record(serverPlayer, gameId(), Outcome.FAIL, 0, 0, pos, true);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Standing on a neighbouring block (diagonals included), kneeling over the herb. */
    @Override
    public double startReach() {
        return 1.75;
    }

    @Override
    public double startHeightTolerance() {
        return 1.0;
    }

    @Override
    public PlayerPose pose() {
        return PlayerPose.PULL_HERB;
    }

    @Override
    public String outcomeKey(Outcome outcome) {
        return "bhgame.herb.outcome." + outcome.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void onWin(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players, Outcome outcome,
                      TrainingBlockEntity blockEntity) {
        boolean perfect = outcome == Outcome.PERFECT;
        // one drop at the herb, even for two pullers: sharing it is up to them (twin_grass spec)
        Rewards.drop(level, pos, players.isEmpty() ? null : players.get(0),
                blockEntity.lootTable(perfect, defaultLootTable(perfect)));
        removeHerb(level, pos, state);
    }

    @Override
    public void onFail(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players, Penalty penalty,
                       TrainingBlockEntity blockEntity) {
        for (ServerPlayer player : players) {
            penalty.apply(player, pos);
        }
        removeHerb(level, pos, state);
    }

    /** The roots tear when one puller lets go. */
    @Override
    public void onAbandoned(ServerLevel level, BlockPos pos, BlockState state, TrainingBlockEntity blockEntity) {
        removeHerb(level, pos, state);
    }

    private static void removeHerb(ServerLevel level, BlockPos pos, BlockState state) {
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.removeBlock(pos, false);
    }
}
