package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.workbench.GooAmountGrid;
import com.mercuriusxeno.goo.client.workbench.Lettering;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Lays and draws an in-world HUD panel's goo amounts as the workbench lays a tooltip's: max(1, ceil(sqrt(count)))
 * columns filled row by row, each column as wide as its widest cell, a cell its type icon then its amount lettered at
 * the workbench's amount scale, one gap between columns. A panel carries the grid as one row block, so the panel's one
 * shrink rule scales it with the rest.
 * decision one-class-lays-every-huds-goo-values
 *
 * @param cells the cells in the order the machine supplies them
 */
public record PanelAmountGrid(List<Cell> cells) {
    /** The room between one column's widest cell and the next column's icons, the workbench grid's own. */
    public static final float COLUMN_GAP = GooAmountGrid.COLUMN_GAP;
    /** The room a cell's icon and its gap take before the digits. */
    private static final float ICON_AND_GAP = PanelPainter.ICON_SIZE + PanelPainter.ICON_TEXT_GAP;

    /**
     * Copies the cells so the grid holds its own list.
     *
     * @param cells the cells in the order the machine supplies them
     */
    public PanelAmountGrid {
        cells = List.copyOf(cells);
    }

    /**
     * Builds the grid of a goo map's amounts, one white cell per type in the map's order.
     *
     * @param volumes each type's volume in mB, in the order the grid lays them
     * @return the grid
     */
    public static PanelAmountGrid ofVolumes(Map<ResourceKey<GooTypeDefinition>, Integer> volumes) {
        List<Cell> cells = new ArrayList<>(volumes.size());
        volumes.forEach((type, volume) -> cells.add(Cell.amount(type, GooFormat.formatAmount(volume))));
        return new PanelAmountGrid(cells);
    }

    /**
     * Answers whether the grid lays no cell.
     *
     * @return true for no cells
     */
    public boolean isEmpty() {
        return cells.isEmpty();
    }

    /**
     * Answers the column count, the workbench tooltip's square layout.
     *
     * @return max(1, ceil(sqrt(cell count)))
     */
    public int columns() {
        return Math.max(1, (int) Math.ceil(Math.sqrt(cells.size())));
    }

    /**
     * Answers the row count the columns fill to.
     *
     * @return the rows, 0 for no cells
     */
    public int rows() {
        return (cells.size() + columns() - 1) / columns();
    }

    /**
     * Measures a cell's digits at the amount scale: its segments together, or its floor text where wider, at the
     * font's width times {@link Lettering#AMOUNT_SCALE}, rounded up to a whole pixel.
     *
     * @param cell      the cell
     * @param textWidth the width in pixels the font gives a string at its own size
     * @return the digits' width in scaled pixels
     */
    public float digitsWidth(Cell cell, ToIntFunction<String> textWidth) {
        int segmentsWidth = 0;
        for (PanelRow.TextSegment segment : cell.segments()) {
            segmentsWidth += textWidth.applyAsInt(segment.text());
        }
        int floorWidth = cell.floorText() == null ? 0 : textWidth.applyAsInt(cell.floorText());
        return (float) Math.ceil(Math.max(segmentsWidth, floorWidth) * Lettering.AMOUNT_SCALE);
    }

    /**
     * Measures one cell: its icon, the gap, then its digits.
     *
     * @param index     the cell's place in the grid, from 0
     * @param textWidth the width in pixels the font gives a string at its own size
     * @return the cell's width in scaled pixels
     */
    public float cellWidth(int index, ToIntFunction<String> textWidth) {
        return ICON_AND_GAP + digitsWidth(cells.get(index), textWidth);
    }

    /**
     * Measures one column as its widest cell.
     *
     * @param column    the column, from 0 at the left
     * @param textWidth the width in pixels the font gives a string at its own size
     * @return the column's width in scaled pixels
     */
    public float columnWidth(int column, ToIntFunction<String> textWidth) {
        float widest = 0;
        for (int index = column; index < cells.size(); index += columns()) {
            widest = Math.max(widest, cellWidth(index, textWidth));
        }
        return widest;
    }

