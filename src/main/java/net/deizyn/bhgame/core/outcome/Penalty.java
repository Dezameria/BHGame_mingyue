package net.deizyn.bhgame.core.outcome;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Something bad that happens to the player only (never to bystanders, see D25). */
@FunctionalInterface
public interface Penalty {
    Penalty NONE = (player, source) -> {
    };

    void apply(ServerPlayer player, BlockPos source);
}
