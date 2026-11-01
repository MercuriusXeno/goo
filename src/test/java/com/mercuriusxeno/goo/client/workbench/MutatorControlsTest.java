package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void emptyChainLeadsWithNoWords() {
        assertEquals(List.of(), MutatorControls.chainLead(List.of()));
    }

    @Test
    void chainHoldingAMutatorLeadsWithThenApplies() {
        assertEquals(List.of("then applies"), MutatorControls.chainLead(List.of("@smelt")));
    }

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
            controls.draw(frame.graphics, DrawnFrame.font(), SECTION, KEY, new GroupsPane.Bounds(0, 0, 590, 390), 0,
                    frame.targets);
            overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
            return frame;
        }

        @Test
        void kindLineDrawsOneButtonReadingTheChosenKind() {
            List<String> texts = frame().texts();
            assertTrue(texts.contains(ControlFlow.dropdownLabel("additive")), texts.toString());
            assertFalse(texts.contains("formula") || texts.contains("stack"), texts.toString());
        }

        @Test
        void pickingFormulaWritesAFormula() {
            frame().click(ControlFlow.dropdownLabel("additive"));
            DrawnFrame open = frame();
            assertTrue(open.texts().containsAll(List.of("formula", "additive", "stack")), open.texts().toString());
            open.click("formula");
            verify(processes).setForm(SECTION, KEY, new Processes.Formula("metal", 1, "0", "/", 0));
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
        void pickingFormulaOnAFormulaKeepsItsTerms() {
            pickTheChosenKind(new Processes.Formula("metal", 4, "2", "/", 1), "formula");
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
            iterator().shiftClick("#ores");
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
