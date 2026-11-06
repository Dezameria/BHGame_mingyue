package net.deizyn.bhgame.client.render.station.qi_station;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.deizyn.bhgame.client.ClientSession;
import net.deizyn.bhgame.client.NearbySessions;
import net.deizyn.bhgame.client.render.TrainingBlockRenderer;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.deizyn.bhgame.core.gauge.Gauge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The qi orb over the pedestal, drawn on a duellist's client only (D58). It hangs on the line between the block and
 * this player, as far toward them as the tug gauge says: pale in the middle, red as it closes in on you, gold as it
 * closes in on your opponent. A burst aimed at you makes it shrink and brighten for the whole gather; a feint looks the
 * same until {@link #FEINT_REVEAL} ticks before the window, then turns purple (D59). Your own burst on its way to the
 * opponent shows faintly. Everyone else near the pedestal sees the same orb in neutral colours from the shared state
 * ({@link #renderNearby}, D60), brightening while a burst gathers whichever kind it is. Placeholder looks until P6.
 */
public final class QiOrbRenderer {
    private static final float ORB_Y = 1.3F;
    /** How far the orb travels from the centre toward a duellist before it reaches them. */
    private static final float MAX_OFFSET = 0.85F;
    private static final float SIZE = 0.16F;
    private static final float OUTGOING_BRIGHTNESS = 0.4F;
    /** How long before the window opens a feint shows its purple (D59). */
    private static final int FEINT_REVEAL = 6;
    private static final float[] MIDDLE = {0.7F, 0.95F, 1.0F};
    private static final float[] DANGER = {1.0F, 0.3F, 0.2F};
    private static final float[] WINNING = {1.0F, 0.85F, 0.35F};
    private static final float[] FEINT = {0.75F, 0.35F, 1.0F};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    /** Scratch colour and position, client thread only. */
    private static final float[] RGB = new float[3];
    private static final float[] LOCAL = new float[3];

    /**
     * Where the orb hangs, relative to the block's corner, as a scratch array read at once: on the line from the
     * centre to role 0, toward role 0 as {@code tug} nears 0 and away as it nears 1. Shared by the bystanders' orb
     * and the particles.
     */
    static float[] orbLocal(BlockPos pos, float tug, @Nullable Player role0, @Nullable Player role1, double time) {
        double dx = 0.0;
        double dz = 1.0;
        Player toward = role0 != null ? role0 : role1;
        if (toward != null) {
            double sign = toward == role0 ? 1.0 : -1.0;
            double x = (toward.getX() - (pos.getX() + 0.5)) * sign;
            double z = (toward.getZ() - (pos.getZ() + 0.5)) * sign;
            double length = Math.sqrt(x * x + z * z);
            if (length > 1.0E-3) {
                dx = x / length;
                dz = z / length;
            }
        }
        float offset = (0.5F - tug) * 2.0F * MAX_OFFSET;
        LOCAL[0] = 0.5F + (float) dx * offset;
        LOCAL[1] = ORB_Y + (float) Math.sin(time * 0.12) * 0.03F;
        LOCAL[2] = 0.5F + (float) dz * offset;
        return LOCAL;
    }

    /** The orb for someone watching (D60): pale, warming toward gold the closer it is to either duellist. */
    public static void renderNearby(NearbySessions.State state, double time, PoseStack poseStack,
                                    MultiBufferSource buffers) {
        float tug = state.value(time);
        float[] at = orbLocal(state.pos, tug, state.player(0), state.player(1), time);
        float cx = at[0];
        float cy = at[1];
        float cz = at[2];
        float pressure = Math.abs(tug - 0.5F) * 2.0F;
        float gather = QiEffects.gather(state.pos, time);
        float[] rgb = RGB;
        for (int i = 0; i < 3; i++) {
            rgb[i] = MIDDLE[i] + (WINNING[i] - MIDDLE[i]) * pressure;
            rgb[i] += (WHITE[i] - rgb[i]) * gather * 0.7F;
        }
        draw(poseStack, buffers, cx, cy, cz, rgb, gather);
    }

    private static void draw(PoseStack poseStack, MultiBufferSource buffers, float cx, float cy, float cz,
                             float[] rgb, float gather) {
        float size = SIZE * (1.0F - 0.35F * gather);
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();
        TrainingBlockRenderer.box(consumer, matrix, cx - size, cy - size, cz - size, cx + size, cy + size, cz + size,
                rgb[0], rgb[1], rgb[2], 0.55F + 0.4F * gather);
        float halo = size * (1.7F + 0.6F * gather);
        TrainingBlockRenderer.box(consumer, matrix, cx - halo, cy - halo, cz - halo, cx + halo, cy + halo, cz + halo,
                rgb[0], rgb[1], rgb[2], 0.12F + 0.25F * gather);
    }

    public static void render(ClientSession session, double time, PoseStack poseStack, MultiBufferSource buffers) {
        Player me = Minecraft.getInstance().player;
        if (me == null) {
            return;
        }
        BlockPos pos = session.pos;
        double dx = me.getX() - (pos.getX() + 0.5);
        double dz = me.getZ() - (pos.getZ() + 0.5);
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3) {
            dx = 0.0;
            dz = 1.0;
            length = 1.0;
        }
        int index = session.gaugeWithFlag(Gauge.FLAG_TUG);
        float tug = index < 0 ? 0.5F : session.gauge(index, time);
        // the gauge runs from role 0 (0) to role 1 (1); +1 = right at me, -1 = right at my opponent
        float fromMe = session.role == 0 ? tug : 1.0F - tug;
        float toward = (0.5F - fromMe) * 2.0F;
        float offset = toward * MAX_OFFSET;
        float cx = 0.5F + (float) (dx / length) * offset;
        float cz = 0.5F + (float) (dz / length) * offset;
        float cy = ORB_Y + (float) Math.sin(time * 0.12) * 0.03F;
        NearbySessions.State shared = NearbySessions.get(pos);
        if (shared != null) {
            // the same spot the particles aim at, also when the two do not stand exactly opposite
            float[] at = orbLocal(pos, tug, shared.player(0), shared.player(1), time);
            cx = at[0];
            cy = at[1];
            cz = at[2];
        }

        float[] rgb = RGB;
        float danger = Math.max(0.0F, toward);
        float winning = Math.max(0.0F, -toward);
        for (int i = 0; i < 3; i++) {
            rgb[i] = MIDDLE[i] + (DANGER[i] - MIDDLE[i]) * danger + (WINNING[i] - MIDDLE[i]) * winning;
        }

        float gather = 0.0F;
        boolean feint = false;
        if (!session.isFocusing()) {
            for (ClientSession.LocalCue cue : session.cues()) {
                CueVisual visual = cue.data().visual();
                if (visual != CueVisual.QI_BURST && visual != CueVisual.QI_FEINT) {
                    continue;
                }
                long arrival = ClientSession.arrival(cue);
                if (time < arrival || time >= cue.close() + 4) {
                    continue;
                }
                float value = time < cue.open() ? (float) ((time - arrival) / Math.max(1L, cue.open() - arrival))
                        : time < cue.close() ? 1.0F : 1.0F - (float) ((time - cue.close()) / 4.0);
                boolean own = session.isOwn(cue);
                if (!own) {
                    value *= OUTGOING_BRIGHTNESS;
                }
                if (value > gather) {
                    gather = value;
                    feint = own && visual == CueVisual.QI_FEINT && time >= cue.open() - FEINT_REVEAL;
                }
            }
        }
        float[] flash = feint ? FEINT : WHITE;
        for (int i = 0; i < 3; i++) {
            rgb[i] += (flash[i] - rgb[i]) * gather * 0.7F;
        }

        draw(poseStack, buffers, cx, cy, cz, rgb, gather);
    }

    private QiOrbRenderer() {
    }
}
