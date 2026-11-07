package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.ability.AbilityIconTexture;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Draws the Abilities tab: the search line every list stands at its top, then each goo type's section, each ability
 * its search matches indented under it, its heading followed on its line by its price fields alone, each a small
 * label and a box. A cost or an upkeep reads as its by-type value, its items drawn as their icons, and opens the
 * expression builder restricted to the ability's own goo type; every other price field reads its number and opens a
 * text box over it. A value that would not load, or a cost reading an item holding none of the ability's type, reads
 * one red line under its field (decisions abilities-tab-lists-every-numeric-param-as-expression-boxes,
 * ability-cost-builds-as-a-one-type-by-type-value, one-builder-frame-on-every-pane, one-red-error-line-under-every-value).
 * The state lives in {@link AbilitiesTab}.
 */
final class AbilitiesPane {
    private static final int GAP = 4;
    private static final int HEADING_HEIGHT = ButtonPainter.HEIGHT;
    private static final String DRAFT_BADGE = "draft";
    private static final String NO_TEXT = "";
    // Decision value-expression-boxes-take-a-cancel-button: the box's pair, as the builder draws it.
    private static final String CANCEL = "✗ Cancel";
    private static final String DONE = "✓ Done";
    // The Audit pane's chevron marks and toggle-all labels.
    private static final String OPEN_MARK = "v";
    private static final String CLOSED_MARK = ">";
    private static final String COLLAPSE_ALL = "Collapse all";
    private static final String EXPAND_ALL = "Expand all";
    // A hover key no item, prefab or type spells, carrying the ability's description key.
    private static final String DESCRIPTION_HOVER = "goo-ability-description:";
    // The icon sits in its sprite row as an item sprite does.
    private static final int ICON_INSET = (RowLayout.SPRITE_ROW_HEIGHT - RowLayout.SPRITE_SIZE) / 2;
    private static final int HALF = 2;
    // Decision ability-params-lay-in-a-four-column-grid-spanning-whole-cells: the names take the left third, the
    // price fields four equal columns right of it.
    private static final int NAME_SHARE = 3;
    private static final int COLUMNS = 4;
    // A pointer no row stands under.
    private static final double NO_POINTER = -1;
    // The least room an open text box takes, so a short value's box still reads what is typed.
    private static final int EDITOR_WIDTH = 60;

    private final AbilitiesTab tab;
    private final ManagerActions actions;
    private final ItemIcons icons;
    private final ExpressionBuilderPane builder;
    private final Function<String, String> translate;
    private final Function<ExpressionBuilder.Ref, Map<String, String>> worth;
    private final Supplier<Set<String>> perTypePrefabs;
    private final ScrollBar bar = new ScrollBar(ScrollBar.WHEEL_STEP);
    private @Nullable EditBox search;
    private @Nullable EditBox editor;
    private @Nullable Font font;
    // The slots each section laid this frame, by its goo type, and the rail held in a drag.
    private final Map<String, List<Slot>> slots = new LinkedHashMap<>();
    private @Nullable Held held;
    // The row the pointer hovered, as the last frame laid it.
    private @Nullable Slot hovered;
    // The leaf the text box or the builder stands over, as the ability's id and the leaf's path.
    private @Nullable Editing editing;
    private boolean shown;

    /**
     * The leaf being edited.
     *
     * @param id     the ability's id
     * @param path   the leaf's path
     * @param before the leaf's value when editing started, which Escape restores
     */
    private record Editing(String id, String path, String before) {
        boolean holds(AbilitiesTab.Ability ability, AbilitiesTab.Leaf leaf) {
            return id.equals(ability.id()) && path.equals(leaf.path());
        }
    }

    /**
     * @param tab            the tab's state
     * @param actions        the panes' line back to the screen
     * @param icons          the items' icons
     * @param builder        the expression builder a cost opens
     * @param translate      each translation key's text, which names each ability
     * @param worth          each item's or prefab's resolved amounts by goo type
     * @param perTypePrefabs the prefabs holding an amount per goo type
     */
    AbilitiesPane(AbilitiesTab tab, ManagerActions actions, ItemIcons icons, ExpressionBuilderPane builder,
                  Function<String, String> translate, Function<ExpressionBuilder.Ref, Map<String, String>> worth,
                  Supplier<Set<String>> perTypePrefabs) {
        this.tab = tab;
        this.actions = actions;
        this.icons = icons;
        this.builder = builder;
        this.translate = translate;
        this.worth = worth;
        this.perTypePrefabs = perTypePrefabs;
    }

