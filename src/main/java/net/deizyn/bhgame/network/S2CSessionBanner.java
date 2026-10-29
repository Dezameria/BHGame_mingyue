package net.deizyn.bhgame.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A line in large letters at the top of the player's screen, e.g. "Stage 4" (D40). Players of the session only. */
public record S2CSessionBanner(String translationKey, int value) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(translationKey, 128);
        buf.writeVarInt(value);
    }

    public static S2CSessionBanner decode(FriendlyByteBuf buf) {
        return new S2CSessionBanner(buf.readUtf(128), buf.readVarInt());
    }

    public static void handle(S2CSessionBanner message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onBanner(message));
    }
}
