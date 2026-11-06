package net.deizyn.bhgame.mixin.client;

import net.deizyn.bhgame.client.FreeLook;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** FREE_LOOK (D66): the mouse turns the camera, not the training player's body. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {

    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void bhgame$freeLook(double yRot, double xRot, CallbackInfo ci) {
        if (FreeLook.turn((Entity) (Object) this, yRot, xRot)) {
            ci.cancel();
        }
    }
}
