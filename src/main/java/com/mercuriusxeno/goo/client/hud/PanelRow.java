package com.mercuriusxeno.goo.client.hud;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * One row of an in-world HUD panel: an optional icon followed by colored text
 * segments. A machine supplies rows as data and PanelPainter paints them
 * (decision one-panel-painter-takes-rows).
 *
 * @param icon       the icon texture, or null for a text-only header row
 * @param segments   the text segments drawn left to right after the icon
 * @param seeThrough whether the row draws over world geometry
 */
public record PanelRow(@Nullable Identifier icon, List<TextSegment> segments, boolean seeThrough) {

    /**
     * Builds a text-only header row in one color.
     *
     * @param text  the header text
     * @param color the ARGB text color
     * @return the header row
     */
    public static PanelRow header(String text, int color) {
        return new PanelRow(null, List.of(new TextSegment(text, color)), false);
    }

    /**
     * Builds an icon row whose text is one segment in one color.
     *
     * @param icon  the icon texture
     * @param text  the text after the icon
     * @param color the ARGB text color
     * @return the icon row
     */
    public static PanelRow iconText(Identifier icon, String text, int color) {
        return new PanelRow(icon, List.of(new TextSegment(text, color)), false);
    }

    /**
     * Measures the row's width: the icon and its gap when present, then every segment.
     *
     * @param textWidth the width in pixels the font gives a string
     * @return the row width in scaled pixels
     */
    public float width(ToIntFunction<String> textWidth) {
        float width = icon == null ? 0 : PanelPainter.ICON_SIZE + PanelPainter.ICON_TEXT_GAP;
        for (TextSegment segment : segments) {
            width += textWidth.applyAsInt(segment.text());
        }
        return width;
    }

    /**
     * A run of text in one color.
     *
     * @param text  the text
     * @param color the ARGB color
     */
    public record TextSegment(String text, int color) {
    }
}
