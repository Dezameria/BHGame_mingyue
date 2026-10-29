package net.deizyn.bhgame.registry;

import net.deizyn.bhgame.BhGame;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Minigame block items are added through {@link Minigames#register}. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BhGame.MODID);

    private ModItems() {
    }
}
