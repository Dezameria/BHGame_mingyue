package net.deizyn.bhgame.network;

import net.deizyn.bhgame.BhGame;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Session packets go to the one player playing; nobody else ever receives cues (D17). Poses go to whoever tracks
 * the player. Packet budget rules: docs/multiplayer-performance.md section 2.
 */
public final class BhGameNetwork {
    private static final String VERSION = "6";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(BhGame.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    // counters for /bhgame debug (server thread only); a broadcast counts once
    private static long sentTotal;
    private static long receivedTotal;
    private static int sentThisSecond;
    private static int receivedThisSecond;
    private static int sentLastSecond;
    private static int receivedLastSecond;
    private static int simulatedThisSecond;
    private static int simulatedLastSecond;

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(S2CSessionStart.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSessionStart::encode).decoder(S2CSessionStart::decode)
                .consumerMainThread(S2CSessionStart::handle).add();
        CHANNEL.messageBuilder(S2CCue.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCue::encode).decoder(S2CCue::decode)
                .consumerMainThread(S2CCue::handle).add();
        CHANNEL.messageBuilder(S2CSessionUpdate.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSessionUpdate::encode).decoder(S2CSessionUpdate::decode)
                .consumerMainThread(S2CSessionUpdate::handle).add();
        CHANNEL.messageBuilder(S2CSessionEnd.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSessionEnd::encode).decoder(S2CSessionEnd::decode)
                .consumerMainThread(S2CSessionEnd::handle).add();
        CHANNEL.messageBuilder(C2SInput.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SInput::encode).decoder(C2SInput::decode)
                .consumerMainThread(C2SInput::handle).add();
        CHANNEL.messageBuilder(C2SAbort.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAbort::encode).decoder(C2SAbort::decode)
                .consumerMainThread(C2SAbort::handle).add();
        CHANNEL.messageBuilder(S2CPlayerPose.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CPlayerPose::encode).decoder(S2CPlayerPose::decode)
                .consumerMainThread(S2CPlayerPose::handle).add();
        CHANNEL.messageBuilder(S2CGaugeSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CGaugeSync::encode).decoder(S2CGaugeSync::decode)
                .consumerMainThread(S2CGaugeSync::handle).add();
        CHANNEL.messageBuilder(S2CSessionBanner.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSessionBanner::encode).decoder(S2CSessionBanner::decode)
                .consumerMainThread(S2CSessionBanner::handle).add();
        CHANNEL.messageBuilder(S2CPlayerAction.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CPlayerAction::encode).decoder(S2CPlayerAction::decode)
                .consumerMainThread(S2CPlayerAction::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object message) {
        sentTotal++;
        sentThisSecond++;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** To every client tracking {@code player}, but not the player. */
    public static void sendToTracking(ServerPlayer player, Object message) {
        sentTotal++;
        sentThisSecond++;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), message);
    }

    /** To every client tracking {@code player}, including the player (for third-person view). */
    public static void sendToTrackingAndSelf(ServerPlayer player, Object message) {
        sentTotal++;
        sentThisSecond++;
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
    }

    /** A packet a /bhgame stress bot would have received; counted, never sent. */
    public static void countSimulated() {
        simulatedThisSecond++;
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    static void countReceived() {
        receivedTotal++;
        receivedThisSecond++;
    }

    /** Called every server tick; rolls the per-second counters. */
    public static void tickStats(long serverTick) {
        if (serverTick % 20 == 0) {
            sentLastSecond = sentThisSecond;
            receivedLastSecond = receivedThisSecond;
            simulatedLastSecond = simulatedThisSecond;
            simulatedThisSecond = 0;
            sentThisSecond = 0;
            receivedThisSecond = 0;
        }
    }

    public static String statsLine() {
        return "packets/s: out " + sentLastSecond + ", in " + receivedLastSecond
                + " | total: out " + sentTotal + ", in " + receivedTotal
                + (simulatedLastSecond > 0 ? " | stress (not sent) " + simulatedLastSecond + "/s" : "");
    }

    private BhGameNetwork() {
    }
}
