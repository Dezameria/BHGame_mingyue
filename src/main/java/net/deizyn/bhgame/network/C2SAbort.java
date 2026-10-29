package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.session.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The player pressed the abort key. */
public record C2SAbort() {

    public void encode(FriendlyByteBuf buf) {
    }

    public static C2SAbort decode(FriendlyByteBuf buf) {
        return new C2SAbort();
    }

    public static void handle(C2SAbort message, Supplier<NetworkEvent.Context> context) {
        BhGameNetwork.countReceived();
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            SessionManager.leave(player.getUUID(), false);
        }
    }
}
