package net.deizyn.bhgame.client.pose;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.deizyn.bhgame.client.ClientSession;
import net.deizyn.bhgame.client.render.carry.water_yoke.YokeVisuals;
import net.deizyn.bhgame.compat.epicfight.EpicFightCompat;
import net.deizyn.bhgame.compat.epicfight.client.EpicFightClientBridge;
import net.deizyn.bhgame.core.config.BhGameConfig;
import net.deizyn.bhgame.core.input.InputKey;
import net.deizyn.bhgame.core.pose.PlayerAction;
import net.deizyn.bhgame.core.pose.PlayerPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
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
    /** PALM_PUSH: each arm turns in this much (radians) so the palms meet at the orb. */
    private static final float PALM_INWARD = 0.22F;
    /** PALM_PUSH: how far (model pixels) the arms thrust forward on a burst, and their slow idle press. */
    private static final float PALM_THRUST = 3.0F;
    private static final float PALM_PRESS = 0.6F;
    /** PALM_PUSH while drawing the orb back (Shift, D76): the arms come in this far and the elbows drop. */
    private static final float PALM_DRAW = 3.5F;
    private static final float PALM_DRAW_DROP = 0.25F;

    private static final class Entry {
        final PlayerPose pose;
        int data;
        long nextSwing;
        long lastAction = Long.MIN_VALUE / 2;
        long missingSince = -1;
        boolean offHandNext;

        Entry(PlayerPose pose, int data, long now) {
            this.pose = pose;
            this.data = data;
            this.nextSwing = now + 10;
        }
    }

    private static final Int2ObjectMap<Entry> POSES = new Int2ObjectOpenHashMap<>();
    private static boolean pushedForRender;

    public static void set(int entityId, PlayerPose pose, int data) {
        Entry old = POSES.get(entityId);
        if (pose == PlayerPose.NONE) {
            POSES.remove(entityId);
            YokeVisuals.forget(entityId);
        } else if (old != null && old.pose == pose) {
            // same pose, new data (water_yoke: a spill or refill)
            if (pose == PlayerPose.CARRY_YOKE) {
                YokeVisuals.onWaterChanged(entityId, old.data, data);
            }
            old.data = data;
        } else {
            POSES.put(entityId, new Entry(pose, data, ClientSession.clock()));
        }
    }

    /** Whether this client shows {@code player} carrying the water yoke (D51). */
    public static boolean isCarrying(Player player) {
        Entry entry = POSES.get(player.getId());
        return entry != null && entry.pose == PlayerPose.CARRY_YOKE;
    }

    /**
     * Whether this client shows {@code player} in a session drawn by the vanilla model, kept in Epic Fight's vanilla
     * mode (every game but the wooden dummy, D78).
     */
    public static boolean usesVanillaModel(Player player) {
        Entry entry = POSES.get(player.getId());
        return entry != null && PlayerPose.usesVanillaModel(entry.pose);
    }

    /** Whether this client shows {@code player} training on the wooden dummy (Epic Fight battle mode is locked, D66). */
    public static boolean isWingChun(Player player) {
        Entry entry = POSES.get(player.getId());
        return entry != null && entry.pose == PlayerPose.WING_CHUN;
    }

    /** Whether this client shows any session pose on the entity {@code entityId}. */
    public static boolean hasPose(int entityId) {
        return POSES.containsKey(entityId);
    }

    /** Whether this client shows {@code player} pushing the qi orb with both palms, within the LOD distance. */
    public static boolean isPalmPushing(Player player) {
        Entry entry = POSES.get(player.getId());
        Minecraft mc = Minecraft.getInstance();
        return entry != null && entry.pose == PlayerPose.PALM_PUSH && mc.player != null
                && player.distanceToSqr(mc.player) <= maxDistanceSqr();
    }

    /**
     * qi_station: both arms straight out along the head, which the locked camera keeps on the orb, so the palms
     * follow it. A slow press in and out while pushing; a swing (the burst's PUNCH_FINISH) becomes a two-palm thrust.
     * {@code drawing}: the palms drawn back toward the chest, trembling a little, as if hauling the orb in.
     * Runs at the end of setupAnim, after vanilla posed and swung the arms.
     */
    public static void posePalmPush(HumanoidModel<?> model, float ageInTicks, boolean drawing) {
        float xRot = model.head.xRot - Mth.HALF_PI + (drawing ? PALM_DRAW_DROP : 0.0F);
        float yRot = model.head.yRot;
        float forward = drawing
                ? -PALM_DRAW + 0.15F * Mth.sin(ageInTicks * 1.3F)
                : PALM_PRESS * (1.0F + Mth.sin(ageInTicks * 0.15F));
        forward += PALM_THRUST * Mth.sin(model.attackTime * Mth.PI);
        model.rightArm.xRot = xRot;
        model.rightArm.yRot = yRot - PALM_INWARD;
        model.rightArm.zRot = 0.0F;
        model.rightArm.z -= forward;
        model.leftArm.xRot = xRot;
        model.leftArm.yRot = yRot + PALM_INWARD;
        model.leftArm.zRot = 0.0F;
        model.leftArm.z -= forward;
    }

    /** The pose's data byte, 0 when the player has no pose. */
    public static int data(Player player) {
        Entry entry = POSES.get(player.getId());
        return entry != null ? entry.data : 0;
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
        if (entry == null || entry.pose != PlayerPose.PALM_PUSH) {
            // qi_station draws its own burst (QiEffects, D60), not the dummy's sparks
            PunchEffects.spawn(player, action, key);
        }
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
        YokeVisuals.clear();
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
