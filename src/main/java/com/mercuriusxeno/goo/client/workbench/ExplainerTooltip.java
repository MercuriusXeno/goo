package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;
import java.util.List;

/**
 * An explainer paragraph as tooltip lines: wrapped to a readable pixel width, and drawn at a slightly smaller font
 * where at full size it fits on neither side of its element, so the whole paragraph stays readable (decisions
 * question-button-toggles-explainer-mode-with-the-drag-mask,
 * tooltips-anchor-below-their-element-and-grow-upward-when-short-of-room).
 */
final class ExplainerTooltip {
    /** The font scale a paragraph too tall for either side draws at. */
    static final float SHRUNK = 0.75f;
    private static final float FULL = 1f;
    // The widest a paragraph's line runs before it wraps.
    private static final int WRAP_WIDTH = 240;
    private static final int SIDE_MARGIN = 16;
    private static final int LINE_HEIGHT = ExpressionLines.TEXT_LINE_HEIGHT;
    // The tooltip opens a gap under its first line.
    private static final int FIRST_LINE_GAP = 2;
    private static final int WHITE = -1;

    private ExplainerTooltip() {
    }

    /**
     * @param fullHeight   the paragraph's height at full size
     * @param element      the element it explains
     * @param screenHeight the screen's height
     * @return full size where the paragraph fits on one side of the element, the shrunk scale where it fits neither
     */
    static float scaleFor(int fullHeight, GroupsPane.Bounds element, int screenHeight) {
        return fullHeight <= TooltipAnchor.roomFor(element, screenHeight) ? FULL : SHRUNK;
    }

    /**
     * @param font         the screen's font
     * @param paragraph    the styled paragraph
     * @param element      the element it explains
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return the paragraph's lines at the scale its room allows, each as wide at most as the wrap width
     */
    static List<ClientTooltipComponent> components(Font font, FormattedText paragraph, GroupsPane.Bounds element,
                                                   int screenWidth, int screenHeight) {
        int width = Math.min(WRAP_WIDTH, screenWidth - SIDE_MARGIN);
        List<FormattedCharSequence> full = font.split(paragraph, width);
        float scale = scaleFor(full.size() * LINE_HEIGHT + FIRST_LINE_GAP, element, screenHeight);
        List<FormattedCharSequence> lines = scale == FULL ? full : font.split(paragraph, (int) (width / scale));
        return lines.stream().<ClientTooltipComponent>map(line -> new ScaledLine(line, scale)).toList();
    }

    /**
     * One line of text drawn at a scale, its size scaled with it.
     *
     * @param text  the line
     * @param scale its font scale
     */
    private record ScaledLine(FormattedCharSequence text, float scale) implements ClientTooltipComponent {
        @Override
        public int getWidth(Font font) {
            return (int) Math.ceil(font.width(text) * scale);
        }

        @Override
        public int getHeight(Font font) {
            return (int) Math.ceil(LINE_HEIGHT * scale);
        }

        @Override
        public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
            Matrix3x2fStack pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(scale);
            graphics.text(font, text, 0, 0, WHITE, true);
            pose.popMatrix();
        }
    }
}
