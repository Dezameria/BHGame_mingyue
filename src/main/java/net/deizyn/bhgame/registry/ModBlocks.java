package net.deizyn.bhgame.registry;

import net.deizyn.bhgame.BhGame;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Minigame blocks are added through {@link Minigames#register}. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BhGame.MODID);

    private ModBlocks() {
    }
}
