package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A searchable list of items, drawn with their icons, and prefabs: typed text filters it, one click opens it and a
 * click on a choice answers the choice, so a term names an item or prefab without its id typed
 * (decision expression-builder-composes-every-form-without-typing).
 */
final class SearchDropdown {
    /** A choice row's height, an item icon's and its margin. */
    static final int ROW_HEIGHT = 18;
    /** How many choices the list shows at once. */
    static final int SHOWN = 8;
    private static final String TAG_MARK = "#";
    private static final String COUNT_SEPARATOR = ", ";
    private static final int ICON_INSET = 1;
    private static final int LABEL_X = 20;

    private final List<Choice> all;
    // The list scrolls a row per wheel notch, so every match is reachable however few rows fit under the opener.
    private final ScrollBar bar = new ScrollBar(1);
    private int shownRows = SHOWN;
    // The search read: its text past any # or $ mark, and the kind that mark lists alone.
    private Search.Query query = Search.EVERYTHING;
    private boolean open;

    /**
     * What a choice names.
     */
    enum Kind {
        /** An item, by its namespaced id. */
        ITEM,
        /** A prefab, by its name. */
        PREFAB,
        /** An item tag, by its #id. */
        TAG,
        /** A mutator, by its name. */
        MUTATOR
    }

    /**
     * One choice of the list.
     *
     * @param kind  what it names
     * @param id    an item's namespaced id or a prefab's name
     * @param label what the list draws
     */
    record Choice(Kind kind, String id, String label) {
        /**
         * @return the item id its icon draws from, null for a prefab
         */
        @Nullable String iconId() {
            return kind == Kind.ITEM ? id : null;
        }
    }

    private SearchDropdown(List<Choice> all) {
        this.all = List.copyOf(all);
    }

    /**
     * @param inventory the server's items and tags, the tags left out
     * @param prefabs   the prefabs' names
     * @param items     whether the items list beside the prefabs
     * @return the dropdown, items first
     */
    static SearchDropdown over(List<ValuesTab.ManagerEntry> inventory, List<String> prefabs, boolean items) {
        List<Choice> choices = new ArrayList<>();
        if (items) {
            inventory.stream().filter(entry -> !entry.tag() && !entry.id().startsWith(TAG_MARK))
                    .forEach(entry -> choices.add(new Choice(Kind.ITEM, entry.id(), entry.name())));
        }
        // Decision hash-marks-groups-and-dollar-marks-prefabs-everywhere: a prefab reads after $.
        prefabs.forEach(name -> choices.add(new Choice(Kind.PREFAB, name, PrefabNames.marked(name))));
        return new SearchDropdown(choices);
    }

    /**
     * @param mutators the mutators' names
     * @return a dropdown over them, each read by its plain name
     */
    static SearchDropdown overMutators(List<String> mutators) {
        return new SearchDropdown(mutators.stream().map(name -> new Choice(Kind.MUTATOR, name, name)).toList());
    }

    /**
     * @param inventory the server's items and tags
     * @return a dropdown over every item, drawn with its icon, and every #tag, as a group's member
     */
    static SearchDropdown overMembers(List<ValuesTab.ManagerEntry> inventory) {
        return new SearchDropdown(inventory.stream().map(entry -> new Choice(entry.tag()
                || entry.id().startsWith(TAG_MARK) ? Kind.TAG : Kind.ITEM, entry.id(), entry.tag()
                ? entry.id() : entry.name())).toList());
    }

    /**
     * @param tags the item tags to list, each named #id with its member count
     * @return a dropdown over them, each choice its #id and member count
     */
    static SearchDropdown overTags(List<ValuesTab.ManagerEntry> tags) {
        return new SearchDropdown(tags.stream().map(entry -> new Choice(Kind.TAG, entry.id(),
                entry.id() + COUNT_SEPARATOR + entry.name())).toList());
    }

    /**
     * @param typed the search box's text
     */
    void search(String typed) {
        query = Search.query(typed);
        bar.reset();
    }

    /**
     * A query opening on # lists the tags alone and one opening on $ the prefabs alone, the rest of it filtering
     * them (decision hash-marks-groups-and-dollar-marks-prefabs-everywhere).
     *
     * @return every choice whose label or id holds the search text, ignoring case
     */
    List<Choice> choices() {
        Search.Query read = query;
        return all.stream().filter(choice -> holds(choice, read)).toList();
    }

