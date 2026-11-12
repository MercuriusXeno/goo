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
 * Draws the life mark over each rallied or charmed mob, and each mob Heal or
 * Drain touched lately or Leader senses, turned to face the camera in vanilla's heart sprites: the empty
 * heart whole, the full heart over it cropped where the life's share ends,
 * and the count right of the heart. The mark draws see-through, over any
 * block between it and the camera, so a wall or ceiling the mark floats into
 * cuts none of it.
 * health-bar-is-one-heart-filling-left-to-right
 * leader-shows-life-bars-while-it-stands
 * rallied-and-charmed-mobs-always-show-their-health-bar
 */
final class LifeMarkBillboard {

    /** Vanilla's heart sprites, read as whole textures as InWorldHud reads the effect background. */
    private static final Identifier CONTAINER =
            Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    private static final Identifier FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    /** A heart sprite's side, in pixels. */
    private static final float HEART_SIZE = 9f;
    /** The full heart sprite's leftmost column holding red ink; column 0 is transparent. */
    private static final int FIRST_INK_COLUMN = 1;
    /** The full heart sprite's columns holding red ink, 1 through 7. */
    private static final int INK_COLUMNS = 7;
    /** Slack under a whole column, so a share landing on a column edge fills no extra column. */
    private static final float COLUMN_SLACK = 1e-4f;
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
    /** Flips the camera's y so the mark's y runs downward, as the font draws. */
    private static final float Y_DOWNWARD = -1f;

    private LifeMarkBillboard() {
    }

    /**
     * Where one mark's parts stand, in blocks: x rightward from the head's
     * center, y downward from the mark's top edge, the heart then the count,
     * the whole mark centered over the head.
     * health-bar-draws-two-thirds-with-half-font
     *
     * @param left      the heart's left edge, the mark's left edge
     * @param countLeft the count's left edge
     * @param countTop  the top of the count's digits
     * @param right     the count's right edge, the mark's right edge
     */
    record Layout(float left, float countLeft, float countTop, float right) {

        /** The heart's side, 2/3 of the 0.225 blocks it drew at name-tag scale. */
        private static final float HEART_WIDTH = 0.15f;
        /** Blocks per font pixel, half of name-tag scale's 0.025. */
        private static final float COUNT_SCALE = 0.0125f;
        /** Blocks per heart sprite pixel. */
        private static final float HEART_PIXEL = HEART_WIDTH / HEART_SIZE;
        /** Between the heart and its count: two of the heart's pixels. */
        private static final float GAP = 2f * HEART_PIXEL;
        /** A digit's height in font pixels, the count's ink with no descender under it. */
        private static final float DIGIT_HEIGHT = 7f;

        /**
         * The layout of a mark whose count is as wide as given.
         *
         * @param countFontWidth the count's width in font pixels
         * @return the parts' places
         */
        static Layout of(float countFontWidth) {
            float width = HEART_WIDTH + GAP + countFontWidth * COUNT_SCALE;
            float left = -width / HALVED;
            float countTop = (HEART_WIDTH - DIGIT_HEIGHT * COUNT_SCALE) / HALVED;
            return new Layout(left, left + HEART_WIDTH + GAP, countTop, left + width);
        }

        float heartWidth() {
            return HEART_WIDTH;
        }

        float countScale() {
            return COUNT_SCALE;
        }

        float heartMiddle() {
            return HEART_WIDTH / HALVED;
        }

        float countMiddle() {
            return countTop + DIGIT_HEIGHT * COUNT_SCALE / HALVED;
        }
    }

    /**
     * One mob's life mark as this frame draws it.
     *
     * @param living the mob
     * @param mark   what its life draws
     * @param color  the white tint, its alpha the mark's fade
     * @param layout where the mark's parts stand over the head
     */
    private record Shown(LivingEntity living, LifeMark mark, int color, Layout layout) {
    }

