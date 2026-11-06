package net.deizyn.bhgame.mixin.client;

import net.deizyn.bhgame.client.FreeLook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FREE_LOOK (D66): the camera takes its angles from {@link FreeLook} instead of the player's rotation. Changed at the
 * first {@code setRotation} in {@code setup}, before the third-person camera is moved back along them, so it orbits
 * the player in every perspective.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private Entity bhgame$entity;
    @Unique
    private float bhgame$partialTick;

    @Inject(method = "setup", at = @At("HEAD"))
    private void bhgame$remember(BlockGetter level, Entity entity, boolean detached, boolean mirror,
                                 float partialTick, CallbackInfo ci) {
        bhgame$entity = entity;
        bhgame$partialTick = partialTick;
    }

    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
            ordinal = 0), index = 0)
    private float bhgame$freeLookYaw(float yaw) {
        return FreeLook.yaw(bhgame$entity, bhgame$partialTick, yaw);
    }

    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
            ordinal = 0), index = 1)
    private float bhgame$freeLookPitch(float pitch) {
        return FreeLook.pitch(bhgame$entity, bhgame$partialTick, pitch);
    }
}
