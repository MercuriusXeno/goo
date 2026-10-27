package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import com.mercuriusxeno.goo.workbench.Processes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        private final GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

        /**
         * Each prefab run an expression lands lays one hover area keyed to its prefab.
         */
        @Test
        void eachPrefabRunLaysOneHoverArea() {
            List<ExpressionLines.Run> runs = List.of(new ExpressionLines.Run(ExpressionLines.Kind.LINK, "waxed",
                    "waxed"), new ExpressionLines.Run(ExpressionLines.Kind.TEXT, " + ", null),
                    new ExpressionLines.Run(ExpressionLines.Kind.LINK, "base", "base"));
            ExpressionLines.draw(font(), runs, 0, 0, 400, new ExpressionLines.Painter(graphics, null, targets,
                    0, 0, (name, x, y, width) -> { }));
            assertEquals(1, prefabHovers(targets, "waxed"));
            assertEquals(1, prefabHovers(targets, "base"));
        }

        private MutatorControls controls(Processes.Form form) {
            ProcessesTab processes = mock(ProcessesTab.class);
            when(processes.formOf(anyString(), anyString())).thenReturn(form);
            Supplier<List<String>> none = List::of;
            return new MutatorControls(processes, new BuilderOverlays(mock(ManagerActions.class), null), none, none,
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

        /**
         * Each mutator an assignment's chain applies lays one hover area keyed to its name.
         */
        @Test
        void eachAppliedMutatorHoversItsName() {
            controls(new Processes.Assignment("logs", 1, 1, List.of("@waxed", "@smelt"))).draw(graphics, font(),
                    "_mutators", "#logs", new GroupsPane.Bounds(0, 0, 400, 400), 0, targets);
            assertEquals(1, prefabHovers(targets, "waxed"));
            assertEquals(1, prefabHovers(targets, "smelt"));
        }
    }

    @Nested
    class Tooltip {
        private final VariablesTab variables = new VariablesTab(new ManagerDraft());

        @BeforeEach
        void readEvaluated() {
            variables.readEvaluated(EVALUATED);
        }

        private List<TooltipLine> lines(String name) {
            return PrefabHovers.tooltipFor(variables, PrefabHovers.hoverKey(name)).orElseThrow();
        }

        private static TooltipLine text(String text) {
            return new TooltipLine.Text(Component.literal(text));
        }

        /**
         * A tree prefab reads its name then one goo amount line per type, carrying the type's id and its amount and
         * no type name (decision value-tooltips-draw-each-types-icon-before-its-amount).
         */
        @Test
        void treeReadsEachTypesAmount() {
            assertEquals(List.of(text("waxed"), new TooltipLine.GooAmount("weird", "3"),
                    new TooltipLine.GooAmount("jelly", "1/2")), lines("waxed"));
        }

        /**
         * A scalar reads its value, and an errored prefab its error, as text lines.
         */
        @Test
        void scalarReadsItsValueAndAnErrorItsText() {
            assertEquals(List.of(text("base"), text("8")), lines("base"));
            assertEquals(List.of(text("broken"), text("Unknown constant $missing")), lines("broken"));
        }
    }
}
