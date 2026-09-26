package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooTooltipHandler;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Measures, places, backs and paints every machine's in-world HUD panel from
 * the rows the machine supplies (decision one-panel-painter-takes-rows).
 */
public final class PanelPainter {
    /** Height of one panel row (icon + text line). */
    public static final float ROW_HEIGHT = 11f;
    /** Icon render size in scaled pixels (matches the 10x10 tooltip icons). */
    public static final float ICON_SIZE = 10f;
    /** Gap between a row's icon and its text. */
    public static final float ICON_TEXT_GAP = 2f;
    /** Amount text color (white). */
    public static final int TEXT_COLOR = 0xFFFFFFFF;
    /** Label header color (vanilla gold). */
    public static final int LABEL_COLOR = 0xFFFFAA00;
    /** Upgrade level header color (aqua). */
    public static final int UPGRADE_COLOR = 0xFF55FFFF;

    /**
     * Pixel rows the vanilla font draws a digit's ink in, from the top of the
     * text line: the ascii.png provider's ascent (decision diagnose-then-fix-goo-count-alignment).
     */
    public static final float DIGIT_GLYPH_HEIGHT = 7f;

    /** Upgrade level display prefix. */
    private static final String UPGRADE_PREFIX = "Lv ";
    /** Texture path prefix for goo type icons. */
    private static final String ICON_PATH_PREFIX = "textures/goo/type/";
    /** Texture path suffix for goo type icons. */
    private static final String ICON_PATH_SUFFIX = ".png";
    /** Water bucket item texture for a vanilla fluid row. */
    private static final Identifier WATER_BUCKET_ICON =
            Identifier.withDefaultNamespace("textures/item/water_bucket.png");
    /** Lava bucket item texture for a vanilla fluid row. */
    private static final Identifier LAVA_BUCKET_ICON =
            Identifier.withDefaultNamespace("textures/item/lava_bucket.png");
    /** The whole of an icon texture, as a goo type icon draws. */
    private static final GooRenderUtil.UvRect WHOLE_TEXTURE = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);
    /** Divisor for centering. */
    private static final float HALF = 2f;
    /** Border count across a panel, one on each side. */
    private static final int BORDERS_ACROSS = 2;

    private PanelPainter() {
    }

    /**
     * Places the panel in the world and paints its rows on a nine-slice background.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     * @param placement where and how the panel stands
     * @param rows      the rows top to bottom
     */
    public static void paint(PoseStack poseStack, Camera camera, PanelPlacement placement, List<PanelRow> rows) {
        poseStack.pushPose();
        orient(poseStack, camera, placement);
        Font font = Minecraft.getInstance().font;
        PanelSize size = measure(rows, font::width);
        if (placement.face() == Direction.DOWN) {
            poseStack.translate(0, size.height(), 0);
        }
        paintBody(poseStack, font, size, rows);
        poseStack.popPose();
    }

    /**
     * Measures the panel: the widest row and the row stack, inside a border on each side.
     *
     * @param rows      the rows top to bottom
     * @param textWidth the width in pixels the font gives a string
     * @return the panel size in scaled pixels
     */
    public static PanelSize measure(List<PanelRow> rows, ToIntFunction<String> textWidth) {
        float contentWidth = 0;
        for (PanelRow row : rows) {
            contentWidth = Math.max(contentWidth, row.width(textWidth));
        }
        return new PanelSize(
                contentWidth + InWorldHud.BORDER * BORDERS_ACROSS,
                InWorldHud.BORDER * BORDERS_ACROSS + rows.size() * ROW_HEIGHT);
    }

    /**
     * Appends one amount row per goo type to the header rows.
     *
     * @param headers the header rows, top first
     * @param goo     the goo contents, one row per type
     * @return the header rows followed by the goo rows
     */
    public static List<PanelRow> rows(List<PanelRow> headers, GooContents goo) {
        List<PanelRow> rows = new ArrayList<>(headers);
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : goo.getAll().entrySet()) {
            rows.add(gooRow(entry.getKey(), GooTooltipHandler.formatFluidDisplayCompact(entry.getValue())));
        }
        return rows;
    }

    /**
     * Builds the gold label header row.
     *
     * @param label the label text
     * @return the header row
     */
    public static PanelRow labelRow(String label) {
        return PanelRow.header(label, LABEL_COLOR);
    }

    /**
     * Builds the aqua upgrade level header row.
     *
     * @param compression the compression level
     * @return the header row
     */
    public static PanelRow upgradeRow(int compression) {
        return PanelRow.header(UPGRADE_PREFIX + compression, UPGRADE_COLOR);
    }

    /**
     * Builds a goo type icon row with white text.
     *
     * @param type the goo type
     * @param text the text after the icon
     * @return the row
     */
    public static PanelRow gooRow(ResourceKey<GooTypeDefinition> type, String text) {
        return PanelRow.iconText(gooIcon(type), text, TEXT_COLOR);
    }

    /**
     * Builds a vanilla fluid row: bucket icon and amount, drawn over world geometry.
     *
     * @param fluid  the vanilla fluid
     * @param amount the volume in mB
     * @return the row
     */
    public static PanelRow fluidRow(Fluid fluid, long amount) {
        return bucketRow(fluid.isSame(Fluids.WATER) ? WATER_BUCKET_ICON : LAVA_BUCKET_ICON, amount);
    }

    /**
     * Builds the water row: water bucket icon and amount, drawn over world geometry.
     * It names no Fluid, so it builds without a bootstrapped registry
     * (decision diagnose-then-fix-vat-hud-water-row).
     *
     * @param amount the volume in mB
     * @return the row
     */
    public static PanelRow waterRow(long amount) {
        return bucketRow(WATER_BUCKET_ICON, amount);
    }

    /**
     * Returns the icon texture of the water row.
     *
     * @return the water bucket icon identifier
     */
    public static Identifier waterIcon() {
        return WATER_BUCKET_ICON;
    }

    /**
     * Builds a bucket icon row with the compact amount, drawn over world geometry.
     *
     * @param icon   the bucket icon texture
     * @param amount the volume in mB
     * @return the row
     */
    private static PanelRow bucketRow(Identifier icon, long amount) {
        String text = GooTooltipHandler.formatFluidDisplayCompact(amount);
        return new PanelRow(icon, List.of(new PanelRow.TextSegment(text, TEXT_COLOR)), true);
    }

    /**
     * Returns the icon texture of a goo type.
     *
     * @param type the goo type
     * @return the icon texture identifier
     */
    public static Identifier gooIcon(ResourceKey<GooTypeDefinition> type) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, ICON_PATH_PREFIX + GooTypes.id(type) + ICON_PATH_SUFFIX);
    }

    /**
     * Draws one row: the icon centered in the row, the text's glyph block
     * centered on the icon, or on the row when no icon leads it.
     *
     * @param poseStack the pose stack for rendering
     * @param font      the font renderer
     * @param buffers   the buffer source
     * @param row       the row
     * @param x         the row's left X
     * @param y         the row's top Y
     */
    public static void drawRow(PoseStack poseStack, Font font, MultiBufferSource buffers,
                               PanelRow row, float x, float y) {
        RowGeometry geometry = rowGeometry(y, row.icon() != null, DIGIT_GLYPH_HEIGHT);
        Identifier icon = row.icon();
        Identifier secondIcon = row.secondIcon();
        if (icon != null) {
            GooRenderUtil.UvRect uv = row.iconUv() == null ? WHOLE_TEXTURE : row.iconUv();
            drawIcon(poseStack, buffers, row.seeThrough(), new IconQuad(icon, uv), x, geometry.iconTop());
        }
        if (icon != null && secondIcon != null) {
            drawIcon(poseStack, buffers, row.seeThrough(), new IconQuad(secondIcon, WHOLE_TEXTURE),
                    x + ICON_SIZE + ICON_TEXT_GAP, geometry.iconTop());
        }
        float textX = x + row.iconsWidth();
        for (PanelRow.TextSegment segment : row.segments()) {
            drawSegment(poseStack, font, buffers, row.seeThrough(), segment, textX, geometry.textTop());
            textX += font.width(segment.text());
        }
    }

    /**
     * Places a row's icon and text vertically: the icon centers in the row, and
     * the text's glyph block, not its line box, centers on the icon's center,
     * or on the row's center for a header row (decision diagnose-then-fix-goo-count-alignment).
     *
     * @param rowTop      the row's top Y
     * @param hasIcon     whether an icon leads the row
     * @param glyphHeight the pixel rows the font draws the glyphs' ink in, from the text top
     * @return the icon top and the text top
     */
    public static RowGeometry rowGeometry(float rowTop, boolean hasIcon, float glyphHeight) {
        float iconTop = rowTop + (ROW_HEIGHT - ICON_SIZE) / HALF;
        float center = hasIcon ? iconTop + ICON_SIZE / HALF : rowTop + ROW_HEIGHT / HALF;
        return new RowGeometry(iconTop, center - glyphHeight / HALF);
    }

    /**
     * Translates to the anchor, turns toward the camera, nudges off the surface and scales to pixels.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     * @param placement where and how the panel stands
     */
    private static void orient(PoseStack poseStack, Camera camera, PanelPlacement placement) {
        Vec3 cam = camera.position();
        Vec3 anchor = placement.anchor();
        poseStack.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
        switch (placement.facing()) {
            case SIDE_FACE -> InWorldHud.applyFaceRotation(poseStack, placement.face());
            case FLAT -> InWorldHud.applyFlatRotation(poseStack, camera);
            case BILLBOARD, RIM -> InWorldHud.applyBillboardRotation(poseStack, camera, placement.pitch());
        }
        poseStack.translate(0, 0, placement.facing().zNudge());
        poseStack.scale(InWorldHud.PIXEL_SCALE, -InWorldHud.PIXEL_SCALE, InWorldHud.PIXEL_SCALE);
    }

    /**
     * Draws the background centered on the anchor with the rows stacked above it, then flushes.
     *
     * @param poseStack the pose stack for rendering
     * @param font      the font renderer
     * @param size      the measured panel size
     * @param rows      the rows top to bottom
     */
    private static void paintBody(PoseStack poseStack, Font font, PanelSize size, List<PanelRow> rows) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float halfW = size.width() / HALF;
        InWorldHud.renderBackground(poseStack, buffers,
                new PanelRectangle(-halfW, -size.height(), size.width(), size.height()));
        float x = -halfW + InWorldHud.BORDER;
        float y = -size.height() + InWorldHud.BORDER;
        for (PanelRow row : rows) {
            drawRow(poseStack, font, buffers, row, x, y);
            y += ROW_HEIGHT;
        }
        buffers.endBatch();
    }

    /**
     * Draws one text segment at the content depth.
     *
     * @param poseStack  the pose stack for rendering
     * @param font       the font renderer
     * @param buffers    the buffer source
     * @param seeThrough whether the text draws over world geometry
     * @param segment    the text segment
     * @param x          the left X
     * @param y          the top Y
     */
    private static void drawSegment(PoseStack poseStack, Font font, MultiBufferSource buffers,
                                    boolean seeThrough, PanelRow.TextSegment segment, float x, float y) {
        if (seeThrough) {
            InWorldHud.drawTextSeeThrough(font, buffers, poseStack, segment.text(), x, y, segment.color());
        } else {
            InWorldHud.drawText(font, buffers, poseStack, segment.text(), x, y, segment.color());
        }
    }

    /**
     * Draws one icon quad at the content depth.
     *
     * @param poseStack  the pose stack for rendering
     * @param buffers    the buffer source
     * @param seeThrough whether the icon draws over world geometry
     * @param icon       the icon texture and the region of it drawn
     * @param x          the icon's left X
     * @param y          the icon's top Y
     */
    private static void drawIcon(PoseStack poseStack, MultiBufferSource buffers, boolean seeThrough,
                                 IconQuad icon, float x, float y) {
        VertexConsumer vc = buffers.getBuffer(
                seeThrough ? RenderTypes.textSeeThrough(icon.texture()) : RenderTypes.text(icon.texture()));
        PoseStack.Pose pose = poseStack.last();
        GooRenderUtil.UvRect uv = icon.uv();
        float x2 = x + ICON_SIZE;
        float y2 = y + ICON_SIZE;
        InWorldHud.iconVertex(vc, pose, x, y, InWorldHud.CONTENT_Z, uv.u0(), uv.v0());
        InWorldHud.iconVertex(vc, pose, x, y2, InWorldHud.CONTENT_Z, uv.u0(), uv.v1());
        InWorldHud.iconVertex(vc, pose, x2, y2, InWorldHud.CONTENT_Z, uv.u1(), uv.v1());
        InWorldHud.iconVertex(vc, pose, x2, y, InWorldHud.CONTENT_Z, uv.u1(), uv.v0());
    }

    /**
     * An icon texture and the region of it one icon quad draws.
     *
     * @param texture the texture
     * @param uv      the region drawn
     */
    private record IconQuad(Identifier texture, GooRenderUtil.UvRect uv) {
    }

    /**
     * A measured panel size in scaled pixels.
     *
     * @param width  the panel width including borders
     * @param height the panel height including borders
     */
    public record PanelSize(float width, float height) {
    }

    /**
     * Where a row's icon and text stand vertically, in scaled pixels.
     *
     * @param iconTop the icon's top Y
     * @param textTop the text's top Y
     */
    public record RowGeometry(float iconTop, float textTop) {
    }
}
