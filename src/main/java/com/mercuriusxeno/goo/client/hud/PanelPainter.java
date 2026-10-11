package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
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
import net.minecraft.util.ARGB;
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
    /**
     * The most of the screen's height a panel covers before it splits or shrinks
     * (decision wheel-fills-eighty-percent-of-screen).
     */
    private static final double SCREEN_HEIGHT_CAP = 0.8;
    /** Columns a panel splits into past the cap. */
    private static final int COLUMNS = 2;
    /** Gap between two columns: the panel's left and right borders together. */
    private static final float COLUMN_GAP = InWorldHud.BORDER * BORDERS_ACROSS;

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
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        double distance = camera.position().distanceTo(placement.anchor());
        double fovDegrees = minecraft.options.fov().get();
        PanelLayout layout = layOut(rows, font::width, distance, fovDegrees);
        float shrink = (float) shrinkFactor(
                projectedFraction(layout.size().height() * InWorldHud.PIXEL_SCALE, distance, fovDegrees));
        poseStack.pushPose();
        orient(poseStack, camera, placement);
        // scales about the bottom center, the anchor (decision panel-wraps-to-two-columns-then-shrinks)
        poseStack.scale(shrink, shrink, shrink);
        if (placement.face() == Direction.DOWN) {
            poseStack.translate(0, layout.size().height(), 0);
        }
        paintBody(poseStack, font, layout, rows, placement.opacity());
        poseStack.popPose();
    }

    /**
     * Scales a packed ARGB color's alpha by the panel's fade, keeping its RGB
     * (decision diagnose-then-fix-hud-panel-fade).
     *
     * @param argb    the packed ARGB color
     * @param opacity the fade factor [0, 1]
     * @return the color with its alpha scaled
     */
    public static int fadeColor(int argb, float opacity) {
        return ARGB.multiplyAlpha(argb, opacity);
    }

    /**
     * Answers how much of the screen's height a panel covers at a distance from
     * the camera: its world height over the view frustum's height there
     * (decision panel-wraps-to-two-columns-then-shrinks).
     *
     * @param worldHeight the panel's height in blocks
     * @param distance    the panel's distance from the camera in blocks
     * @param fovDegrees  the vertical field of view in degrees
     * @return the panel's height as a fraction of the screen's height
     */
    public static double projectedFraction(double worldHeight, double distance, double fovDegrees) {
        return worldHeight / (HALF * distance * Math.tan(Math.toRadians(fovDegrees) / HALF));
    }

    /**
     * Answers the uniform scale that brings a panel covering a fraction of the
     * screen's height down to {@link #SCREEN_HEIGHT_CAP}, never above its
     * natural size (decision panel-wraps-to-two-columns-then-shrinks).
     *
     * @param fraction the laid out panel's height as a fraction of the screen's height
     * @return the cap over the fraction past the cap, 1 at or under it
     */
    public static double shrinkFactor(double fraction) {
        return Math.min(1, SCREEN_HEIGHT_CAP / fraction);
    }

    /**
     * Lays the rows out in one column, or in two side by side when one column
     * would cover more than {@link #SCREEN_HEIGHT_CAP} of the screen's height
     * (decision panel-wraps-to-two-columns-then-shrinks).
     *
     * @param rows       the rows top to bottom
     * @param textWidth  the width in pixels the font gives a string
     * @param distance   the panel's distance from the camera in blocks
     * @param fovDegrees the vertical field of view in degrees
     * @return the panel size and where each row stands in it
     */
    public static PanelLayout layOut(List<PanelRow> rows, ToIntFunction<String> textWidth,
                                     double distance, double fovDegrees) {
        PanelSize oneColumn = measure(rows, textWidth);
        double fraction = projectedFraction(oneColumn.height() * InWorldHud.PIXEL_SCALE, distance, fovDegrees);
        if (fraction <= SCREEN_HEIGHT_CAP) {
            return new PanelLayout(oneColumn, placeColumn(rows.size(), 0, 0));
        }
        int firstColumnRows = (rows.size() + 1) / COLUMNS;
        float firstWidth = contentWidth(rows.subList(0, firstColumnRows), textWidth);
        float secondWidth = contentWidth(rows.subList(firstColumnRows, rows.size()), textWidth);
        List<RowSpot> spots = new ArrayList<>(placeColumn(firstColumnRows, 0, 0));
        spots.addAll(placeColumn(rows.size() - firstColumnRows, 1, firstWidth + COLUMN_GAP));
        PanelSize size = new PanelSize(
                firstWidth + COLUMN_GAP + secondWidth + InWorldHud.BORDER * BORDERS_ACROSS,
                InWorldHud.BORDER * BORDERS_ACROSS + firstColumnRows * ROW_HEIGHT);
        return new PanelLayout(size, spots);
    }

    /**
     * Stacks a column's rows from the panel's top border down.
     *
     * @param rowCount the rows in the column
     * @param column   the column index, 0 for the left
     * @param offset   the column's left edge past the left border
     * @return each row's spot, top first
     */
    private static List<RowSpot> placeColumn(int rowCount, int column, float offset) {
        List<RowSpot> spots = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            spots.add(new RowSpot(column, InWorldHud.BORDER + offset, InWorldHud.BORDER + i * ROW_HEIGHT));
        }
        return spots;
    }

    /**
     * Answers the widest row's width.
     *
     * @param rows      the rows
     * @param textWidth the width in pixels the font gives a string
     * @return the widest row's width, 0 for no rows
     */
    private static float contentWidth(List<PanelRow> rows, ToIntFunction<String> textWidth) {
        float width = 0;
        for (PanelRow row : rows) {
            width = Math.max(width, row.width(textWidth));
        }
        return width;
    }

    /**
     * Measures the panel: the widest row and the row stack, inside a border on each side.
     *
     * @param rows      the rows top to bottom
     * @param textWidth the width in pixels the font gives a string
     * @return the panel size in scaled pixels
     */
    public static PanelSize measure(List<PanelRow> rows, ToIntFunction<String> textWidth) {
        return new PanelSize(
                contentWidth(rows, textWidth) + InWorldHud.BORDER * BORDERS_ACROSS,
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
            rows.add(gooRow(entry.getKey(), GooFormat.formatAmount(entry.getValue())));
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
     * Builds the text row of a container's total against its capacity.
     *
     * @param total    the volume every type holds together
     * @param capacity the container's capacity
     * @return the row
     */
    public static PanelRow fillRow(long total, long capacity) {
        return PanelRow.header(GooFormat.formatFill(total, capacity), TEXT_COLOR);
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
        String text = GooFormat.formatAmount(amount);
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
        drawRow(poseStack, font, buffers, row, new RowOrigin(x, y, 1f));
    }

    /**
     * Draws one row at the panel's fade.
     *
     * @param poseStack the pose stack for rendering
     * @param font      the font renderer
     * @param buffers   the buffer source
     * @param row       the row
     * @param origin    the row's top left and the fade it draws at
     */
    private static void drawRow(PoseStack poseStack, Font font, MultiBufferSource buffers,
                                PanelRow row, RowOrigin origin) {
        float x = origin.x();
        RowGeometry geometry = rowGeometry(origin.y(), row.icon() != null, DIGIT_GLYPH_HEIGHT);
        int iconColor = fadeColor(InWorldHud.OPAQUE_WHITE, origin.opacity());
        Identifier icon = row.icon();
        Identifier secondIcon = row.secondIcon();
        if (icon != null) {
            GooRenderUtil.UvRect uv = row.iconUv() == null ? WHOLE_TEXTURE : row.iconUv();
            drawIcon(poseStack, buffers, row.seeThrough(), new IconQuad(icon, uv, iconColor), x, geometry.iconTop());
        }
        if (icon != null && secondIcon != null) {
            drawIcon(poseStack, buffers, row.seeThrough(), new IconQuad(secondIcon, WHOLE_TEXTURE, iconColor),
                    x + ICON_SIZE + ICON_TEXT_GAP, geometry.iconTop());
        }
        float textX = x + row.iconsWidth();
        for (PanelRow.TextSegment segment : row.segments()) {
            PanelRow.TextSegment faded = new PanelRow.TextSegment(
                    segment.text(), fadeColor(segment.color(), origin.opacity()));
            drawSegment(poseStack, font, buffers, row.seeThrough(), faded, textX, geometry.textTop());
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
     * @param layout    the panel size and where each row stands
     * @param rows      the rows top to bottom
     * @param opacity   the fade every part of the panel draws at
     */
    private static void paintBody(PoseStack poseStack, Font font, PanelLayout layout, List<PanelRow> rows,
                                  float opacity) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        PanelSize size = layout.size();
        float left = -size.width() / HALF;
        float top = -size.height();
        InWorldHud.renderBackground(poseStack, buffers, new PanelRectangle(left, top, size.width(), size.height()),
                fadeColor(InWorldHud.OPAQUE_WHITE, opacity));
        for (int i = 0; i < rows.size(); i++) {
            RowSpot spot = layout.spots().get(i);
            drawRow(poseStack, font, buffers, rows.get(i), new RowOrigin(left + spot.x(), top + spot.y(), opacity));
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
     * @param icon       the icon texture, the region of it drawn and its tint
     * @param x          the icon's left X
     * @param y          the icon's top Y
     */
    private static void drawIcon(PoseStack poseStack, MultiBufferSource buffers, boolean seeThrough,
                                 IconQuad icon, float x, float y) {
        VertexConsumer vc = buffers.getBuffer(
                seeThrough ? RenderTypes.textSeeThrough(icon.texture()) : RenderTypes.text(icon.texture()));
        PoseStack.Pose pose = poseStack.last();
        GooRenderUtil.UvRect uv = icon.uv();
        int color = icon.color();
        float x2 = x + ICON_SIZE;
        float y2 = y + ICON_SIZE;
        InWorldHud.iconVertex(vc, pose, x, y, InWorldHud.CONTENT_Z, uv.u0(), uv.v0(), color);
        InWorldHud.iconVertex(vc, pose, x, y2, InWorldHud.CONTENT_Z, uv.u0(), uv.v1(), color);
        InWorldHud.iconVertex(vc, pose, x2, y2, InWorldHud.CONTENT_Z, uv.u1(), uv.v1(), color);
        InWorldHud.iconVertex(vc, pose, x2, y, InWorldHud.CONTENT_Z, uv.u1(), uv.v0(), color);
    }

    /**
     * An icon texture, the region of it one icon quad draws, and the tint it draws at.
     *
     * @param texture the texture
     * @param uv      the region drawn
     * @param color   the ARGB tint, whose alpha carries the panel's fade
     */
    private record IconQuad(Identifier texture, GooRenderUtil.UvRect uv, int color) {
    }

    /**
     * Where a row starts and the fade it draws at.
     *
     * @param x       the row's left X
     * @param y       the row's top Y
     * @param opacity the fade factor [0, 1]
     */
    private record RowOrigin(float x, float y, float opacity) {
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
     * A laid out panel: its size and each row's spot, in row order.
     *
     * @param size  the panel size including borders
     * @param spots each row's spot, one per row in row order
     */
    public record PanelLayout(PanelSize size, List<RowSpot> spots) {
        /**
         * Copies the spots so the layout holds its own list.
         *
         * @param size  the panel size including borders
         * @param spots each row's spot, one per row in row order
         */
        public PanelLayout {
            spots = List.copyOf(spots);
        }
    }

    /**
     * Where one row stands in its panel, from the panel's top left corner.
     *
     * @param column the column index, 0 for the left
     * @param x      the row's left X
     * @param y      the row's top Y
     */
    public record RowSpot(int column, float x, float y) {
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
