package net.deizyn.bhgame.compat.epicfight;

import net.deizyn.bhgame.BhGame;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.fml.ModList;

import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * Whether Epic Fight can be used. Never imports Epic Fight, so this class is safe to load without it; the only classes
 * that touch its API are the bridges in this package, called after {@link #isAvailable()} (docs/integrations/epicfight.md).
 */
public final class EpicFightCompat {
    public static final String MOD_ID = "epicfight";
    private static Boolean loaded;
    private static boolean linkageFailed;

    public static boolean isAvailable() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return loaded && !linkageFailed;
    }

    /** An Epic Fight call blew up with a LinkageError (API changed); stop using it for the rest of the game. */
    public static void markFailed(LinkageError error) {
        if (!linkageFailed) {
            linkageFailed = true;
            BhGame.LOGGER.error("[bhgame] Epic Fight API mismatch; falling back to vanilla poses", error);
        }
    }

    /**
     * Puts the player in Epic Fight's vanilla mode (water_yoke is drawn by the vanilla model only, D51). Returns
     * whether they were in Epic Fight mode, to give it back with {@link #restoreEpicFightMode}.
     */
    public static boolean forceVanillaMode(ServerPlayer player) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return EpicFightBridge.toVanillaMode(player);
        } catch (LinkageError error) {
            markFailed(error);
            return false;
        }
    }

    public static void restoreEpicFightMode(ServerPlayer player) {
        if (!isAvailable()) {
            return;
        }
        try {
            EpicFightBridge.toEpicFightMode(player);
        } catch (LinkageError error) {
            markFailed(error);
        }
    }

    /**
     * Puts the player in Epic Fight's battle mode (the dummy's fist attacks, D66). Returns whether they were in vanilla
     * mode, to give it back with {@link #restoreVanillaMode}.
     */
    public static boolean forceEpicFightMode(ServerPlayer player) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return EpicFightBridge.toEpicFightModeIfVanilla(player);
        } catch (LinkageError error) {
            markFailed(error);
            return false;
        }
    }

    public static void restoreVanillaMode(ServerPlayer player) {
        if (!isAvailable()) {
            return;
        }
        try {
            EpicFightBridge.toVanillaMode(player);
        } catch (LinkageError error) {
            markFailed(error);
        }
    }

    /** Plays a sparring dummy move like {@code /epicfight animator play} (D73); false if it could not. */
    public static boolean playMove(LivingEntity entity, ResourceLocation animation, float transition, LivingEntity target) {
        if (!isAvailable()) {
            return false;
        }
        try {
            return EpicFightMobBridge.play(entity, animation, transition, target);
        } catch (LinkageError error) {
            markFailed(error);
            return false;
        }
    }

    /**
     * The entity's Epic Fight patch, for a caller to keep and hand to {@link #isBusy(Object)} every tick instead of
     * looking the capability up each time; null without Epic Fight or before the patch is attached.
     */
    @Nullable
    public static Object patchOf(LivingEntity entity) {
        if (!isAvailable()) {
            return null;
        }
        try {
            return EpicFightMobBridge.patchOf(entity);
        } catch (LinkageError error) {
            markFailed(error);
            return null;
        }
    }

    /** The patch from {@link #patchOf} is in an Epic Fight action (attack, stun); false without Epic Fight. */
    public static boolean isBusy(@Nullable Object patch) {
        if (patch == null || !isAvailable()) {
            return false;
        }
        try {
            return EpicFightMobBridge.isBusy(patch);
        } catch (LinkageError error) {
            markFailed(error);
            return false;
        }
    }

    /** Whether Epic Fight knows this animation id; true without Epic Fight (nothing to check against). */
    public static boolean animationExists(ResourceLocation animation) {
        if (!isAvailable()) {
            return true;
        }
        try {
            return EpicFightMobBridge.exists(animation);
        } catch (LinkageError error) {
            markFailed(error);
            return true;
        }
    }

    /** Every animation id Epic Fight has, for command suggestions; empty without Epic Fight. */
    public static Stream<ResourceLocation> animationIds() {
        if (!isAvailable()) {
            return Stream.empty();
        }
        try {
            return EpicFightMobBridge.animationIds();
        } catch (LinkageError error) {
            markFailed(error);
            return Stream.empty();
        }
    }

    private EpicFightCompat() {
    }
}
