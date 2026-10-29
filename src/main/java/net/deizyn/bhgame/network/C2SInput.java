package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.session.SessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A key edge from the client. No client timestamp (the server uses arrival time + ping grace).
 * The optional aim payload (yaw/pitch) is sent when the game's input profile asks for it (AIM cues, D24).
 */
public record C2SInput(InputKey key, boolean press, boolean hasAim, float yaw, float pitch) {
    private static final int FLAG_PRESS = 1;
    private static final int FLAG_AIM = 2;

    public static C2SInput of(InputKey key, boolean press) {
        return new C2SInput(key, press, false, 0.0F, 0.0F);
    }

    /** With where the player looks, for games whose input profile asks for an aim payload (AIM cues). */
    public static C2SInput aimed(InputKey key, boolean press, float yaw, float pitch) {
        return new C2SInput(key, press, true, yaw, pitch);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(key.ordinal());
        buf.writeByte((press ? FLAG_PRESS : 0) | (hasAim ? FLAG_AIM : 0));
        if (hasAim) {
            buf.writeFloat(yaw);
            buf.writeFloat(pitch);
        }
    }

    public static C2SInput decode(FriendlyByteBuf buf) {
        InputKey key = InputKey.byId(buf.readUnsignedByte());
        int flags = buf.readUnsignedByte();
        boolean hasAim = (flags & FLAG_AIM) != 0;
        float yaw = hasAim ? buf.readFloat() : 0.0F;
        float pitch = hasAim ? buf.readFloat() : 0.0F;
        return new C2SInput(key, (flags & FLAG_PRESS) != 0, hasAim, yaw, pitch);
    }

    public static void handle(C2SInput message, Supplier<NetworkEvent.Context> context) {
        BhGameNetwork.countReceived();
        ServerPlayer player = context.get().getSender();
        if (player != null && message.key != null) {
            SessionManager.onInput(player, message.key, message.press);
        }
    }
}
