package net.deizyn.bhgame.game.carry.water_yoke;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.deizyn.bhgame.network.BhGameNetwork;
import net.deizyn.bhgame.network.S2CPlayerPose;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Prototype for water_yoke (D51): {@code /bhgame yoke} puts the yoke on the player's shoulders with no session, so the
 * look can be checked in game with and without Epic Fight. Forces Epic Fight's vanilla mode while carrying and gives
 * the previous mode back at the end. Replaced by the real Carry session later.
 */
@Mod.EventBusSubscriber(modid = BhGame.MODID)
public final class YokePrototype {

    private record Carry(int left, int right, boolean restoreEpicFight) {
        int data() {
            return left | right << 2;
        }
    }

    private static final Map<UUID, Carry> CARRIERS = new HashMap<>();

    public static boolean isCarrying(UUID playerId) {
        return CARRIERS.containsKey(playerId);
    }

    /** Returns true if the player now carries the yoke. */
    public static boolean toggle(ServerPlayer player) {
        if (CARRIERS.containsKey(player.getUUID())) {
            stop(player, false);
            return false;
        }
        setWater(player, 3, 3);
        return true;
    }

    /** Starts carrying if needed; a lower level than before shows the spill stagger on every client. */
    public static void setWater(ServerPlayer player, int left, int right) {
        Carry old = CARRIERS.get(player.getUUID());
        boolean restore = old != null ? old.restoreEpicFight() : EpicFightCompat.forceVanillaMode(player);
        Carry carry = new Carry(left, right, restore);
        CARRIERS.put(player.getUUID(), carry);
        BhGameNetwork.sendToTrackingAndSelf(player,
                new S2CPlayerPose(player.getId(), PlayerPose.CARRY_YOKE, carry.data()));
    }

    private static void stop(ServerPlayer player, boolean disconnecting) {
        Carry carry = CARRIERS.remove(player.getUUID());
        if (carry == null || disconnecting) {
            return;
        }
        // pose first: clients refuse Epic Fight mode for a player they still see carrying
        BhGameNetwork.sendToTrackingAndSelf(player, new S2CPlayerPose(player.getId(), PlayerPose.NONE));
        if (carry.restoreEpicFight()) {
            EpicFightCompat.restoreEpicFightMode(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer tracker) {
            Carry carry = CARRIERS.get(target.getUUID());
            if (carry != null) {
                BhGameNetwork.sendTo(tracker, new S2CPlayerPose(target.getId(), PlayerPose.CARRY_YOKE, carry.data()));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player, false);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CARRIERS.clear();
    }

    private YokePrototype() {
    }
}
