package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The links an Audit finding's line lays over the things it names: an item by its namespaced id, a prefab after $ and
 * a group after #, each hovering and following on shift + left click as it does on every other tab, underlined while
 * shift is held as every link is (decisions audit-findings-link-and-hover-like-every-other-tab,
 * holding-shift-underlines-every-link).
 */
final class FindingLinks {
    private static final String WORD_BREAK = " ";
    // A split limit keeping every word, the empty ones between doubled spaces included, so offsets stay true.
    private static final int EVERY_WORD = -1;
    private static final String PREFAB_MARK = "$";
    private static final String GROUP_MARK = "#";
    // A namespaced id: a namespace, a colon and a path, each opening on a letter, as minecraft:iron_ingot reads.
    private static final Pattern ITEM_ID = Pattern.compile("[a-z][a-z0-9_.-]*:[a-z][a-z0-9_./-]*");
    // The marks a sentence wraps a name in, which the name's link leaves out.
    private static final String WRAPPING = "().,;:'\"";

    private FindingLinks() {
    }

    /**
     * What one word names.
     */
    enum Kind {
        /** An item, by its namespaced id. */
        ITEM,
        /** A prefab, by its name after $. */
        PREFAB,
        /** A group, by its name after #. */
        GROUP
    }

    /**
     * A name a word carries.
     *
     * @param kind  what it names
     * @param id    the item's id, the prefab's or the group's name
     * @param start where the name starts in its word
     * @param shown the name as the line draws it, its mark included
     */
    record Link(Kind kind, String id, int start, String shown) {
    }

    /**
     * @param word one word of a finding, the punctuation around it included
     * @return the item, prefab or group it names, empty for a plain word
     */
    static Optional<Link> linkOf(String word) {
        int start = coreStart(word);
        String core = word.substring(start, coreEnd(word, start));
        if (marks(core, PREFAB_MARK)) {
            return Optional.of(new Link(Kind.PREFAB, core.substring(PREFAB_MARK.length()), start, core));
        }
        if (marks(core, GROUP_MARK)) {
            return Optional.of(new Link(Kind.GROUP, core.substring(GROUP_MARK.length()), start, core));
        }
        return ITEM_ID.matcher(core).matches() ? Optional.of(new Link(Kind.ITEM, core, start, core))
                : Optional.empty();
    }

    // Where a word's name starts, past the marks the sentence opens it with.
    private static int coreStart(String word) {
        int start = 0;
        while (start < word.length() && WRAPPING.indexOf(word.charAt(start)) >= 0) {
            start++;
        }
        return start;
    }

    // Where a word's name ends, short of the marks the sentence closes it with.
    private static int coreEnd(String word, int start) {
        int end = word.length();
        while (end > start && WRAPPING.indexOf(word.charAt(end - 1)) >= 0) {
            end--;
        }
        return end;
    }

    private static boolean marks(String core, String mark) {
        return core.length() > mark.length() && core.startsWith(mark);
    }

    /**
     * Lays the links over one drawn line of a finding.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param line     the line as drawn
     * @param x        its left edge
     * @param y        its text's top
     * @param targets  the frame's click areas
     * @param actions  the panes' line back to the screen, which the follows open
     */
    static void lay(GuiGraphicsExtractor graphics, Font font, String line, int x, int y, ClickTargets targets,
                    ManagerActions actions) {
        int at = 0;
        for (String word : line.split(WORD_BREAK, EVERY_WORD)) {
            int wordAt = at;
            linkOf(word).ifPresent(link -> {
                int left = x + font.width(line.substring(0, wordAt + link.start()));
                int width = font.width(link.shown());
                follow(targets, actions, link, left, y, width, ExpressionLines.TEXT_LINE_HEIGHT);
                ButtonPainter.underlineWhileShiftHeld(graphics, targets, left, y, width, Palette.LINK);
            });
            at += word.length() + WORD_BREAK.length();
        }
    }

    private static void follow(ClickTargets targets, ManagerActions actions, Link link, int x, int y, int width,
                               int height) {
        String id = link.id();
        switch (link.kind()) {
            case ITEM -> ItemLinks.add(targets, actions, id, x, y, width, height);
            case PREFAB -> targets.addFollow(x, y, width, height, () -> actions.openVariable(id),
                    PrefabHovers.hoverKey(id));
            case GROUP -> targets.addFollow(x, y, width, height, () -> actions.openGroup(id),
                    GroupHovers.hoverKey(id));
        }
    }
}
