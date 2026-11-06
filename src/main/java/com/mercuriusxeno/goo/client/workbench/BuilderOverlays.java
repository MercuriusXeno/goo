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
import java.util.function.Predicate;

/**
 * What the builders' controls open against themselves, each standing by one placement rule: one number box, one
 * searchable dropdown with its search box, an exclusive set's option list, and the goo type picker, at most one
 * standing at a time, so the Items, Prefabs and Mutators builders pick and
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
    private static final int OPTION_ROW = ButtonPainter.HEIGHT;
    // The options list's border, a pixel above its rows and a pixel below.
    private static final int OPTIONS_BORDER = 1;
    private static final int OPTIONS_BORDERS = OPTIONS_BORDER + OPTIONS_BORDER;
    private static final int SCROLL_STRIP = ScrollBar.GAP + ScrollBar.WIDTH;

    private final ManagerActions actions;
    private final ItemIcons icons;
    private final GooTypePicker picker = GooTypePicker.overKnownTypes();
    private @Nullable EditBox numberField;
    private @Nullable EditBox searchField;
    private @Nullable SearchDropdown dropdown;
    private @Nullable List<Option> options;
    // The options list scrolls a row per wheel notch where it holds more rows than its placement shows.
    private final ScrollBar optionsBar = new ScrollBar(1);
    private int dropdownChoices = SearchDropdown.SHOWN;
    // The screen as the last frame measured it, so a box opened between frames stands where the frame will draw it;
    // unbounded until the first frame, which draws before any control can be clicked.
    private int lastScreenWidth = Integer.MAX_VALUE;
    private int lastScreenHeight = Integer.MAX_VALUE;
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
    record Option(String label, @Nullable String hint, Consumer<Anchor> picked) {
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
        return place(at, GRID_WIDTH + 1, GRID_HEIGHT + 1, screenWidth, screenHeight);
    }

    /**
     * @param at           where the opening control was clicked
     * @param choices      how many choices the list holds, of which it shows at most SearchDropdown.SHOWN
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return where the search box and its dropdown stand
     */
    static Placement placeDropdown(Anchor at, int choices, int screenWidth, int screenHeight) {
        return place(at, SEARCH_WIDTH + 1, dropdownHeight(choices), screenWidth, screenHeight);
    }

    private static int dropdownHeight(int rows) {
        return DROPDOWN_CHROME + Math.clamp(rows, 1, SearchDropdown.SHOWN) * SearchDropdown.ROW_HEIGHT;
    }

    /**
     * @param at           where the opening dropdown was clicked
     * @param width        the widest option's width
     * @param rows         how many options the list holds
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return where the options list stands, its border included
     */
    static Placement placeOptions(Anchor at, int width, int rows, int screenWidth, int screenHeight) {
        return place(at, width, optionsHeight(rows), screenWidth, screenHeight);
    }

    private static int optionsHeight(int rows) {
        return rows * OPTION_ROW + OPTIONS_BORDERS;
    }

    /**
     * @param at           where the opening control was clicked
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return where the number box stands
     */
    static Placement placeNumber(Anchor at, int screenWidth, int screenHeight) {
        return place(at, NUMBER_WIDTH, BOX_HEIGHT, screenWidth, screenHeight);
    }

    /**
     * The one rule every popup stands by, as TooltipAnchor places a tooltip: below the opener's line where the whole
     * content fits there, above it, its bottom on the line's top, where only above fits it whole, and otherwise on
     * the side with more room at the height that side holds, the popup scrolling what it cannot show. A popup that
     * would run off the right edge shifts left.
     * Decision every-popup-measures-flows-to-fit-and-scrolls.
     * Decision popups-anchor-under-the-line-that-opened-them.
     *
     * @param at           where the opening control was clicked
     * @param width        the popup's width
     * @param content      the popup's whole content's height, measured
     * @param screenWidth  the screen's width
     * @param screenHeight the screen's height
     * @return where the popup stands
     */
    static Placement place(Anchor at, int width, int content, int screenWidth, int screenHeight) {
        int below = at.y() + LINE;
        int left = Math.max(0, Math.min(at.x(), screenWidth - width));
        int roomBelow = Math.max(0, screenHeight - below);
        int roomAbove = Math.max(0, at.y());
        if (content <= roomBelow) {
            return new Placement(left, below, content);
        }
        if (content <= roomAbove) {
            return new Placement(left, at.y() - content, content);
        }
        if (roomBelow >= roomAbove) {
            return new Placement(left, below, roomBelow);
        }
        return new Placement(left, at.y() - roomAbove, roomAbove);
    }

    // A popup drawing shorter than its placement keeps the edge facing its line: its top below the line, its bottom
    // above it.
    private static int drawnTop(Placement placed, Anchor at, int drawnHeight) {
        return placed.top() < at.y() ? placed.top() + placed.height() - drawnHeight : placed.top();
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
                () -> pressed.accept(new Anchor(x, y)), hintKey(hint));
        return box.right() + GAP;
    }

    /**
     * @param hint what a control does
     * @return the hover key whose tooltip shows it, null where the hint is null and no tooltip shows
     */
    static @Nullable String hintKey(@Nullable String hint) {
        return hint == null ? null : HINT_PREFIX + hint;
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
     * @return whether the number box stands open
     */
    boolean numberOpen() {
        return numberField != null && numberField.visible;
    }

    /**
     * Stands the open number box under another place, as a factor segment a bay drop drew after the box opened
     * (decision x-and-slash-drag-from-the-bay-onto-a-term).
     *
     * @param at where the segment the box edits now stands
     */
    void moveNumber(Anchor at) {
        numberOpener = at;
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
     * Closes every overlay and drops what the number box holds uncommitted.
     */
    void discard() {
        numberCommits = null;
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
        // Measured as it opens, so a search narrowing the choices leaves the box where it opened.
        dropdownChoices = list.choices().size();
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
        optionsBar.reset();
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
        openBox(at, current, commits, typed -> BuilderGrammar.acceptsTyped(typed, factor));
    }

    /**
     * Opens the number box for a term's number, which takes a whole, a fraction or a whole then a fraction as it is
     * typed (decision number-box-takes-a-mixed-number).
     *
     * @param at      where the control was clicked
     * @param current the mixed number the box starts from
     * @param commits takes the box's text on Enter or blur, a blank box committing nothing
     */
    void openMixedNumber(Anchor at, String current, Consumer<String> commits) {
        openBox(at, current, commits, BuilderGrammar::acceptsTypedMixed);
    }

    private void openBox(Anchor at, String current, Consumer<String> commits,
                         Predicate<String> accepts) {
        closeLists();
        numberCommits = typed -> {
            if (!typed.isBlank()) {
                commits.accept(typed);
            }
        };
        if (numberField != null) {
            numberField.setFilter(accepts);
            numberField.setValue(current);
            numberOpener = at;
            Placement placed = placeNumber(at, lastScreenWidth, lastScreenHeight);
            numberField.setPosition(placed.left(), placed.top());
            numberField.visible = visible;
            actions.focus(numberField);
            // Decision every-number-box-opens-with-its-text-selected.
            numberField.moveCursorToEnd(false);
            numberField.setHighlightPos(0);
            numberHeldFocus = false;
        }
    }

    /**
     * Scrolls the open dropdown's choices, the open options list, or the open goo type picker's grid, where it stands
     * shorter than its rows.
     *
     * @param amount the wheel's turn
     * @return whether a list or the grid stood open and took the turn
     */
    boolean scrollList(double amount) {
        if (dropdown != null) {
            dropdown.scroll(amount);
            return true;
        }
        if (options != null) {
            optionsBar.wheel(amount);
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
        lastScreenWidth = graphics.guiWidth();
        lastScreenHeight = graphics.guiHeight();
        drawDropdown(graphics, font, targets);
        drawOptions(graphics, font, targets);
        drawPicker(graphics, font, targets);
        if (numberField != null) {
            Placement box = placeNumber(numberOpener, lastScreenWidth, lastScreenHeight);
            numberField.setPosition(box.left(), box.top());
        }
        drawOnTop(graphics, searchField);
        drawOnTop(graphics, numberField);
    }

    private void drawPicker(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        Runnable nothing = pickerNothing;
        Placement grid = placePicker(opener, lastScreenWidth, lastScreenHeight);
        int gridRows = Math.max(1, (grid.height() - 1) / GooTypePicker.CELL);
        int gridTop = drawnTop(grid, opener, gridRows * GooTypePicker.CELL + 1);
        picker.draw(graphics, font, targets, grid.left(), gridTop, gridRows, type -> {
            Consumer<String> picks = pickerPicks;
            closeLists();
            if (picks != null) {
                picks.accept(type);
            }
        }, nothing == null ? null : () -> {
            closeLists();
            nothing.run();
        });
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
        targets.addShield(-EVERYWHERE, -EVERYWHERE, EVERYWHERE_SPAN, EVERYWHERE_SPAN, this::closeLists);
        Placement placed = placeDropdown(opener, dropdownChoices, lastScreenWidth, lastScreenHeight);
        int rows = Math.max(1, (placed.height() - DROPDOWN_CHROME) / SearchDropdown.ROW_HEIGHT);
        int top = drawnTop(placed, opener, DROPDOWN_CHROME + rows * SearchDropdown.ROW_HEIGHT);
        searchField.setPosition(placed.left(), top);
        open.draw(graphics, font, icons, targets, placed.left(), top + BOX_HEIGHT + 1, SEARCH_WIDTH, rows,
                this::pickChoice);
    }

    // Decision exclusive-choice-sets-draw-as-dropdowns: the set's options stand listed under its button, each
    // naming what it does on hover; a pick closes the list before the option runs, so a popup it opens stays open.
    // Decision every-popup-measures-flows-to-fit-and-scrolls: a list longer than its placement draws the rows it holds
    // beside a scroll bar.
    private void drawOptions(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        List<Option> open = options;
        if (open == null) {
            return;
        }
        targets.addShield(-EVERYWHERE, -EVERYWHERE, EVERYWHERE_SPAN, EVERYWHERE_SPAN, this::closeLists);
        int labels = open.stream().mapToInt(option -> ButtonPainter.width(font, option.label())).max().orElse(0);
        Placement placed = placeOptions(opener, labels, open.size(), lastScreenWidth, lastScreenHeight);
        int rows = Math.clamp((placed.height() - OPTIONS_BORDERS) / OPTION_ROW, 1, Math.max(1, open.size()));
        boolean scrolls = rows < open.size();
        int width = scrolls ? labels + SCROLL_STRIP : labels;
        if (scrolls) {
            placed = placeOptions(opener, width, open.size(), lastScreenWidth, lastScreenHeight);
        }
        optionsBar.measure(open.size(), rows);
        int left = placed.left();
        int top = drawnTop(placed, opener, optionsHeight(rows)) + OPTIONS_BORDER;
        graphics.nextStratum();
        Frame.around(graphics, Frame.POPUP, new GroupsPane.Bounds(left, top, left + width, top + rows * OPTION_ROW));
        drawOptionRows(graphics, font, targets, open.subList(optionsBar.offset(),
                Math.min(open.size(), optionsBar.offset() + rows)), new ButtonPainter.Box(left, top, labels, 0));
        if (scrolls) {
            optionsBar.draw(graphics, targets, left + width, top, top + rows * OPTION_ROW);
        }
    }

    // Each shown option's label and click, row under row from the rows' top-left, as wide as the widest label.
    private void drawOptionRows(GuiGraphicsExtractor graphics, Font font, ClickTargets targets, List<Option> shown,
                                ButtonPainter.Box rows) {
        for (int index = 0; index < shown.size(); index++) {
            Option option = shown.get(index);
            int rowTop = rows.top() + index * OPTION_ROW;
            graphics.text(font, option.label(), rows.left() + ButtonPainter.PADDING,
                    Lettering.full(font).centeredTop(rowTop, OPTION_ROW), Palette.TEXT);
            targets.add(rows.left(), rowTop, rows.width(), OPTION_ROW, () -> pickOption(option),
                    hintKey(option.hint()));
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
