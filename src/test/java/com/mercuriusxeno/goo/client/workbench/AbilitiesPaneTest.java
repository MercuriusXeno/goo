package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.ability.AbilityIconTexture;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the Abilities tab's pane through drawn frames of a recorded model, with no Screen: each ability's display name
 * heads its numeric leaves, each leaf a row of the one builder frame, and a cost naming an item holding none of the
 * ability's goo type reads a red line under its row (decisions
 * abilities-tab-lists-every-numeric-param-as-expression-boxes, ability-cost-builds-as-a-one-type-by-type-value).
 */
class AbilitiesPaneTest {
    private static final GroupsPane.Bounds VIEW = new GroupsPane.Bounds(0, 0, 500, 400);
    private static final String NAME_KEY = "goo.ability.unstable.lurker";
    private static final String NAME = "Lurker";
    private static final String MODEL = """
            {"abilities": [{"id": "goo:unstable_lurker", "gooType": "unstable", "displayName": "%s", "draft": false,
              "leaves": [
                {"path": "cost", "value": "3 * minecraft:cobblestone", "kind": "COST"},
                {"path": "behaviors/0/amount", "value": "health / 2", "kind": "EXPRESSION"},
                {"path": "behaviors/1/range", "value": 16, "kind": "NUMBER"}
              ]}]}
            """.formatted(NAME_KEY);

    private static AbilitiesPane pane(Map<String, Map<String, String>> worths) {
        AbilitiesTab tab = new AbilitiesTab();
        tab.readModel(MODEL);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        return new AbilitiesPane(tab, actions, icons, builder, key -> NAME_KEY.equals(key) ? NAME : key,
                ref -> worths.getOrDefault(ref.id(), Map.of()), java.util.Set::of);
    }

