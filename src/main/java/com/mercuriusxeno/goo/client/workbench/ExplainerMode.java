package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The mode the [?] on the title row toggles: the drag mask's shadow over the screen with each explainer target cut
 * out and lit, a shield taking every click beneath, and each target hovering its text, which the screen's anchored
 * tooltip opens against the target.
 * Decision question-button-toggles-explainer-mode-with-the-drag-mask.
 */
final class ExplainerMode {
    private static final String HOVER_PREFIX = "explainer:";
    // The characters a tooltip line holds before the text wraps, a readable width for a paragraph.
    private static final int LINE_CHARACTERS = 50;
    private static final String WORD_BREAK = " ";

    private final List<ExplainerTargets.Target> targets;
    private boolean active;

    /**
     * @param targets the elements the mode keeps lit and explains
     */
    ExplainerMode(List<ExplainerTargets.Target> targets) {
        this.targets = List.copyOf(targets);
    }

    /**
     * Enters the mode, or leaves it where it stands.
     */
    void toggle() {
        active = !active;
    }

    /**
     * Leaves the mode, as Escape does.
     *
     * @return whether the mode stood
     */
    boolean leave() {
        boolean was = active;
        active = false;
        return was;
    }

    /**
     * @return whether the mode stands
     */
    boolean active() {
        return active;
    }

    /**
     * Draws the mask and lays the shield and each target's hover, last in the frame so they stand over the panes.
     *
     * @param graphics the frame's graphics
     * @param width    the screen's width
     * @param height   the screen's height
     * @param clicks   the frame's click areas
     */
    void extract(GuiGraphicsExtractor graphics, int width, int height, ClickTargets clicks) {
        if (!active) {
            return;
        }
        List<DragMask.Rect> holes = targets.stream().map(target -> target.bounds().get()).toList();
        DragMask.draw(graphics, new DragMask.Rect(0, 0, width, height), holes);
        clicks.addShield(0, 0, width, height, () -> { });
        for (int index = 0; index < holes.size(); index++) {
            DragMask.Rect hole = holes.get(index);
            clicks.addHover(hole.left(), hole.top(), hole.right() - hole.left(), hole.bottom() - hole.top(),
                    HOVER_PREFIX + targets.get(index).key());
        }
    }

    /**
     * @param hoverKey what a hovered area names
     * @return the target's text wrapped to a readable width, empty where the key names no target
     */
    Optional<List<TooltipLine>> tooltipFor(String hoverKey) {
        return targets.stream().filter(target -> hoverKey.equals(HOVER_PREFIX + target.key())).findFirst()
                .map(target -> wrapped(target.text()).stream()
                        .<TooltipLine>map(line -> new TooltipLine.Text(Component.literal(line))).toList());
    }

    // Breaks the text at the last space before each line fills.
    static List<String> wrapped(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(WORD_BREAK)) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > LINE_CHARACTERS) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }
}
