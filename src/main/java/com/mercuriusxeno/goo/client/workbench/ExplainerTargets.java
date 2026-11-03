package com.mercuriusxeno.goo.client.workbench;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The elements explainer mode keeps lit and explains on hover: the five tabs now, so a pane's interior joins later by
 * adding a row (decision question-button-toggles-explainer-mode-with-the-drag-mask).
 */
final class ExplainerTargets {
    private ExplainerTargets() {
    }

    /**
     * One element explainer mode explains.
     *
     * @param key    what names it among the targets
     * @param bounds where it stands this frame
     * @param text   what hovering it explains
     */
    record Target(String key, Supplier<DragMask.Rect> bounds, String text) {
    }

    /**
     * @param tabBounds where each tab's button stands, read each frame
     * @return a target per tab, in the tab row's order, each carrying the tab's paragraph
     */
    static List<Target> forTabs(Function<ManagerTab, DragMask.Rect> tabBounds) {
        return Arrays.stream(ManagerTab.values()).map(tab -> new Target(tab.name(), () -> tabBounds.apply(tab),
                ExplainerTexts.forTab(tab))).toList();
    }
}