    /**
     * Stands the search box and the leaf's text box among the screen's widgets.
     *
     * @param font the screen's font
     */
    void init(Font font) {
        this.font = font;
        builder.init(font);
        String typed = search == null ? NO_TEXT : search.getValue();
        search = actions.addWidget(new EditBox(font, 0, 0, 1, SearchRow.HEIGHT, Component.literal(NO_TEXT)));
        search.setValue(typed);
        editor = actions.addWidget(new EditBox(font, 0, 0, 1, ButtonPainter.HEIGHT, Component.literal(NO_TEXT)));
        editor.setResponder(this::typed);
        show(shown);
    }

    /**
     * @param visible whether the tab is the one drawn
     */
    void show(boolean visible) {
        shown = visible;
        if (search != null) {
            search.visible = visible;
        }
        if (editor != null) {
            editor.visible = visible && editing != null && !builder.isOpen();
        }
        builder.show(visible);
    }

    /**
     * Reads every ability's model afresh.
     */
    void refresh() {
        actions.request(WorkbenchOperations.ABILITY_MODEL, NO_TEXT, tab::readModel);
    }

    /**
     * Writes a draft for each edited ability, which the server applies to every player at once, then reads the
     * model it answers (decision ability-drafts-promote-per-file-like-base-values).
     *
     * @param saved runs once the save answered, or at once where no leaf stands edited
     */
    void save(Runnable saved) {
        if (!tab.edited()) {
            saved.run();
            return;
        }
        actions.request(WorkbenchOperations.SAVE_ABILITIES, tab.postedText(), reply -> {
            tab.saved(reply);
            saved.run();
        });
    }

    /**
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double amount) {
        if (!builder.scrollList(amount)) {
            bar.wheel(amount);
        }
    }

    /**
     * Enter closes the leaf's text box keeping what it holds, and Escape closes it restoring the value it opened on.
     *
     * @param key the GLFW key pressed
     * @return whether the pane took the key
     */
    boolean keyPressed(int key) {
        // Escape over a held rail puts its ability back.
        if (key == GLFW.GLFW_KEY_ESCAPE && held != null) {
            held = null;
            return true;
        }
        return editorKey(key) || builder.keyPressed(key);
    }

    // Enter closes the open text box keeping its value, Escape restoring the one it opened on.
    private boolean editorKey(int key) {
        if (editing == null || builder.isOpen()) {
            return false;
        }
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (enter || key == GLFW.GLFW_KEY_ESCAPE) {
            stopEditing(!enter);
            return true;
        }
        return false;
    }

    /**
     * @return the builder's open lists, which the overlay stratum draws
     */
    List<OverlayStratum.Layer> overlayLayers() {
        return builder.isOpen() ? List.of(builder::drawOverlays) : List.of();
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        extract(graphics, font, bounds, targets, NO_POINTER, NO_POINTER);
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets,
                 double pointerX, double pointerY) {
        hovered = hoveredRow(pointerX, pointerY, bounds);
        List<AbilitiesTab.Section> sections = tab.sections(translate, search == null ? NO_TEXT : search.getValue());
        SearchRow.Laid row = drawSearchRow(graphics, font, bounds, sections, targets);
        GroupsPane.Bounds area = new GroupsPane.Bounds(bounds.left(), row.listTop(), bounds.right(), bounds.bottom());
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        targets.clip(area.left(), area.top(), area.right(), area.bottom());
        // The text box hides unless the leaf it edits draws this frame, as a search can filter it out.
        if (editor != null) {
            editor.visible = false;
        }
        int y = area.top() - bar.offset();
        for (AbilitiesTab.Section section : sections) {
            y = drawSection(graphics, font, section, new GroupsPane.Bounds(area.left(), y, area.right()
                    - ScrollBar.WIDTH - ScrollBar.GAP, area.bottom()), targets) + GAP;
        }
        targets.unclip();
        graphics.disableScissor();
        drawHeld(graphics, area);
        bar.measureAndDraw(graphics, targets, y, area.right(), area.top(), area.bottom());
    }

