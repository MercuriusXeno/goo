package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Draws the expression builder over a value's curated view: [add type] opens the goo type picker and adds a row whose
 * amount is a [#] number, a [var] scalar prefab or a [relative] item or prefab member, [relate to item] adds a
 * whole-value term from the searchable dropdown, [+] and [-] join terms, [( )] wraps the selected ones, each with a
 * multiply or divide toggle and a factor; every change writes the string the file grammar reads, as the edit box did
 * (decisions expression-builder-composes-every-form-without-typing, goo-type-picker-is-a-seven-by-four-icon-grid).
 * The state lives in {@link ExpressionBuilder}.
 */
final class ExpressionBuilderPane {
    private static final int LINE = ExpressionLines.ICON_LINE_HEIGHT;
    private static final int TEXT_NUDGE = 5;
    private static final int ICON_NUDGE = 1;
    private static final int GAP = 6;
    private static final int BOX_HEIGHT = 14;
    private static final int NUMBER_WIDTH = 44;
    private static final int SEARCH_WIDTH = 160;
    private static final int OPERAND_WIDTH = 150;
    private static final int TEXT_LINE = 10;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;
    private static final int LINK = 0xFF80C0FF;
    private static final int PANEL_COLOR = 0x30FFFFFF;
    private static final String ADD_TYPE = "[add type]";
    private static final String RELATE = "[relate to item]";
    private static final String WRAP = "[( )]";
    private static final String DONE = "[done]";
    private static final String NUMBER_MODE = "[#]";
    private static final String VAR_MODE = "[var]";
    private static final String RELATIVE_MODE = "[relative]";
    private static final String MULTIPLY = "[x]";
    private static final String DIVIDE = "[/]";
    private static final String PLUS = "[+]";
    private static final String MINUS = "[-]";
    private static final String SELECTED = "[o]";
    private static final String UNSELECTED = "[ ]";
    private static final String MEMBER = "[type]";
    private static final String WHOLE = "[whole]";
    private static final String REMOVE = "[remove]";
    private static final String SEARCH_HINT = "Search";
    private static final String NOTHING = "";

    private final ManagerActions actions;
    private final ItemIcons icons;
    private final Supplier<List<ValuesTab.ManagerEntry>> inventory;
    private final Supplier<List<String>> prefabs;
    private final Supplier<List<String>> scalarPrefabs;
    private final GooTypePicker picker = GooTypePicker.overKnownTypes();
    private final SortedSet<Integer> selected = new TreeSet<>();
    private @Nullable ExpressionBuilder builder;
    private @Nullable Consumer<String> writes;
    private @Nullable Runnable done;
    private @Nullable EditBox numberField;
    private @Nullable EditBox searchField;
    private @Nullable SearchDropdown dropdown;
    private @Nullable Consumer<SearchDropdown.Choice> dropdownPicks;
    private @Nullable Consumer<String> pickerPicks;
    private @Nullable Consumer<String> numberCommits;
    private boolean numberHeldFocus;
    private boolean visible;
    private int anchorX;
    private int anchorY;

    /**
     * @param actions       the panes' line back to the screen
     * @param icons         the items' icons
     * @param inventory     the server's items, which [relate to item] and [relative] list
     * @param prefabs       every prefab's name
     * @param scalarPrefabs the scalar prefabs' names, which [var] lists
     */
    ExpressionBuilderPane(ManagerActions actions, ItemIcons icons, Supplier<List<ValuesTab.ManagerEntry>> inventory,
                          Supplier<List<String>> prefabs, Supplier<List<String>> scalarPrefabs) {
        this.actions = actions;
        this.icons = icons;
        this.inventory = inventory;
        this.prefabs = prefabs;
        this.scalarPrefabs = scalarPrefabs;
    }

    /**
     * Stands the builder's number and search boxes among the screen's widgets.
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
     * Opens the builder over a value.
     *
     * @param shown  the value as the draft's edit box shows it
     * @param change takes the string each change writes
     * @param finish runs when [done] closes the builder
     */
    void open(String shown, Consumer<String> change, Runnable finish) {
        builder = ExpressionBuilder.of(shown);
        writes = change;
        done = finish;
        selected.clear();
        closeOverlays();
    }

    /**
     * Closes the builder, keeping what it wrote.
     */
    void close() {
        commitNumber();
        builder = null;
        closeOverlays();
    }

    /**
     * @return whether the builder stands open
     */
    boolean isOpen() {
        return builder != null;
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
     * @param key the GLFW key pressed
     * @return whether the builder took the key: Enter commits the number box, Escape closes a list or the box
     */
    boolean keyPressed(int key) {
        if (numberField != null && numberField.visible) {
            return numberKey(numberField, key);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && (dropdown != null || picker.isOpen())) {
            closeOverlays();
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
     * Draws the builder at a top edge across an area's width.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param area     the area the builder spans
     * @param top      the builder's top edge
     * @param targets  the frame's click areas
     * @return the height the builder took
     */
    int extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds area, int top, ClickTargets targets) {
        ExpressionBuilder edited = builder;
        if (edited == null) {
            return 0;
        }
        commitOnBlur();
        int y = top;
        y = drawControls(graphics, font, area.left(), y, targets);
        if (edited.refusal().isPresent()) {
            graphics.text(font, font.plainSubstrByWidth(edited.refusal().get(), area.right() - area.left()),
                    area.left(), y, RED);
            y += TEXT_LINE;
        }
        y = drawTerms(graphics, font, edited, area.left(), y, targets);
        graphics.fill(area.left() - 1, top - 1, area.right(), y, PANEL_COLOR);
        drawOverlays(graphics, font, targets);
        return y - top;
    }

    // Answers the y below the rows and terms, a line each.
    private int drawTerms(GuiGraphicsExtractor graphics, Font font, ExpressionBuilder edited, int left, int top,
                          ClickTargets targets) {
        int y = top;
        List<ExpressionBuilder.Row> rows = edited.rows();
        for (int index = 0; index < rows.size(); index++) {
            drawRow(graphics, font, index, rows.get(index), left, y, targets);
            y += LINE;
        }
        List<ExpressionBuilder.Piece> pieces = edited.terms();
        for (int index = 0; index < pieces.size(); index++) {
            drawPiece(graphics, font, index, pieces.get(index), left, y, targets);
            y += LINE;
        }
        return y;
    }

    private int drawControls(GuiGraphicsExtractor graphics, Font font, int left, int y, ClickTargets targets) {
        int x = left;
        x = control(graphics, font, ADD_TYPE, x, y, targets, at -> openPicker(at, type -> apply(b -> b.addType(type))));
        x = control(graphics, font, RELATE, x, y, targets,
                at -> openDropdown(at, true, prefabs.get(), choice -> apply(b -> b.relate(choice))));
        x = control(graphics, font, WRAP, x, y, targets, at -> wrapSelected());
        control(graphics, font, DONE, x, y, targets, at -> finish());
        return y + LINE;
    }

    private void drawRow(GuiGraphicsExtractor graphics, Font font, int row, ExpressionBuilder.Row amount, int left,
                         int y, ClickTargets targets) {
        GooTypeIcons.draw(graphics, targets, amount.type(), left, y + ICON_NUDGE);
        int x = left + GooTypeIcons.SIZE + GAP;
        ExpressionBuilder.Piece piece = amount.pieces().getFirst();
        x = amount.pieces().size() == 1 && !(piece.operand() instanceof ExpressionBuilder.Kept)
                ? drawAmountControls(graphics, font, row, piece, x, y, targets)
                : drawOperand(graphics, font, ExpressionBuilder.rowSum(amount), x, y, targets);
        control(graphics, font, REMOVE, x, y, targets, at -> apply(b -> b.removeRow(row)));
    }

    // The amount's [#], [var] and [relative] modes, its operand and its factor; answers the x past them.
    private int drawAmountControls(GuiGraphicsExtractor graphics, Font font, int row, ExpressionBuilder.Piece piece,
                                   int left, int y, ClickTargets targets) {
        int x = control(graphics, font, NUMBER_MODE, left, y, targets, at -> {
            apply(b -> b.setRowNumber(row, numberOf(piece)));
            openNumber(at, numberOf(piece), digits -> apply(b -> b.setRowNumber(row, digits)));
        });
        x = control(graphics, font, VAR_MODE, x, y, targets, at -> openDropdown(at, false, scalarPrefabs.get(),
                choice -> apply(b -> b.setRowVar(row, choice.id()))));
        x = control(graphics, font, RELATIVE_MODE, x, y, targets, at -> openDropdown(at, true, prefabs.get(),
                choice -> openPicker(at, type -> apply(b -> b.setRowRelative(row, choice, type)))));
        x = drawOperand(graphics, font, ExpressionBuilder.operandSum(piece), x, y, targets);
        return drawScale(graphics, font, piece, x, y, targets,
                (op, factor) -> apply(b -> b.setRowScale(row, op, factor)));
    }

    private void drawPiece(GuiGraphicsExtractor graphics, Font font, int index, ExpressionBuilder.Piece piece,
                           int left, int y, ClickTargets targets) {
        int x = left;
        if (index > 0) {
            boolean plus = piece.join() == ExpressionTerms.Join.PLUS;
            x = control(graphics, font, plus ? PLUS : MINUS, x, y, targets, at -> apply(b -> b.setJoin(index, plus ? ExpressionTerms.Join.MINUS : ExpressionTerms.Join.PLUS)));
        }
        x = control(graphics, font, selected.contains(index) ? SELECTED : UNSELECTED, x, y, targets, at -> {
            if (!selected.remove(index)) {
                selected.add(index);
            }
        });
        x = drawOperand(graphics, font, ExpressionBuilder.operandSum(piece), x, y, targets);
        if (!(piece.operand() instanceof ExpressionBuilder.Kept)) {
            x = drawScale(graphics, font, piece, x, y, targets, (op, factor) -> apply(b -> b.setScale(index, op, factor)));
        }
        if (piece.operand() instanceof ExpressionBuilder.Ref ref) {
            x = ref.member() == null
                    ? control(graphics, font, MEMBER, x, y, targets, at -> openPicker(at, type -> apply(b -> b.setMember(index, type))))
                    : control(graphics, font, WHOLE, x, y, targets, at -> apply(b -> b.setMember(index, null)));
        }
        control(graphics, font, REMOVE, x, y, targets, at -> {
            selected.clear();
            apply(b -> b.remove(index));
        });
    }

    // Answers the x past the operand's curated runs.
    private int drawOperand(GuiGraphicsExtractor graphics, Font font, ExpressionTerms.Sum sum, int x, int y,
                            ClickTargets targets) {
        List<ExpressionLines.Run> runs = ExpressionLines.runs(new ExpressionTerms.Expression(sum), icons::nameOf);
        ExpressionLines.draw(font, runs, x, y, OPERAND_WIDTH, new ExpressionLines.Painter(graphics, icons, targets,
                WHITE, LINK, (name, linkX, linkY, width) -> { }));
        return x + OPERAND_WIDTH + GAP;
    }

    /**
     * A factor's change.
     */
    @FunctionalInterface
    private interface ScaleChange {
        void changed(ExpressionBuilder.Op op, String factor);
    }

    // The multiply or divide toggle and the factor, which a click opens in the number box.
    private int drawScale(GuiGraphicsExtractor graphics, Font font, ExpressionBuilder.Piece piece, int left, int y,
                          ClickTargets targets, ScaleChange change) {
        boolean multiplies = piece.op() == ExpressionBuilder.Op.MULTIPLY;
        int x = control(graphics, font, multiplies ? MULTIPLY : DIVIDE, left, y, targets, at -> change.changed(
                multiplies ? ExpressionBuilder.Op.DIVIDE : ExpressionBuilder.Op.MULTIPLY, piece.factor()));
        return control(graphics, font, piece.factor(), x, y, targets, at -> openNumber(at, piece.factor(),
                digits -> change.changed(piece.op(), digits)));
    }

    private static String numberOf(ExpressionBuilder.Piece piece) {
        return piece.operand() instanceof ExpressionBuilder.Num num ? num.digits() : ExpressionBuilder.ONE_TEXT;
    }

    /**
     * Where a control was clicked, so a list or box it opens stands below it.
     *
     * @param x the control's left edge
     * @param y the control's line top
     */
    private record Anchor(int x, int y) {
    }

    // Answers the x past the control.
    private int control(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, ClickTargets targets,
                        Consumer<Anchor> pressed) {
        graphics.text(font, label, x, y + TEXT_NUDGE, LINK);
        int width = font.width(label);
        targets.add(x, y, width, LINE, () -> pressed.accept(new Anchor(x, y)));
        return x + width + GAP;
    }

    private void apply(Consumer<ExpressionBuilder> edit) {
        ExpressionBuilder edited = builder;
        if (edited == null) {
            return;
        }
        String before = edited.text();
        edit.accept(edited);
        if (writes != null && !before.equals(edited.text())) {
            writes.accept(edited.text());
        }
    }

    private void wrapSelected() {
        if (!selected.isEmpty()) {
            int from = selected.first();
            int to = selected.last();
            selected.clear();
            apply(b -> b.wrap(from, to));
        }
    }

    private void finish() {
        Runnable finished = done;
        close();
        if (finished != null) {
            finished.run();
        }
    }

    private void openPicker(Anchor at, Consumer<String> picks) {
        closeOverlays();
        anchorX = at.x();
        anchorY = at.y() + LINE;
        pickerPicks = picks;
        picker.open();
    }

    private void openDropdown(Anchor at, boolean items, List<String> names, Consumer<SearchDropdown.Choice> picks) {
        closeOverlays();
        anchorX = at.x();
        anchorY = at.y() + LINE;
        dropdown = SearchDropdown.over(inventory.get(), names, items);
        dropdown.open();
        dropdownPicks = picks;
        if (searchField != null) {
            searchField.setValue(NOTHING);
            searchField.visible = visible;
            actions.focus(searchField);
        }
    }

    private void openNumber(Anchor at, String current, Consumer<String> commits) {
        closeOverlays();
        numberCommits = commits;
        if (numberField != null) {
            numberField.setValue(current);
            numberField.setPosition(at.x(), at.y() + LINE - BOX_HEIGHT);
            numberField.visible = visible;
            actions.focus(numberField);
            numberHeldFocus = false;
        }
    }

    private void drawOverlays(GuiGraphicsExtractor graphics, Font font, ClickTargets targets) {
        drawDropdown(graphics, font, targets);
        picker.draw(graphics, targets, anchorX, anchorY, type -> {
            Consumer<String> picks = pickerPicks;
            pickerPicks = null;
            if (picks != null) {
                picks.accept(type);
            }
        });
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
        searchField.setPosition(anchorX, anchorY);
        open.draw(graphics, font, icons, targets, anchorX, anchorY + BOX_HEIGHT + 1, SEARCH_WIDTH, choice -> {
            Consumer<SearchDropdown.Choice> picks = dropdownPicks;
            closeOverlays();
            if (picks != null) {
                picks.accept(choice);
            }
        });
    }

    private void commitOnBlur() {
        if (numberField == null || !numberField.visible) {
            return;
        }
        if (numberField.isFocused()) {
            numberHeldFocus = true;
        } else if (numberHeldFocus) {
            commitNumber();
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

    private void closeOverlays() {
        closeDropdown();
        picker.close();
        pickerPicks = null;
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
