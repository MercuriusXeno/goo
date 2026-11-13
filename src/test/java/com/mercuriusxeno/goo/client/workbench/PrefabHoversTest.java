package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.mercuriusxeno.goo.workbench.Processes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that every prefab reference the manager draws hovers its value, and what that tooltip reads, with mocked
 * graphics and no Screen (decision every-prefab-reference-hovers-its-value).
 */
class PrefabHoversTest {
    private static final String EVALUATED = """
            {"constants": [
              {"name": "waxed", "kind": "tree", "tree": {"weird": "3", "jelly": "1/2"}, "value": null, "error": null,
               "usedBy": [], "readers": []},
              {"name": "base", "kind": "scalar", "value": "8", "error": null, "usedBy": [], "readers": []},
              {"name": "broken", "kind": "scalar", "value": null, "error": "Unknown constant $missing",
               "usedBy": [], "readers": []}
            ]}
            """;

    private static Font font() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    private static long prefabHovers(ClickTargets targets, String name) {
        return targets.hoverKeys().stream().filter(PrefabHovers.hoverKey(name)::equals).count();
    }

    @Nested
    class Areas {
        private final ClickTargets targets = new ClickTargets();
        // A drawn frame's graphics carry the pose the rows' lettered words scale through.
        private final GuiGraphicsExtractor graphics = new DrawnFrame().graphics;

        /**
         * Each prefab run an expression lands lays one hover area keyed to its prefab.
         */
        @Test
        void eachPrefabRunLaysOneHoverArea() {
            List<ExpressionLines.Run> runs = List.of(new ExpressionLines.Run(ExpressionLines.Kind.LINK, "waxed",
                    "waxed"), new ExpressionLines.Run(ExpressionLines.Kind.TEXT, " + ", null),
                    new ExpressionLines.Run(ExpressionLines.Kind.LINK, "base", "base"));
            ExpressionLines.draw(Lettering.full(font()), runs, 0, 0, 400, new ExpressionLines.Painter(graphics, null,
                    targets,
                    0, 0, (name, x, y, width) -> { }));
            assertEquals(1, prefabHovers(targets, "waxed"));
            assertEquals(1, prefabHovers(targets, "base"));
        }

        private MutatorControls controls(Processes.Form form) {
            ProcessesTab processes = mock(ProcessesTab.class);
            when(processes.formOf(anyString(), anyString())).thenReturn(form);
            Supplier<List<String>> none = List::of;
            return new MutatorControls(processes, new BuilderOverlays(mock(ManagerActions.class), null),
                    mock(ManagerActions.class), none, none,
                    () -> { });
        }

        /**
         * An additive's constant name lays one hover area.
         */
        @Test
        void additiveConstantHoversItsPrefab() {
            controls(new Processes.Additive("waxed")).draw(graphics, font(), "_mutators", "waxing",
                    new GroupsPane.Bounds(0, 0, 400, 400), 0, targets);
            assertEquals(1, prefabHovers(targets, "waxed"));
        }
    }

    @Nested
    class Tooltip {
        private static final String MODEL = """
                {"path": "base_values.json", "members": [
                  {"key": "_constants", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "base", "keySeparator": null, "kind": "VALUE", "text": "6", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null},
                     {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "6", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null},
                     {"key": "double", "keySeparator": null, "kind": "VALUE", "text": "\\"$base * 2\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "x", "keySeparator": null, "kind": "VALUE", "text": "{\\"leaf\\": \\"$stick\\"}",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "blend", "keySeparator": null, "kind": "VALUE",
                      "text": "{\\"metal\\": \\"1/2\\", \\"weird\\": \\"3\\", \\"pulse\\": \\"3\\"}",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "broken", "keySeparator": null, "kind": "VALUE", "text": "\\"$missing + 1\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "coral", "keySeparator": null, "kind": "VALUE",
                      "text": "{\\"zoo\\": \\"2 $base\\", \\"deep\\": \\"2 $base\\"}",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "dead_coral", "keySeparator": null, "kind": "VALUE",
                      "text": "{\\"deep\\": \\"$coral.deep\\", \\"nether\\": \\"$coral.zoo\\"}",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}
                ]}
                """;
        private static final String VALUES = """
                {"constants": [
                  {"name": "base", "kind": "scalar", "value": "6", "error": null, "usedBy": ["oak"], "readers": []},
                  {"name": "stick", "kind": "scalar", "value": "6", "error": null, "usedBy": [], "readers": []},
                  {"name": "double", "kind": "scalar", "value": "12", "error": null, "usedBy": [], "readers": []},
                  {"name": "x", "kind": "tree", "tree": {"leaf": "6"}, "value": null, "error": null,
                   "usedBy": [], "readers": []},
                  {"name": "blend", "kind": "tree", "tree": {"metal": "1/2", "weird": "3", "pulse": "3"},
                   "value": null, "error": null, "usedBy": [], "readers": []},
                  {"name": "broken", "kind": "scalar", "value": null, "error": "Unknown constant $missing",
                   "usedBy": [], "readers": []},
                  {"name": "coral", "kind": "tree", "tree": {"zoo": "12", "deep": "12"}, "value": null,
                   "error": null, "usedBy": [], "readers": []},
                  {"name": "dead_coral", "kind": "tree", "tree": {"deep": "12", "nether": "12"}, "value": null,
                   "error": null, "usedBy": [], "readers": []}
                ]}
                """;
        private final VariablesTab variables;

