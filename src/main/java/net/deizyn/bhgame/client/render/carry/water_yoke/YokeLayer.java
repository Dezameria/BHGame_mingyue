package net.deizyn.bhgame.client.render.carry.water_yoke;

import com.mojang.blaze3d.vertex.PoseStack;
import net.deizyn.bhgame.client.pose.ClientPoses;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** The yoke on a carrier's shoulders, seen in third person by everyone (D51). */
public final class YokeLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float PX = 1.0F / 16.0F;

    public YokeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        if (!ClientPoses.isCarrying(player) || player.isInvisible()) {
            return;
        }
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // on the shoulders just behind the neck
        poseStack.translate(0.0F, -0.5F * PX, 3.0F * PX);
        YokeModel.render(poseStack, buffers, light, YokeVisuals.roll(player, partialTick),
                YokeVisuals.bucketSwing(player, partialTick), ClientPoses.data(player), getParentModel().body.xRot);
        poseStack.popPose();
    }
}
