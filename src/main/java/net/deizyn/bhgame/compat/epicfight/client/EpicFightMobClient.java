package net.deizyn.bhgame.compat.epicfight.client;

import net.deizyn.bhgame.registry.ModEntities;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer;

/** Draws the sparring dummy with Epic Fight's biped mesh, so its animations show (D73). */
public final class EpicFightMobClient {

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register(IEventBus modBus) {
        modBus.addListener((PatchedRenderersEvent.Add event) -> event.addPatchedEntityRenderer(
                ModEntities.SPARRING_DUMMY.get(),
                type -> new PHumanoidRenderer(Meshes.BIPED, event.getContext(), type)
                        .initLayerLast(event.getContext(), type)));
    }

    private EpicFightMobClient() {
    }
}
