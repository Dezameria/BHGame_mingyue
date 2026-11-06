package net.deizyn.bhgame.client.render.carry.water_yoke;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.deizyn.bhgame.client.ClientSession;
import net.deizyn.bhgame.client.pose.ClientPoses;
import net.deizyn.bhgame.core.cue.CueResult;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.deizyn.bhgame.core.input.InputKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * How far the yoke rolls for a carrier, worked out on each client from what it already sees (walking, sprinting,
 * landing, water levels from the pose packet), so no packet is sent per tick (D13, D51). Only the arms, the yoke and
 * the buckets roll; the body stays upright. A positive roll lowers the player's left (+x) side.
 *
 * <p>A balanced load barely moves: a small roll with each step. What makes it look hard is the water: a spill starts
 * a wobble that the carrier keeps correcting and that dies down over a few seconds, and uneven buckets make the yoke
 * hang toward the heavy side and keep searching for balance.
 */
public final class YokeVisuals {

    /** Roll per step, radians (about 1.5 and 3.5 degrees). */
    private static final float WALK_ROLL = 0.025F;
    private static final float SPRINT_ROLL = 0.06F;
    private static final float LAND_ROLL = 0.12F;
    private static final int LAND_TICKS = 16;
    /** Lean toward the heavier bucket per level of difference, and the slow searching sway that comes with it. */
    private static final float IMBALANCE_LEAN = 0.035F;
    private static final float IMBALANCE_SWAY = 0.015F;
    /** One spill adds this much wobble (radians at its peak); stacks up to MAX_SHAKE spills, fades over SHAKE_TICKS. */
    private static final float SHAKE_ROLL = 0.09F;
    private static final float MAX_SHAKE = 2.0F;
    private static final float SHAKE_TICKS = 50.0F;
    private static final float STAGGER_ROLL = 0.18F;
    private static final int STAGGER_TICKS = 14;
    /** Buckets swing forward and back a little with the steps, lagging behind the feet. */
    private static final float BUCKET_SWING = 0.10F;

    /** Spill splash: a bucket hangs this far to the side and its rim this high above the feet (blocks). */
    private static final double BUCKET_REACH = 1.0;
    private static final double BUCKET_RIM_HEIGHT = 0.9;
    /** No splash for carriers farther than this (docs/multiplayer-performance.md: effects within 24 blocks). */
    private static final double SPLASH_DISTANCE_SQR = 24.0 * 24.0;

    /** Hands on the yoke behind the neck: arms out to the sides and slightly back. Tune in game. */
    private static final float ARM_OUT = 1.75F;
    private static final float ARM_BACK = 0.30F;

    private static final class State {
        boolean wasOnGround = true;
        long landTick = Long.MIN_VALUE / 2;
        float landDir = 1.0F;
        long spillTick = Long.MIN_VALUE / 2;
        float shakeAtSpill;
        long staggerTick = Long.MIN_VALUE / 2;
        float staggerDir;
    }

    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();
    /** Local player's tilt cues already answered: cue id -> client tick. */
    private static final Int2LongMap ANSWERED = new Int2LongOpenHashMap();

    /** The needle at either end tilts the yoke this far (about 14 degrees). */
    private static final float NEEDLE_TILT = 0.25F;
    /** Needle in the red (D70): the yoke trembles this much (radians) at the very end, and the view this much. */
    private static final float DANGER_TREMBLE_ROLL = 0.06F;
    private static final float DANGER_TREMBLE_VIEW = 0.025F;
    /** A tilt cue dips the yoke this far (about 12 degrees) by the end of its window. */
    private static final float CUE_TILT = 0.21F;
    private static final int TILT_LEAD_TICKS = 4;
    private static final int RECOVER_TICKS = 3;
    private static final int MISS_HOLD_TICKS = 6;

