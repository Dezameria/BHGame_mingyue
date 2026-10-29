package net.deizyn.bhgame.mixin.epicfight;

import net.deizyn.bhgame.core.session.TrainingPins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Epic Fight's attack and hit animations move the body (root motion) in {@code ActionAnimation.move}. While a player
 * trains, the animation still plays but the body stays where the session put it (D44). Epic Fight's own classes are
 * not obfuscated, hence {@code remap = false}.
 */
@Mixin(value = ActionAnimation.class, remap = false)
public abstract class ActionAnimationMixin {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void bhgame$stayPutWhileTraining(LivingEntityPatch<?> entitypatch,
                                            AssetAccessor<? extends DynamicAnimation> animation, CallbackInfo ci) {
        if (TrainingPins.isPinned(entitypatch.getOriginal())) {
            ci.cancel();
        }
    }
}
