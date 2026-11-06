package net.deizyn.bhgame;

import com.mojang.logging.LogUtils;
import net.deizyn.bhgame.compat.epicfight.EpicFightBridge;
import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.compat.epicfight.EpicFightMobBridge;
import net.deizyn.bhgame.compat.epicfight.client.EpicFightMobClient;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.core.session.SessionManager;
import net.deizyn.bhgame.game.carry.water_yoke.YokePrototype;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.registry.ModBlockEntities;
import net.deizyn.bhgame.registry.ModBlocks;
import net.deizyn.bhgame.registry.ModCreativeTabs;
import net.deizyn.bhgame.registry.ModEntities;
import net.deizyn.bhgame.registry.ModItems;
import net.deizyn.bhgame.registry.Minigames;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
        ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener((ModConfigEvent.Loading event) -> BhGameConfig.onConfigEvent(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> BhGameConfig.onConfigEvent(event));
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, BhGameConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BhGameConfig.CLIENT_SPEC);
        if (EpicFightCompat.isAvailable()) {
            try {
                // every game but the wooden dummy keeps the player in vanilla mode (D51, D78)
                EpicFightBridge.lockVanillaMode(player -> player instanceof ServerPlayer serverPlayer
                        && (YokePrototype.isCarrying(serverPlayer.getUUID())
                        || PlayerPose.usesVanillaModel(SessionManager.poseOf(serverPlayer))));
                EpicFightBridge.lockEpicFightMode(player -> player instanceof ServerPlayer serverPlayer
                        && SessionManager.poseOf(serverPlayer) == PlayerPose.WING_CHUN);
                EpicFightMobBridge.register(modBus);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EpicFightMobClient.register(modBus));
            } catch (LinkageError error) {
                EpicFightCompat.markFailed(error);
            }
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(BhGameNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
