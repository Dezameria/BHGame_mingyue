package net.deizyn.bhgame.compat.epicfight;

import net.deizyn.bhgame.registry.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import javax.annotation.Nullable;
import java.util.stream.Stream;

/**
 * The sparring dummy's side of Epic Fight (D73): its patch and armature, and playing a move the way
 * {@code /epicfight animator play} does. Call only through {@link EpicFightCompat}.
 */
public final class EpicFightMobBridge {

    public static void register(IEventBus modBus) {
        modBus.addListener((EntityPatchRegistryEvent event) -> {
            event.getTypeEntry().put(ModEntities.SPARRING_DUMMY.get(), entity -> SparringDummyPatch::new);
            Armatures.registerEntityTypeArmature(ModEntities.SPARRING_DUMMY.get(), Armatures.BIPED);
        });
    }

    /** Faces the target and plays the animation synchronized (server side, so attacks hit). */
    public static boolean play(LivingEntity entity, ResourceLocation animationId, float transition, LivingEntity target) {
        AnimationManager.AnimationAccessor<? extends StaticAnimation> animation = AnimationManager.byKey(animationId);
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
        if (animation == null || patch == null) {
            return false;
        }
        patch.rotateTo(target, 360F, true);
        patch.playAnimationSynchronized(animation, transition);
        return true;
    }

    @Nullable
    public static Object patchOf(LivingEntity entity) {
        return EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
    }

    /** Playing an action (attack, hit stun, knockdown): don't start another move yet. */
    public static boolean isBusy(Object patch) {
        return patch instanceof LivingEntityPatch<?> living && living.getEntityState().inaction();
    }

    public static boolean exists(ResourceLocation animationId) {
        return AnimationManager.byKey(animationId) != null;
    }

    public static Stream<ResourceLocation> animationIds() {
        return AnimationManager.getInstance().getAnimations(animation -> animation.registryName() != null)
                .keySet().stream();
    }

    private EpicFightMobBridge() {
    }
}
