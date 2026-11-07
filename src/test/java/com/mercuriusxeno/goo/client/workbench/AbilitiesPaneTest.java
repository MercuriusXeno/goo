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
 * Tests the Abilities tab's pane through drawn frames of a recorded model, with no Screen: each ability's name line
 * reads its price fields alone, and a cost naming an item holding none of the ability's goo type reads a red line
 * under its field (decisions
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
                {"path": "behaviors/1/range", "value": 16, "kind": "NUMBER"},
                {"path": "cost_per_block", "value": 100, "kind": "NUMBER"}
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
    void theNameLineReadsThePriceFieldsAndNoOtherParam() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("unstable", "250"))));
        DrawnFrame.Text name = frame.find(NAME);
        for (String label : List.of("cost", "cost_per_block")) {
            assertTrue(frame.find(label).x() > name.x(), label + " stands left of the name");
        }
        for (String absent : List.of("health / 2", "16", "amount", "range")) {
            assertFalse(frame.texts().contains(absent), absent + " draws: " + frame.texts());
        }
    }

    /** A cost drawing an item's icon stands taller than a number's box, and its label centers on it all the same. */
    @Test
    void aCostsLabelCentersOnItsTallerBox() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("unstable", "250"))));
        DrawnFrame.Text label = frame.find("cost");
        int[] box = frame.buttonAround(frame.find("3"));
        assertEquals(Lettering.builder(DrawnFrame.font()).centeredTop(box[1], box[3] - box[1]), label.y(),
                label + " stands off the center of its box " + java.util.Arrays.toString(box));
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
        private static final String RANGE = "100";
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
            return tab.abilities().getFirst().leaves().get(3);
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
            int iconLeft = VIEW.left() + ControlFlow.INDENT + ControlFlow.RAIL_ADVANCE;
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
        /**
         * The heading's rail stands at the ability's indent under its type's heading
         * (decision abilities-tab-lists-every-numeric-param-as-expression-boxes).
         */
        @Test
        void aRailStandsAtTheIndentedHeadingsLeftAndNoOrderBoxDraws() {
            DrawnFrame frame = drawn();
            int rowTop = frame.find(NAME).y() - RowLayout.nameTop(DrawnFrame.font(), 0);
            int left = VIEW.left() + ControlFlow.INDENT;
            assertTrue(frame.fillsOf(Palette.RAIL).stream().anyMatch(fill -> fill[0] == left
                    && fill[1] == rowTop && fill[2] == left + ControlFlow.RAIL_WIDTH), "no rail at the heading");
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
     * The tab is for costs: an ability's price fields alone draw, on its name's line in four equal columns right of
     * the left third its name reserves, each label at the builder's smaller lettering hovering its description and no
     * mechanism heading drawn, a field past the last column wrapping to the first (decisions
     * abilities-tab-lists-every-numeric-param-as-expression-boxes,
     * ability-params-read-named-under-their-mechanism-and-hover-a-description,
     * ability-params-lay-in-a-four-column-grid-spanning-whole-cells).
     */
    @Nested
    class Prices {
        private static final String PER_BLOCK_DESCRIPTION = "Goo each block of the trip adds.";
        private static final Map<String, String> LANG = Map.ofEntries(
                Map.entry("goo.ability.mechanism.cost_per_block", "Price per block"),
                Map.entry("goo.ability.mechanism.particles", "Particles"),
                Map.entry("goo.ability.mechanism.outsider", "Outsider"),
                Map.entry("goo.ability.param.cost.cost", "Cost"),
                Map.entry("goo.ability.param.cost_per_block.cost_per_block", "Cost per block"),
                Map.entry("goo.ability.param.cost_per_block.cost_per_block.description", PER_BLOCK_DESCRIPTION),
                Map.entry("goo.ability.param.wall_cost.wall_cost", "Wall surcharge"),
                Map.entry("goo.ability.param.node_percent.node_percent", "Oculus percent"),
                Map.entry("goo.ability.param.particles.count", "Count"),
                Map.entry("goo.ability.param.outsider.charge", "Charge"),
                Map.entry("goo.ability.param.outsider.enter", "Entry cost"),
                Map.entry("goo.ability.param.outsider.cell_cost", "Cell cost"),
                Map.entry("goo.ability.param.outsider.distance_cost", "Distance cost"));
        private static final String BLINK = "ender_blink";
        private static final String PRICES_MODEL = """
                {"typeOrder": ["ender", "void"], "abilities": [
                  {"id": "goo:ender_blink", "gooType": "ender", "displayName": "ender_blink", "draft": false,
                   "badge": "self", "order": 10, "leaves": [
                    {"path": "cost", "value": "200", "kind": "COST", "mechanism": "cost", "field": "cost"},
                    %s, %s, %s, %s]},
                  {"id": "goo:void_outsider", "gooType": "void", "displayName": "void_outsider", "draft": false,
                   "badge": "channeled", "order": 10, "leaves": [%s, %s, %s, %s]}]}
                """.formatted(leaf("cost_per_block", "cost_per_block", "cost_per_block", 100),
                leaf("wall_cost", "wall_cost", "wall_cost", 400), leaf("node_percent", "node_percent", "node_percent",
                        10), leaf("behaviors/0/count", "particles", "count", 32),
                leaf("behaviors/0/charge", "outsider", "charge", 20), leaf("behaviors/0/enter", "outsider", "enter", 6),
                leaf("behaviors/0/cell_cost", "outsider", "cell_cost", 10),
                leaf("behaviors/0/distance_cost", "outsider", "distance_cost", 1));

        private static String leaf(String path, String mechanism, String field, int value) {
            return "{\"path\": \"" + path + "\", \"value\": " + value + ", \"kind\": \"NUMBER\","
                    + " \"mechanism\": \"" + mechanism + "\", \"field\": \"" + field + "\"}";
        }

        private static DrawnFrame drawn(GroupsPane.Bounds view) {
            AbilitiesTab tab = new AbilitiesTab();
            tab.readModel(PRICES_MODEL);
            ManagerActions actions = mock(ManagerActions.class);
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(anyString())).thenAnswer(call -> call.getArgument(0));
            AbilitiesPane pane = new AbilitiesPane(tab, actions, icons, new ExpressionBuilderPane(actions, icons,
                    List::of, List::of), key -> LANG.getOrDefault(key, key), ref -> Map.of(), java.util.Set::of);
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), view, frame.targets);
            return frame;
        }

        // The name's sprite row, as its heading centers the name in it.
        private static int rowTopOf(DrawnFrame.Text name) {
            return name.y() - RowLayout.nameTop(DrawnFrame.font(), 0);
        }

        @Test
        void onlyThePriceFieldsDrawAndNoMechanismHeading() {
            DrawnFrame frame = drawn(VIEW);
            assertTrue(frame.texts().containsAll(List.of("Cost", "Cost per block", "Wall surcharge", "Oculus percent",
                    "Entry cost", "Cell cost", "Distance cost")), frame.texts().toString());
            for (String absent : List.of("Count", "Charge", "Price per block", "Particles", "Outsider", "32", "20")) {
                assertFalse(frame.texts().contains(absent), absent + " draws: " + frame.texts());
            }
            assertTrue(frame.texts().stream().noneMatch(text -> text.contains("behaviors/")), frame.texts().toString());
        }

        @Test
        void priceLabelsDrawAtTheBuildersSmallerLettering() {
            DrawnFrame frame = drawn(VIEW);
            for (String label : List.of("Cost", "Cost per block", "Entry cost")) {
                assertEquals(ControlFlow.BUILDER_SCALE, frame.placed(label).scale(), label);
            }
        }

        /** The operator's ruling of 2026-11-07: the labels read in the yellow the mechanism headings drew in. */
        @Test
        void aLabelReadsYellowCenteredOnItsValuesBox() {
            DrawnFrame frame = drawn(VIEW);
            DrawnFrame.Text label = frame.find("Cost per block");
            int[] box = frame.buttonAround(frame.find("100"));
            assertEquals(Palette.FILE_HEADING, label.color());
            assertEquals(Lettering.builder(DrawnFrame.font()).centeredTop(box[1], box[3] - box[1]), label.y(),
                    label + " stands off the center of its box " + java.util.Arrays.toString(box));
        }

        // The section's width, the scroll bar's room taken from the view.
        private static int widthOf(GroupsPane.Bounds view) {
            return view.right() - ScrollBar.WIDTH - ScrollBar.GAP - view.left();
        }

        // Where the price columns start: the left third the names reserve.
        private static int priceLeft(GroupsPane.Bounds view) {
            return view.left() + widthOf(view) / 3;
        }

        private static int columnLeft(GroupsPane.Bounds view, int column) {
            int cell = (view.left() + widthOf(view) - priceLeft(view)) / 4;
            return priceLeft(view) + column * cell;
        }

        @Test
        void theFirstPriceStartsAtTheLeftThirdOnTheNamesLine() {
            DrawnFrame frame = drawn(VIEW);
            DrawnFrame.Text name = frame.find(BLINK);
            DrawnFrame.Text cost = frame.find("Cost");
            int rowTop = rowTopOf(name);
            assertEquals(priceLeft(VIEW), cost.x(), cost + " starts off the left third");
            assertTrue(cost.y() >= rowTop && cost.y() < rowTop + RowLayout.SPRITE_ROW_HEIGHT,
                    cost + " stands off the name's line at " + rowTop);
        }

        @Test
        void everyAbilitysPricesStandAtTheSameFourColumnLefts() {
            GroupsPane.Bounds wide = new GroupsPane.Bounds(0, 0, 900, 400);
            DrawnFrame frame = drawn(wide);
            List<List<String>> abilities = List.of(List.of("Cost", "Cost per block", "Wall surcharge",
                    "Oculus percent"), List.of("Entry cost", "Cell cost", "Distance cost"));
            for (List<String> labels : abilities) {
                for (int index = 0; index < labels.size(); index++) {
                    assertEquals(columnLeft(wide, index), frame.find(labels.get(index)).x(), labels.get(index));
                }
            }
        }

        @Test
        void aFieldWiderThanAColumnSpansWholeColumns() {
            GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 300, 400);
            DrawnFrame frame = drawn(narrow);
            int cell = columnLeft(narrow, 1) - columnLeft(narrow, 0);
            DrawnFrame.Text value = frame.find("100");
            int right = frame.buttonAround(frame.drawn().stream().filter(text -> text.text().equals("✎")
                    && text.x() > value.x() && Math.abs(text.y() - value.y()) <= 1).findFirst()
                    .orElseThrow(() -> new AssertionError("no pencil beside 100")))[2];
            assertEquals(0, (right - priceLeft(narrow)) % cell, "the box ends at " + right + ", off a column's edge");
            assertTrue((right - priceLeft(narrow)) / cell > 1, "the field spans one column");
        }

        @Test
        void aFieldPastTheLastColumnWrapsToTheFirst() {
            GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 300, 400);
            DrawnFrame frame = drawn(narrow);
            DrawnFrame.Text last = frame.find("Oculus percent");
            assertEquals(priceLeft(narrow), last.x(), last + " wrapped off the first column");
            assertTrue(last.y() >= rowTopOf(frame.find(BLINK)) + RowLayout.SPRITE_ROW_HEIGHT,
                    last + " stands on the name's line");
        }
        @Test
        void aLabelHoversItsDescription() {
            DrawnFrame frame = drawn(VIEW);
            DrawnFrame.Text perBlock = frame.find("Cost per block");
            String hoverKey = frame.targets.hoveredArea(perBlock.x() + 1, perBlock.y() + 1)
                    .map(ClickTargets.HoveredArea::hoverKey).orElseThrow(() -> new AssertionError("no hover"));
            assertEquals(java.util.Optional.of(PER_BLOCK_DESCRIPTION),
                    AbilitiesPane.descriptionFor(hoverKey, key -> LANG.getOrDefault(key, key)));
        }

        /**
         * Every hover a drawn Abilities frame registers reads the pencil's Rename, a goo type's or an item's name, or
         * an ability's or a param's description (decision values-manager-controls-carry-no-tooltip-but-rename-delete-and-names).
         */
        @Test
        void everyHoverIsAPencilANameOrADescription() {
            DrawnFrame frame = drawn(VIEW);
            List<String> unexplained = frame.targets.hoverKeys().stream()
                    .filter(key -> BuilderOverlays.tooltipFor(key).isEmpty() && GooTypeIcons.tooltipFor(key).isEmpty()
                            && !key.equals(ItemIcons.hoverKey(key.substring(key.indexOf(':') + 1)))
                            && AbilitiesPane.descriptionFor(key, k -> k).isEmpty())
                    .toList();
            assertEquals(List.of(), unexplained);
            assertTrue(frame.targets.hoverKeys().stream().map(key -> AbilitiesPane.descriptionFor(key,
                    k -> LANG.getOrDefault(k, k))).flatMap(java.util.Optional::stream).toList()
                    .contains(PER_BLOCK_DESCRIPTION));
        }
    }
    @Test
    void aCostItemHoldingTheTypeReadsNoLine() {
        DrawnFrame frame = draw(pane(Map.of("minecraft:cobblestone", Map.of("unstable", "250"))));
        assertTrue(frame.drawn().stream().noneMatch(text -> text.color() == Palette.DANGER), frame.texts().toString());
    }
}