    /**
     * Whether a mob draws a life mark: it is rallied or charmed, Heal or
     * Drain touched it lately, or Leader senses it.
     * leader-shows-life-bars-while-it-stands
     * rallied-and-charmed-mobs-always-show-their-health-bar
     *
     * @param entityId   the mob's id
     * @param tick       the game tick
     * @param touched    the mobs Heal or Drain touched
     * @param leader     the mobs Leader senses
     * @param headMarked whether the mob wears the rally or the charm head mark
     * @return true for a head-marked, a touched or a sensed mob
     */
    static boolean showsLifeMark(int entityId, long tick, TouchedMobs touched, LeaderSense leader,
                                 boolean headMarked) {
        return headMarked || touched.marked(entityId, tick) || leader.appearsGlowing(entityId);
    }

    /**
     * How strongly a mob's life mark draws: whole always on a rallied or
     * charmed mob; otherwise whole while Leader senses it and stands, fading
     * with Leader's outline after it ends, and at TouchedMobs' own fade where
     * only a touch marks it, the stronger of the two answering.
     * leader-shows-life-bars-while-it-stands
     * rallied-and-charmed-mobs-always-show-their-health-bar
     *
     * @param entityId   the mob's id
     * @param time       the game time including the partial tick
     * @param touched    the mobs Heal or Drain touched
     * @param leader     the mobs Leader senses
     * @param headMarked whether the mob wears the rally or the charm head mark
     * @return the strength, zero to one
     */
    static float markStrength(int entityId, float time, TouchedMobs touched, LeaderSense leader,
                              boolean headMarked) {
        if (headMarked) {
            return 1f;
        }
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
            faceCamera(poseStack, camera, mark.living(), partialTick, () -> drawCount(poseStack, buffers, mc.font,
                    mark));
        }
        buffers.endBatch();
    }

    private static void drawCount(PoseStack poseStack, MultiBufferSource buffers, Font font, Shown mark) {
        Layout layout = mark.layout();
        poseStack.pushPose();
        poseStack.translate(layout.countLeft(), layout.countTop(), 0f);
        poseStack.scale(layout.countScale(), layout.countScale(), layout.countScale());
        InWorldHud.drawTextSeeThrough(font, buffers, poseStack, mark.mark().count(), 0f, 0f, mark.color());
        poseStack.popPose();
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
        float strength = markStrength(living.getId(), time, TouchedMobs.CLIENT, LeaderSense.CLIENT,
                HeadMarks.wearsHeadMark(living));
        return new Shown(living, mark, tint(strength), Layout.of(font.width(mark.count())));
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
            float edge = sprite.equals(CONTAINER) ? HEART_SIZE : fillEdge(mark.mark().fill());
            if (edge > 0f) {
                faceCamera(poseStack, camera, mark.living(), partialTick,
                        () -> quad(consumer, poseStack.last(), mark.layout(), edge, mark.color()));
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
     * Runs a draw in block space over the mob's head, turned to the camera,
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
        poseStack.scale(1f, Y_DOWNWARD, 1f);
        poseStack.translate(0f, -Layout.HEART_WIDTH, 0f);
        draw.run();
        poseStack.popPose();
    }

    /**
     * One heart quad cut at a sprite column, the quad's width and its u cut
     * together so the texels keep their size.
     *
     * @param consumer the sprite's buffer
     * @param pose     the pose in block space
     * @param layout   where the mark's heart stands
     * @param edge     the sprite column the quad ends at, at most the heart's side
     * @param color    the tint
     */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Layout layout, float edge, int color) {
        float u = edge / HEART_SIZE;
        float side = layout.heartWidth();
        float x = layout.left();
        float right = x + side * u;
        InWorldHud.iconVertex(consumer, pose, x, 0f, 0f, 0f, 0f, color);
        InWorldHud.iconVertex(consumer, pose, x, side, 0f, 0f, 1f, color);
        InWorldHud.iconVertex(consumer, pose, right, side, 0f, u, 1f, color);
        InWorldHud.iconVertex(consumer, pose, right, 0f, 0f, u, 0f, color);
    }
}
