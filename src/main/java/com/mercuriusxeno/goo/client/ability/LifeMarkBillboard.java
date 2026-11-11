package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ability.LifeMark.HeartFill;
import com.mercuriusxeno.goo.client.hud.InWorldHud;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Draws the life mark over each mob Heal or Drain touched lately, turned to
 * face the camera in vanilla's heart sprites: an empty heart's outline under
 * every heart, the full or half heart over it, and a clustered mark's count
 * beside its one heart. The mark draws see-through, over any block between
 * it and the camera, so a wall or ceiling the mark floats into cuts none of it.
 * touched-mobs-show-hearts-or-a-count-over-max
 */
final class LifeMarkBillboard {

    /** Vanilla's heart sprites, read as whole textures as InWorldHud reads the effect background. */
    private static final Identifier CONTAINER =
            Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    private static final Identifier FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    private static final Identifier HALF = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/half.png");
    /** Blocks per sprite pixel, the scale of vanilla's name tags. */
    private static final float BLOCKS_PER_PIXEL = 0.025f;
    /** A heart sprite's side, in pixels. */
    private static final float HEART_SIZE = 9f;
    /** Pixels from one heart's left edge to the next, overlapping their outlines as the hotbar does. */
    private static final float HEART_STEP = 8f;
    /** Pixels between the clustered heart and its count. */
    private static final float COUNT_GAP = 2f;
    /** Blocks between a bare head and the mark's bottom edge. */
    private static final double OVER_BARE_HEAD = 0.3;
    /** Blocks between a head and the mark's bottom edge where a head mark floats there too. */
    private static final double OVER_HEAD_MARK = 0.75;
    private static final double SIGHT_RANGE = 48.0;
    private static final int WHITE = 0xFFFFFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE = 0xFF;
    /** The faintest alpha drawn; the font reads a lower one as fully opaque. */
    private static final int FAINTEST = 8;
    private static final float HALVED = 2f;

    private LifeMarkBillboard() {
    }

    /**
     * One mob's life mark as this frame draws it.
     *
     * @param living the mob
     * @param mark   what its life draws
     * @param color  the white tint, its alpha the mark's fade
     */
    private record Shown(LivingEntity living, LifeMark mark, int color) {
    }

    /**
     * Draws every touched mob's life mark, the outlines first, then the
     * fills, then the counts, one batch each.
     *
     * @param poseStack   the level's pose stack
     * @param shown       the living in sight that wear a life mark
     * @param partialTick the frame's partial tick
     */
    static void drawAll(PoseStack poseStack, List<LivingEntity> shown, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || shown.isEmpty()) {
            return;
        }
        float time = mc.level.getGameTime() + partialTick;
        List<Shown> marks = shown.stream().map(living -> new Shown(living,
                LifeMark.of(living.getHealth(), living.getMaxHealth()),
                tint(TouchedMobs.CLIENT.opacity(living.getId(), time)))).toList();
        Camera camera = mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawHearts(poseStack, camera, buffers, marks, partialTick, CONTAINER);
        drawHearts(poseStack, camera, buffers, marks, partialTick, FULL);
        drawHearts(poseStack, camera, buffers, marks, partialTick, HALF);
        for (Shown mark : marks) {
            if (mark.mark() instanceof LifeMark.Clustered clustered) {
                faceCamera(poseStack, camera, mark.living(), partialTick, () -> InWorldHud.drawTextSeeThrough(mc.font, buffers,
                        poseStack, clustered.count(), left(1) + HEART_SIZE + COUNT_GAP, 1f, mark.color()));
            }
        }
        buffers.endBatch();
    }

    private static int tint(float opacity) {
        return Math.max(FAINTEST, Math.round(opacity * OPAQUE)) << ALPHA_SHIFT | WHITE;
    }

    private static void drawHearts(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource buffers,
                                   List<Shown> marks, float partialTick, Identifier sprite) {
        RenderType icon = RenderTypes.textSeeThrough(sprite);
        VertexConsumer consumer = buffers.getBuffer(icon);
        for (Shown mark : marks) {
            List<HeartFill> hearts = heartsOf(mark.mark());
            faceCamera(poseStack, camera, mark.living(), partialTick, () -> {
                for (int heart = 0; heart < hearts.size(); heart++) {
                    if (sprite.equals(CONTAINER) || spriteOf(hearts.get(heart)).equals(sprite)) {
                        quad(consumer, poseStack.last(), left(hearts.size()) + heart * HEART_STEP, mark.color());
                    }
                }
            });
        }
        buffers.endBatch(icon);
    }

    /**
     * The hearts a mark draws: its row, or the clustered mark's one heart, drawn full.
     *
     * @param mark the life mark
     * @return the hearts, left to right
     */
    private static List<HeartFill> heartsOf(LifeMark mark) {
        return switch (mark) {
            case LifeMark.Row row -> row.hearts();
            case LifeMark.Clustered ignored -> List.of(HeartFill.FULL);
        };
    }

    private static Identifier spriteOf(HeartFill fill) {
        return switch (fill) {
            case FULL -> FULL;
            case HALF -> HALF;
            case EMPTY -> CONTAINER;
        };
    }

    /**
     * The left edge of a row of hearts, in pixels, so the row centers over the head.
     *
     * @param hearts the hearts in the row
     * @return the left edge
     */
    private static float left(int hearts) {
        return -((hearts - 1) * HEART_STEP + HEART_SIZE) / HALVED;
    }

    /**
     * Runs a draw in pixel space over the mob's head, turned to the camera,
     * x rightward and y downward from the mark's top edge.
     *
     * @param poseStack   the level's pose stack
     * @param camera      the render camera
     * @param living      the mob the mark floats over
     * @param partialTick the frame's partial tick
     * @param draw        the draw run in that space
     */
    private static void faceCamera(PoseStack poseStack, Camera camera, LivingEntity living, float partialTick,
                                   Runnable draw) {
        double over = HeadMarks.wearsHeadMark(living) ? OVER_HEAD_MARK : OVER_BARE_HEAD;
        Vec3 above = living.getPosition(partialTick).add(0, living.getBbHeight() + over, 0)
                .subtract(camera.position());
        if (above.lengthSqr() > SIGHT_RANGE * SIGHT_RANGE) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(above.x, above.y, above.z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(BLOCKS_PER_PIXEL, -BLOCKS_PER_PIXEL, BLOCKS_PER_PIXEL);
        poseStack.translate(0f, -HEART_SIZE, 0f);
        draw.run();
        poseStack.popPose();
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x, int color) {
        InWorldHud.iconVertex(consumer, pose, x, 0f, 0f, 0f, 0f, color);
        InWorldHud.iconVertex(consumer, pose, x, HEART_SIZE, 0f, 0f, 1f, color);
        InWorldHud.iconVertex(consumer, pose, x + HEART_SIZE, HEART_SIZE, 0f, 1f, 1f, color);
        InWorldHud.iconVertex(consumer, pose, x + HEART_SIZE, 0f, 0f, 1f, 0f, color);
    }
}
