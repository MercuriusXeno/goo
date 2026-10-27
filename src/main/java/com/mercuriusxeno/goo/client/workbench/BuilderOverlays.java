package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What the builders' controls open below themselves: one number box, one searchable dropdown with its search box,
 * an exclusive set's option list, and the goo type picker, at most one standing at a time, so the Items, Prefabs and Mutators builders pick and
 * count with nothing typed but a number or a search (decisions expression-builder-composes-every-form-without-typing,
 * goo-type-picker-is-a-seven-by-four-icon-grid). They draw after the pane they open over, outside its clip, so they
 * stand above everything the pane drew, and a click outside the open list or grid closes it. A control names what it
 * does on hover through its hint.
 */
final class BuilderOverlays {
    /** A control line's height, an icon's. */
    static final int LINE = ExpressionLines.ICON_LINE_HEIGHT;
    /** The gap between controls. */
    static final int GAP = 6;
    private static final int BOX_HEIGHT = 14;
    private static final int NUMBER_WIDTH = 44;
    private static final int SEARCH_WIDTH = 160;
    private static final String SEARCH_HINT = "Search";
    private static final String NOTHING = "";
    // A hover key no prefab name or item id spells, since neither holds a space after a colon.
    private static final String HINT_PREFIX = "hint: ";
    // Wider than any GUI, so a click anywhere outside the open list lands on it.
    private static final int EVERYWHERE = 100_000;
    private static final int EVERYWHERE_SPAN = EVERYWHERE + EVERYWHERE;
    private static final int GRID_WIDTH = GooTypePicker.COLUMNS * GooTypePicker.CELL;
    private static final int GRID_HEIGHT = GooTypePicker.ROWS * GooTypePicker.CELL;
    // The search box, the pixel under it and the list's bottom border.
    private static final int DROPDOWN_CHROME = BOX_HEIGHT + 1 + 1;
    private static final int DROPDOWN_HEIGHT = DROPDOWN_CHROME + SearchDropdown.SHOWN * SearchDropdown.ROW_HEIGHT;
    private static final int OPTION_ROW = ButtonPainter.HEIGHT;
    private static final int OPTION_TEXT_NUDGE = 3;
    private static final int OPTION_BACKGROUND = 0xF0101010;
    private static final int OPTION_BORDER = 0xFF606060;
    private static final int WHITE = 0xFFFFFFFF;

    private final ManagerActions actions;
    private final ItemIcons icons;
    private final GooTypePicker picker = GooTypePicker.overKnownTypes();
    private @Nullable EditBox numberField;
    private @Nullable EditBox searchField;
    private @Nullable SearchDropdown dropdown;
    private @Nullable List<Option> options;
    private @Nullable Consumer<SearchDropdown.Choice> dropdownPicks;
    private @Nullable Consumer<String> pickerPicks;
    private @Nullable Runnable pickerNothing;
    private @Nullable Consumer<String> numberCommits;
    private boolean numberHeldFocus;
    private boolean visible;
    private Anchor opener = new Anchor(0, 0);
    private Anchor numberOpener = new Anchor(0, 0);

    /**
     * Where a control was clicked, so what it opens stands below it.
     *
     * @param x the control's left edge
     * @param y the control's line top
     */
    record Anchor(int x, int y) {
    }

    /**
     * One option of an exclusive set, listed under the set's dropdown.
     *
     * @param label  what the option reads
     * @param hint   what picking it does, shown on hover
     * @param picked takes the dropdown's anchor, so a further popup it opens stands under the same button
     */
    record Option(String label, String hint, Consumer<Anchor> picked) {
    }

    /**
     * Where a popup stands on the screen.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param height the height it draws in, clamped to the screen
     */
    record Placement(int left, int top, int height) {
    }

    /**
     * @param at           where the opening control was clicked
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return where the goo type picker's grid stands
     */
    static Placement placePicker(Anchor at, int screenWidth, int screenHeight) {
        return placeUnder(at, GRID_WIDTH + 1, GRID_HEIGHT + 1, screenWidth, screenHeight);
    }

    /**
     * @param at           where the opening control was clicked
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return where the search box and its dropdown stand
     */
    static Placement placeDropdown(Anchor at, int screenWidth, int screenHeight) {
        return placeUnder(at, SEARCH_WIDTH + 1, DROPDOWN_HEIGHT, screenWidth, screenHeight);
    }

