package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the words an assignment's chain of mutators draws and the kind line's dropdown, with mocked graphics and no
 * Screen (decisions expression-builder-composes-every-form-without-typing, exclusive-choice-sets-draw-as-dropdowns).
 */
class MutatorControlsTest {

    /**
     * A process's kind line draws one dropdown reading its kind; picking another kind writes that kind.
     */
    @Nested
    class KindDropdown {
        private static final String SECTION = "_post_conversions";
        private static final String KEY = "rust";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final BuilderOverlays overlays = new BuilderOverlays(mock(ManagerActions.class), null);
        private final MutatorControls controls = new MutatorControls(processes, overlays, mock(ManagerActions.class),
                () -> List.of("tree"), () -> List.of("metal"), () -> { });

        KindDropdown() {
            when(processes.formOf(anyString(), anyString())).thenReturn(new Processes.Additive("tree"));
            when(processes.processNames(SECTION)).thenReturn(List.of(KEY, "smelt"));
        }

        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            controls.drawKind(frame.graphics, DrawnFrame.font(), SECTION, KEY, 0, 0, frame.targets);
            overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
            return frame;
        }

        @Test
        void kindLineDrawsOneButtonReadingTheChosenKind() {
            List<String> texts = frame().texts();
            assertTrue(texts.contains(ControlFlow.dropdownLabel("additive")), texts.toString());
            assertFalse(texts.contains("convert") || texts.contains("stack"), texts.toString());
        }

        @Test
        void pickingConvertWritesAFormula() {
            frame().click(ControlFlow.dropdownLabel("additive"));
            DrawnFrame open = frame();
            assertTrue(open.texts().containsAll(List.of("convert", "additive", "stack")), open.texts().toString());
            open.click("convert");
            verify(processes).setForm(SECTION, KEY, new Processes.Formula("metal", 1, "0", 1, 1));
            assertFalse(overlays.listOpen());
        }

        private void pickTheChosenKind(Processes.Form form, String kind) {
            when(processes.formOf(SECTION, KEY)).thenReturn(form);
            frame().click(ControlFlow.dropdownLabel(kind));
            frame().click(kind);
            verify(processes, never()).setForm(anyString(), anyString(), any());
            assertFalse(overlays.listOpen());
        }

        @Test
        void pickingConvertOnAFormulaKeepsItsTerms() {
            pickTheChosenKind(new Processes.Formula("metal", 4, "yore", 2, 1), "convert");
        }

        @Test
        void pickingAdditiveOnAnAdditiveKeepsItsPrefab() {
            pickTheChosenKind(new Processes.Additive("oak"), "additive");
        }

