package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.pose.PlayerPose;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells nearby clients which pose a player shows. Sent only when a session starts or ends, when the pose's data
 * changes (water_yoke: a spill or refill), and to a client that starts tracking a player mid-session; never per key
 * press. {@code data} is one byte whose meaning depends on the pose (0 for most).
 */
public record S2CPlayerPose(int entityId, PlayerPose pose, int data) {

    public S2CPlayerPose(int entityId, PlayerPose pose) {
        this(entityId, pose, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(pose.ordinal());
        buf.writeByte(data);
    }

    public static S2CPlayerPose decode(FriendlyByteBuf buf) {
        return new S2CPlayerPose(buf.readVarInt(), PlayerPose.byId(buf.readUnsignedByte()), buf.readUnsignedByte());
    }

    public static void handle(S2CPlayerPose message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.deizyn.bhgame.client.pose.ClientPoses.set(
                message.entityId(), message.pose(), message.data()));
    }
}
