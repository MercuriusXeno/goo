package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.hud.InWorldHud;
import com.mercuriusxeno.goo.registry.GooAttachments;
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
import java.util.Optional;

/**
 * Draws the life mark over each rallied, charmed or downed mob, and each mob
 * Heal or Drain touched lately or Kin senses, turned to face the camera in
 * vanilla's heart sprites: the empty heart whole, the full heart over it
 * cropped where the life's share ends, and the count right of the heart; a
 * downed mob draws the skull whole in the heart's slot, its negative count
 * beside it. The mark draws see-through, over any block between it and the
 * camera, so a wall or ceiling the mark floats into cuts none of it.
 * health-bar-is-one-heart-filling-left-to-right
 * leader-shows-life-bars-while-it-stands
 * rallied-and-charmed-mobs-always-show-their-health-bar
 * downed-mob-shows-a-skull-and-its-negative-health
 */
final class LifeMarkBillboard {

    /** Vanilla's full heart sprite, read as a whole texture as InWorldHud reads the effect background. */
    private static final Identifier FULL = Identifier.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    /** A heart sprite's side, in pixels. */
    private static final float HEART_SIZE = 9f;
    /** The full heart sprite's leftmost column holding red ink; column 0 is transparent. */
    private static final int FIRST_INK_COLUMN = 1;
    /** The full heart sprite's columns holding red ink, 1 through 7. */
    private static final int INK_COLUMNS = 7;
    /** Slack under a whole column, so a share landing on a column edge fills no extra column. */
    private static final float COLUMN_SLACK = 1e-4f;
    /** Blocks between the head and the row's bottom edge. */
    private static final double OVER_HEAD = 0.3;
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
     * center, y downward from the row's top edge, the rally or charm icon on
     * a head-marked mob, then the heart, then the count, the whole row
     * centered over the head.
     * health-bar-draws-two-thirds-with-half-font
     * rally-and-charm-icons-sit-left-of-the-heart
     *
     * @param left      the row's left edge, the icon's where it draws
     * @param heartLeft the heart's left edge
     * @param countLeft the count's left edge
     * @param countTop  the top of the count's digits
     * @param right     the count's right edge, the row's right edge
     */
    record Layout(float left, float heartLeft, float countLeft, float countTop, float right) {

        /** The heart's side, 2/3 of the 0.225 blocks it drew at name-tag scale. */
        private static final float HEART_WIDTH = 0.15f;
        /** The icon's side, the heart's, so the fist reads the heart's size. */
        private static final float ICON_WIDTH = HEART_WIDTH;
        /** Blocks per font pixel, half of name-tag scale's 0.025. */
        private static final float COUNT_SCALE = 0.0125f;
        /** Blocks per heart sprite pixel. */
        private static final float HEART_PIXEL = HEART_WIDTH / HEART_SIZE;
        /** Between one part and the next: two of the heart's pixels. */
        private static final float GAP = 2f * HEART_PIXEL;
        /** A digit's height in font pixels, the count's ink with no descender under it. */
        private static final float DIGIT_HEIGHT = 7f;

        /**
         * The layout of a mark whose count is as wide as given.
         *
         * @param countFontWidth the count's width in font pixels
         * @param withIcon       whether the row leads with the rally or charm icon
         * @return the parts' places
         */
        static Layout of(float countFontWidth, boolean withIcon) {
            float iconSlot = withIcon ? ICON_WIDTH + GAP : 0f;
            float width = iconSlot + HEART_WIDTH + GAP + countFontWidth * COUNT_SCALE;
            float left = -width / HALVED;
            float heartLeft = left + iconSlot;
            float countTop = (HEART_WIDTH - DIGIT_HEIGHT * COUNT_SCALE) / HALVED;
            return new Layout(left, heartLeft, heartLeft + HEART_WIDTH + GAP, countTop, left + width);
        }

        float iconLeft() {
            return left;
        }

        float iconWidth() {
            return ICON_WIDTH;
        }

