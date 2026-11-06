package net.deizyn.bhgame.registry;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringBatonItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Minigame block items are added through {@link Minigames#register}. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BhGame.MODID);

    public static final RegistryObject<Item> SPARRING_DUMMY_SPAWN_EGG = ITEMS.register("sparring_dummy_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SPARRING_DUMMY, 0x9C7042, 0x8B1E1E, new Item.Properties()));
    public static final RegistryObject<Item> SPARRING_BATON = ITEMS.register("sparring_baton",
            () -> new SparringBatonItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
