package net.deizyn.bhgame.core.session;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.command.BhGameCommand;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.S2CNearbyState;
import net.deizyn.bhgame.network.S2CPlayerPose;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Drives the session tick and closes sessions on every way a player can leave (multiplayer-performance.md 6). */
@Mod.EventBusSubscriber(modid = BhGame.MODID)
public final class SessionEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            SessionManager.tick(server);
            BhGameNetwork.tickStats(server.getTickCount());
        }
    }

    /** A client that comes into range mid-session still sees the player's pose (playeranim.md). */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker) {
            PlayerPose pose = SessionManager.poseOf(target);
            if (pose != PlayerPose.NONE) {
                BhGameNetwork.sendTo(tracker,
                        new S2CPlayerPose(target.getId(), pose, SessionManager.poseDataOf(target)));
            }
            S2CNearbyState nearby = SessionManager.nearbyStateOf(target);
            if (nearby != null) {
                BhGameNetwork.sendTo(tracker, nearby);
            }
        }
    }

    // carried games (D49): the server sees jumps and falls in the movement packets
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SessionManager.onCarrierJump(player);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SessionManager.onCarrierFall(player, event.getDistance());
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        // a hard landing already spilled; its fall damage is not a second blow
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0.0F
                && !event.getSource().is(DamageTypeTags.IS_FALL)) {
            SessionManager.onCarrierHurt(player);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BhGameCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // the connection is closing: don't send packets or apply results to the player
        SessionManager.leave(event.getEntity().getUUID(), true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SessionManager.leave(player.getUUID(), false);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SessionManager.leave(player.getUUID(), false);
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        SessionManager.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SessionManager.shutdown();
    }

    private SessionEvents() {
    }
}
