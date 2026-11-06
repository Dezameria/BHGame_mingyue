package net.deizyn.bhgame.compat.carryon;

import net.deizyn.bhgame.BhGame;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reads whether Carry On has something on a player's back. Never imports Carry On: the two calls are looked up by name
 * (checked against carryon-forge-1.20.1-2.1.2.7), and any failure means "not carrying" for the rest of the game
 * (docs/design/minigames/water_yoke.md, Carry On).
 */
public final class CarryOnCompat {
    public static final String MOD_ID = "carryon";
    private static boolean resolved;
    private static MethodHandle getCarryData;
    private static MethodHandle isCarrying;

    public static boolean isCarrying(Player player) {
        if (!resolved) {
            resolve();
        }
        if (getCarryData == null) {
            return false;
        }
        try {
            return (boolean) isCarrying.invoke(getCarryData.invoke(player));
        } catch (Throwable error) {
            BhGame.LOGGER.error("[bhgame] Carry On API mismatch; no longer checking what players carry", error);
            getCarryData = null;
            return false;
        }
    }

    private static void resolve() {
        resolved = true;
        if (ModList.get() == null || !ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            Class<?> data = Class.forName("tschipp.carryon.common.carry.CarryOnData");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            getCarryData = lookup.findStatic(manager, "getCarryData", MethodType.methodType(data, Player.class));
            isCarrying = lookup.findVirtual(data, "isCarrying", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError error) {
            BhGame.LOGGER.error("[bhgame] Carry On API mismatch; no longer checking what players carry", error);
            getCarryData = null;
        }
    }

    private CarryOnCompat() {
    }
}