        Tooltip() {
            ManagerDraft draft = new ManagerDraft();
            draft.read(MODEL);
            variables = new VariablesTab(draft);
        }

        @BeforeEach
        void readEvaluated() {
            variables.readEvaluated(VALUES);
        }

        private List<TooltipLine> lines(String name) {
            return PrefabHovers.tooltipFor(variables, PrefabHovers.hoverKey(name)).orElseThrow();
        }

        private static TooltipLine text(String text) {
            return new TooltipLine.Text(Component.literal(text));
        }

        // Each line as text: a grid as its cells, a type icon as {type}, a suffix flush against the run before it; an
        // amount grid as its cells, each {type} then its amount, inside its parens where it reads a reduction.
        private List<Object> read(String name) {
            return lines(name).stream().map(line -> switch (line) {
                case TooltipLine.RunsGrid grid -> (Object) grid.cells().stream().map(Tooltip::cellText).toList();
                case TooltipLine.GooAmounts grid -> (Object) amountCells(grid);
                default -> (Object) line;
            }).toList();
        }

        private static List<String> amountCells(TooltipLine.GooAmounts grid) {
            List<String> cells = new ArrayList<>();
            if (grid.reduced()) {
                cells.add(GooAmountGrid.OPEN.text());
            }
            grid.amounts().forEach(amount -> cells.add(amount.type().isEmpty() ? amount.amount()
                    : "{" + amount.type() + "} " + amount.amount()));
            if (grid.reduced()) {
                cells.add(GooAmountGrid.CLOSE.text());
            }
            return cells;
        }

        /**
         * A reduction's opening and closing parens are symbol runs (decision tooltip-reduction-parens-draw-one-color).
         */
        @Test
        void reductionParensAreSymbols() {
            assertTrue(ExpressionLines.drawsAsSymbol(GooAmountGrid.OPEN.kind())
                    && ExpressionLines.drawsAsSymbol(GooAmountGrid.CLOSE.kind()));
        }

        private static String cellText(List<ExpressionLines.Run> cell) {
            StringBuilder text = new StringBuilder();
            for (ExpressionLines.Run run : cell) {
                if (!text.isEmpty() && !ExpressionLines.placesFlush(run.kind())) {
                    text.append(' ');
                }
                text.append(run.kind() == ExpressionLines.Kind.TYPE ? "{" + run.ref() + "}" : run.text());
            }
            return text.toString();
        }

        /**
         * A typed prefab reads its recipe, each type's cell {type}: its terms with a member read as the prefab's own,
         * then its reduction in parentheses, each type's cell {type}: its amount, the closing parenthesis on the last
         * cell, both laid as grids (decisions read-view-reads-type-equals-possessive-terms,
         * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
         */
        @Test
        void typedPrefabReadsItsRecipeThenItsReduction() {
            assertEquals(List.of(List.of("{deep} : $coral's {deep}", "{nether} : $coral's {zoo}"),
                    List.of("(", "{deep} 12", "{nether} 12", ")")), read("dead_coral"));
        }

        /**
         * A typed prefab over a scalar prefab reads {type}: $prefab, its amount in the reduction.
         */
        @Test
        void typedPrefabOverAScalarReadsItsAmountInTheReduction() {
            assertEquals(List.of(List.of("{leaf} : $stick"), List.of("(", "{leaf} 6", ")")), read("x"));
        }

        /**
         * A scalar prefab reads its expression, then its reduced value in parentheses where it differs from it.
         */
        @Test
        void scalarReadsItsExpressionThenItsReducedValue() {
            assertEquals(List.of(List.of("6")), read("base"));
            assertEquals(List.of(List.of("$base x 2"), List.of("(", "12", ")")), read("double"));
        }

        /**
         * A typed prefab's cells list its types highest amount first, ties by type key, and a reduction reading as
         * the recipe does stands out (decision value-lists-order-highest-first-ties-by-type-key).
         */
        @Test
        void typedPrefabListsItsTypesHighestFirstTiesByTypeKey() {
            assertEquals(List.of(List.of("{pulse} : 3", "{weird} : 3", "{metal} : 1 / 2")), read("blend"));
        }

        /**
         * An errored prefab reads its recipe, then its error.
         */
        @Test
        void erroredPrefabReadsItsRecipeThenItsError() {
            assertEquals(List.of(List.of("$missing + 1"), text("Unknown constant $missing")), read("broken"));
        }
    }
}
