package net.deizyn.bhgame.client.render.mob;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Vanilla renderer of the sparring dummy (player-shaped, 64x64 skin layout). With Epic Fight, its patched renderer
 * draws the same texture on the biped mesh (D73).
 */
public class SparringDummyRenderer extends HumanoidMobRenderer<SparringDummy, HumanoidModel<SparringDummy>> {
    private static final ResourceLocation TEXTURE = BhGame.id("textures/entity/sparring_dummy.png");

    public SparringDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(SparringDummy entity) {
        return TEXTURE;
    }
}