        float iconTop() {
            return heartBottom() - ICON_WIDTH;
        }

        float iconBottom() {
            return heartBottom();
        }

        float heartTop() {
            return 0f;
        }

        float heartBottom() {
            return HEART_WIDTH;
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
     * @param layout   where the mark's parts stand over the head
     * @param headMark the rally or charm icon leading the row, empty on a bare mob
     */
    private record Shown(LivingEntity living, LifeMark mark, int color, Layout layout, Optional<HeadMark> headMark) {
    }

    /**
     * Whether a mob draws a life mark: it is rallied, charmed or downed, Heal
     * or Drain touched it lately, or Kin senses it. Downed outlasts the
     * rally, so a downed mob whose rally ended keeps its mark.
     * leader-shows-life-bars-while-it-stands
     * rallied-and-charmed-mobs-always-show-their-health-bar
     * downed-mob-shows-a-skull-and-its-negative-health
     *
     * @param entityId   the mob's id
     * @param tick       the game tick
     * @param touched    the mobs Heal or Drain touched
     * @param kin     the mobs Kin senses
     * @param headMarked whether the mob wears the rally or the charm head mark
     * @param downed     whether the mob lies downed
     * @return true for a head-marked, a downed, a touched or a sensed mob
     */
    static boolean showsLifeMark(int entityId, long tick, TouchedMobs touched, KinSense kin,
                                 boolean headMarked, boolean downed) {
        return headMarked || downed || touched.marked(entityId, tick) || kin.appearsGlowing(entityId);
    }

    /**
     * How strongly a mob's life mark draws: whole always on a rallied,
     * charmed or downed mob; otherwise whole while Kin senses it and stands,
     * fading with Kin's outline after it ends, and at TouchedMobs' own fade
     * where only a touch marks it, the stronger of the two answering.
     * leader-shows-life-bars-while-it-stands
     * rallied-and-charmed-mobs-always-show-their-health-bar
     * downed-mob-shows-a-skull-and-its-negative-health
     *
     * @param entityId   the mob's id
     * @param time       the game time including the partial tick
     * @param touched    the mobs Heal or Drain touched
     * @param kin     the mobs Kin senses
     * @param headMarked whether the mob wears the rally or the charm head mark
     * @param downed     whether the mob lies downed
     * @return the strength, zero to one
     */
    static float markStrength(int entityId, float time, TouchedMobs touched, KinSense kin,
                              boolean headMarked, boolean downed) {
        if (headMarked || downed) {
            return 1f;
        }
        float touchedStrength = touched.opacity(entityId, time);
        return kin.appearsGlowing(entityId) ? Math.max(kin.strength(time), touchedStrength) : touchedStrength;
    }

    /**
     * Draws every marked mob's life mark, the rally and charm icons first,
     * then the empty hearts and the skulls, then the fills, then the counts,
     * one batch each.
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
        for (HeadMark headMark : HeadMark.values()) {
            drawIcons(poseStack, camera, buffers, marks, partialTick, headMark);
        }
        drawHearts(poseStack, camera, buffers, marks, partialTick, LifeMark.HEART);
        drawHearts(poseStack, camera, buffers, marks, partialTick, LifeMark.SKULL);
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
        boolean downed = living.hasData(GooAttachments.DOWNED);
        LifeMark mark = downed ? LifeMark.downed(living.getData(GooAttachments.DOWNED).negativeHealth())
                : LifeMark.of(living.getHealth(), living.getMaxHealth());
        Optional<HeadMark> headMark = HeadMarks.headMarkOf(living);
        float strength = markStrength(living.getId(), time, TouchedMobs.CLIENT, KinSense.CLIENT,
                headMark.isPresent(), downed);
        return new Shown(living, mark, tint(strength), Layout.of(font.width(mark.count()), headMark.isPresent()),
                headMark);
    }

    private static int tint(float opacity) {
        return Math.max(FAINTEST, Math.round(opacity * OPAQUE)) << ALPHA_SHIFT | WHITE;
    }

    /**
     * Draws one heart-slot sprite on every mark: the empty heart or the
     * skull whole on each mark naming it, or the full heart cropped at the
     * right edge of its filled columns, the quad's width and its u cut
     * together.
     *
     * @param poseStack   the level's pose stack
     * @param camera      the render camera
     * @param buffers     the frame's buffers
     * @param marks       the marks to draw
     * @param partialTick the frame's partial tick
     * @param sprite      the empty heart, the skull or the full heart
     */
    private static void drawHearts(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource buffers,
                                   List<Shown> marks, float partialTick, Identifier sprite) {
        RenderType icon = RenderTypes.textSeeThrough(sprite);
        VertexConsumer consumer = buffers.getBuffer(icon);
        for (Shown mark : marks) {
            float edge = sprite.equals(FULL) ? fillEdge(mark.mark().fill()) : wholeWhereNamed(mark.mark(), sprite);
            if (edge > 0f) {
                Layout layout = mark.layout();
                faceCamera(poseStack, camera, mark.living(), partialTick, () -> quad(consumer, poseStack.last(),
                        layout.heartLeft(), layout.heartTop(), layout.heartWidth(), edge / HEART_SIZE,
                        mark.color()));
            }
        }
        buffers.endBatch(icon);
    }

    /**
     * Draws one head mark's icon at the left of the row on every mob wearing
     * it, in the row's see-through pass and at the row's strength.
     * rally-and-charm-icons-sit-left-of-the-heart
     *
     * @param poseStack   the level's pose stack
     * @param camera      the render camera
     * @param buffers     the frame's buffers
     * @param marks       the marks to draw
     * @param partialTick the frame's partial tick
     * @param headMark    the charm heart or the rally fist
     */
    private static void drawIcons(PoseStack poseStack, Camera camera, MultiBufferSource.BufferSource buffers,
                                  List<Shown> marks, float partialTick, HeadMark headMark) {
        RenderType icon = RenderTypes.textSeeThrough(headMark.texture());
        VertexConsumer consumer = buffers.getBuffer(icon);
        for (Shown mark : marks) {
            if (mark.headMark().filter(headMark::equals).isPresent()) {
                Layout layout = mark.layout();
                faceCamera(poseStack, camera, mark.living(), partialTick, () -> quad(consumer, poseStack.last(),
                        layout.iconLeft(), layout.iconTop(), layout.iconWidth(), 1f, mark.color()));
            }
        }
        buffers.endBatch(icon);
    }

    /**
     * The edge a whole heart-slot sprite draws to on a mark: all of it where
     * the mark names the sprite, none where it names the other.
     *
     * @param mark   the mark
     * @param sprite the empty heart or the skull
     * @return the edge in sprite pixels
     */
    private static float wholeWhereNamed(LifeMark mark, Identifier sprite) {
        return mark.sprite().equals(sprite) ? HEART_SIZE : 0f;
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
        Vec3 above = living.getPosition(partialTick).add(0, living.getBbHeight() + OVER_HEAD, 0)
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
     * One square sprite quad, its left part kept up to a share of its width,
     * the quad's width and its u cut together so the texels keep their size.
     *
     * @param consumer the sprite's buffer
     * @param pose     the pose in block space
     * @param x        the quad's left edge
     * @param y        the quad's top edge
     * @param side     the whole sprite's side
     * @param u        the share of the sprite kept from its left edge, one for all of it
     * @param color    the tint
     */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float side, float u,
                             int color) {
        float right = x + side * u;
        float bottom = y + side;
        InWorldHud.iconVertex(consumer, pose, x, y, 0f, 0f, 0f, color);
        InWorldHud.iconVertex(consumer, pose, x, bottom, 0f, 0f, 1f, color);
        InWorldHud.iconVertex(consumer, pose, right, bottom, 0f, u, 1f, color);
        InWorldHud.iconVertex(consumer, pose, right, y, 0f, u, 0f, color);
    }
}
