package net.deizyn.bhgame.compat.epicfight.client;

import net.deizyn.bhgame.core.pose.PlayerAction;
import net.minecraft.world.entity.player.Player;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * The only class that calls Epic Fight (client side). Animations are played with {@code playAnimationInClientSide}
 * on each client, never on the server: Epic Fight's fist animations are attacks, and played on the server they would
 * run real hit detection on whoever stands in front of the dummy (D41).
 * Call only when {@link net.deizyn.bhgame.compat.epicfight.EpicFightCompat#isAvailable()}.
 */
public final class EpicFightClientBridge {

    /** Returns false if the player has no Epic Fight patch, so the caller uses the vanilla fallback. */
    public static boolean play(Player player, PlayerAction action) {
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, LivingEntityPatch.class);
        if (patch == null) {
            return false;
        }
        AssetAccessor<? extends StaticAnimation> animation = switch (action) {
            case PUNCH_RIGHT -> Animations.FIST_AUTO1;
            case PUNCH_LEFT -> Animations.FIST_AUTO2;
            case PUNCH_FINISH -> Animations.FIST_AUTO3;
            case KNOCKED_BACK -> Animations.BIPED_HIT_SHORT;
        };
        patch.playAnimationInClientSide(animation, 0.0F);
        return true;
    }

    private EpicFightClientBridge() {
    }
}