    public static float roll(Player player, float partialTick) {
        State state = STATES.computeIfAbsent(player.getId(), id -> new State());
        long now = ClientSession.clock();
        float time = now + partialTick;
        boolean onGround = player.onGround();
        if (onGround && !state.wasOnGround) {
            state.landTick = now;
            state.landDir = -state.landDir;
        }
        state.wasOnGround = onGround;

        // a steady load: one small roll per step
        float speed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
        float roll = Mth.sin(player.walkAnimation.position(partialTick) * 0.6662F)
                * (player.isSprinting() ? SPRINT_ROLL : WALK_ROLL) * speed;

        // uneven buckets: hang toward the heavy side and keep searching for balance
        int data = ClientPoses.data(player);
        int diff = left(data) - right(data);
        if (diff != 0) {
            roll += diff * IMBALANCE_LEAN
                    + Math.abs(diff) * IMBALANCE_SWAY * (Mth.sin(time * 0.21F) + 0.5F * Mth.sin(time * 0.53F + 1.3F));
        }

        // after a spill: a wobble the carrier keeps correcting, fading over a few seconds
        float shake = shake(state, now);
        if (shake > 0.01F) {
            float seed = player.getId() * 0.37F;
            roll += shake * SHAKE_ROLL
                    * (Mth.sin(time * 0.45F + seed) + 0.6F * Mth.sin(time * 1.1F + seed * 2.0F));
        }

        float sinceLand = now - state.landTick + partialTick;
        if (sinceLand < LAND_TICKS) {
            roll += state.landDir * Mth.sin(sinceLand * 0.8F) * LAND_ROLL * (1.0F - sinceLand / LAND_TICKS);
        }
        float sinceStagger = now - state.staggerTick + partialTick;
        if (sinceStagger < STAGGER_TICKS) {
            roll += state.staggerDir * STAGGER_ROLL * Mth.sin(sinceStagger / STAGGER_TICKS * Mth.PI);
        }
        // the carrier's own client follows the balance needle: the big dips come from the game, never from walking
        ClientSession session = ClientSession.get();
        if (player == Minecraft.getInstance().player && session != null && session.profile.freeMovement()) {
            int gauge = session.gaugeWithFlag(Gauge.FLAG_BALANCE);
            if (gauge >= 0) {
                // needle toward 1 = the right bucket dips = negative roll
                float value = session.gauge(gauge, time);
                roll -= (value - 0.5F) * 2.0F * NEEDLE_TILT;
                roll += danger(value) * DANGER_TREMBLE_ROLL * trembleWave(time);
            }
            roll += cueRoll(session, time);
        }
        return roll;
    }

    /**
     * Sum of the tilt cues at {@code time}: each dips its side from a few ticks before its window opens to
     * {@link #CUE_TILT} when it closes, snaps back within {@link #RECOVER_TICKS} once answered, and holds a little
     * past the close when missed (the spill stagger then takes over).
     */
    private static float cueRoll(ClientSession session, double time) {
        float roll = 0.0F;
        for (ClientSession.LocalCue cue : session.cues()) {
            CueVisual visual = cue.data().visual();
            if ((visual != CueVisual.TILT_LEFT && visual != CueVisual.TILT_RIGHT) || !session.isOwn(cue)) {
                continue;
            }
            float dir = visual == CueVisual.TILT_LEFT ? 1.0F : -1.0F;
            long resolved = ANSWERED.getOrDefault(cue.data().id(), Long.MAX_VALUE);
            if (time >= resolved) {
                float back = 1.0F - (float) (time - resolved) / RECOVER_TICKS;
                if (back > 0.0F) {
                    roll += dir * tiltAt(cue, resolved) * back;
                }
            } else if (time < cue.close() + MISS_HOLD_TICKS) {
                roll += dir * tiltAt(cue, time);
            }
        }
        return roll;
    }

    private static float tiltAt(ClientSession.LocalCue cue, double time) {
        double start = cue.open() - TILT_LEAD_TICKS;
        double progress = (time - start) / (cue.close() - start);
        return CUE_TILT * (float) Mth.clamp(progress, 0.0, 1.0);
    }

    /** Instant feedback: a right key inside an open tilt window levels the yoke before the server's answer. */
    public static void onLocalPress(ClientSession session, InputKey key) {
        long now = ClientSession.clock();
        for (ClientSession.LocalCue cue : session.cues()) {
            CueVisual visual = cue.data().visual();
            if ((visual == CueVisual.TILT_LEFT || visual == CueVisual.TILT_RIGHT) && session.isOwn(cue)
                    && now >= cue.open() && now < cue.close() && (cue.data().keys() & key.bit()) != 0) {
                answer(cue.data().id(), now);
                return;
            }
        }
    }

    /** The server's verdict; a HIT levels the yoke if the local press didn't already. */
    public static void onResult(int cueId, CueResult result) {
        if (result == CueResult.HIT) {
            answer(cueId, ClientSession.clock());
        }
    }

    private static void answer(int cueId, long now) {
        if (ANSWERED.size() > 64) {
            ANSWERED.clear();
        }
        if (!ANSWERED.containsKey(cueId)) {
            ANSWERED.put(cueId, now);
        }
    }

    /** First person while carrying: the yoke across the bottom of the view, rolling like the third-person one. */
    public static void renderFirstPerson(PoseStack poseStack, MultiBufferSource buffers, int light, Player player,
                                         float partialTick) {
        poseStack.pushPose();
        poseStack.translate(0.0F, -0.55F, -0.6F);
        // needle in the red: the whole load shakes in the carrier's hands
        float shake = localDanger(partialTick) * DANGER_TREMBLE_VIEW;
        if (shake > 0.0F) {
            float time = ClientSession.clock() + partialTick;
            poseStack.translate(shake * trembleWave(time + 3.1F), shake * trembleWave(time + 7.4F), 0.0F);
        }
        // view space is y up and x to the right; the model is y down with +x on the carrier's left
        poseStack.scale(-0.5F, -0.5F, 0.5F);
        YokeModel.render(poseStack, buffers, light, roll(player, partialTick), bucketSwing(player, partialTick),
                ClientPoses.data(player), 0.0F);
        poseStack.popPose();
    }

