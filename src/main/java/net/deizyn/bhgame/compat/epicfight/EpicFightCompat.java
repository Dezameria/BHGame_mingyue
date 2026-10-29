package net.deizyn.bhgame.compat.epicfight;

import net.deizyn.bhgame.BhGame;
import net.minecraftforge.fml.ModList;

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

    private EpicFightCompat() {
    }
}
