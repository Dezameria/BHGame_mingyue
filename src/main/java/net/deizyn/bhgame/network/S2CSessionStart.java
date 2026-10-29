package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.input.InputProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sent to each participant when a session begins, and earlier with {@code waiting} to the first player of a
 * multi-player game. {@code role} picks this client's own cues; {@code targets} is the target set (empty for block games).
 */
public record S2CSessionStart(BlockPos pos, String gameId, InputProfile profile, int maxHp, int focusTicks,
                              List<BlockPos> targets, int role, int players, boolean waiting, float lookHeight,
                              CueChannel channel) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeUtf(gameId, 64);
        profile.write(buf);
        buf.writeVarInt(maxHp);
        buf.writeVarInt(focusTicks);
        buf.writeVarInt(targets.size());
        for (BlockPos target : targets) {
            buf.writeBlockPos(target);
        }
        buf.writeByte(role);
        buf.writeByte(players);
        buf.writeBoolean(waiting);
        buf.writeFloat(lookHeight);
        buf.writeByte(channel.ordinal());
    }

    public static S2CSessionStart decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String gameId = buf.readUtf(64);
        InputProfile profile = InputProfile.read(buf);
        int maxHp = buf.readVarInt();
        int focusTicks = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), 32);
        List<BlockPos> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(buf.readBlockPos());
        }
        int role = buf.readUnsignedByte();
        int players = buf.readUnsignedByte();
        boolean waiting = buf.readBoolean();
        float lookHeight = buf.readFloat();
        CueChannel channel = CueChannel.byId(buf.readUnsignedByte());
        return new S2CSessionStart(pos, gameId, profile, maxHp, focusTicks, targets, role, players, waiting,
                lookHeight, channel);
    }

    public static void handle(S2CSessionStart message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onSessionStart(message));
    }
}
