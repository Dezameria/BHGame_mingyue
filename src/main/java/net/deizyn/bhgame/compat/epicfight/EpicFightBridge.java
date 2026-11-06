package net.deizyn.bhgame.compat.epicfight;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import yesman.epicfight.api.forgeevent.ChangePlayerModeEvent;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

import java.util.function.Predicate;

/**
 * The only class that calls Epic Fight on both sides: player mode (vanilla / Epic Fight). water_yoke is drawn by the
 * vanilla model only (D51); the wooden dummy needs battle mode for its fist attacks (D66).
 * Call only when {@link EpicFightCompat#isAvailable()}, catching LinkageError.
 */
public final class EpicFightBridge {

    /** Switches to vanilla mode and syncs it; returns whether the player was in Epic Fight mode. */
    public static boolean toVanillaMode(ServerPlayer player) {
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        if (patch == null || !patch.isEpicFightMode()) {
            return false;
        }
        patch.toVanillaMode(true);
        return true;
    }

    public static void toEpicFightMode(ServerPlayer player) {
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        if (patch != null) {
            patch.toEpicFightMode(true);
        }
    }

    /** Switches to Epic Fight mode and syncs it; returns whether the player was in vanilla mode (D66). */
    public static boolean toEpicFightModeIfVanilla(ServerPlayer player) {
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
        if (patch == null || patch.isEpicFightMode()) {
            return false;
        }
        patch.toEpicFightMode(true);
        return true;
    }

    /**
     * Refuses every switch to Epic Fight mode for players matching {@code locked} (the mode key, a skill, or a sync).
     * Register once per side; the predicate must check the side itself.
     */
    public static void lockVanillaMode(Predicate<Player> locked) {
        lockMode(PlayerPatch.PlayerMode.EPICFIGHT, locked);
    }

    /** The other way round: refuses every switch to vanilla mode for players matching {@code locked} (D66). */
    public static void lockEpicFightMode(Predicate<Player> locked) {
        lockMode(PlayerPatch.PlayerMode.VANILLA, locked);
    }

    private static void lockMode(PlayerPatch.PlayerMode refused, Predicate<Player> locked) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ChangePlayerModeEvent.class, event -> {
            if (event.getPlayerMode() == refused && locked.test(event.getPlayerPatch().getOriginal())) {
                event.setCanceled(true);
            }
        });
    }

    private EpicFightBridge() {
    }
}
