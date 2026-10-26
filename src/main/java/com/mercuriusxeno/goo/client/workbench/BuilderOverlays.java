package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What the builders' controls open below themselves: one number box, one searchable dropdown with its search box,
 * and the goo type picker, at most one standing at a time, so the Items, Prefabs and Mutators builders pick and
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
    private static final int TEXT_NUDGE = 5;
    private static final int BOX_HEIGHT = 14;
    private static final int NUMBER_WIDTH = 44;
    private static final int SEARCH_WIDTH = 160;
    private static final int LINK = 0xFF80C0FF;
    private static final String SEARCH_HINT = "Search";
    private static final String NOTHING = "";
    // A hover key no prefab name or item id spells, since neither holds a space after a colon.
    private static final String HINT_PREFIX = "hint: ";
    // Wider than any GUI, so a click anywhere outside the open list lands on it.
    private static final int EVERYWHERE = 100_000;
    private static final int EVERYWHERE_SPAN = EVERYWHERE + EVERYWHERE;
    private static final int GRID_WIDTH = GooTypePicker.COLUMNS * GooTypePicker.CELL;
    private static final int GRID_HEIGHT = GooTypePicker.ROWS * GooTypePicker.CELL;

    private final ManagerActions actions;
    private final ItemIcons icons;
    private final GooTypePicker picker = GooTypePicker.overKnownTypes();
    private @Nullable EditBox numberField;
    private @Nullable EditBox searchField;
    private @Nullable SearchDropdown dropdown;
    private @Nullable Consumer<SearchDropdown.Choice> dropdownPicks;
    private @Nullable Consumer<String> pickerPicks;
    private @Nullable Runnable pickerNothing;
    private @Nullable Consumer<String> numberCommits;
    private boolean numberHeldFocus;
    private boolean visible;
    private int anchorX;
    private int anchorY;

    /**
     * Where a control was clicked, so what it opens stands below it.
     *
     * @param x the control's left edge
     * @param y the control's line top
     */
    record Anchor(int x, int y) {
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
     * Draws a control as a link and takes its click.
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
     * Draws a control as a link that names what it does on hover, and takes its click.
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
        graphics.text(font, label, x, y + TEXT_NUDGE, LINK);
        int width = font.width(label);
        targets.add(x, y, width, LINE, () -> pressed.accept(new Anchor(x, y)), hint == null ? null : hintKey(hint));
        return x + width + GAP;
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
        return dropdown != null || picker.isOpen();
    }

    /**
     * @param key the GLFW key pressed
     * @return whether an overlay took the key: Enter commits the number box, Escape closes a list or the box
     */
    boolean keyPressed(int key) {
        if (numberField != null && numberField.visible) {
            return numberKey(numberField, key);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (dropdown != null || picker.isOpen())) {
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
        closeLists();
        anchor(at);
        pickerPicks = picks;
        pickerNothing = nothing;
        picker.open();
    }

    /**
     * @param at    where the control was clicked
     * @param list  the dropdown to open
     * @param picks takes the choice picked
     */
    void openDropdown(Anchor at, SearchDropdown list, Consumer<SearchDropdown.Choice> picks) {
        closeLists();
        anchor(at);
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
     * @param at      where the control was clicked
     * @param current the number the box starts from
     * @param commits takes the box's text on Enter or blur
     */
    void openNumber(Anchor at, String current, Consumer<String> commits) {
        closeLists();
        numberCommits = commits;
        if (numberField != null) {
            numberField.setValue(current);
            numberField.setPosition(at.x(), at.y() + LINE - BOX_HEIGHT);
            numberField.visible = visible;
            actions.focus(numberField);
            numberHeldFocus = false;
        }
    }

    private void anchor(Anchor at) {
        anchorX = at.x();
        anchorY = at.y() + LINE;
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
        Runnable nothing = pickerNothing;
        int gridX = Math.max(0, Math.min(anchorX, graphics.guiWidth() - GRID_WIDTH - 1));
        int gridY = anchorY + GRID_HEIGHT < graphics.guiHeight() ? anchorY : anchorY - LINE - GRID_HEIGHT - 1;
        picker.draw(graphics, font, targets, gridX, gridY, type -> {
            Consumer<String> picks = pickerPicks;
            closeLists();
            if (picks != null) {
                picks.accept(type);
            }
        }, nothing == null ? null : () -> {
            closeLists();
            nothing.run();
        });
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
        searchField.setPosition(anchorX, anchorY);
        open.draw(graphics, font, icons, targets, anchorX, anchorY + BOX_HEIGHT + 1, SEARCH_WIDTH, choice -> {
            Consumer<SearchDropdown.Choice> picks = dropdownPicks;
            closeLists();
            if (picks != null) {
                picks.accept(choice);
            }
        });
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
