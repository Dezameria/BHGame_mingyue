package net.deizyn.bhgame.client.render.carry.water_yoke;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.deizyn.bhgame.BhGame;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The carried yoke (D51): a pole that rolls, and two buckets that hang from its ends with their water. Built in code,
 * drawn in entity model space (pixels, y down, +x = the carrier's left) by the third-person layer and the first-person
 * view. One texture laid out for the boxes below (pole, rope, bucket staves with iron bands, water); vanilla block
 * textures stretched over this layout came out grey.
 */
final class YokeModel {

    private static final ResourceLocation TEXTURE = new ResourceLocation(BhGame.MODID, "textures/entity/water_yoke.png");
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 64;
    private static final float PX = 1.0F / 16.0F;
    /** Bucket ends of the pole, model pixels from the middle. */
    private static final float POLE_END = 16.0F;
    private static final float ROPE = 10.0F;
    private static final float BUCKET = 8.0F;
    /** How far the water drops per level lost, model pixels; level 3 sits just under the rim. */
    private static final float WATER_STEP = 2.0F;
    /** The water stops just short of the walls, so its edges never share a plane with them. */
    private static final float WATER_INSET = 0.05F;
    /** How much of the pole's roll the rope cancels: 1 = always plumb, less = the bucket trails the roll. */
    private static final float BUCKET_HANG = 0.8F;

    private static ModelPart pole;
    private static ModelPart rope;
    private static ModelPart bucket;
    private static ModelPart water;

    private static void build() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // texture offsets match the regions painted in textures/entity/water_yoke.png
        root.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-19.0F, -1.0F, -1.0F, 38.0F, 2.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("rope", CubeListBuilder.create().texOffs(0, 8)
                .addBox(-0.25F, 0.0F, -0.25F, 0.5F, ROPE, 0.5F), PartPose.ZERO);
        float half = BUCKET / 2.0F;
        // a whole pixel, so every face starts on a whole texel and the iron bands line up all around the bucket
        float wall = 1.0F;
        // the boxes only touch, never overlap: overlapping faces in one plane flicker (z-fighting). Front and back
        // walls are full width, the side walls fit between them, the bottom fits inside all four.
        float inner = half - wall;
        float gap = BUCKET - 2.0F * wall;
        root.addOrReplaceChild("bucket", CubeListBuilder.create()
                .texOffs(0, 24).addBox(-inner, BUCKET - wall, -inner, gap, wall, gap)
                .texOffs(0, 32).addBox(-half, 0.0F, -half, BUCKET, BUCKET, wall)
                .texOffs(20, 32).addBox(-half, 0.0F, half - wall, BUCKET, BUCKET, wall)
                .texOffs(40, 24).addBox(-half, 0.0F, -inner, wall, BUCKET, gap)
                .texOffs(56, 24).addBox(half - wall, 0.0F, -inner, wall, BUCKET, gap), PartPose.ZERO);
        // below the pole's area (0,0 to 80,4), which it overlapped before and turned half the pole blue
        root.addOrReplaceChild("water", CubeListBuilder.create().texOffs(64, 8)
                .addBox(-inner + WATER_INSET, 0.0F, -inner + WATER_INSET, gap - 2.0F * WATER_INSET, 0.2F,
                        gap - 2.0F * WATER_INSET), PartPose.ZERO);
        ModelPart parts = LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT).bakeRoot();
        pole = parts.getChild("pole");
        rope = parts.getChild("rope");
        bucket = parts.getChild("bucket");
        water = parts.getChild("water");
    }

    /**
     * Draws at the pole's middle; the pose stack is already in place. {@code bodyPitch} is how far the stack leans
     * forward (crouching): the pole leans with the shoulders, the buckets still hang straight down.
     */
    static void render(PoseStack poseStack, MultiBufferSource buffers, int light, float roll, float swing, int data,
                       float bodyPitch) {
        if (pole == null) {
            build();
        }
        VertexConsumer wood = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        VertexConsumer waterBuffer = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotation(roll));
        pole.render(poseStack, wood, light, OverlayTexture.NO_OVERLAY);
        renderBucket(poseStack, wood, waterBuffer, light, POLE_END, roll, swing - bodyPitch, YokeVisuals.left(data));
        renderBucket(poseStack, wood, waterBuffer, light, -POLE_END, roll, -swing - bodyPitch,
                YokeVisuals.right(data));
        poseStack.popPose();
    }

    private static void renderBucket(PoseStack poseStack, VertexConsumer wood, VertexConsumer waterBuffer, int light,
                                     float x, float roll, float swing, int level) {
        poseStack.pushPose();
        poseStack.translate(x * PX, 0.0F, 0.0F);
        // hangs nearly straight down, trailing the pole's roll a little like a pendulum, and swings with the steps
        poseStack.mulPose(Axis.ZP.rotation(-roll * BUCKET_HANG));
        poseStack.mulPose(Axis.XP.rotation(swing));
        // a full bucket pulls its rope a little longer
        float ropeLength = ROPE + level * 0.4F;
        poseStack.pushPose();
        poseStack.scale(1.0F, ropeLength / ROPE, 1.0F);
        rope.render(poseStack, wood, light, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        poseStack.translate(0.0F, ropeLength * PX, 0.0F);
        bucket.render(poseStack, wood, light, OverlayTexture.NO_OVERLAY);
        if (level > 0) {
            poseStack.translate(0.0F, (BUCKET - 1.5F - level * WATER_STEP) * PX, 0.0F);
            water.render(poseStack, waterBuffer, light, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    private YokeModel() {
    }
}