    /**
     * @param at           where the opening control was clicked
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return where the number box stands
     */
    static Placement placeNumber(Anchor at, int screenWidth, int screenHeight) {
        return placeUnder(at, NUMBER_WIDTH, BOX_HEIGHT, screenWidth, screenHeight);
    }

    // Decision popups-anchor-under-the-line-that-opened-them: the popup never flips above its line; short of room it
    // draws shorter, and only a popup that would run off the right edge shifts left.
    private static Placement placeUnder(Anchor at, int width, int height, int screenWidth, int screenHeight) {
        int top = at.y() + LINE;
        int left = Math.max(0, Math.min(at.x(), screenWidth - width));
        return new Placement(left, top, Math.max(0, Math.min(height, screenHeight - top)));
    }

    /**
     * @param actions the panes' line back to the screen
     * @param icons   the items' icons
     */
    BuilderOverlays(ManagerActions actions, ItemIcons icons) {
        this.actions = actions;
        this.icons = icons;
    }

    /**
     * Stands the number and search boxes among the screen's widgets.
     *
     * @param font the screen's font
     */
    void init(Font font) {
        numberField = actions.addWidget(new EditBox(font, 0, 0, NUMBER_WIDTH, BOX_HEIGHT, Component.literal(NOTHING)));
        searchField = actions.addWidget(new EditBox(font, 0, 0, SEARCH_WIDTH, BOX_HEIGHT,
                Component.literal(SEARCH_HINT)));
        searchField.setHint(Component.literal(SEARCH_HINT));
        searchField.setResponder(typed -> {
            if (dropdown != null) {
                dropdown.search(typed);
            }
        });
        hideBoxes();
    }

    /**
     * @param shown whether the builder's tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        if (!shown) {
            hideBoxes();
        }
    }

    /**
     * Draws a control as a button and takes its click.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param label    the control's label
     * @param x        its left edge
     * @param y        its line's top
     * @param targets  the frame's click areas
     * @param pressed  takes where it was clicked
     * @return the x past the control
     */
    static int control(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, ClickTargets targets,
                       Consumer<Anchor> pressed) {
        return control(graphics, font, label, null, x, y, targets, pressed);
    }

    /**
     * Draws a control as a button that names what it does on hover, and takes its click.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param label    the control's label
     * @param hint     what the control does, shown on hover, null for nothing
     * @param x        its left edge
     * @param y        its line's top
     * @param targets  the frame's click areas
     * @param pressed  takes where it was clicked
     * @return the x past the control
     */
    static int control(GuiGraphicsExtractor graphics, Font font, String label, @Nullable String hint, int x, int y,
                       ClickTargets targets, Consumer<Anchor> pressed) {
        ButtonPainter.Box box = ButtonPainter.draw(graphics, font, label, x, y, LINE, targets,
                () -> pressed.accept(new Anchor(x, y)), hint == null ? null : hintKey(hint));
        return box.right() + GAP;
    }

