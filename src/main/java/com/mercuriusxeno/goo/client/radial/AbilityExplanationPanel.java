package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.client.ClientDimension;
import com.mercuriusxeno.goo.client.hud.MixedNumberLabel;
import com.mercuriusxeno.goo.client.hud.NineSlice;
import com.mercuriusxeno.goo.client.hud.PanelPainter;
import com.mercuriusxeno.goo.client.hud.PanelRectangle;
import com.mercuriusxeno.goo.client.hud.PanelRow;
import com.mercuriusxeno.goo.client.hud.WordWrap;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.CostMechanicsLookup;
import com.mercuriusxeno.goo.client.network.OfferedAbility;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The panel right of the wheel explaining the hovered ability: its name, its
 * description, and for an ability costing nothing up front its cost-mechanics
 * text, each wrapped to the panel's width and painted as PanelPainter's rows
 * on the HUD panels' nine-slice background.
 * decision explanation-panel-draws-right-of-the-wheel
 * decision one-panel-painter-takes-rows
 */
final class AbilityExplanationPanel {

    /** The gap between the wheel's outer radius and the panel's left edge. */
    private static final int WHEEL_GAP = 8;
    /** The gap the panel keeps from the screen's right edge. */
    private static final int EDGE_MARGIN = 4;
    /** The widest the panel's text runs before it wraps. */
    private static final int MAX_TEXT_WIDTH = 160;
    /** The narrowest the panel's text wraps to, however little room the screen leaves. */
    private static final int MIN_TEXT_WIDTH = 60;
    /** The color the cost-mechanics lines draw in, apart from the description's white. */
    static final int COST_MECHANICS_COLOR = 0xFF55FFFF;
    private static final int HALF = 2;
    private static final int BORDERS_ACROSS = 2;

    private AbilityExplanationPanel() {
    }

    /**
     * Draws the panel for the open type's hovered ability; nothing draws while
     * no unlocked ability is hovered.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param frame    what the frame draws from
     */
    static void paint(GuiGraphicsExtractor graphics, Font font, RadialWheelRenderer.Frame frame) {
        RadialWheel wheel = frame.wheel();
        int hovered = wheel.hoveredAbility();
        if (!wheel.isOpen() || hovered == RadialWheel.NONE) {
            return;
        }
        OfferedAbility offered = frame.abilities().get(wheel.selectedType()).get(hovered);
        if (offered.refused()) {
            return;
        }
        int left = panelLeft(frame);
        int textWidth = textWidth(frame.centerX() * HALF - EDGE_MARGIN - left);
        ToIntFunction<String> fontWidth = text -> font.width(Component.literal(text));
        // every-mixed-number-takes-the-spacing-rule: the text wraps and the panel sizes to the respaced widths
        ToIntFunction<String> widthOf = MixedNumberLabel.spacedWidth(fontWidth);
        List<PanelRow> rows = rows(offered.ability(), frame.look().costMechanicsLookup(), textWidth, widthOf,
                ClientDimension.standingIn());
        PanelPainter.PanelSize size = PanelPainter.measure(rows, widthOf);
        int top = frame.centerY() - Math.round(size.height()) / HALF;
        NineSlice.blit(graphics, new PanelRectangle(left, top, Math.round(size.width()), Math.round(size.height())));
        drawRows(graphics, font, rows, new RowsOrigin(left + Math.round(NineSlice.BORDER),
                top + Math.round(NineSlice.BORDER)), fontWidth);
    }

    /**
     * The panel's left edge: a gap past the wheel's outer radius, right of the wheel.
     *
     * @param frame what the frame draws from
     * @return the left edge in pixels
     */
    static int panelLeft(RadialWheelRenderer.Frame frame) {
        return frame.centerX() + frame.radius() + WHEEL_GAP;
    }

    /**
     * How wide the panel's text wraps: as wide as the room right of the wheel
     * allows, inside its borders, between the narrowest and widest it runs.
     *
     * @param room the pixels from the panel's left edge to the screen's margin
     * @return the wrap width in pixels
     */
    static int textWidth(int room) {
        int inside = room - Math.round(NineSlice.BORDER) * BORDERS_ACROSS;
        return Math.clamp(inside, MIN_TEXT_WIDTH, MAX_TEXT_WIDTH);
    }

    /**
     * The panel's rows: the ability's name as the gold label, its description
     * wrapped below it, then its cost-mechanics text wrapped below that.
     *
     * @param ability   the hovered ability
     * @param lookup    the lang, item names and goo values the texts resolve through
     * @param textWidth the width the texts wrap to
     * @param widthOf   the width in pixels the font gives a string
     * @param standingIn the dimension the player stands in, whose name the ability shows
     *                  (decision lunar-reads-as-terran-while-you-are-on-the-moon)
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(ClientAbility ability, CostMechanicsLookup lookup, int textWidth,
                               ToIntFunction<String> widthOf, ResourceKey<Level> standingIn) {
        List<PanelRow> rows = new ArrayList<>();
        rows.add(PanelPainter.labelRow(lookup.translate(ability.shownIn(standingIn).displayName())));
        for (String line : WordWrap.lines(lookup.translate(ability.description()), textWidth, widthOf)) {
            rows.add(PanelRow.header(line, PanelPainter.TEXT_COLOR));
        }
        // zero-cost-field-formats-the-cost-mechanics: the text filled from the ability's own numbers
        ability.costMechanics(lookup).ifPresent(text -> {
            for (String line : WordWrap.lines(text, textWidth, widthOf)) {
                rows.add(PanelRow.header(line, COST_MECHANICS_COLOR));
            }
        });
        return rows;
    }

    /**
     * A row's segments laid out left to right, each segment's mixed number by
     * the spacing rule and the next segment past its respaced width.
     * every-mixed-number-takes-the-spacing-rule
     *
     * @param row     the row
     * @param widthOf the width in pixels the font gives a string
     * @return each segment's laid text and its left edge from the row's
     */
    static List<LaidSegment> laySegments(PanelRow row, ToIntFunction<String> widthOf) {
        List<LaidSegment> laid = new ArrayList<>(row.segments().size());
        int x = 0;
        for (PanelRow.TextSegment segment : row.segments()) {
            MixedNumberLabel label = MixedNumberLabel.lay(segment.text(), widthOf);
            laid.add(new LaidSegment(label, segment.color(), x));
            x += label.fullWidth();
        }
        return laid;
    }

    /**
     * One segment of a row as it draws.
     *
     * @param label its text laid out
     * @param color its ARGB color
     * @param x     its left edge from the row's
     */
    record LaidSegment(MixedNumberLabel label, int color, int x) {
    }

    /**
     * Where the panel's first row starts, inside its border.
     *
     * @param left the rows' left edge
     * @param top  the first row's top
     */
    private record RowsOrigin(int left, int top) {
    }

    private static void drawRows(GuiGraphicsExtractor graphics, Font font, List<PanelRow> rows, RowsOrigin origin,
                                 ToIntFunction<String> widthOf) {
        int textOffset = Math.round((PanelPainter.ROW_HEIGHT - font.lineHeight) / HALF) + 1;
        for (int index = 0; index < rows.size(); index++) {
            int rowTop = origin.top() + Math.round(index * PanelPainter.ROW_HEIGHT);
            for (LaidSegment segment : laySegments(rows.get(index), widthOf)) {
                segment.label().draw(graphics, font, origin.left() + segment.x(), rowTop + textOffset,
                        segment.color(), true);
            }
        }
    }
}
