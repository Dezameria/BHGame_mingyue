package net.deizyn.bhgame.core.session;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who must not be moved by other mods' animations right now (D44): on the server, anyone in a session; on a client,
 * the local player while its own session runs. Read from the Epic Fight mixin on both sides, so it must not touch
 * client classes.
 */
public final class TrainingPins {
    /** Set and cleared by the client's session; only read on the client thread. */
    @Nullable
    private static volatile UUID clientPinned;

    public static boolean isPinned(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (player.level().isClientSide) {
            return player.getUUID().equals(clientPinned);
        }
        return SessionManager.isInSession(player.getUUID());
    }

    public static void setClientPinned(@Nullable UUID playerId) {
        clientPinned = playerId;
    }

    private TrainingPins() {
    }
}
