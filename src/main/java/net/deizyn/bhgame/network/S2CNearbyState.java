package net.deizyn.bhgame.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * What everyone near a session's block may see (D60): the public gauge as {@code value + ratePerTick}, the
 * participants' entity ids and held keys, and an optional one-shot event the game defines ({@link #SYNC} = none).
 * Cosmetic only, for the orb and particles of qi_station. Sent to every client tracking the block's chunk, the
 * participants included, throttled by the session; events are never sent per frame.
 */
public record S2CNearbyState(BlockPos pos, int event, int role, int data, int[] entityIds, int[] heldKeys,
                             float value, float ratePerTick) {
    /** No event: the state changed (a held key, the gauge's rate). */
    public static final int SYNC = 0;
    public static final int NO_ENTITY = -1;

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(event);
        buf.writeByte(role);
        buf.writeVarInt(data);
        buf.writeByte(entityIds.length);
        for (int i = 0; i < entityIds.length; i++) {
            buf.writeVarInt(entityIds[i] + 1);
            buf.writeByte(heldKeys[i]);
        }
        buf.writeFloat(value);
        buf.writeFloat(ratePerTick);
    }

    public static S2CNearbyState decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int event = buf.readUnsignedByte();
        int role = buf.readUnsignedByte();
        int data = buf.readVarInt();
        int count = Math.min(buf.readUnsignedByte(), 8);
        int[] entityIds = new int[count];
        int[] heldKeys = new int[count];
        for (int i = 0; i < count; i++) {
            entityIds[i] = buf.readVarInt() - 1;
            heldKeys[i] = buf.readUnsignedByte();
        }
        return new S2CNearbyState(pos, event, role, data, entityIds, heldKeys, buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CNearbyState message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onNearby(message));
    }
}