    /** Forward/back swing of the buckets, radians; lags a quarter step behind the feet. */
    public static float bucketSwing(Player player, float partialTick) {
        float speed = Math.min(1.0F, player.walkAnimation.speed(partialTick));
        return Mth.sin(player.walkAnimation.position(partialTick) * 0.6662F * 2.0F - 1.2F) * BUCKET_SWING * speed;
    }

    /** How deep the needle is in the red, 0 outside to 1 at an end (D70). */
    public static float danger(float value) {
        float off = Math.abs(value - 0.5F) * 2.0F;
        float start = 1.0F - Gauge.BALANCE_DANGER * 2.0F;
        return off <= start ? 0.0F : Math.min(1.0F, (off - start) / (1.0F - start));
    }

    private static float localDanger(float partialTick) {
        ClientSession session = ClientSession.get();
        if (session == null || !session.profile.freeMovement()) {
            return 0.0F;
        }
        int gauge = session.gaugeWithFlag(Gauge.FLAG_BALANCE);
        return gauge < 0 ? 0.0F : danger(session.gauge(gauge, ClientSession.clock() + partialTick));
    }

    /** A fast, uneven tremble in -1..1 (straining to hold the load). */
    private static float trembleWave(float time) {
        return 0.6F * Mth.sin(time * 2.7F) + 0.4F * Mth.sin(time * 4.3F + 1.7F);
    }

    private static float shake(State state, long now) {
        float since = now - state.spillTick;
        return since < 0 ? 0.0F : state.shakeAtSpill * (float) Math.exp(-since / SHAKE_TICKS);
    }

    /** Arms hold the yoke and follow its roll. Called at the end of {@code HumanoidModel.setupAnim}. */
    public static void poseArms(HumanoidModel<?> model, Player player, float partialTick) {
        float roll = roll(player, partialTick);
        model.rightArm.xRot = ARM_BACK;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = ARM_OUT + roll;
        model.leftArm.xRot = ARM_BACK;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = -ARM_OUT + roll;
    }

    /**
     * Data byte: left level in bits 0-1, right in bits 2-3. Any spill adds wobble; a spill from one side also
     * staggers toward that side.
     */
    public static void onWaterChanged(int entityId, int oldData, int newData) {
        boolean leftSpilled = left(newData) < left(oldData);
        boolean rightSpilled = right(newData) < right(oldData);
        if (!leftSpilled && !rightSpilled) {
            return;
        }
        State state = STATES.computeIfAbsent(entityId, id -> new State());
        long now = ClientSession.clock();
        state.shakeAtSpill = Math.min(MAX_SHAKE, shake(state, now) + 1.0F);
        state.spillTick = now;
        if (leftSpilled != rightSpilled) {
            state.staggerTick = now;
            state.staggerDir = leftSpilled ? 1.0F : -1.0F;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.level.getEntity(entityId) instanceof Player carrier
                && carrier.distanceToSqr(mc.player) <= SPLASH_DISTANCE_SQR) {
            if (leftSpilled) {
                splash(carrier, 1.0F);
            }
            if (rightSpilled) {
                splash(carrier, -1.0F);
            }
        }
    }

    /**
     * Water sloshing over the rim of one bucket ({@code side} 1 = the carrier's left) and splashing at their feet,
     * with a splash sound. Local only, from the pose packet every nearby client already gets.
     */
    private static void splash(Player carrier, float side) {
        Level level = carrier.level();
        float yaw = carrier.yBodyRot * Mth.DEG_TO_RAD;
        // the carrier's left in the world: +x when facing south (yaw 0)
        double x = carrier.getX() + Mth.cos(yaw) * BUCKET_REACH * side;
        double z = carrier.getZ() + Mth.sin(yaw) * BUCKET_REACH * side;
        double rim = carrier.getY() + BUCKET_RIM_HEIGHT;
        RandomSource random = level.random;
        for (int i = 0; i < 10; i++) {
            level.addParticle(ParticleTypes.SPLASH, x + (random.nextDouble() - 0.5) * 0.4, rim,
                    z + (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.3, 0.2,
                    (random.nextDouble() - 0.5) * 0.3);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.FALLING_WATER, x + (random.nextDouble() - 0.5) * 0.35,
                    rim - random.nextDouble() * 0.3, z + (random.nextDouble() - 0.5) * 0.35, 0.0, 0.0, 0.0);
        }
        double ground = carrier.getY() + 0.05;
        for (int i = 0; i < 8; i++) {
            level.addParticle(ParticleTypes.SPLASH, x + (random.nextDouble() - 0.5) * 0.7, ground,
                    z + (random.nextDouble() - 0.5) * 0.7, 0.0, 0.1, 0.0);
        }
        level.playLocalSound(x, rim, z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.35F,
                1.2F + random.nextFloat() * 0.3F, false);
    }

    public static int left(int data) {
        return data & 3;
    }

    public static int right(int data) {
        return data >> 2 & 3;
    }

    public static void forget(int entityId) {
        STATES.remove(entityId);
    }

    public static void clear() {
        STATES.clear();
        ANSWERED.clear();
    }

    private YokeVisuals() {
    }
}
