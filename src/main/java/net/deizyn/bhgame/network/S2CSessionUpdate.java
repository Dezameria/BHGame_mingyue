package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.cue.CueResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Result of one cue plus the new internal HP/mistakes. {@code cueId} is -1 for a press during a FEINT gap. */
public record S2CSessionUpdate(int cueId, CueResult result, int hp, int mistakes) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(cueId + 1);
        buf.writeByte(result.ordinal());
        buf.writeVarInt(hp);
        buf.writeVarInt(mistakes);
    }

    public static S2CSessionUpdate decode(FriendlyByteBuf buf) {
        return new S2CSessionUpdate(buf.readVarInt() - 1, CueResult.byId(buf.readUnsignedByte()), buf.readVarInt(),
                buf.readVarInt());
    }

    public static void handle(S2CSessionUpdate message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onUpdate(message));
    }
}
