package net.deizyn.bhgame.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One gauge as {@code value + ratePerTick}; the client animates it until the next sync (D13). Players only. */
public record S2CGaugeSync(int index, int flags, float value, float ratePerTick) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(index);
        buf.writeByte(flags);
        buf.writeFloat(value);
        buf.writeFloat(ratePerTick);
    }

    public static S2CGaugeSync decode(FriendlyByteBuf buf) {
        return new S2CGaugeSync(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CGaugeSync message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onGauge(message));
    }
}
