package net.deizyn.bhgame.core.block;

import net.deizyn.bhgame.core.outcome.Penalty;
import net.deizyn.bhgame.core.outcome.Rewards;
import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.List;

/**
 * A permanent training station (wooden dummy, wind mat, candle altar): nothing about the block changes when a session
 * ends, so it is ready for the next player at once. It cannot be broken outside creative; hitting it makes it swat
 * back (D19, {@code BruteForce=KEEP}). Loot drops only when the admin set a loot table in NBT.
 */
public abstract class StationBlock extends TrainingBlock {

    protected StationBlock(Properties properties) {
        super(properties);
    }

    public static BlockBehaviour.Properties stationProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .noOcclusion()
                // unbreakable outside creative, like bedrock; creative players still remove it instantly
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.WOOD)
                .pushReaction(PushReaction.BLOCK);
    }

    /** What hitting the station by hand does to the player. Defaults to the game's small penalty. */
    public Penalty bruteForcePenalty() {
        return smallPenalty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()
                && !serverPlayer.isSpectator()) {
            bruteForcePenalty().apply(serverPlayer, pos);
            serverPlayer.displayClientMessage(Component.translatable("bhgame.station.brute_force"), true);
        }
        super.attack(state, level, pos, player);
    }

    @Override
    public void onWin(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players, Outcome outcome,
                      TrainingBlockEntity blockEntity) {
        ResourceLocation table = blockEntity.customLootTable(outcome == Outcome.PERFECT);
        if (table != null) {
            Rewards.drop(level, pos, players.isEmpty() ? null : players.get(0), table);
        }
    }

    @Override
    public void onFail(ServerLevel level, BlockPos pos, BlockState state, List<ServerPlayer> players, Penalty penalty,
                       TrainingBlockEntity blockEntity) {
        for (ServerPlayer player : players) {
            penalty.apply(player, pos);
        }
    }
}
