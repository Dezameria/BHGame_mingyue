package net.deizyn.bhgame.mixin.client;

import net.deizyn.bhgame.client.pose.ClientPoses;
import net.deizyn.bhgame.client.render.carry.water_yoke.YokeVisuals;
import net.deizyn.bhgame.client.render.station.qi_station.QiEffects;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * water_yoke (D51): both hands on the yoke; qi_station: both palms out at the orb. At the end of HumanoidModel's setupAnim, so PlayerModel still copies the
 * arms to the sleeves afterwards and armor layers copy them from the parent model.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void bhgame$holdYoke(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                 float netHeadYaw, float headPitch, CallbackInfo ci) {
        // ageInTicks 0 is the first-person hand render: hidden while carrying, left vanilla for the palms
        if (!(entity instanceof Player player) || ageInTicks == 0.0F) {
            return;
        }
        if (ClientPoses.isCarrying(player)) {
            YokeVisuals.poseArms((HumanoidModel<?>) (Object) this, player, ageInTicks - player.tickCount);
        } else if (ClientPoses.isPalmPushing(player)) {
            ClientPoses.posePalmPush((HumanoidModel<?>) (Object) this, ageInTicks, QiEffects.isDrawing(player));
        }
    }
}
