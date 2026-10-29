package net.deizyn.bhgame;

import com.mojang.logging.LogUtils;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.registry.ModBlockEntities;
import net.deizyn.bhgame.registry.ModBlocks;
import net.deizyn.bhgame.registry.ModCreativeTabs;
import net.deizyn.bhgame.registry.ModItems;
import net.deizyn.bhgame.registry.Minigames;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BhGame.MODID)
public final class BhGame {
    public static final String MODID = "bhgame";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BhGame() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        Minigames.init();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener((ModConfigEvent.Loading event) -> BhGameConfig.onConfigEvent(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> BhGameConfig.onConfigEvent(event));
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BhGameConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BhGameConfig.CLIENT_SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(BhGameNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
