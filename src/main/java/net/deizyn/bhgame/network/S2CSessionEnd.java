package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.session.Outcome;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CSessionEnd(Outcome outcome) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(outcome.ordinal());
    }

    public static S2CSessionEnd decode(FriendlyByteBuf buf) {
        return new S2CSessionEnd(Outcome.byId(buf.readUnsignedByte()));
    }

    public static void handle(S2CSessionEnd message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onSessionEnd(message));
    }
}
