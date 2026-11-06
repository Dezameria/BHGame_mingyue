package net.deizyn.bhgame.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.deizyn.bhgame.client.ClientSession;
import net.deizyn.bhgame.client.NearbySessions;
import net.deizyn.bhgame.client.render.station.qi_station.QiOrbRenderer;
import net.deizyn.bhgame.core.block.TrainingBlockEntity;
import net.deizyn.bhgame.core.cue.CueChannel;
import net.deizyn.bhgame.core.cue.CueVisual;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import org.joml.Matrix4f;

/**
 * Draws a session's cues at its block, only on the client of the player whose session points at this block.
 * Everyone else (and this client when idle) sees just the plain block model, except the qi_station orb, which
 * everyone nearby sees (D60).
 * <ul>
 *   <li>Channel BLOCK (herbs): a glow on the soil in the colour of the brightest cue, and a root column that rises
 *       with progress.</li>
 *   <li>Channel WORLD_ARROW (wooden dummy): nothing here; the arrow row is on the HUD (D39).</li>
 *   <li>qi_station: the orb between the duellists ({@link QiOrbRenderer}).</li>
 * </ul>
 * Placeholder looks until P6.
 */
public class TrainingBlockRenderer implements BlockEntityRenderer<TrainingBlockEntity> {
    private static final float[] GLOW = {0.35F, 1.0F, 0.75F};
    private static final float[] YIN = {0.35F, 0.6F, 1.0F};
    private static final float[] YANG = {1.0F, 0.78F, 0.25F};
    private static final float[] SPARK = {0.75F, 0.9F, 1.0F};
    private static final float[] SPARK_FAKE = {0.75F, 0.35F, 1.0F};

    @Override
    public void render(TrainingBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        ClientSession session = ClientSession.get();
        if (session == null || !session.pos.equals(blockEntity.getBlockPos())) {
            NearbySessions.State nearby = NearbySessions.get(blockEntity.getBlockPos());
            if (nearby != null) {
                QiOrbRenderer.renderNearby(nearby, ClientSession.clock() + partialTick, poseStack, buffers);
            }
            return;
        }
        double time = ClientSession.clock() + partialTick;
        if (session.channel == CueChannel.WORLD_ARROW) {
            // the dummy's arrow row is drawn on the HUD (ArrowRowHud, D39); the dummy itself stays plain
            return;
        }
        if (session.gameId.equals("qi_station")) {
            QiOrbRenderer.render(session, time, poseStack, buffers);
            return;
        }
        float glow = session.isFocusing() ? 0.12F : session.glow(time);
        float[] rgb = colour(session.isFocusing() ? CueVisual.GLOW : session.glowVisual());
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        box(consumer, matrix, 0.12F, 0.01F, 0.12F, 0.88F, 0.05F, 0.88F, rgb[0], rgb[1], rgb[2], 0.15F + 0.6F * glow);
        float rootTop = 0.05F + 0.85F * session.progress(time);
        box(consumer, matrix, 0.46F, 0.0F, 0.46F, 0.54F, rootTop, 0.54F, 1.0F, 0.9F, 0.5F, 0.55F);
    }

    private static float[] colour(CueVisual visual) {
        return switch (visual) {
            case YIN -> YIN;
            case YANG -> YANG;
            case SPARK -> SPARK;
            case SPARK_FAKE -> SPARK_FAKE;
            default -> GLOW;
        };
    }

    /** An axis-aligned box with every face drawn from both sides (the lightning render type culls back faces). */
    public static void box(VertexConsumer c, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                    float r, float g, float b, float a) {
        quad(c, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a); // top
        quad(c, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a); // bottom
        quad(c, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a); // north
        quad(c, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a); // south
        quad(c, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a); // west
        quad(c, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a); // east
    }

    static void quad(VertexConsumer c, Matrix4f m,
                     float ax, float ay, float az, float bx, float by, float bz,
                     float cx, float cy, float cz, float dx, float dy, float dz,
                     float r, float g, float b, float a) {
        c.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        c.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        c.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        c.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        c.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        c.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        c.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        c.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
    }
}
