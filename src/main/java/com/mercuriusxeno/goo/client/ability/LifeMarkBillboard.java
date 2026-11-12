package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.hud.InWorldHud;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Draws the life mark over each mob Heal or Drain touched lately or Leader
 * senses, turned to face the camera in vanilla's heart sprites: the empty
 * heart whole, the full heart over it cropped where the life's share ends,
 * and the count right of the heart. The mark draws see-through, over any
 * block between it and the camera, so a wall or ceiling the mark floats into
 * cuts none of it.
 * health-bar-is-one-heart-filling-left-to-right
 * leader-shows-life-bars-while-it-stands
 */
final class LifeMarkBillboard {

    /** Vanilla's heart sprites, read as whole textures as InWorldHud reads the effect background. */
    private static final Identifier CONTAINER =
            Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    private static final Identifier FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    /** Blocks per sprite pixel, the scale of vanilla's name tags. */
    private static final float BLOCKS_PER_PIXEL = 0.025f;
    /** A heart sprite's side, in pixels. */
    private static final float HEART_SIZE = 9f;
    /** The full heart sprite's leftmost column holding red ink; column 0 is transparent. */
    private static final int FIRST_INK_COLUMN = 1;
    /** The full heart sprite's columns holding red ink, 1 through 7. */
    private static final int INK_COLUMNS = 7;
    /** Slack under a whole column, so a share landing on a column edge fills no extra column. */
    private static final float COLUMN_SLACK = 1e-4f;
    /** Pixels between the heart and its count. */
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
     * @param left   the mark's left edge in pixels, which centers the whole mark over the head
     */
    private record Shown(LivingEntity living, LifeMark mark, int color, float left) {
    }

    /**
     * Whether a mob draws a life mark: Heal or Drain touched it lately, or
     * Leader senses it.
     * leader-shows-life-bars-while-it-stands
     *
     * @param entityId the mob's id
     * @param tick     the game tick
     * @param touched  the mobs Heal or Drain touched
     * @param leader   the mobs Leader senses
     * @return true for a touched or a sensed mob
     */
    static boolean showsLifeMark(int entityId, long tick, TouchedMobs touched, LeaderSense leader) {
        return touched.marked(entityId, tick) || leader.appearsGlowing(entityId);
    }

    /**
     * How strongly a mob's life mark draws: whole while Leader senses it and
     * stands, fading with Leader's outline after it ends, and at TouchedMobs'
     * own fade where only a touch marks it; the stronger of the two answers.
     * leader-shows-life-bars-while-it-stands
     *
     * @param entityId the mob's id
     * @param time     the game time including the partial tick
     * @param touched  the mobs Heal or Drain touched
     * @param leader   the mobs Leader senses
     * @return the strength, zero to one
     */
    static float markStrength(int entityId, float time, TouchedMobs touched, LeaderSense leader) {
        float touchedStrength = touched.opacity(entityId, time);
        return leader.appearsGlowing(entityId) ? Math.max(leader.strength(time), touchedStrength) : touchedStrength;
    }

    /**
     * Draws every marked mob's life mark, the empty hearts first, then the
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
        List<Shown> marks = shown.stream().map(living -> shownAt(living, time, mc.font)).toList();
        Camera camera = mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawHearts(poseStack, camera, buffers, marks, partialTick, CONTAINER);
        drawHearts(poseStack, camera, buffers, marks, partialTick, FULL);
        for (Shown mark : marks) {
            faceCamera(poseStack, camera, mark.living(), partialTick, () -> InWorldHud.drawTextSeeThrough(
                    mc.font, buffers, poseStack, mark.mark().count(), mark.left() + HEART_SIZE + COUNT_GAP, 1f,
                    mark.color()));
        }
        buffers.endBatch();
    }

    /**
     * How many of the full heart's inked columns a share of life fills,
     * snapped up to a whole sprite column so no texel splits, and at least
     * one column while any life is left.
     * health-bar-is-one-heart-filling-left-to-right
     *
     * @param share the life over the max life, zero to one
     * @return the filled columns, zero through all seven
     */
    static int filledInkColumns(float share) {
        if (share <= 0f) {
            return 0;
        }
        return Math.clamp((int) Math.ceil(share * INK_COLUMNS - COLUMN_SLACK), 1, INK_COLUMNS);
    }

    /**
     * One mob's life mark at a moment, tinted by its mark's strength.
     *
     * @param living the mob
     * @param time   the game time including the partial tick
     * @param font   the font the count draws in
     * @return the mark as the frame draws it
     */
    private static Shown shownAt(LivingEntity living, float time, Font font) {
        LifeMark mark = LifeMark.of(living.getHealth(), living.getMaxHealth());
        return new Shown(living, mark, tint(markStrength(living.getId(), time, TouchedMobs.CLIENT, LeaderSense.CLIENT)),
                leftOf(mark, font));
    }

    /**
     * The left edge that centers a mark over the head: its heart, gap and
     * count taken as one width.
     *
     * @param mark the life mark
     * @param font the font the count draws in
     * @return the left edge, in pixels
     */
    private static float leftOf(LifeMark mark, Font font) {
        return -(HEART_SIZE + COUNT_GAP + font.width(mark.count())) / HALVED;
    }

    private static int tint(float opacity) {
        return Math.max(FAINTEST, Math.round(opacity * OPAQUE)) << ALPHA_SHIFT | WHITE;
    }

    /**
     * Draws one heart sprite on every mark: the empty heart whole, or the
     * full heart cropped at the right edge of its filled columns, the quad's
     * width and its u cut together.
     *
     * @param poseStack   the level's pose stack
     * @param camera      the render camera
     * @param buffers     the frame's buffers
     * @param marks       the marks to draw
     * @param partialTick the frame's partial tick
     * @param sprite      the empty or the full heart
     */
    private static void drawHearts(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource buffers,
                                   List<Shown> marks, float partialTick, Identifier sprite) {
        RenderType icon = RenderTypes.textSeeThrough(sprite);
        VertexConsumer consumer = buffers.getBuffer(icon);
        for (Shown mark : marks) {
            float width = sprite.equals(CONTAINER) ? HEART_SIZE : fillEdge(mark.mark().fill());
            if (width > 0f) {
                faceCamera(poseStack, camera, mark.living(), partialTick,
                        () -> quad(consumer, poseStack.last(), mark.left(), width, mark.color()));
            }
        }
        buffers.endBatch(icon);
    }

    /**
     * The full heart's crop edge, in sprite pixels from its left edge.
     *
     * @param share the life over the max life
     * @return the edge, zero where no life is left
     */
    private static float fillEdge(float share) {
        int columns = filledInkColumns(share);
        return columns == 0 ? 0f : FIRST_INK_COLUMN + columns;
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

    /**
     * One heart quad, its left part as wide as the width given, the sprite's u
     * cut at the same share so the texels keep their size.
     *
     * @param consumer the sprite's buffer
     * @param pose     the pose in pixel space
     * @param x        the quad's left edge, in pixels
     * @param width    the quad's width, in pixels, at most the heart's side
     * @param color    the tint
     */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x, float width, int color) {
        float u = width / HEART_SIZE;
        InWorldHud.iconVertex(consumer, pose, x, 0f, 0f, 0f, 0f, color);
        InWorldHud.iconVertex(consumer, pose, x, HEART_SIZE, 0f, 0f, 1f, color);
        InWorldHud.iconVertex(consumer, pose, x + width, HEART_SIZE, 0f, u, 1f, color);
        InWorldHud.iconVertex(consumer, pose, x + width, 0f, 0f, u, 0f, color);
    }
}