    // Decision one-search-rule-serves-every-box-and-dropdown: a tag matches its #id, never its member count.
    private static boolean holds(Choice choice, Search.Query read) {
        return switch (choice.kind()) {
            case TAG -> Search.matches(read, Search.Kind.GROUP, choice.id());
            case PREFAB -> Search.matches(read, Search.Kind.PREFAB, choice.id(), choice.label());
            default -> Search.matches(read, Search.Kind.ITEM, choice.id(), choice.label());
        };
    }

    /**
     * @return whether the list stands open
     */
    boolean isOpen() {
        return open;
    }

    /**
     * Opens the list or grid, as its opener's click does.
     */
    void open() {
        open = true;
        bar.reset();
    }

    /**
     * @param amount the wheel's turn, positive away from the player, which moves toward the list's start
     */
    void scroll(double amount) {
        bar.measure(choices().size(), shownRows);
        bar.wheel(amount);
    }

    /**
     * Closes the list with no pick.
     */
    void close() {
        open = false;
    }

    /**
     * Leaves the area whose click opens the list.
     *
     * @param targets the frame's click areas
     * @param x       the area's left edge
     * @param y       the area's top edge
     * @param width   its width
     * @param height  its height
     */
    void addOpener(ClickTargets targets, int x, int y, int width, int height) {
        targets.add(x, y, width, height, () -> {
            open = true;
            query = Search.EVERYTHING;
        });
    }

    /**
     * Leaves each shown choice's click while the list stands open; a click closes it and answers the choice.
     *
     * @param targets the frame's click areas
     * @param x       the list's left edge
     * @param y       the list's top edge
     * @param width   its width
     * @param picked  takes the choice picked
     */
    void layTargets(ClickTargets targets, int x, int y, int width, Consumer<Choice> picked) {
        layTargets(targets, x, y, width, SHOWN, picked);
    }

    private void layTargets(ClickTargets targets, int x, int y, int width, int rows, Consumer<Choice> picked) {
        if (!open) {
            return;
        }
        List<Choice> shown = shown(rows);
        for (int index = 0; index < shown.size(); index++) {
            Choice choice = shown.get(index);
            targets.add(x, y + index * ROW_HEIGHT, width, ROW_HEIGHT, () -> {
                open = false;
                picked.accept(choice);
            });
        }
    }

    /**
     * Draws the open list over what stands under it and leaves its targets.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param icons    the items' icons
     * @param targets  the frame's click areas
     * @param x        the list's left edge
     * @param y        the list's top edge
     * @param width    its width
     * @param rows     how many choices fit under its opener, the screen's bottom clamping it
     * @param picked   takes the choice picked
     */
    void draw(GuiGraphicsExtractor graphics, Font font, ItemIcons icons, ClickTargets targets, int x, int y, int width,
              int rows, Consumer<Choice> picked) {
        if (!open) {
            return;
        }
        List<Choice> shown = shown(rows);
        int height = Math.max(1, shown.size()) * ROW_HEIGHT;
        graphics.nextStratum();
        Frame.around(graphics, Frame.POPUP, new GroupsPane.Bounds(x, y, x + width, y + height));
        boolean scrolls = choices().size() > rows;
        int labelWidth = width - LABEL_X - (scrolls ? ScrollBar.WIDTH + ScrollBar.GAP : 0);
        for (int index = 0; index < shown.size(); index++) {
            drawChoice(graphics, font, icons, shown.get(index), x, y + index * ROW_HEIGHT, labelWidth);
        }
        targets.addShield(x, y, width, height, () -> { });
        layTargets(targets, x, y, width, rows, picked);
        if (scrolls) {
            bar.draw(graphics, targets, x + width, y, y + height);
        }
    }

    private static void drawChoice(GuiGraphicsExtractor graphics, Font font, ItemIcons icons, Choice choice, int x,
                                   int rowY, int labelWidth) {
        String iconId = choice.iconId();
        if (iconId != null) {
            ItemLinks.sprite(graphics, icons.of(iconId), x + ICON_INSET, rowY + ICON_INSET);
        }
        graphics.text(font, font.plainSubstrByWidth(choice.label(), labelWidth), x + LABEL_X,
                Lettering.full(font).centeredTop(rowY, ROW_HEIGHT),
                choice.kind() == Kind.PREFAB ? Palette.LINK : Palette.TEXT);
    }

    // The rows the list stands scrolled to; the bar clamps its offset to the matches each time.
    private List<Choice> shown(int rows) {
        List<Choice> choices = choices();
        shownRows = rows;
        bar.measure(choices.size(), rows);
        int first = bar.offset();
        return choices.subList(first, Math.min(choices.size(), first + rows));
    }
}
