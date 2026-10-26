package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;
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
    private static final int LINE = BuilderOverlays.LINE;
    private static final int GAP = BuilderOverlays.GAP;
    private static final int ICON_NUDGE = 1;
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

    private final ItemIcons icons;
    private final BuilderOverlays overlays;
    private final Supplier<List<ValuesTab.ManagerEntry>> inventory;
    private final Supplier<List<String>> prefabs;
    private final Supplier<List<String>> scalarPrefabs;
    private final SortedSet<Integer> selected = new TreeSet<>();
    private @Nullable ExpressionBuilder builder;
    private @Nullable Consumer<String> writes;
    private @Nullable Runnable done;

    /**
     * @param actions       the panes' line back to the screen
     * @param icons         the items' icons
     * @param inventory     the server's items, which [relate to item] and [relative] list
     * @param prefabs       every prefab's name
     * @param scalarPrefabs the scalar prefabs' names, which [var] lists
     */
    ExpressionBuilderPane(ManagerActions actions, ItemIcons icons, Supplier<List<ValuesTab.ManagerEntry>> inventory,
                          Supplier<List<String>> prefabs, Supplier<List<String>> scalarPrefabs) {
        this.icons = icons;
        this.overlays = new BuilderOverlays(actions, icons);
        this.inventory = inventory;
        this.prefabs = prefabs;
        this.scalarPrefabs = scalarPrefabs;
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        overlays.init(font);
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
        overlays.close();
    }

    /**
     * Closes the builder, keeping what it wrote.
     */
    void close() {
        overlays.close();
        builder = null;
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
        overlays.show(shown);
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the builder took the key
     */
    boolean keyPressed(int key) {
        return overlays.keyPressed(key);
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
        overlays.commitOnBlur();
        int y = drawControls(graphics, font, area.left(), top, targets);
        if (edited.refusal().isPresent()) {
            graphics.text(font, font.plainSubstrByWidth(edited.refusal().get(), area.right() - area.left()),
                    area.left(), y, RED);
            y += TEXT_LINE;
        }
        y = drawTerms(graphics, font, edited, area.left(), y, targets);
        graphics.fill(area.left() - 1, top - 1, area.right(), y, PANEL_COLOR);
        overlays.draw(graphics, font, targets);
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
        int x = BuilderOverlays.control(graphics, font, ADD_TYPE, left, y, targets,
                at -> overlays.openPicker(at, type -> apply(b -> b.addType(type))));
        x = BuilderOverlays.control(graphics, font, RELATE, x, y, targets, at -> overlays.openDropdown(at,
                SearchDropdown.over(inventory.get(), prefabs.get(), true), choice -> apply(b -> b.relate(choice))));
        x = BuilderOverlays.control(graphics, font, WRAP, x, y, targets, at -> wrapSelected());
        BuilderOverlays.control(graphics, font, DONE, x, y, targets, at -> finish());
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
        BuilderOverlays.control(graphics, font, REMOVE, x, y, targets, at -> apply(b -> b.removeRow(row)));
    }

    // The amount's [#], [var] and [relative] modes, its operand and its factor; answers the x past them.
    private int drawAmountControls(GuiGraphicsExtractor graphics, Font font, int row, ExpressionBuilder.Piece piece,
                                   int left, int y, ClickTargets targets) {
        int x = BuilderOverlays.control(graphics, font, NUMBER_MODE, left, y, targets, at -> {
            apply(b -> b.setRowNumber(row, numberOf(piece)));
            overlays.openNumber(at, numberOf(piece), digits -> apply(b -> b.setRowNumber(row, digits)));
        });
        x = BuilderOverlays.control(graphics, font, VAR_MODE, x, y, targets, at -> overlays.openDropdown(at,
                SearchDropdown.over(List.of(), scalarPrefabs.get(), false),
                choice -> apply(b -> b.setRowVar(row, choice.id()))));
        x = BuilderOverlays.control(graphics, font, RELATIVE_MODE, x, y, targets, at -> overlays.openDropdown(at,
                SearchDropdown.over(inventory.get(), prefabs.get(), true),
                choice -> overlays.openPicker(at, type -> apply(b -> b.setRowRelative(row, choice, type)))));
        x = drawOperand(graphics, font, ExpressionBuilder.operandSum(piece), x, y, targets);
        return drawScale(graphics, font, piece, x, y, targets,
                (op, factor) -> apply(b -> b.setRowScale(row, op, factor)));
    }

    private void drawPiece(GuiGraphicsExtractor graphics, Font font, int index, ExpressionBuilder.Piece piece,
                           int left, int y, ClickTargets targets) {
        int x = left;
        if (index > 0) {
            boolean plus = piece.join() == ExpressionTerms.Join.PLUS;
            x = BuilderOverlays.control(graphics, font, plus ? PLUS : MINUS, x, y, targets, at -> apply(b ->
                    b.setJoin(index, plus ? ExpressionTerms.Join.MINUS : ExpressionTerms.Join.PLUS)));
        }
        x = BuilderOverlays.control(graphics, font, selected.contains(index) ? SELECTED : UNSELECTED, x, y, targets,
                at -> toggleSelected(index));
        x = drawOperand(graphics, font, ExpressionBuilder.operandSum(piece), x, y, targets);
        if (!(piece.operand() instanceof ExpressionBuilder.Kept)) {
            x = drawScale(graphics, font, piece, x, y, targets,
                    (op, factor) -> apply(b -> b.setScale(index, op, factor)));
        }
        if (piece.operand() instanceof ExpressionBuilder.Ref ref) {
            x = ref.member() == null
                    ? BuilderOverlays.control(graphics, font, MEMBER, x, y, targets,
                            at -> overlays.openPicker(at, type -> apply(b -> b.setMember(index, type))))
                    : BuilderOverlays.control(graphics, font, WHOLE, x, y, targets,
                            at -> apply(b -> b.setMember(index, null)));
        }
        BuilderOverlays.control(graphics, font, REMOVE, x, y, targets, at -> {
            selected.clear();
            apply(b -> b.remove(index));
        });
    }

    private void toggleSelected(int index) {
        if (!selected.remove(index)) {
            selected.add(index);
        }
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
        int x = BuilderOverlays.control(graphics, font, multiplies ? MULTIPLY : DIVIDE, left, y, targets,
                at -> change.changed(multiplies ? ExpressionBuilder.Op.DIVIDE : ExpressionBuilder.Op.MULTIPLY,
                        piece.factor()));
        return BuilderOverlays.control(graphics, font, piece.factor(), x, y, targets, at -> overlays.openNumber(at,
                piece.factor(), digits -> change.changed(piece.op(), digits)));
    }

    private static String numberOf(ExpressionBuilder.Piece piece) {
        return piece.operand() instanceof ExpressionBuilder.Num num ? num.digits() : ExpressionBuilder.ONE_TEXT;
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
}
