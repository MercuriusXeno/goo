package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the expression builder through its hover hints' static seams and through frames it draws onto mocked graphics
 * with a mocked font, no Screen.
 */
class ExpressionBuilderPaneTest {
    private static final List<BuilderGrammar.Prefab> PREFABS = List.of(new BuilderGrammar.Prefab("base", false),
            new BuilderGrammar.Prefab("waxed", true), new BuilderGrammar.Prefab("edited", true));
    private static final GroupsPane.Bounds AREA = new GroupsPane.Bounds(0, 0, 590, 390);

    /**
     * An open builder, each frame it draws and each string it writes.
     */
    static final class Builder {
        final Font font = DrawnFrame.font();
        final List<String> written = new ArrayList<>();
        final ExpressionBuilderPane pane;

        Builder(List<BuilderGrammar.Prefab> prefabs) {
            ManagerActions actions = mock(ManagerActions.class);
            when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
            ItemIcons icons = mock(ItemIcons.class);
            when(icons.nameOf(any())).thenAnswer(call -> call.getArgument(0));
            pane = new ExpressionBuilderPane(actions, icons, List::of, () -> prefabs);
            pane.init(font);
            pane.show(true);
        }

        static Builder item(String shown) {
            Builder builder = new Builder(PREFABS);
            builder.pane.open(shown, builder.written::add, () -> { });
            return builder;
        }

        static Builder prefab(List<BuilderGrammar.Prefab> prefabs, String shown) {
            Builder builder = new Builder(prefabs);
            builder.pane.openPrefab("edited", shown, builder.written::add, () -> { });
            return builder;
        }

        DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, font, AREA, 0, frame.targets);
            pane.drawOverlays(frame.graphics, font, frame.targets);
            return frame;
        }
    }

    private static String dropdown(String label) {
        return ControlFlow.dropdownLabel(label);
    }

    /**
     * The goo kind choice's hover hint, per tab (decision items-kind-hint-says-worth-based-on-other-items-prefabs).
     */
    @Nested
    class GooKindHint {

        @Test
        void itemsKindHintReadsWorthBasedOnOtherItemsPrefabs() {
            assertEquals("worth based on other items/prefabs", ExpressionBuilderPane.gooKindHint(true));
        }

        @Test
        void prefabsKindHintReadsAsItDid() {
            assertEquals("Worth what the per-type prefabs above this one are worth, scaled and summed",
                    ExpressionBuilderPane.gooKindHint(false));
        }
    }

    /**
     * The done control's hover hint, read from the opened text against the written text (decision
     * done-hint-reads-no-unsaved-changes-or-changes-already-saved).
     */
    @Nested
    class DoneHint {

        @Test
        void unchangedValueReadsNoUnsavedChanges() {
            assertEquals("No unsaved changes", ExpressionBuilderPane.doneHint("4 * minecraft:stone",
                    "4 * minecraft:stone"));
        }

        @Test
        void changedValueReadsChangesAlreadySaved() {
            assertEquals("Changes already saved to the draft", ExpressionBuilderPane.doneHint("4 * minecraft:stone",
                    "2 * minecraft:stone"));
        }
    }

    /**
     * The Kind line draws one dropdown reading the chosen kind, its options listed under it on click (decision
     * exclusive-choice-sets-draw-as-dropdowns).
     */
    @Nested
    class KindDropdown {

        @Test
        void kindLineDrawsOneButtonReadingTheChosenKind() {
            List<String> texts = Builder.item("anvil").frame().texts();
            assertTrue(texts.contains(dropdown("from other items")), texts.toString());
            assertFalse(texts.contains("per goo type") || texts.contains(dropdown("per goo type")), texts.toString());
        }

        @Test
        void clickingTheKindListsEveryKindUnderIt() {
            Builder builder = Builder.item("anvil");
            DrawnFrame closed = builder.frame();
            DrawnFrame.Text button = closed.find(dropdown("from other items"));
            closed.click(dropdown("from other items"));
            DrawnFrame open = builder.frame();
            DrawnFrame.Text perType = open.find("per goo type");
            DrawnFrame.Text items = open.find("from other items");
            assertTrue(perType.y() > button.y() && items.y() > perType.y(), open.drawn().toString());
            assertTrue(builder.pane.listOpen());
        }

        @Test
        void pickingPerGooTypeSwitchesTheBuilderPerType() {
            Builder builder = Builder.item("anvil");
            builder.frame().click(dropdown("from other items"));
            builder.frame().click("per goo type");
            DrawnFrame after = builder.frame();
            assertTrue(after.texts().contains("Pick the first goo type:"), after.texts().toString());
            assertTrue(after.texts().contains(dropdown("per goo type")), after.texts().toString());
            assertFalse(builder.pane.listOpen());
        }

        @Test
        void clickOutsideTheListClosesItAndChangesNothing() {
            Builder builder = Builder.item("anvil");
            builder.frame().click(dropdown("from other items"));
            DrawnFrame open = builder.frame();
            assertTrue(open.targets.click(DrawnFrame.SCREEN_WIDTH - 1, DrawnFrame.SCREEN_HEIGHT - 1));
            assertFalse(builder.pane.listOpen());
            assertEquals(List.of(), builder.written);
            assertFalse(builder.frame().texts().contains("per goo type"));
        }
    }

    /**
     * The amount-from dropdown names what the amount comes from (decisions amount-modes-name-what-the-amount-comes-from,
     * exclusive-choice-sets-draw-as-dropdowns).
     */
    @Nested
    class AmountDropdown {
        private static final String ROW = "{\"metal\": \"5\"}";

        private static List<String> openModes(Builder builder) {
            builder.frame().click(dropdown("a number"));
            DrawnFrame open = builder.frame();
            List<String> texts = open.texts();
            int first = texts.lastIndexOf(dropdown("a number")) + 1;
            return texts.subList(first, texts.size()).stream().filter(text -> text.startsWith("a ")).toList();
        }

        @Test
        void prefabsRowListsTheThreeModesInOrder() {
            assertEquals(List.of("a number", "a number prefab", "a goo type of a prefab"),
                    openModes(Builder.prefab(PREFABS, ROW)));
        }

        @Test
        void itemsRowReadsTheItemWording() {
            assertEquals(List.of("a number", "a number prefab", "a goo type of an item or prefab"),
                    openModes(Builder.item(ROW)));
        }

        @Test
        void draftWithNoScalarPrefabOmitsTheNumberPrefabMode() {
            List<BuilderGrammar.Prefab> perTypeOnly = List.of(new BuilderGrammar.Prefab("waxed", true),
                    new BuilderGrammar.Prefab("edited", true));
            assertEquals(List.of("a number", "a goo type of a prefab"), openModes(Builder.prefab(perTypeOnly, ROW)));
        }

        @Test
        void amountLineDrawsNoSiblingMode() {
            List<String> texts = Builder.prefab(PREFABS, ROW).frame().texts();
            assertFalse(texts.contains("a number prefab") || texts.contains("prefab"), texts.toString());
        }

        @Test
        void pickingNumberPrefabOpensThePrefabListUnderTheSameButton() {
            Builder builder = Builder.prefab(PREFABS, ROW);
            DrawnFrame closed = builder.frame();
            DrawnFrame.Text button = closed.find(dropdown("a number"));
            closed.click(dropdown("a number"));
            builder.frame().click("a number prefab");
            DrawnFrame after = builder.frame();
            assertTrue(builder.pane.listOpen());
            DrawnFrame.Text prefabRow = after.find("base");
            assertEquals(button.x() - ButtonPainter.PADDING + 20, prefabRow.x(), after.drawn().toString());
            assertTrue(prefabRow.y() > button.y());
        }

        @Test
        void pickingNumberLeavesNoOverlayAndTheOperandANumber() {
            Builder builder = Builder.prefab(PREFABS, "{\"metal\": \"$base\"}");
            builder.frame().click(dropdown("a number prefab"));
            builder.frame().click("a number");
            assertFalse(builder.pane.listOpen());
            assertTrue(builder.frame().texts().contains(dropdown("a number")));
            assertEquals(1, builder.written.size(), builder.written.toString());
            assertFalse(builder.written.getFirst().contains("$base"), builder.written.toString());
        }
    }
}
