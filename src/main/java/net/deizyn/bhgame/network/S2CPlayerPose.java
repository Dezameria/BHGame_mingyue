package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.pose.PlayerPose;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells nearby clients which pose a player shows. Sent only when a session starts or ends, and to a client that
 * starts tracking a player mid-session; never per key press.
 */
public record S2CPlayerPose(int entityId, PlayerPose pose) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(pose.ordinal());
    }

    public static S2CPlayerPose decode(FriendlyByteBuf buf) {
        return new S2CPlayerPose(buf.readVarInt(), PlayerPose.byId(buf.readUnsignedByte()));
    }

    public static void handle(S2CPlayerPose message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.pose.ClientPoses.set(message.entityId(), message.pose()));
    }
}
