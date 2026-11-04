package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
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
    private static final String CODE_MARK = "`";
    // A negative split limit keeps a span a backtick ends, so the spans alternate plain and styled.
    private static final int KEEP_EMPTY_SPANS = -1;
    /** The style a span the operator set between backticks draws in. */
    static final Style CODE_STYLE = Style.EMPTY.withColor(ChatFormatting.GOLD);

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
        clicks.addShield(0, 0, width, height, this::leave);
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
    Optional<Component> tooltipFor(String hoverKey) {
        return targets.stream().filter(target -> hoverKey.equals(HOVER_PREFIX + target.key())).findFirst()
                .map(target -> styled(target.text()));
    }

    // The operator's backticks mark styling: each span between a pair draws in the code style, the backticks unshown.
    private static Component styled(String text) {
        MutableComponent shown = Component.empty();
        boolean code = false;
        for (String span : text.split(CODE_MARK, KEEP_EMPTY_SPANS)) {
            if (!span.isEmpty()) {
                shown.append(Component.literal(span).setStyle(code ? CODE_STYLE : Style.EMPTY));
            }
            code = !code;
        }
        return shown;
    }
}
