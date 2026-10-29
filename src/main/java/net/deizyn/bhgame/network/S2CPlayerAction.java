package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A one-shot movement of a player (a punch on the dummy) and the arrow key behind it, for the clients around them.
 * The player's own client plays it locally when the key is pressed, so this is never sent back to them. Throttled on
 * the server to one every few ticks per player (D41, D42).
 */
public record S2CPlayerAction(int entityId, PlayerAction action, @Nullable InputKey key) {
    private static final int NO_KEY = 0xFF;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(action.ordinal());
        buf.writeByte(key == null ? NO_KEY : key.ordinal());
    }

    public static S2CPlayerAction decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        PlayerAction action = PlayerAction.byId(buf.readUnsignedByte());
        int keyId = buf.readUnsignedByte();
        return new S2CPlayerAction(entityId, action, keyId == NO_KEY ? null : InputKey.byId(keyId));
    }

    public static void handle(S2CPlayerAction message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.deizyn.bhgame.client.pose.ClientPoses.action(
                message.entityId(), message.action(), message.key()));
    }
}