    // The search box, then the toggle-all right of it reading as the Audit tab's does; answers the row laid.
    // Decision type-sections-fold-by-the-audit-chevron-and-start-expanded.
    private SearchRow.Laid drawSearchRow(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                                         List<AbilitiesTab.Section> sections, ClickTargets targets) {
        List<String> types = sections.stream().map(AbilitiesTab.Section::type).toList();
        String toggleAll = tab.anyOpen(types) ? COLLAPSE_ALL : EXPAND_ALL;
        int controls = types.isEmpty() ? 0 : ButtonPainter.width(font, toggleAll);
        SearchRow.Laid row = SearchRow.lay(bounds.left(), bounds.top(), bounds.right(), controls);
        SearchRow.place(search, row);
        if (!types.isEmpty()) {
            ButtonPainter.draw(graphics, font, toggleAll, row.controlsLeft(), row.top(), SearchRow.HEIGHT, targets,
                    () -> tab.toggleAll(types));
        }
        return row;
    }

    // The section's heading, as the Audit pane heads a section, then its abilities while open, a rule above each but
    // the first; answers the y below its last ability.
    // Decisions abilities-tab-sections-by-type-in-picker-order-fan-order-inside, entries-draw-line-separators.
    private int drawSection(GuiGraphicsExtractor graphics, Font font, AbilitiesTab.Section section,
                            GroupsPane.Bounds from, ClickTargets targets) {
        boolean open = tab.open(section.type());
        drawSectionHeading(graphics, font, section.type(), open, from, targets);
        int y = from.top() + HEADING_HEIGHT + GAP;
        if (!open) {
            return y;
        }
        List<AbilitiesTab.Ability> abilities = section.abilities();
        List<Slot> laid = new ArrayList<>();
        for (int index = 0; index < abilities.size(); index++) {
            RowLayout.drawSeparatorAbove(graphics, index, y, from.left(), from.right());
            int top = y;
            drawHover(graphics, abilities.get(index).id(), new GroupsPane.Bounds(from.left(), y, from.right(),
                    from.bottom()));
            y = drawAbility(graphics, font, abilities.get(index), new GroupsPane.Bounds(from.left(), y, from.right(),
                    from.bottom()), targets);
            laid.add(new Slot(abilities.get(index).id(), top, y, from.left(), from.right()));
        }
        slots.put(section.type(), List.copyOf(laid));
        return y;
    }

