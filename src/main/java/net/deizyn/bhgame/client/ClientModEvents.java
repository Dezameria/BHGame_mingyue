package net.deizyn.bhgame.client;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.client.pose.ClientPoses;
import net.deizyn.bhgame.client.render.TrainingBlockRenderer;
import net.deizyn.bhgame.client.render.mob.SparringDummyRenderer;
import net.deizyn.bhgame.client.render.carry.water_yoke.YokeLayer;
import net.deizyn.bhgame.compat.epicfight.EpicFightBridge;
import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.compat.epicfight.client.EpicFightClientBridge;
import net.deizyn.bhgame.registry.Minigames;
import net.deizyn.bhgame.registry.ModBlockEntities;
import net.deizyn.bhgame.registry.ModEntities;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BhGame.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TRAINING.get(), context -> new TrainingBlockRenderer());
        event.registerEntityRenderer(ModEntities.SPARRING_DUMMY.get(), SparringDummyRenderer::new);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new YokeLayer(renderer));
            }
        }
    }

    /**
     * Every game but the wooden dummy is drawn by the vanilla model only: Epic Fight steps aside for its players and
     * keeps them in vanilla mode (water_yoke D51, the rest D78). The wooden dummy keeps its players in battle mode (D66).
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!EpicFightCompat.isAvailable()) {
            return;
        }
        try {
            EpicFightClientBridge.renderVanillaFor(ClientPoses::usesVanillaModel);
            EpicFightBridge.lockVanillaMode(player -> player.level().isClientSide()
                    && ClientPoses.usesVanillaModel(player));
            EpicFightBridge.lockEpicFightMode(player -> player.level().isClientSide() && ClientPoses.isWingChun(player));
        } catch (LinkageError error) {
            EpicFightCompat.markFailed(error);
        }
    }

    /** The water in the placed yoke's buckets is tinted like vanilla water (tintindex 0). */
    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? WATER_COLOR : -1,
                Minigames.WATER_YOKE.get());
    }

    private static final int WATER_COLOR = 0x3F76E4;

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.ABORT);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("session", SessionHud::render);
    }

    private ClientModEvents() {
    }
}
