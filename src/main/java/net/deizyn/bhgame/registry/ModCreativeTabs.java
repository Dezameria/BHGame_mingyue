package net.deizyn.bhgame.registry;

import net.deizyn.bhgame.BhGame;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BhGame.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bhgame"))
            .icon(() -> new ItemStack(Minigames.PULSE_GRASS.get()))
            .displayItems((params, output) -> {
                Minigames.all().forEach(block -> output.accept(block.get()));
                output.accept(ModItems.SPARRING_DUMMY_SPAWN_EGG.get());
                output.accept(ModItems.SPARRING_BATON.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