    /**
     * Where an ability laid this frame, the slot a dropped rail lands on.
     *
     * @param id     the ability's id
     * @param top    its heading's top
     * @param bottom the bottom of its last param
     * @param left   its left edge
     * @param right  its right edge
     */
    private record Slot(String id, int top, int bottom, int left, int right) {
        boolean holds(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /**
     * A rail held in a drag.
     *
     * @param id   the dragged ability's id
     * @param type its section's goo type
     * @param y    the pointer's y
     */
    private record Held(String id, String type, double y) {
    }

    // The row the last frame laid under the pointer, none while a rail is held or the pointer stands off the list.
    private @Nullable Slot hoveredRow(double pointerX, double pointerY, GroupsPane.Bounds bounds) {
        if (held != null || pointerY < bounds.top() || pointerY >= bounds.bottom()) {
            return null;
        }
        return slots.values().stream().flatMap(List::stream).filter(slot -> slot.holds(pointerX, pointerY))
                .findFirst().orElse(null);
    }

    // The hovered ability's row fills with the selected fill under its name and prices, as tall as it last laid
    // (the operator's ruling of 2026-11-07: style hovered rows, with the selected fill).
    private void drawHover(GuiGraphicsExtractor graphics, String id, GroupsPane.Bounds row) {
        Slot under = hovered;
        if (under != null && under.id().equals(id)) {
            graphics.fill(row.left(), row.top(), row.right(), row.top() + under.bottom() - under.top(),
                    Palette.SELECTED_FILL);
        }
    }

    // The slot of the held ability's section under the pointer, the first or last past either end.
    private Optional<Slot> heldSlot() {
        Held dragged = held;
        if (dragged == null) {
            return Optional.empty();
        }
        List<Slot> laid = slots.getOrDefault(dragged.type(), List.of());
        return laid.stream().filter(slot -> dragged.y() < slot.bottom()).findFirst()
                .or(() -> laid.isEmpty() ? Optional.empty() : Optional.of(laid.getLast()));
    }

    // The ability's rail grabs it; the drag follows the pointer and the drop moves it onto the slot it lands on.
    // Decision abilities-reorder-by-a-drag-rail-and-the-order-field-hides.
    private ClickTargets.PointerGrab railGrab(AbilitiesTab.Ability ability) {
        return new ClickTargets.PointerGrab() {
            @Override
            public void moved(double x, double y) {
                held = new Held(ability.id(), ability.gooType(), y);
            }

            @Override
            public void released() {
                Optional<Slot> landed = heldSlot();
                held = null;
                if (landed.isPresent() && !landed.get().id().equals(ability.id())) {
                    tab.moveTo(ability.id(), landed.get().id());
                    actions.markUnsaved();
                }
            }
        };
    }

    // While a rail is held, masks the screen but the slot it would drop on, as a group member's rail does.
    private void drawHeld(GuiGraphicsExtractor graphics, GroupsPane.Bounds area) {
        heldSlot().ifPresent(slot -> {
            DragMask.Rect lit = new DragMask.Rect(slot.left(), slot.top(), slot.right(), slot.bottom())
                    .within(new DragMask.Rect(area.left(), area.top(), area.right(), area.bottom()));
            DragMask.draw(graphics, new DragMask.Rect(0, 0, graphics.guiWidth(), graphics.guiHeight()),
                    lit.isEmpty() ? List.of() : List.of(lit));
        });
    }

    // The chevron button, the type's icon at its texture's size hovering its name, then its name bold; the chevron
    // and the heading past it fold the section, as the Audit pane's header does.
    // Decisions section-chevron-becomes-a-button, goo-type-icons-draw-native-everywhere.
    private void drawSectionHeading(GuiGraphicsExtractor graphics, Font font, String type, boolean open,
                                    GroupsPane.Bounds from, ClickTargets targets) {
        Runnable fold = () -> tab.toggle(type);
        int x = ButtonPainter.draw(graphics, font, open ? OPEN_MARK : CLOSED_MARK, from.left(), from.top(),
                HEADING_HEIGHT, targets, fold).right() + GAP;
        int size = GooTypeIcons.sizeOf(type);
        GooTypeIcons.draw(graphics, targets, type, x, from.top() + GooTypeIcons.centered(HEADING_HEIGHT, size), size);
        x += size + GAP;
        Component name = GooTypeIcons.nameOf(type).copy().withStyle(ChatFormatting.BOLD);
        graphics.text(font, name, x, Lettering.full(font).centeredTop(from.top(), HEADING_HEIGHT), Palette.TEXT);
        targets.add(x, from.top(), from.right() - x, HEADING_HEIGHT, fold);
    }

    // The ability indented under its type's heading: its heading in the left third the names reserve, then its price
    // fields in four equal columns right of that third, the same four lefts in every ability, a field wider than a
    // column taking whole columns and a cost's open builder all four; answers the y below its last line.
    // Decisions abilities-tab-lists-every-numeric-param-as-expression-boxes,
    // ability-params-lay-in-a-four-column-grid-spanning-whole-cells.
    private int drawAbility(GuiGraphicsExtractor graphics, Font font, AbilitiesTab.Ability ability,
                            GroupsPane.Bounds from, ClickTargets targets) {
        drawHeading(graphics, font, ability, from.left() + ControlFlow.INDENT, from.top(), targets);
        int priceLeft = from.left() + (from.right() - from.left()) / NAME_SHARE;
        ColumnFlow flow = new ColumnFlow(priceLeft, (from.right() - priceLeft) / COLUMNS, from.top());
        for (AbilitiesTab.Leaf leaf : ability.prices()) {
            int columns = isBuilding(ability, leaf) ? COLUMNS : flow.columnsFor(fieldWidth(font, ability, leaf));
            GroupsPane.Bounds field = flow.place(columns, from.bottom());
            flow.took(drawField(graphics, font, new Priced(ability, leaf), field, from.right(), targets), columns);
        }
        return flow.bottom() + GAP;
    }

    /**
     * Where an ability's price fields stand: four equal columns from the left third's edge, a field taking the whole
     * columns its width needs, and a field past the last column wrapping to the first on the next line.
     */
    private static final class ColumnFlow {
        private final int left;
        private final int cell;
        private int column;
        private int top;
        private int bottom;

        ColumnFlow(int left, int cell, int top) {
            this.left = left;
            this.cell = Math.max(1, cell);
            this.top = top;
            this.bottom = top + RowLayout.SPRITE_ROW_HEIGHT;
        }

        // The whole columns a field of the width given takes, at most all four.
        int columnsFor(int width) {
            return Math.clamp((width + cell - 1) / cell, 1, COLUMNS);
        }

        // The next field's bounds, a new line begun where its columns pass the last.
        GroupsPane.Bounds place(int columns, int floor) {
            if (column > 0 && column + columns > COLUMNS) {
                top = bottom + GAP;
                column = 0;
            }
            int fieldLeft = left + column * cell;
            return new GroupsPane.Bounds(fieldLeft, top, fieldLeft + columns * cell, floor);
        }

        // Moves past the field placed last, which reached the y given.
        void took(int fieldBottom, int columns) {
            bottom = Math.max(bottom, fieldBottom);
            column += columns;
        }

        int bottom() {
            return bottom;
        }
    }


    // The rail's room at the left edge, the ability's 16px icon as an item sprite sits in its row, its name bold
    // hovering its description, and the gold draft badge after it while a draft stands over its live file.
    // Decision ability-heading-is-rail-icon-name-draft-badge-and-hovers-its-description.
    private void drawHeading(GuiGraphicsExtractor graphics, Font font, AbilitiesTab.Ability ability, int left,
                             int top, ClickTargets targets) {
        int bottom = top + RowLayout.SPRITE_ROW_HEIGHT;
        Rails.draw(graphics, left, top, bottom);
        targets.addGrab(left, top, ControlFlow.RAIL_WIDTH, bottom - top, railGrab(ability), ability.id());
        int x = left + ControlFlow.RAIL_ADVANCE;
        graphics.blit(RenderPipelines.GUI_TEXTURED, iconOf(ability), x, top + ICON_INSET, 0.0f, 0.0f,
                RowLayout.SPRITE_SIZE, RowLayout.SPRITE_SIZE, RowLayout.SPRITE_SIZE, RowLayout.SPRITE_SIZE,
                Palette.TEXT);
        int nameLeft = x + RowLayout.SPRITE_SIZE + GAP;
        Component name = Component.literal(translate.apply(ability.displayName())).withStyle(ChatFormatting.BOLD);
        int textTop = RowLayout.nameTop(font, top);
        graphics.text(font, name, nameLeft, textTop, Palette.TEXT);
        targets.addHover(nameLeft, top, font.width(name), RowLayout.SPRITE_ROW_HEIGHT,
                DESCRIPTION_HOVER + ability.description());
        if (ability.draft()) {
            graphics.text(font, DRAFT_BADGE, nameLeft + font.width(name) + GAP, textTop, Palette.GOLD);
        }
    }

    private boolean isBuilding(AbilitiesTab.Ability ability, AbilitiesTab.Leaf leaf) {
        return editing != null && editing.holds(ability, leaf) && leaf.kind() == AbilitiesTab.Kind.COST
                && builder.isOpen();
    }

    // The room a price field takes: its small label, then its box as wide as its value and pencil, and while its text
    // box stands open the room the box and its ✗ Cancel and ✓ Done take.
    private int fieldWidth(Font font, AbilitiesTab.Ability ability, AbilitiesTab.Leaf leaf) {
        int box = ExpressionLines.width(font, runsOf(ability, leaf, tab.valueOf(ability, leaf)))
                + ButtonPainter.PADDING + ButtonPainter.PADDING + ButtonPainter.width(font, ReadValueButton.PENCIL);
        if (editing != null && editing.holds(ability, leaf)) {
            box = Math.max(box, EDITOR_WIDTH) + GAP + ButtonPainter.width(font, CANCEL) + GAP
                    + ButtonPainter.width(font, DONE);
        }
        return Lettering.builder(font).width(AbilityWording.label(leaf, translate)) + BuilderFrame.LABEL_GAP + box;
    }

    // The ability's icon texture, as the radial reads it: the file's override, else the convention path.
    private static Identifier iconOf(AbilitiesTab.Ability ability) {
        Identifier id = Identifier.tryParse(ability.id());
        return AbilityIconTexture.of(id == null ? Identifier.withDefaultNamespace(ability.id()) : id, ability.icon());
    }

    /**
     * @param hoverKey  what the hovered area names
     * @param translate each translation key's text
     * @return the ability's translated description where the area is an ability name's, empty otherwise
     */
    static Optional<String> descriptionFor(String hoverKey, Function<String, String> translate) {
        return hoverKey.startsWith(DESCRIPTION_HOVER)
                ? Optional.of(translate.apply(hoverKey.substring(DESCRIPTION_HOVER.length()))) : Optional.empty();
    }

    // The field's label in the builder's smaller lettering hovering its description, then its box past it centered
    // on the heading's sprite row, then its red line; answers the y below it.
    // Decision ability-params-read-named-under-their-mechanism-and-hover-a-description.
    private int drawField(GuiGraphicsExtractor graphics, Font font, Priced priced, GroupsPane.Bounds field,
                          int lineRight, ClickTargets targets) {
        AbilitiesTab.Ability ability = priced.ability();
        AbilitiesTab.Leaf leaf = priced.leaf();
        boolean building = isBuilding(ability, leaf);
        List<ExpressionLines.Run> runs = runsOf(ability, leaf, tab.valueOf(ability, leaf));
        String label = AbilityWording.label(leaf, translate);
        Lettering small = Lettering.builder(font);
        int valueLeft = field.left() + small.width(label) + BuilderFrame.LABEL_GAP;
        // The box centers on the heading's sprite row, and the label centers on the box, however tall its icons stand
        // it (the operator's ruling of 2026-11-07).
        int boxHeight = editing != null && editing.holds(ability, leaf) ? ButtonPainter.HEIGHT
                : ReadValueButton.height(font, runs, field.right() - valueLeft);
        int boxTop = field.top() + Math.max(0, (RowLayout.SPRITE_ROW_HEIGHT - boxHeight) / HALF);
        int besideBox = building ? boxTop + ExpressionBuilderPane.PANEL_ABOVE : boxTop;
        // The labels read in the yellow the mechanism headings drew in (the operator's ruling of 2026-11-07).
        small.draw(graphics, label, field.left(), small.centeredTop(besideBox, boxHeight), Palette.FILE_HEADING);
        targets.addHover(field.left(), besideBox, small.width(label), boxHeight,
                DESCRIPTION_HOVER + AbilityWording.descriptionKey(leaf.mechanism(), leaf.field()));
        // The frame stands its box at its row's indent, so the row starts that indent short of the value.
        BuilderFrame.Row framed = new BuilderFrame.Row(new GroupsPane.Bounds(valueLeft - ControlFlow.INDENT, boxTop,
                field.right(), field.bottom()), null, GooAmountGrid.NONE, false);
        int y = boxTop + BuilderFrame.draw(graphics, font, framed, new BuilderFrame.Value(building, runs, NO_TEXT,
                boxOf(graphics, font, priced, runs, targets)));
        // one-red-error-line-under-every-value: the line runs under the field to the ability's right edge, so it
        // reads whole however narrow the field
        for (String line : BuilderFrame.errorLines(font, tab.errorOf(ability, leaf, worth), lineRight - valueLeft)) {
            graphics.text(font, line, valueLeft, y, Palette.DANGER);
            y += ExpressionLines.TEXT_LINE_HEIGHT;
        }
        return y;
    }

    // The builder while it stands open over the cost, the text box while it stands open over a number, else the
    // field's value read in its button.
    private BuilderFrame.Box boxOf(GuiGraphicsExtractor graphics, Font font, Priced priced,
                                   List<ExpressionLines.Run> runs, ClickTargets targets) {
        AbilitiesTab.Ability ability = priced.ability();
        AbilitiesTab.Leaf leaf = priced.leaf();
        if (isBuilding(ability, leaf)) {
            return area -> builder.extract(graphics, font, area, area.top(), targets);
        }
        if (editing != null && editing.holds(ability, leaf) && leaf.kind() != AbilitiesTab.Kind.COST) {
            return area -> standEditor(graphics, font, area, targets);
        }
        return area -> ReadValueButton.draw(graphics, font, runs, new ReadValueButton.Place(area.left(), area.top(),
                area.right(), () -> startEditing(ability, leaf, area)),
                ExpressionLines.Painter.linked(graphics, icons, targets, Palette.TEXT, actions), targets);
    }
    /**
     * A price field of an ability.
     *
     * @param ability the ability
     * @param leaf    its price field
     */
    private record Priced(AbilitiesTab.Ability ability, AbilitiesTab.Leaf leaf) {
    }

    // The leaf's text box over its row's box, then ✗ Cancel from the moment it opens and ✓ Done once its value
    // changed, as the builder's pair stands; answers the height it takes.
    // Decision value-expression-boxes-take-a-cancel-button.
    private int standEditor(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds area, ClickTargets targets) {
        Editing edited = editing;
        if (editor == null || edited == null) {
            return ButtonPainter.HEIGHT;
        }
        boolean changed = !editor.getValue().equals(edited.before());
        int x = placeEditor(font, area, changed) + GAP;
        x = ButtonPainter.drawColored(graphics, font, CANCEL, Palette.DANGER, x, area.top(), ButtonPainter.HEIGHT,
                targets, () -> stopEditing(true), null).right() + GAP;
        if (changed) {
            ButtonPainter.drawColored(graphics, font, DONE, Palette.GOOD, x, area.top(), ButtonPainter.HEIGHT,
                    targets, () -> stopEditing(false), null);
        }
        editor.visible = shown;
        return ButtonPainter.HEIGHT;
    }

    // Stands the text box at the row's box, short of the room its buttons take; answers its right edge.
    private int placeEditor(Font font, GroupsPane.Bounds area, boolean changed) {
        int buttons = ButtonPainter.width(font, CANCEL) + GAP
                + (changed ? ButtonPainter.width(font, DONE) + GAP : 0);
        int width = Math.max(1, area.right() - area.left() - buttons);
        if (editor != null) {
            editor.setPosition(area.left(), area.top());
            editor.setWidth(width);
        }
        return area.left() + width;
    }

    // A cost reads as its by-type value, its items drawn as their icons; any other leaf reads as it is written.
    private List<ExpressionLines.Run> runsOf(AbilitiesTab.Ability ability, AbilitiesTab.Leaf leaf, String value) {
        if (leaf.kind() != AbilitiesTab.Kind.COST) {
            return List.of(new ExpressionLines.Run(ExpressionLines.Kind.TEXT, value, null));
        }
        return ExpressionTerms.parseShown(AbilityCosts.shown(value, ability.gooType(), perTypePrefabs.get()))
                .map(shownCost -> ExpressionLines.runs(shownCost, icons::nameOf))
                .orElseGet(() -> List.of(new ExpressionLines.Run(ExpressionLines.Kind.ERROR, value, null)));
    }

    private void startEditing(AbilitiesTab.Ability ability, AbilitiesTab.Leaf leaf, GroupsPane.Bounds area) {
        stopEditing(false);
        String value = tab.valueOf(ability, leaf);
        editing = new Editing(ability.id(), leaf.path(), value);
        if (leaf.kind() == AbilitiesTab.Kind.COST) {
            String type = ability.gooType();
            builder.openOneType(type, AbilityCosts.shown(value, type, perTypePrefabs.get()),
                    written -> AbilityCosts.written(written, type).ifPresent(source -> {
                        tab.edit(ability, leaf, source);
                        actions.markUnsaved();
                    }), () -> editing = null);
            return;
        }
        if (editor != null && font != null) {
            // The box stands at its full width before it takes the value, so the value reads whole from its start.
            placeEditor(font, area, false);
            editor.setValue(value);
            editor.visible = shown;
            actions.focus(editor);
            // Decision every-number-box-opens-with-its-text-selected.
            editor.moveCursorToEnd(false);
            editor.setHighlightPos(0);
        }
    }

    // The text box's every keystroke lands in the edited leaf.
    private void typed(String text) {
        Editing edited = editing;
        if (edited == null || builder.isOpen()) {
            return;
        }
        tab.abilities().stream().filter(ability -> ability.id().equals(edited.id())).findFirst()
                .flatMap(ability -> ability.leaves().stream().filter(leaf -> leaf.path().equals(edited.path()))
                        .findFirst().map(leaf -> {
                            tab.edit(ability, leaf, text);
                            return leaf;
                        }))
                .ifPresent(leaf -> actions.markUnsaved());
    }

    private void stopEditing(boolean restore) {
        Editing edited = editing;
        if (edited != null && restore && editor != null) {
            editor.setValue(edited.before());
        }
        editing = null;
        builder.close();
        if (editor != null) {
            editor.visible = false;
        }
    }
}
