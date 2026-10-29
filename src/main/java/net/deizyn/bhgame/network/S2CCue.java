package net.deizyn.bhgame.network;

import net.deizyn.bhgame.core.cue.CueData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** One or more upcoming cues, sent ahead of their windows (multiplayer-performance.md 4.1–4.2). */
public record S2CCue(List<CueData> cues) {
    private static final int MAX_CUES = 16;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(cues.size());
        for (CueData cue : cues) {
            cue.write(buf);
        }
    }

    public static S2CCue decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_CUES);
        List<CueData> cues = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cues.add(CueData.read(buf));
        }
        return new S2CCue(cues);
    }

    public static void handle(S2CCue message, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.deizyn.bhgame.client.ClientPacketHandler.onCues(message));
    }
}