    private static DrawnFrame draw(AbilitiesPane pane) {
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), VIEW, frame.targets);
        return frame;
    }

    @Test
    void theDisplayNameHeadsEachLeafsRow() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("unstable", "250"))));
        int heading = frame.find(NAME).y();
        for (String label : List.of("cost", "behaviors/0/amount", "behaviors/1/range")) {
            assertTrue(frame.find(label).y() > heading, label + " stands above the heading");
        }
        assertTrue(frame.texts().contains("health / 2"), frame.texts().toString());
        assertTrue(frame.texts().contains("16"), frame.texts().toString());
    }

    @Test
    void aCostItemHoldingNoneOfTheTypeReadsARedLine() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("rock", "250"))));
        DrawnFrame.Text error = frame.find("minecraft:cobblestone holds no unstable");
        assertEquals(Palette.DANGER, error.color());
        assertTrue(error.y() > frame.find("cost").y());
    }

    /**
     * A number or expression row's box opens over the value it held with that text selected, draws ✗ Cancel from the
     * moment it opens and ✓ Done once the value changed, as every box in the manager does (decisions
     * every-number-box-opens-with-its-text-selected, value-expression-boxes-take-a-cancel-button).
     */
    @Nested
    class EditBoxes {
        private static final String RANGE = "16";
        private static final String CANCEL = "✗ Cancel";
        private static final String DONE = "✓ Done";

        private final AbilitiesTab tab = new AbilitiesTab();
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private AbilitiesPane pane;

        private EditBox opened() {
            tab.readModel(MODEL);
            ManagerActions actions = mock(ManagerActions.class);
            when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
                widgets.add(call.getArgument(0));
                return call.getArgument(0);
            });
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
            pane = new AbilitiesPane(tab, actions, icons, new ExpressionBuilderPane(actions, icons, List::of,
                    List::of), key -> key, ref -> Map.of(), java.util.Set::of);
            pane.init(DrawnFrame.font());
            pane.show(true);
            draw(pane).click(RANGE);
            return widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .filter(box -> RANGE.equals(box.getValue())).findFirst()
                    .orElseThrow(() -> new AssertionError("no box opened holding " + RANGE));
        }

        private AbilitiesTab.Leaf range() {
            return tab.abilities().getFirst().leaves().get(2);
        }

        @Test
        void theBoxOpensHoldingItsValueSelected() {
            EditBox box = opened();
            assertEquals(RANGE, box.getHighlighted());
        }

        @Test
        void cancelDrawsAtOnceAndRestoresTheValueLeavingNoEdit() {
            EditBox box = opened();
            DrawnFrame open = draw(pane);
            assertTrue(open.texts().contains(CANCEL), open.texts().toString());
            assertTrue(!open.texts().contains(DONE), open.texts().toString());
            box.setValue("99");
            draw(pane).click(CANCEL);
            assertEquals(RANGE, tab.valueOf(tab.abilities().getFirst(), range()));
            assertTrue(!tab.edited(), "a cancelled edit still stands");
        }

        @Test
        void doneDrawsOnceChangedAndKeepsTheEdit() {
            EditBox box = opened();
            box.setValue("24");
            draw(pane).click(DONE);
            assertEquals("24", tab.valueOf(tab.abilities().getFirst(), range()));
            assertTrue(draw(pane).texts().contains("24"), "the row reads its new value once the box closes");
        }
    }

    /**
     * One section per goo type in the model's type order, each headed by the Audit chevron, the type's icon and its
     * bold name, its abilities in fan order with a separator between them, every section open at first and folded by
     * its chevron or the Expand all / Collapse all button (decisions
     * abilities-tab-sections-by-type-in-picker-order-fan-order-inside,
     * type-sections-fold-by-the-audit-chevron-and-start-expanded, entries-draw-line-separators).
     */
    @Nested
    class Sections {
        private static final String OPEN_MARK = "v";
        private static final String CLOSED_MARK = ">";
        private static final String COLLAPSE_ALL = "Collapse all";
        private static final String EXPAND_ALL = "Expand all";
        private final List<AbstractWidget> widgets = new ArrayList<>();

        private AbilitiesPane sectioned() {
            AbilitiesTab tab = new AbilitiesTab();
            tab.readModel(AbilitiesTabTest.MODEL);
            ManagerActions actions = mock(ManagerActions.class);
            when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
                widgets.add(call.getArgument(0));
                return call.getArgument(0);
            });
            ItemIcons icons = mock(ItemIcons.class);
            AbilitiesPane pane = new AbilitiesPane(tab, actions, icons, new ExpressionBuilderPane(actions, icons,
                    List::of, List::of), key -> key, ref -> Map.of(), java.util.Set::of);
            pane.init(DrawnFrame.font());
            pane.show(true);
            return pane;
        }

        private static String typeName(String type) {
            return GooTypeIcons.nameOf(type).getString();
        }

        // An ability heading's row top, from its name's top, as the heading centers its name on its sprite row.
        private static int rowTopOf(DrawnFrame.Text heading) {
            return heading.y() - RowLayout.nameTop(DrawnFrame.font(), 0);
        }

        @Test
        void typesStandInTheModelsOrderAndAbilitiesInFanOrder() {
            DrawnFrame frame = draw(sectioned());
            assertTrue(frame.find(typeName("rock")).y() > frame.find(typeName("unstable")).y());
            assertTrue(frame.find("rock_mob_first").y() > frame.find("rock_channeled").y());
            assertTrue(frame.find("rock_world").y() > frame.find("rock_mob").y());
        }

        @Test
        void aSectionHeadsWithTheChevronTheTypesIconAndItsBoldName() {
            DrawnFrame frame = draw(sectioned());
            DrawnFrame.Text name = frame.find(typeName("unstable"));
            assertTrue(frame.bold(typeName("unstable")));
            assertTrue(frame.findFirst(OPEN_MARK).x() < name.x(), "the chevron stands before the name");
            verify(frame.graphics, atLeastOnce()).blit(any(), eq(GooTypeIcons.texture("unstable")), anyInt(),
                    anyInt(), anyFloat(), anyFloat(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        }

        @Test
        void theChevronFoldsTheSectionsAbilitiesOutAndBack() {
            AbilitiesPane pane = sectioned();
            draw(pane).clickFirst(OPEN_MARK);
            DrawnFrame folded = draw(pane);
            assertTrue(folded.texts().contains(typeName("unstable")), "the heading left with its abilities");
            assertTrue(folded.texts().contains(CLOSED_MARK), folded.texts().toString());
            assertFalse(folded.texts().contains("unstable_world"), folded.texts().toString());
            assertTrue(folded.texts().contains("rock_world"), "folding one section folded another");
            folded.clickFirst(CLOSED_MARK);
            assertTrue(draw(pane).texts().contains("unstable_world"));
        }

        @Test
        void collapseAllStandsRightOfTheSearchBoxAndFoldsEverySection() {
            AbilitiesPane pane = sectioned();
            DrawnFrame frame = draw(pane);
            EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .findFirst().orElseThrow();
            DrawnFrame.Text collapse = frame.find(COLLAPSE_ALL);
            assertTrue(collapse.x() >= search.getRight(), collapse + " stands left of the search box's right edge");
            assertTrue(collapse.y() < search.getBottom(), collapse + " stands below the search row");
            frame.click(COLLAPSE_ALL);
            DrawnFrame collapsed = draw(pane);
            assertTrue(collapsed.texts().contains(EXPAND_ALL), collapsed.texts().toString());
            assertFalse(collapsed.texts().contains("rock_world") || collapsed.texts().contains("unstable_world"));
            collapsed.click(EXPAND_ALL);
            DrawnFrame expanded = draw(pane);
            assertTrue(expanded.texts().containsAll(List.of("unstable_world", "rock_channeled", "rock_world")));
        }

        @Test
        void aRuleStandsAboveEachAbilityButTheFirstOfItsSection() {
            DrawnFrame frame = draw(sectioned());
            List<Integer> rules = frame.fillsOf(Palette.SEPARATOR).stream()
                    .filter(fill -> fill[3] == fill[1] + 1).map(fill -> fill[1]).toList();
            assertTrue(rules.contains(rowTopOf(frame.find("rock_mob_first")) - 1), rules.toString());
            assertFalse(rules.contains(rowTopOf(frame.find("rock_channeled")) - 1), rules.toString());
            assertFalse(rules.contains(rowTopOf(frame.find("unstable_world")) - 1), rules.toString());
        }
    }

    /**
     * An ability's heading reads its rail's room, its 16px icon, its bold name and the gold draft badge while a draft
     * stands, and its name hovers its own description (decision
     * ability-heading-is-rail-icon-name-draft-badge-and-hovers-its-description).
     */
    @Nested
    class Heading {
        private static final String DESCRIPTION_KEY = "goo.ability.unstable.lurker.description";
        private static final String DESCRIPTION = "Lies in wait, then bursts when something walks over it.";
        private static final Map<String, String> LANG = Map.of(NAME_KEY, NAME, DESCRIPTION_KEY, DESCRIPTION);
        private static final String DRAFTED = """
                {"typeOrder": ["unstable"], "abilities": [{"id": "goo:unstable_lurker", "gooType": "unstable",
                  "displayName": "%s", "description": "%s", "icon": "", "badge": "world", "order": 10,
                  "draft": true, "leaves": []}]}
                """.formatted(NAME_KEY, DESCRIPTION_KEY);

        private static DrawnFrame drawn() {
            AbilitiesTab tab = new AbilitiesTab();
            tab.readModel(DRAFTED);
            ManagerActions actions = mock(ManagerActions.class);
            ItemIcons icons = mock(ItemIcons.class);
            AbilitiesPane pane = new AbilitiesPane(tab, actions, icons, new ExpressionBuilderPane(actions, icons,
                    List::of, List::of), key -> LANG.getOrDefault(key, key), ref -> Map.of(), java.util.Set::of);
            return draw(pane);
        }

        @Test
        void theIconBlitsAtSixteenPastTheRailAndTheNameStandsPastIt() {
            DrawnFrame frame = drawn();
            int iconLeft = VIEW.left() + ControlFlow.RAIL_ADVANCE;
            verify(frame.graphics).blit(any(), eq(AbilityIconTexture.of(
                    net.minecraft.resources.Identifier.parse("goo:unstable_lurker"), "")), eq(iconLeft), anyInt(),
                    anyFloat(), anyFloat(), eq(RowLayout.SPRITE_SIZE), eq(RowLayout.SPRITE_SIZE),
                    eq(RowLayout.SPRITE_SIZE), eq(RowLayout.SPRITE_SIZE), anyInt());
            assertTrue(frame.find(NAME).x() >= iconLeft + RowLayout.SPRITE_SIZE, frame.find(NAME).toString());
            assertTrue(frame.bold(NAME));
        }

        /**
         * A drag rail stands at the heading's left edge, and no Order box draws
         * (decision abilities-reorder-by-a-drag-rail-and-the-order-field-hides).
         */
        @Test
        void aRailStandsAtTheHeadingsLeftAndNoOrderBoxDraws() {
            DrawnFrame frame = drawn();
            int rowTop = frame.find(NAME).y() - RowLayout.nameTop(DrawnFrame.font(), 0);
            assertTrue(frame.fillsOf(Palette.RAIL).stream().anyMatch(fill -> fill[0] == VIEW.left()
                    && fill[1] == rowTop && fill[2] == VIEW.left() + ControlFlow.RAIL_WIDTH), "no rail at the heading");
            assertFalse(frame.texts().contains("Order"), frame.texts().toString());
        }

        @Test
        void aDraftReadsTheGoldBadgeRightOfTheName() {
            DrawnFrame frame = drawn();
            DrawnFrame.Text badge = frame.find("draft");
            assertEquals(Palette.GOLD, badge.color());
            assertTrue(badge.x() > frame.find(NAME).x());
        }

        @Test
        void theNameHoversTheAbilitysOwnDescription() {
            DrawnFrame frame = drawn();
            DrawnFrame.Text name = frame.find(NAME);
            String hoverKey = frame.targets.hoveredArea(name.x() + 1, name.y() + 1)
                    .map(ClickTargets.HoveredArea::hoverKey).orElseThrow(() -> new AssertionError("no hover"));
            assertEquals(java.util.Optional.of(DESCRIPTION),
                    AbilitiesPane.descriptionFor(hoverKey, key -> LANG.getOrDefault(key, key)));
        }
    }

    /**
     * Params read plain labels under a sub-heading naming their mechanism, each label hovering its description, and
     * lay in four equal columns across the ability's width, a field needing more taking whole cells, a line wrapping
     * when its four are spent (decisions ability-params-read-named-under-their-mechanism-and-hover-a-description,
     * ability-params-lay-in-a-four-column-grid-spanning-whole-cells).
     */
    @Nested
    class Grid {
        private static final String RADIUS_DESCRIPTION = "Blocks the nova reaches from the player.";
        private static final Map<String, String> LANG = Map.ofEntries(
                Map.entry("goo.ability.mechanism.nova", "Nova"), Map.entry("goo.ability.mechanism.grab", "Grab"),
                Map.entry("goo.ability.mechanism.cost", "Cost"),
                Map.entry("goo.ability.param.cost.cost", "Price"),
                Map.entry("goo.ability.param.nova.radius", "Radius"),
                Map.entry("goo.ability.param.nova.radius.description", RADIUS_DESCRIPTION),
                Map.entry("goo.ability.param.nova.amount", "Amount"), Map.entry("goo.ability.param.nova.damage", "Hurt"),
                Map.entry("goo.ability.param.nova.push", "Push"), Map.entry("goo.ability.param.nova.hold", "Hold"),
                Map.entry("goo.ability.param.grab.range", "Range"), Map.entry("goo.ability.param.grab.near", "Near"),
                Map.entry("goo.ability.param.grab.far", "Far"),
                Map.entry("goo.ability.param.grab.throw_speed", "Throw speed"));
        private static final String GRID_MODEL = """
                {"typeOrder": ["frost", "kinetic"], "abilities": [
                  {"id": "goo:frost_nova", "gooType": "frost", "displayName": "frost_nova", "draft": false,
                   "badge": "world", "order": 10, "leaves": [
                    {"path": "cost", "value": "3 * minecraft:cobblestone", "kind": "COST", "mechanism": "cost",
                     "field": "cost"},
                    %s, %s, %s, %s, %s]},
                  {"id": "goo:kinetic_grab", "gooType": "kinetic", "displayName": "kinetic_grab", "draft": false,
                   "badge": "channeled", "order": 10, "leaves": [%s, %s, %s, %s]}]}
                """.formatted(leaf("nova", "radius", 2), leaf("nova", "amount", 4), leaf("nova", "damage", 2),
                leaf("nova", "push", 3), leaf("nova", "hold", 300), leaf("grab", "range", 16), leaf("grab", "near", 2),
                leaf("grab", "far", 4), leaf("grab", "throw_speed", 1));
        private static final int WIDTH = VIEW.right() - ScrollBar.WIDTH - ScrollBar.GAP - VIEW.left();
        private static final int CELL = WIDTH / 4;

        private static String leaf(String mechanism, String field, int value) {
            return "{\"path\": \"behaviors/0/" + field + "\", \"value\": " + value + ", \"kind\": \"NUMBER\","
                    + " \"mechanism\": \"" + mechanism + "\", \"field\": \"" + field + "\"}";
        }

        private static DrawnFrame drawn() {
            AbilitiesTab tab = new AbilitiesTab();
            tab.readModel(GRID_MODEL);
            ManagerActions actions = mock(ManagerActions.class);
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
            return draw(new AbilitiesPane(tab, actions, icons, new ExpressionBuilderPane(actions, icons, List::of,
                    List::of), key -> LANG.getOrDefault(key, key), ref -> Map.of(), java.util.Set::of));
        }

        private static int cellLeft(int column) {
            return VIEW.left() + column * CELL + ControlFlow.INDENT;
        }

        @Test
        void paramsReadPlainLabelsUnderTheirMechanismAndNoPath() {
            DrawnFrame frame = drawn();
            assertTrue(frame.find("Radius").y() > frame.find("Nova").y(), "the heading stands below its params");
            assertTrue(frame.texts().contains("Range"), frame.texts().toString());
            assertTrue(frame.texts().stream().noneMatch(text -> text.contains("behaviors/")), frame.texts().toString());
        }

        @Test
        void fourParamsLineUpAtTheFourCellLeftsAndTheFifthWraps() {
            DrawnFrame frame = drawn();
            List<String> four = List.of("Radius", "Amount", "Hurt", "Push");
            for (int column = 0; column < four.size(); column++) {
                assertEquals(cellLeft(column), frame.find(four.get(column)).x(), four.get(column));
                assertEquals(frame.find("Radius").y(), frame.find(four.get(column)).y(), four.get(column));
            }
            assertEquals(cellLeft(0), frame.find("Hold").x());
            assertTrue(frame.find("Hold").y() > frame.find("Radius").y(), "the fifth param stands on the first line");
        }

        @Test
        void aSecondSectionsCellsStandAtTheSameLefts() {
            DrawnFrame frame = drawn();
            List<String> four = List.of("Range", "Near", "Far", "Throw speed");
            for (int column = 0; column < four.size(); column++) {
                assertEquals(cellLeft(column), frame.find(four.get(column)).x(), four.get(column));
            }
        }

        @Test
        void aCostWiderThanACellSpansWholeCells() {
            DrawnFrame frame = drawn();
            DrawnFrame.Text price = frame.find("Price");
            DrawnFrame.Text pencil = frame.drawn().stream().filter(text -> text.text().equals("✎")
                    && Math.abs(text.y() - price.y()) <= ButtonPainter.HEIGHT).findFirst()
                    .orElseThrow(() -> new AssertionError("no pencil on the cost's row"));
            int right = frame.buttonAround(pencil)[2];
            assertEquals(0, (right - VIEW.left()) % CELL, "the cost's box ends at " + right + ", off a cell boundary");
            assertTrue((right - VIEW.left()) / CELL > 1, "the cost's box spans " + (right - VIEW.left()) / CELL);
        }

        @Test
        void aLabelHoversItsDescription() {
            DrawnFrame frame = drawn();
            DrawnFrame.Text radius = frame.find("Radius");
            String hoverKey = frame.targets.hoveredArea(radius.x() + 1, radius.y() + 1)
                    .map(ClickTargets.HoveredArea::hoverKey).orElseThrow(() -> new AssertionError("no hover"));
            assertEquals(java.util.Optional.of(RADIUS_DESCRIPTION),
                    AbilitiesPane.descriptionFor(hoverKey, key -> LANG.getOrDefault(key, key)));
        }

        /**
         * Every hover a drawn Abilities frame registers reads the pencil's Rename, a goo type's or an item's name, or
         * an ability's or a param's description (decision values-manager-controls-carry-no-tooltip-but-rename-delete-and-names).
         */
        @Test
        void everyHoverIsAPencilANameOrADescription() {
            DrawnFrame frame = drawn();
            List<String> unexplained = frame.targets.hoverKeys().stream()
                    .filter(key -> BuilderOverlays.tooltipFor(key).isEmpty() && GooTypeIcons.tooltipFor(key).isEmpty()
                            && !key.equals(ItemIcons.hoverKey(key.substring(key.indexOf(':') + 1)))
                            && AbilitiesPane.descriptionFor(key, k -> k).isEmpty())
                    .toList();
            assertEquals(List.of(), unexplained);
            assertTrue(frame.targets.hoverKeys().stream().map(key -> AbilitiesPane.descriptionFor(key,
                    k -> LANG.getOrDefault(k, k))).flatMap(java.util.Optional::stream).toList()
                    .contains(RADIUS_DESCRIPTION));
        }
    }

    @Test
    void aCostItemHoldingTheTypeReadsNoLine() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("unstable", "250"))));
        assertTrue(frame.drawn().stream().noneMatch(text -> text.color() == Palette.DANGER), frame.texts().toString());
    }
}
