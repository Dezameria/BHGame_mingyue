package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.session.SessionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The drop key while carrying, looking at a block: put the carried block down there (D56). The server checks reach. */
public record C2SCarryPlace(BlockPos clicked, Direction face) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(clicked);
        buf.writeByte(face.get3DDataValue());
    }

    public static C2SCarryPlace decode(FriendlyByteBuf buf) {
        return new C2SCarryPlace(buf.readBlockPos(), Direction.from3DDataValue(buf.readUnsignedByte()));
    }

    public static void handle(C2SCarryPlace message, Supplier<NetworkEvent.Context> context) {
        BhGameNetwork.countReceived();
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            SessionManager.putDownAt(player, message.clicked(), message.face());
        }
    }
}
