package net.deizyn.bhgame.core.outcome;

import net.deizyn.bhgame.BhGame;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Rolls a reward loot table and pops the items out of the block, like picking a flower. */
public final class Rewards {

    public static void drop(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player, ResourceLocation tableId) {
        LootTable table = level.getServer().getLootData().getLootTable(tableId);
        if (table == LootTable.EMPTY) {
            BhGame.LOGGER.warn("[bhgame] reward loot table {} is missing or empty", tableId);
            return;
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params)) {
            Block.popResource(level, pos, stack);
        }
    }

    private Rewards() {
    }
}
