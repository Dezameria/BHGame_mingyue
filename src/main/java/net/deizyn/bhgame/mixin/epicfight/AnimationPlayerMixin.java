package net.deizyn.bhgame.mixin.epicfight;

import net.deizyn.bhgame.game.mob.sparring_dummy.SparringDummy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.types.LongHitAnimation;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Epic Fight only scales attack speed for players; mobs always play at 1x. A sparring dummy's moves play at its own
 * speed (D75): the base play speed {@code AnimationPlayer.tick} reads from the animation is multiplied by the dummy's
 * synced move speed, on server and client alike, so hits land when the pose does. Only action animations (attacks,
 * dodges...) are scaled, not walking or being knocked down.
 */
@Mixin(value = AnimationPlayer.class, remap = false)
public abstract class AnimationPlayerMixin {

    @ModifyVariable(method = "tick", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private float bhgame$sparringSpeed(float speed, LivingEntityPatch<?> entitypatch) {
        if (entitypatch.getOriginal() instanceof SparringDummy dummy) {
            DynamicAnimation animation = ((AnimationPlayer) (Object) this).getRealAnimation().get();
            if (animation instanceof ActionAnimation && !(animation instanceof LongHitAnimation)) {
                return speed * dummy.moveSpeed();
            }
        }
        return speed;
    }
}