    /**
     * Places a cell's left edge from the grid's left: the columns before it, each with its gap.
     *
     * @param index     the cell's place in the grid, from 0
     * @param textWidth the width in pixels the font gives a string at its own size
     * @return the cell's left X in scaled pixels
     */
    public float cellLeft(int index, ToIntFunction<String> textWidth) {
        float left = 0;
        for (int column = 0; column < index % columns(); column++) {
            left += columnWidth(column, textWidth) + COLUMN_GAP;
        }
        return left;
    }

    /**
     * Places a cell's top edge from the grid's top: one panel row per grid row above it.
     *
     * @param index the cell's place in the grid, from 0
     * @return the cell's top Y in scaled pixels
     */
    public float cellTop(int index) {
        return Math.floorDiv(index, columns()) * PanelPainter.ROW_HEIGHT;
    }

    /**
     * Measures the grid across: its columns with one gap between each.
     *
     * @param textWidth the width in pixels the font gives a string at its own size
     * @return the grid's width in scaled pixels, 0 for no cells
     */
    public float width(ToIntFunction<String> textWidth) {
        if (cells.isEmpty()) {
            return 0;
        }
        float width = -COLUMN_GAP;
        for (int column = 0; column < columns(); column++) {
            width += columnWidth(column, textWidth) + COLUMN_GAP;
        }
        return width;
    }

    /**
     * Measures the grid down: one panel row per grid row.
     *
     * @return the grid's height in scaled pixels
     */
    public float height() {
        return rows() * PanelPainter.ROW_HEIGHT;
    }

    /**
     * Draws every cell: its icon centered in its row, its digits at the amount scale with their glyph block centered
     * on the icon.
     *
     * @param poseStack the pose stack for rendering
     * @param font      the font renderer
     * @param buffers   the buffer source
     * @param origin    the grid's top left, in the panel's scaled pixels
     * @param opacity   the fade every part draws at
     */
    void draw(PoseStack poseStack, Font font, MultiBufferSource buffers, PanelRectangle origin, float opacity) {
        for (int index = 0; index < cells.size(); index++) {
            Cell cell = cells.get(index);
            float left = origin.x() + cellLeft(index, font::width);
            PanelPainter.RowGeometry geometry = PanelPainter.rowGeometry(origin.y() + cellTop(index), true,
                    PanelPainter.DIGIT_GLYPH_HEIGHT * Lettering.AMOUNT_SCALE);
            PanelPainter.drawTextureIcon(poseStack, buffers, cell.icon(), new PanelRectangle(left, geometry.iconTop(),
                    PanelPainter.ICON_SIZE, PanelPainter.ICON_SIZE), opacity);
            poseStack.pushPose();
            poseStack.translate(left + ICON_AND_GAP, geometry.textTop(), 0);
            poseStack.scale(Lettering.AMOUNT_SCALE, Lettering.AMOUNT_SCALE, 1f);
            float textX = 0;
            for (PanelRow.TextSegment segment : cell.segments()) {
                PanelPainter.drawFadedSegment(poseStack, font, buffers, segment, textX, opacity);
                textX += font.width(segment.text());
            }
            poseStack.popPose();
        }
    }

    /**
     * One amount in the grid: a type icon, then its text in colored segments.
     *
     * @param icon      the goo type's icon texture
     * @param segments  the text segments drawn left to right after the icon
     * @param floorText the text whose width the cell's digits never measure under, or null for no floor
     */
    public record Cell(Identifier icon, List<PanelRow.TextSegment> segments, @Nullable String floorText) {
        /**
         * Copies the segments so the cell holds its own list.
         *
         * @param icon      the goo type's icon texture
         * @param segments  the text segments drawn left to right after the icon
         * @param floorText the text whose width the cell's digits never measure under, or null for no floor
         */
        public Cell {
            segments = List.copyOf(segments);
        }

        /**
         * Builds a cell of one type's amount in white.
         *
         * @param type   the goo type
         * @param amount the amount as the panel reads it
         * @return the cell
         */
        public static Cell amount(ResourceKey<GooTypeDefinition> type, String amount) {
            return new Cell(PanelPainter.gooIcon(type), List.of(new PanelRow.TextSegment(amount,
                    PanelPainter.TEXT_COLOR)), null);
        }
    }
}
