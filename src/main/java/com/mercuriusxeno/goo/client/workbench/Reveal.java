package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * The one reveal every pane follows a link through, and creates a row through: the search cleared at once, then,
 * before the first frame after it draws, the rows measured, the target centered in its list and its glow started.
 * Decisions one-reveal-primitive-centers-before-the-first-draw, create-reveals-the-new-row-like-a-follow,
 * shift-follow-centers-the-target-in-its-scrolling-view, followed-target-glows-briefly,
 * following-a-link-clears-the-panes-search-bar.
 */
final class Reveal {
    private @Nullable String pending;
    private @Nullable Laid found;
    private int glowColumn;

    // Where the target stood inside its list's content in the measuring pass.
    private record Laid(int top, int height, int column) {
    }

    /**
     * Clears the pane's search and holds the target until a frame's rows lay it.
     *
     * @param key         the row's key
     * @param clearSearch empties the pane's search box and its filter
     */
    void follow(String key, Runnable clearSearch) {
        clearSearch.run();
        pending = key;
        found = null;
    }

    /**
     * Records where a row stood, which centers it where it is the target.
     *
     * @param key    the row's key
     * @param top    its top inside its list's content, scroll offset excluded
     * @param height its height
     * @param column which of the pane's lists it stands in, 0 for a pane of one
     */
    void laid(String key, int top, int height, int column) {
        if (key.equals(pending)) {
            found = new Laid(top, height, column);
        }
    }

    /**
     * With a target held, runs the pane's rows once under a scissor holding no room, so nothing reaches the screen,
     * then centers the target in its list and starts its glow, so the visible pass draws it centered and glowing.
     * The target stays held while no row lays it, as when the row a create asked for has yet to arrive.
     *
     * @param graphics the frame's graphics
     * @param measure  lays the pane's rows, calling {@link #laid} for each, onto the click areas it is handed
     * @param barOf    each list's scroll bar by its column
     * @param glow     the pane's glow
     */
    void centerBeforeDraw(GuiGraphicsExtractor graphics, Consumer<ClickTargets> measure, IntFunction<ScrollBar> barOf,
                          RevealGlow glow) {
        if (pending == null) {
            return;
        }
        graphics.enableScissor(0, 0, 0, 0);
        measure.accept(new ClickTargets());
        graphics.disableScissor();
        Laid target = found;
        if (target == null) {
            return;
        }
        barOf.apply(target.column()).center(target.top(), target.height());
        glow.start(target.top(), target.height());
        glowColumn = target.column();
        pending = null;
        found = null;
    }

    /**
     * @param column one of the pane's lists
     * @return whether the glow stands in it
     */
    boolean glowsIn(int column) {
        return column == glowColumn;
    }
}
