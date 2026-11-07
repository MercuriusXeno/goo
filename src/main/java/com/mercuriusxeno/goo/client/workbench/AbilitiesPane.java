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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Draws the Abilities tab: the search line every list stands at its top, then each ability its search matches, its
 * display name heading its numeric leaves, each leaf a row of the one builder frame labeled by its path. A cost or an
 * upkeep reads as its by-type value, its items drawn as their icons, and opens the expression builder restricted to
 * the ability's own goo type; every other leaf reads its number or expression and opens a text box over it. A value
 * that would not load, or a cost reading an item holding none of the ability's type, reads one red line under its
 * row (decisions abilities-tab-lists-every-numeric-param-as-expression-boxes,
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
        if (editing != null && !builder.isOpen()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                stopEditing(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                stopEditing(true);
                return true;
            }
        }
        return builder.keyPressed(key);
    }

    /**
     * @return the builder's open lists, which the overlay stratum draws
     */
    List<OverlayStratum.Layer> overlayLayers() {
        return builder.isOpen() ? List.of(builder::drawOverlays) : List.of();
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
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
        for (int index = 0; index < abilities.size(); index++) {
            RowLayout.drawSeparatorAbove(graphics, index, y, from.left(), from.right());
            y = drawAbility(graphics, font, abilities.get(index), new GroupsPane.Bounds(from.left(), y, from.right(),
                    from.bottom()), targets);
        }
        return y;
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

    // The ability's heading, then each leaf's row; answers the y below its last row.
    private int drawAbility(GuiGraphicsExtractor graphics, Font font, AbilitiesTab.Ability ability,
                            GroupsPane.Bounds from, ClickTargets targets) {
        drawHeading(graphics, font, ability, from.left(), from.top(), targets);
        int y = from.top() + RowLayout.SPRITE_ROW_HEIGHT + GAP;
        for (AbilitiesTab.Leaf leaf : ability.leaves()) {
            GroupsPane.Bounds leafRow = new GroupsPane.Bounds(from.left(), y, from.right(), from.bottom());
            y += drawLeaf(graphics, font, ability, leaf, leafRow, targets) + GAP;
        }
        return y;
    }

    // The rail's room at the left edge, the ability's 16px icon as an item sprite sits in its row, its name bold
    // hovering its description, and the gold draft badge after it while a draft stands over its live file.
    // Decision ability-heading-is-rail-icon-name-draft-badge-and-hovers-its-description.
    private void drawHeading(GuiGraphicsExtractor graphics, Font font, AbilitiesTab.Ability ability, int left,
                             int top, ClickTargets targets) {
        int x = left + ControlFlow.RAIL_ADVANCE;
        graphics.blit(RenderPipelines.GUI_TEXTURED, iconOf(ability), x, top + ICON_INSET, 0.0f, 0.0f,
                RowLayout.SPRITE_SIZE, RowLayout.SPRITE_SIZE, RowLayout.SPRITE_SIZE, RowLayout.SPRITE_SIZE,
                Palette.TEXT);
        x += RowLayout.SPRITE_SIZE + GAP;
        Component name = Component.literal(translate.apply(ability.displayName())).withStyle(ChatFormatting.BOLD);
        int textTop = RowLayout.nameTop(font, top);
        graphics.text(font, name, x, textTop, Palette.TEXT);
        targets.addHover(x, top, font.width(name), RowLayout.SPRITE_ROW_HEIGHT,
                DESCRIPTION_HOVER + ability.description());
        if (ability.draft()) {
            graphics.text(font, DRAFT_BADGE, x + font.width(name) + GAP, textTop, Palette.GOLD);
        }
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

    // Answers the height the leaf's row took.
    private int drawLeaf(GuiGraphicsExtractor graphics, Font font, AbilitiesTab.Ability ability,
                         AbilitiesTab.Leaf leaf, GroupsPane.Bounds row, ClickTargets targets) {
        boolean editingLeaf = editing != null && editing.holds(ability, leaf);
        boolean building = editingLeaf && leaf.kind() == AbilitiesTab.Kind.COST && builder.isOpen();
        String value = tab.valueOf(ability, leaf);
        List<ExpressionLines.Run> runs = runsOf(ability, leaf, value);
        BuilderFrame.Row framed = new BuilderFrame.Row(row, leaf.path(), GooAmountGrid.NONE, false);
        BuilderFrame.Box box;
        if (building) {
            box = area -> builder.extract(graphics, font, area, area.top(), targets);
        } else if (editingLeaf && leaf.kind() != AbilitiesTab.Kind.COST) {
            box = area -> standEditor(graphics, font, area, targets);
        } else {
            box = area -> ReadValueButton.draw(graphics, font, runs, new ReadValueButton.Place(area.left(),
                    area.top(), area.right(), () -> startEditing(ability, leaf, area)),
                    ExpressionLines.Painter.linked(graphics, icons, targets, Palette.TEXT, actions), targets);
        }
        return BuilderFrame.draw(graphics, font, framed, new BuilderFrame.Value(building, runs,
                tab.errorOf(ability, leaf, worth), box));
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
