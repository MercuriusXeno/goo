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
 * @param floorText  the text whose width the row's text never measures under, or null for no floor
 * @param secondIcon the icon texture drawn after the first, or null for one icon
 */
public record PanelRow(@Nullable Identifier icon, List<TextSegment> segments, boolean seeThrough,
                       @Nullable String floorText, @Nullable Identifier secondIcon) {

    /**
     * Builds a row with at most one icon.
     *
     * @param icon       the icon texture, or null for a text-only header row
     * @param segments   the text segments drawn left to right after the icon
     * @param seeThrough whether the row draws over world geometry
     * @param floorText  the text whose width the row's text never measures under, or null for no floor
     */
    public PanelRow(@Nullable Identifier icon, List<TextSegment> segments, boolean seeThrough,
                    @Nullable String floorText) {
        this(icon, segments, seeThrough, floorText, null);
    }

    /**
     * Builds a row with at most one icon and no width floor.
     *
     * @param icon       the icon texture, or null for a text-only header row
     * @param segments   the text segments drawn left to right after the icon
     * @param seeThrough whether the row draws over world geometry
     */
    public PanelRow(@Nullable Identifier icon, List<TextSegment> segments, boolean seeThrough) {
        this(icon, segments, seeThrough, null, null);
    }

    /**
     * Builds a row led by two icons whose text is one segment in one color,
     * as the crucible's combo row (decision combo-row-above-remainder).
     *
     * @param icon       the first icon texture
     * @param secondIcon the icon texture after the first
     * @param text       the text after the icons
     * @param color      the ARGB text color
     * @return the icon pair row
     */
    public static PanelRow iconPairText(Identifier icon, Identifier secondIcon, String text, int color) {
        return new PanelRow(icon, List.of(new TextSegment(text, color)), false, null, secondIcon);
    }

    /**
     * Returns the width the row's icons and their gaps take before the text.
     *
     * @return the icons' width in scaled pixels, 0 for a text-only row
     */
    public float iconsWidth() {
        float oneIcon = PanelPainter.ICON_SIZE + PanelPainter.ICON_TEXT_GAP;
        if (icon == null) {
            return 0;
        }
        return secondIcon == null ? oneIcon : oneIcon + oneIcon;
    }

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
     * Measures the row's width: the icon and its gap when present, then the
     * wider of every segment together and the floor text.
     *
     * @param textWidth the width in pixels the font gives a string
     * @return the row width in scaled pixels
     */
    public float width(ToIntFunction<String> textWidth) {
        float iconWidth = iconsWidth();
        float segmentsWidth = 0;
        for (TextSegment segment : segments) {
            segmentsWidth += textWidth.applyAsInt(segment.text());
        }
        float floorWidth = floorText == null ? 0 : textWidth.applyAsInt(floorText);
        return iconWidth + Math.max(segmentsWidth, floorWidth);
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
