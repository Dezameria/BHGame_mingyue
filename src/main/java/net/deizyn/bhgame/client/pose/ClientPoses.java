package net.deizyn.bhgame.client.pose;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.deizyn.bhgame.client.ClientSession;
import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.compat.epicfight.client.EpicFightClientBridge;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderPlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Player poses on this client: crouching for kneeling poses, slow idle moves on a timer (Epic Fight punches for the
 * wooden dummy when it is installed, D45), and one-shot actions such as real punches (D41). Purely local and cosmetic.
 * Players beyond poseAnimationDistance are not animated (D26).
 */
public final class ClientPoses {

    /** A pose for an entity we haven't seen for this long is dropped (packet arrived for an untracked player). */
    private static final int FORGET_MISSING_TICKS = 100;
    /**
     * Idle moves start this long after the last real punch, so the dummy's player keeps sparring slowly between rows
     * instead of standing still (D45), without mixing into a fast row.
     */
    private static final int IDLE_AFTER_ACTION_TICKS = 10;
    private static final float CROUCH_DROP = 0.125F;

    private static final class Entry {
        final PlayerPose pose;
        long nextSwing;
        long lastAction = Long.MIN_VALUE / 2;
        long missingSince = -1;
        boolean offHandNext;

        Entry(PlayerPose pose, long now) {
            this.pose = pose;
            this.nextSwing = now + 10;
        }
    }

    private static final Int2ObjectMap<Entry> POSES = new Int2ObjectOpenHashMap<>();
    private static boolean pushedForRender;

    public static void set(int entityId, PlayerPose pose) {
        if (pose == PlayerPose.NONE) {
            POSES.remove(entityId);
        } else {
            POSES.put(entityId, new Entry(pose, ClientSession.clock()));
        }
    }

    /**
     * A one-shot movement (D41) and its hit effect on the arrow's side (D42). The punch is Epic Fight's when it is
     * installed; without it a vanilla arm swing, except our own arm in first person (it would sweep across the screen).
     */
    public static void action(int entityId, PlayerAction action, @Nullable InputKey key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.level.getEntity(entityId) instanceof Player player)
                || player.distanceToSqr(mc.player) > maxDistanceSqr()) {
            return;
        }
        Entry entry = POSES.get(entityId);
        if (entry != null) {
            entry.lastAction = ClientSession.clock();
        }
        PunchEffects.spawn(player, action, key);
        if (playEpicFight(player, action)) {
            return;
        }
        boolean isSelf = player == mc.player;
        if (action == PlayerAction.KNOCKED_BACK || (isSelf && mc.options.getCameraType().isFirstPerson())) {
            return;
        }
        player.swing(action == PlayerAction.PUNCH_LEFT ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
    }

    /** Returns false when Epic Fight is missing or the player has no patch, so the caller falls back to vanilla. */
    private static boolean playEpicFight(Player player, PlayerAction action) {
        if (!EpicFightCompat.isAvailable()) {
            return false;
        }
        try {
            return EpicFightClientBridge.play(player, action);
        } catch (LinkageError error) {
            EpicFightCompat.markFailed(error);
            return false;
        }
    }

    public static void clear() {
        POSES.clear();
    }

    public static void tick(Minecraft mc) {
        if (POSES.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }
        long now = ClientSession.clock();
        ObjectIterator<Int2ObjectMap.Entry<Entry>> iterator = POSES.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            Int2ObjectMap.Entry<Entry> mapEntry = iterator.next();
            Entry entry = mapEntry.getValue();
            Entity entity = mc.level.getEntity(mapEntry.getIntKey());
            if (!(entity instanceof Player player)) {
                if (entry.missingSince < 0) {
                    entry.missingSince = now;
                } else if (now - entry.missingSince > FORGET_MISSING_TICKS) {
                    iterator.remove();
                }
                continue;
            }
            entry.missingSince = -1;
            if (now < entry.nextSwing || player.distanceToSqr(mc.player) > maxDistanceSqr()
                    || now - entry.lastAction < IDLE_AFTER_ACTION_TICKS) {
                continue;
            }
            boolean selfFirstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            swing(player, entry, selfFirstPerson);
            entry.nextSwing = now + nextSwingDelay(entry.pose, player);
        }
    }

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        Entry entry = POSES.get(player.getId());
        Minecraft mc = Minecraft.getInstance();
        if (entry == null || !entry.pose.crouched || player.isCrouching() || mc.player == null
                || player.distanceToSqr(mc.player) > maxDistanceSqr()) {
            return;
        }
        event.getRenderer().getModel().crouching = true;
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(0.0F, -CROUCH_DROP, 0.0F);
        pushedForRender = true;
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (pushedForRender) {
            event.getPoseStack().popPose();
            pushedForRender = false;
        }
    }

    /**
     * One idle move. A vanilla swing of our own arm in first person would sweep across the screen, so it is skipped;
     * Epic Fight handles first person itself.
     */
    private static void swing(Player player, Entry entry, boolean selfFirstPerson) {
        switch (entry.pose) {
            case PULL_HERB, QI_FLICK -> {
                if (!selfFirstPerson) {
                    player.swing(InteractionHand.MAIN_HAND);
                }
            }
            case WING_CHUN -> {
                // slow sparring while waiting for the next row (D45): Epic Fight punches when it is there
                if (!playEpicFight(player, entry.offHandNext ? PlayerAction.PUNCH_LEFT : PlayerAction.PUNCH_RIGHT)
                        && !selfFirstPerson) {
                    player.swing(entry.offHandNext ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                }
                entry.offHandNext = !entry.offHandNext;
            }
            default -> {
            }
        }
    }

    private static int nextSwingDelay(PlayerPose pose, Player player) {
        return switch (pose) {
            case PULL_HERB, QI_FLICK -> 20 + player.getRandom().nextInt(20);
            // about one punch a second: unhurried, clearly slower than a row being played
            case WING_CHUN -> 16 + player.getRandom().nextInt(6);
            default -> Integer.MAX_VALUE / 2;
        };
    }

    private ClientPoses() {
    }

    /** LOD: poses of players farther than this are not animated (client config poseAnimationDistance). */
    private static double maxDistanceSqr() {
        double d = BhGameConfig.poseAnimationDistance;
        return d * d;
    }
}