    /**
     * @param hint what a control does
     * @return the hover key whose tooltip shows it
     */
    static String hintKey(String hint) {
        return HINT_PREFIX + hint;
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the hint where the area is a control's, empty otherwise
     */
    static Optional<Component> tooltipFor(String hoverKey) {
        return hoverKey.startsWith(HINT_PREFIX)
                ? Optional.of(Component.literal(hoverKey.substring(HINT_PREFIX.length()))) : Optional.empty();
    }

    /**
     * @return whether a list or the grid stands open
     */
    boolean listOpen() {
        return dropdown != null || options != null || picker.isOpen();
    }

    /**
     * @param key the GLFW key pressed
     * @return whether an overlay took the key: Enter commits the number box, Escape closes a list or the box
     */
    boolean keyPressed(int key) {
        if (numberField != null && numberField.visible) {
            return numberKey(numberField, key);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && listOpen()) {
            closeLists();
            return true;
        }
        return false;
    }

    private boolean numberKey(EditBox field, int key) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitNumber();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            numberCommits = null;
            field.visible = false;
            return true;
        }
        return false;
    }

    /**
     * Commits the number box once it lost the focus it held.
     */
    void commitOnBlur() {
        if (numberField == null || !numberField.visible) {
            return;
        }
        if (numberField.isFocused()) {
            numberHeldFocus = true;
        } else if (numberHeldFocus) {
            commitNumber();
        }
    }

    /**
     * Commits the number box and closes every overlay.
     */
    void close() {
        commitNumber();
        closeLists();
    }

    /**
     * @param at    where the control was clicked
     * @param picks takes the goo type picked
     */
    void openPicker(Anchor at, Consumer<String> picks) {
        openPicker(at, picks, null);
    }

    /**
     * @param at      where the control was clicked
     * @param picks   takes the goo type picked
     * @param nothing runs where no goo type is picked, null where none is allowed
     */
    void openPicker(Anchor at, Consumer<String> picks, @Nullable Runnable nothing) {
        openPicker(at, picks, nothing, Set.of());
    }

    /**
     * @param at      where the control was clicked
     * @param picks   takes the goo type picked
     * @param nothing runs where no goo type is picked, null where none is allowed
     * @param held    the goo types the value already holds, which the grid leaves out
     */
    void openPicker(Anchor at, Consumer<String> picks, @Nullable Runnable nothing, Set<String> held) {
        closeLists();
        opener = at;
        pickerPicks = picks;
        pickerNothing = nothing;
        picker.open(held);
    }

    /**
     * @param at    where the control was clicked
     * @param list  the dropdown to open
     * @param picks takes the choice picked
     */
    void openDropdown(Anchor at, SearchDropdown list, Consumer<SearchDropdown.Choice> picks) {
        closeLists();
        opener = at;
        dropdown = list;
        list.open();
        dropdownPicks = picks;
        if (searchField != null) {
            searchField.setValue(NOTHING);
            searchField.visible = visible;
            actions.focus(searchField);
        }
    }

    /**
     * Opens an exclusive set's options as a plain list under its dropdown, no search box.
     *
     * @param at  where the dropdown was clicked
     * @param set the set's options, in order
     */
    void openOptions(Anchor at, List<Option> set) {
        closeLists();
        opener = at;
        options = List.copyOf(set);
    }

    /**
     * @param at      where the control was clicked
     * @param current the number the box starts from
     * @param commits takes the box's text on Enter or blur
     */
    void openNumber(Anchor at, String current, Consumer<String> commits) {
        openNumber(at, current, commits, false);
    }

    /**
     * Opens the number box, which takes digits alone and, holding a factor, no 0, so a typed key it would refuse
     * never lands.
     *
     * @param at      where the control was clicked
     * @param current the number the box starts from
     * @param commits takes the box's text on Enter or blur, a blank box committing nothing
     * @param factor  whether the box holds a factor
     */
    void openNumber(Anchor at, String current, Consumer<String> commits, boolean factor) {
        closeLists();
        numberCommits = typed -> {
            if (!typed.isBlank()) {
                commits.accept(typed);
            }
        };
        if (numberField != null) {
            numberField.setFilter(typed -> BuilderGrammar.acceptsTyped(typed, factor));
            numberField.setValue(current);
            numberOpener = at;
            Placement placed = placeNumber(at, Integer.MAX_VALUE, Integer.MAX_VALUE);
            numberField.setPosition(placed.left(), placed.top());
            numberField.visible = visible;
            actions.focus(numberField);
            numberHeldFocus = false;
        }
    }

    /**
     * Scrolls the open dropdown's choices, or the open goo type picker's grid, where it stands shorter than its rows.
     *
     * @param amount the wheel's turn
     * @return whether a list or the grid stood open and took the turn
     */
    boolean scrollList(double amount) {
        if (dropdown != null) {
            dropdown.scroll(amount);
            return true;
        }
        if (!picker.isOpen()) {
            return false;
        }
        picker.scroll(amount);
        return true;
    }

    /**
     * Draws the standing list or grid over the pane and leaves its targets.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param targets  the frame's click areas
     */
    void draw(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        drawDropdown(graphics, font, targets);
        drawOptions(graphics, font, targets);
        Runnable nothing = pickerNothing;
        Placement grid = placePicker(opener, graphics.guiWidth(), graphics.guiHeight());
        int gridRows = Math.max(1, (grid.height() - 1) / GooTypePicker.CELL);
        picker.draw(graphics, font, targets, grid.left(), grid.top(), gridRows, type -> {
            Consumer<String> picks = pickerPicks;
            closeLists();
            if (picks != null) {
                picks.accept(type);
            }
        }, nothing == null ? null : () -> {
            closeLists();
            nothing.run();
        });
        if (numberField != null) {
            Placement box = placeNumber(numberOpener, graphics.guiWidth(), graphics.guiHeight());
            numberField.setPosition(box.left(), box.top());
        }
        drawOnTop(graphics, searchField);
        drawOnTop(graphics, numberField);
    }

    // The screen draws its boxes before the panes, so a box standing open draws again above what the pane drew.
    private static void drawOnTop(GuiGraphicsExtractor graphics, @Nullable EditBox box) {
        if (box != null && box.visible) {
            graphics.nextStratum();
            box.extractRenderState(graphics, 0, 0, 0.0f);
        }
    }

    // The dropdown stands below its search box; a pick closes both and hands the choice on.
    private void drawDropdown(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        SearchDropdown open = dropdown;
        if (open == null) {
            return;
        }
        if (!open.isOpen() || searchField == null) {
            closeDropdown();
            return;
        }
        targets.add(-EVERYWHERE, -EVERYWHERE, EVERYWHERE_SPAN, EVERYWHERE_SPAN, this::closeLists);
        Placement placed = placeDropdown(opener, graphics.guiWidth(), graphics.guiHeight());
        searchField.setPosition(placed.left(), placed.top());
        int rows = Math.max(1, (placed.height() - DROPDOWN_CHROME) / SearchDropdown.ROW_HEIGHT);
        open.draw(graphics, font, icons, targets, placed.left(), placed.top() + BOX_HEIGHT + 1, SEARCH_WIDTH, rows,
                this::pickChoice);
    }

    // Decision exclusive-choice-sets-draw-as-dropdowns: the set's options stand listed under its button, each
    // naming what it does on hover; a pick closes the list before the option runs, so a popup it opens stays open.
    private void drawOptions(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        List<Option> open = options;
        if (open == null) {
            return;
        }
        targets.add(-EVERYWHERE, -EVERYWHERE, EVERYWHERE_SPAN, EVERYWHERE_SPAN, this::closeLists);
        int width = open.stream().mapToInt(option -> ButtonPainter.width(font, option.label())).max().orElse(0);
        Placement placed = placeUnder(opener, width, open.size() * OPTION_ROW, graphics.guiWidth(),
                graphics.guiHeight());
        int left = placed.left();
        int top = placed.top();
        graphics.nextStratum();
        graphics.fill(left - 1, top - 1, left + width + 1, top + open.size() * OPTION_ROW + 1, OPTION_BORDER);
        graphics.fill(left, top, left + width, top + open.size() * OPTION_ROW, OPTION_BACKGROUND);
        for (int index = 0; index < open.size(); index++) {
            Option option = open.get(index);
            int rowTop = top + index * OPTION_ROW;
            graphics.text(font, option.label(), left + ButtonPainter.PADDING, rowTop + OPTION_TEXT_NUDGE, WHITE);
            targets.add(left, rowTop, width, OPTION_ROW, () -> pickOption(option), hintKey(option.hint()));
        }
    }

    private void pickOption(Option option) {
        Anchor at = opener;
        closeLists();
        option.picked().accept(at);
    }

    private void pickChoice(SearchDropdown.Choice choice) {
        Consumer<SearchDropdown.Choice> picks = dropdownPicks;
        closeLists();
        if (picks != null) {
            picks.accept(choice);
        }
    }

    private void commitNumber() {
        Consumer<String> commits = numberCommits;
        numberCommits = null;
        numberHeldFocus = false;
        if (numberField != null) {
            numberField.visible = false;
            if (commits != null) {
                commits.accept(numberField.getValue());
            }
        }
    }

    private void closeLists() {
        closeDropdown();
        options = null;
        picker.close();
        pickerPicks = null;
        pickerNothing = null;
    }

    private void closeDropdown() {
        dropdown = null;
        dropdownPicks = null;
        if (searchField != null) {
            searchField.visible = false;
        }
    }

    private void hideBoxes() {
        if (numberField != null) {
            numberField.visible = false;
        }
        if (searchField != null) {
            searchField.visible = false;
        }
    }
}
