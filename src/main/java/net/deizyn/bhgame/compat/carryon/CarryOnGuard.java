package net.deizyn.bhgame.compat.carryon;

import net.deizyn.bhgame.BhGame;
import net.deizyn.bhgame.core.block.TrainingBlock;
import net.deizyn.bhgame.core.session.SessionManager;
import net.deizyn.bhgame.game.carry.water_yoke.YokePrototype;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps Carry On off our blocks and our players without relying on its tag blacklist alone, which a server datapack
 * can replace. Carry On picks up from {@code RightClickBlock}/{@code EntityInteract} at HIGH priority and skips events
 * that are already cancelled, so these run at HIGHEST on the server:
 * <ul>
 *     <li>a training block clicked with empty hands is used here and the event cancelled, so the yoke is only ever
 *     lifted by us (Shift + RMB is the same gesture for both);</li>
 *     <li>a player in a session clicks nothing in the world (the client blocks it too, but not before the session
 *     reaches it one ping later);</li>
 *     <li>nobody picks up a player who is in a session (the server has {@code pickupPlayers = true}).</li>
 * </ul>
 * A player Carry On is carrying for is left to Carry On, which puts its block down instead.
 */
@Mod.EventBusSubscriber(modid = BhGame.MODID)
public final class CarryOnGuard {
    private static Boolean loaded;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!loaded() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (busy(player)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof TrainingBlock) || !emptyHanded(player)
                || event.getUseBlock() == Event.Result.DENY || CarryOnCompat.isCarrying(player)) {
            return;
        }
        // what vanilla does next with empty hands, done before Carry On can see the click
        event.setCancellationResult(state.use(event.getLevel(), player, event.getHand(), event.getHitVec()));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!loaded() || event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (busy(player) || (event.getTarget() instanceof ServerPlayer target && busy(target) && emptyHanded(player))) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static boolean busy(ServerPlayer player) {
        return SessionManager.isInSession(player.getUUID()) || YokePrototype.isCarrying(player.getUUID());
    }

    private static boolean emptyHanded(ServerPlayer player) {
        return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }

    private static boolean loaded() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded(CarryOnCompat.MOD_ID);
        }
        return loaded;
    }

    private CarryOnGuard() {
    }
}