        @Test
        void pickingStackOnAStackKeepsItsCountAndProcess() {
            pickTheChosenKind(new Processes.Stack(3, "smelt"), "stack");
        }
    }

    /**
     * Each form's sentence reads in symbols: a formula as two ratios, an additive as +, a stack as x[n]
     * (decision mutator-sentences-read-in-symbols).
     */
    @Nested
    class Sentences {
        private static final String SECTION = "_post_conversions";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final BuilderOverlays overlays = mock(BuilderOverlays.class);
        private final MutatorControls controls = new MutatorControls(processes, overlays, mock(ManagerActions.class),
                () -> List.of("oak"), () -> List.of("metal"), () -> { });

        Sentences() {
            when(processes.processNames(SECTION)).thenReturn(List.of("rust", "smelt"));
        }

        private DrawnFrame frame(Processes.Form form) {
            when(processes.formOf(SECTION, "rust")).thenReturn(form);
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            return frame;
        }

        private List<String> sentence(Processes.Form form) {
            return frame(form).texts();
        }

        @Test
        void formulaReadsAsTwoRatios() {
            assertEquals(List.of("1", ":", "4", "becomes", "2", ":", "3"),
                    sentence(new Processes.Formula("metal", 4, "yore", 2, 3)));
        }

        @Test
        void formulaToNothingReadsBecomesNothing() {
            assertEquals(List.of("1", ":", "4", "becomes", "nothing"),
                    sentence(new Processes.Formula("metal", 4, "0", 1, 1)));
        }

        @Test
        void additiveReadsPlusItsPrefab() {
            assertEquals(List.of("+", "oak"), sentence(new Processes.Additive("oak")));
        }

        @Test
        void stackReadsAppliesItsMutatorTimesItsCount() {
            assertEquals(List.of("applies", "smelt", "x", "2"), sentence(new Processes.Stack(2, "smelt")));
        }

        @Test
        void kindOptionsReadConvertAdditiveAndStack() {
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Additive("oak"));
            DrawnFrame kind = new DrawnFrame();
            controls.drawKind(kind.graphics, DrawnFrame.font(), SECTION, "rust", 0, 0, kind.targets);
            kind.click(ControlFlow.dropdownLabel("additive"));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<BuilderOverlays.Option>> options = ArgumentCaptor.forClass(List.class);
            verify(overlays).openOptions(any(), options.capture());
            assertEquals(List.of("convert", "additive", "stack"),
                    options.getValue().stream().map(BuilderOverlays.Option::label).toList());
        }

        // Clicks a number box's control and commits the typed text through the box it opened.
        private void commitTyped(DrawnFrame frame, String label, String typed) {
            frame.click(label);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Consumer<String>> commits = ArgumentCaptor.forClass(Consumer.class);
            verify(overlays).openNumber(any(), anyString(), commits.capture(), anyBoolean());
            commits.getValue().accept(typed);
        }

        @Test
        void sourceDivisorRefusesZero() {
            commitTyped(frame(new Processes.Formula("metal", 4, "yore", 2, 3)), "4", "0");
            verify(processes, never()).setFormulaDivisor(anyString(), anyString(), anyInt());
        }

        @Test
        void targetDivisorRefusesZero() {
            DrawnFrame frame = frame(new Processes.Formula("metal", 4, "yore", 2, 3));
            commitTyped(frame, "3", "0");
            verify(processes, never()).setFormulaRatio(anyString(), anyString(), anyInt(), anyInt());
        }

        @Test
        void targetDivisorTakesAWholeNumber() {
            commitTyped(frame(new Processes.Formula("metal", 4, "yore", 2, 3)), "3", "5");
            verify(processes).setFormulaRatio(SECTION, "rust", 2, 5);
        }
    }

    /**
     * An iterator line reads "[#group v] [n] : [m] [@a] [x] [@b] [x] [+]", its source one dropdown over every group
     * and itself (decision iterator-line-is-source-ratio-then-chain).
     */
    @Nested
    class IteratorLine {
        private static final String SECTION = "_post_conversions";
        private static final String MODEL = """
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "ores", "keySeparator": null, "kind": "VALUE", "text": "[\\"iron_ore\\"]",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "_post_conversions", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "smelt", "keySeparator": null, "kind": "VALUE", "text": "\\"metal / 2 -> 0\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "rust", "keySeparator": null, "kind": "VALUE", "text": "\\"@smelt\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null},
                     {"key": "#chained", "keySeparator": null, "kind": "VALUE",
                      "text": "\\"#ores * 2 / 3 @smelt @rust\\"", "entries": [], "leading": null, "trailing": null,
                      "closingGap": null},
                     {"key": "#bare", "keySeparator": null, "kind": "VALUE", "text": "\\"#ores\\"",
                      "entries": [], "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null}
                ]}
                """;
        private final ProcessesTab processes;
        private final BuilderOverlays overlays = new BuilderOverlays(mock(ManagerActions.class), null);
        private final MutatorControls controls;

        IteratorLine() {
            ManagerDraft draft = new ManagerDraft();
            draft.read(MODEL);
            processes = new ProcessesTab(draft);
            controls = new MutatorControls(processes, overlays, mock(ManagerActions.class), List::of,
                    () -> List.of("metal"), () -> { });
        }

        private DrawnFrame frame(String key) {
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, key, new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
            return frame;
        }

        @Test
        void sourcedIteratorReadsSourceRatioThenChain() {
            assertEquals(List.of(ControlFlow.dropdownLabel("#ores"), "2", ":", "3", "smelt", "x", "rust", "x", "+"),
                    frame("#chained").texts());
        }

        @Test
        void sourceDropdownListsEveryGroupAndItself() {
            frame("#chained").click(ControlFlow.dropdownLabel("#ores"));
            List<String> open = frame("#chained").texts();
            assertTrue(open.containsAll(List.of("#ores", "itself")), open.toString());
        }

        /**
         * Picking itself on an iterator at 1:1 with no mutators keeps it an assignment, so the line keeps its
         * controls rather than reading 'No form reads this line'.
         */
        @Test
        void pickingItselfAtOneToOneKeepsTheControls() {
            frame("#bare").click(ControlFlow.dropdownLabel("#ores"));
            frame("#bare").click("itself");
            assertEquals(new Processes.Assignment("", 1, 1, List.of()), processes.formOf(SECTION, "#bare"));
            assertEquals(List.of(ControlFlow.dropdownLabel("itself"), "1", ":", "1", "+"), frame("#bare").texts());
        }
    }

    /**
     * An iterator row draws its source and ratio on its first line and each chained mutator with its x on its own
     * line below, every chain line at one indented x, the [+] right of the last link's x; two rows with equal chains
     * take equal heights (decision each-chained-mutator-takes-its-own-indented-line).
     */
    @Nested
    class ChainLines {
        private static final String SECTION = "_post_conversions";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final MutatorControls controls = new MutatorControls(processes,
                new BuilderOverlays(mock(ManagerActions.class), null), mock(ManagerActions.class), () -> List.of("oak"),
                () -> List.of("metal"), () -> { });
        private int bottom;

        ChainLines() {
            when(processes.processNames(SECTION)).thenReturn(List.of("rust", "smelt", "melt"));
            when(processes.groupNames()).thenReturn(List.of("ores"));
        }

        private DrawnFrame frame(String key, List<String> chain) {
            when(processes.formOf(SECTION, key)).thenReturn(new Processes.Assignment("ores", 1, 1, chain));
            DrawnFrame frame = new DrawnFrame();
            bottom = controls.draw(frame.graphics, DrawnFrame.font(), SECTION, key,
                    new GroupsPane.Bounds(0, 0, 590, 390), 0, frame.targets);
            return frame;
        }

        @Test
        void eachChainedMutatorTakesItsOwnIndentedLine() {
            DrawnFrame frame = frame("#ores", List.of("@smelt", "@melt"));
            DrawnFrame.Text source = frame.find(ControlFlow.dropdownLabel("#ores"));
            DrawnFrame.Text smelt = frame.find("smelt");
            DrawnFrame.Text melt = frame.find("melt");
            assertEquals(smelt.x(), melt.x());
            assertTrue(smelt.x() > source.x(), smelt + " vs " + source);
            assertEquals(source.y() + ControlFlow.LINE, smelt.y(), smelt + " vs " + source);
            assertEquals(smelt.y() + ControlFlow.LINE, melt.y(), melt + " vs " + smelt);
            List<DrawnFrame.Text> removes = frame.drawn().stream().filter(text -> text.text().equals("x")).toList();
            assertEquals(2, removes.size(), frame.drawn().toString());
            assertEquals(smelt.y(), removes.get(0).y());
            assertTrue(removes.get(0).x() > smelt.x());
            assertEquals(melt.y(), removes.get(1).y());
            assertTrue(removes.get(1).x() > melt.x());
            DrawnFrame.Text plus = frame.find("+");
            assertEquals(melt.y(), plus.y());
            assertTrue(plus.x() > removes.get(1).x(), plus + " vs " + removes.get(1));
            int firstHeight = bottom;
            frame("#gems", List.of("@rust", "@melt"));
            assertEquals(firstHeight, bottom, "two-link chains take equal heights");
        }
    }

    /**
     * Every group, prefab and mutator name a row draws follows its ref on shift + left click, opens its dropdown on a
     * plain left click and underlines while shift is held
     * (decision diagnose-and-correct-mutator-references-that-do-not-link).
     */
    @Nested
    class ReferencesLink {
        private static final String SECTION = "_post_conversions";
        private final ProcessesTab processes = mock(ProcessesTab.class);
        private final ManagerActions actions = mock(ManagerActions.class);
        private final BuilderOverlays overlays = new BuilderOverlays(actions, null);
        private final MutatorControls controls = new MutatorControls(processes, overlays, actions,
                () -> List.of("oak"), () -> List.of("metal"), () -> { });

        ReferencesLink() {
            when(processes.processNames(SECTION)).thenReturn(List.of("rust", "smelt"));
            when(processes.groupNames()).thenReturn(List.of("ores"));
        }

        private DrawnFrame frame(String key, Processes.Form form) {
            when(processes.formOf(SECTION, key)).thenReturn(form);
            DrawnFrame frame = new DrawnFrame();
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, key, new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            return frame;
        }

        private DrawnFrame iterator() {
            return frame("#ores", new Processes.Assignment("ores", 1, 1, List.of("@smelt")));
        }

        @Test
        void shiftClickingAnIteratorsGroupOpensTheGroup() {
            iterator().shiftClick(ControlFlow.dropdownLabel("#ores"));
            verify(actions).openGroup("ores");
        }

        @Test
        void shiftClickingAnIteratorsChainedMutatorOpensTheMutator() {
            iterator().shiftClick("smelt");
            verify(actions).openMutator("smelt");
        }

        @Test
        void shiftClickingAnAdditivesPrefabOpensThePrefab() {
            frame("rust", new Processes.Additive("oak")).shiftClick("oak");
            verify(actions).openVariable("oak");
        }

        @Test
        void shiftClickingAStacksMutatorOpensTheMutator() {
            frame("rust", new Processes.Stack(2, "smelt")).shiftClick("smelt");
            verify(actions).openMutator("smelt");
        }

        @Test
        void plainClickingAStacksMutatorOpensItsDropdown() {
            frame("rust", new Processes.Stack(2, "smelt")).click("smelt");
            assertTrue(overlays.listOpen());
            verify(actions, never()).openMutator(anyString());
        }

        @Test
        void holdingShiftUnderlinesEveryReference() {
            DrawnFrame stack = new DrawnFrame().holdingShift();
            when(processes.formOf(SECTION, "rust")).thenReturn(new Processes.Stack(2, "smelt"));
            controls.draw(stack.graphics, DrawnFrame.font(), SECTION, "rust", new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    stack.targets);
            assertTrue(stack.underlined("smelt"));
        }
    }
}
