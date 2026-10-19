package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.client.hud.NineSlice;
import com.mercuriusxeno.goo.client.hud.PanelPainter;
import com.mercuriusxeno.goo.client.hud.PanelRectangle;
import com.mercuriusxeno.goo.client.hud.PanelRow;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.CostMechanicsLookup;
import com.mercuriusxeno.goo.client.network.OfferedAbility;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
    private static final String WORD_SEPARATOR = " ";

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
        if (offered.locked()) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = frame.types().get(wheel.selectedType());
        int left = frame.centerX() + frame.radius() + WHEEL_GAP;
        int textWidth = textWidth(frame.centerX() * HALF - EDGE_MARGIN - left);
        ToIntFunction<String> widthOf = text -> font.width(Component.literal(text));
        List<PanelRow> rows = rows(offered.ability(), frame.look().costMechanicsLookup(type), textWidth, widthOf);
        PanelPainter.PanelSize size = PanelPainter.measure(rows, widthOf);
        int top = frame.centerY() - Math.round(size.height()) / HALF;
        NineSlice.blit(graphics, new PanelRectangle(left, top, Math.round(size.width()), Math.round(size.height())));
        drawRows(graphics, font, rows, left + Math.round(NineSlice.BORDER), top + Math.round(NineSlice.BORDER));
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
     * @return the rows top to bottom
     */
    static List<PanelRow> rows(ClientAbility ability, CostMechanicsLookup lookup, int textWidth,
                               ToIntFunction<String> widthOf) {
        List<PanelRow> rows = new ArrayList<>();
        rows.add(PanelPainter.labelRow(lookup.translate(ability.displayName())));
        for (String line : wrap(lookup.translate(ability.description()), textWidth, widthOf)) {
            rows.add(PanelRow.header(line, PanelPainter.TEXT_COLOR));
        }
        // zero-cost-field-formats-the-cost-mechanics: the text filled from the ability's own numbers
        ability.costMechanics(lookup).ifPresent(text -> {
            for (String line : wrap(text, textWidth, widthOf)) {
                rows.add(PanelRow.header(line, COST_MECHANICS_COLOR));
            }
        });
        return rows;
    }

    /**
     * Breaks a text into lines no wider than a width, word by word; a word
     * wider than the width stands on a line of its own.
     *
     * @param text    the text
     * @param width   the widest a line runs
     * @param widthOf the width in pixels the font gives a string
     * @return the lines, top first
     */
    static List<String> wrap(String text, int width, ToIntFunction<String> widthOf) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(WORD_SEPARATOR)) {
            String joined = line.isEmpty() ? word : line + WORD_SEPARATOR + word;
            if (!line.isEmpty() && widthOf.applyAsInt(joined) > width) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(joined);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static void drawRows(GuiGraphicsExtractor graphics, Font font, List<PanelRow> rows, int left, int top) {
        int textOffset = Math.round((PanelPainter.ROW_HEIGHT - font.lineHeight) / HALF) + 1;
        for (int index = 0; index < rows.size(); index++) {
            int rowTop = top + Math.round(index * PanelPainter.ROW_HEIGHT);
            int x = left;
            for (PanelRow.TextSegment segment : rows.get(index).segments()) {
                Component words = Component.literal(segment.text());
                graphics.text(font, words, x, rowTop + textOffset, segment.color(), true);
                x += font.width(words);
            }
        }
    }
}
