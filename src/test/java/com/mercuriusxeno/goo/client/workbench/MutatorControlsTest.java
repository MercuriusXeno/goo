package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
        private final MutatorControls controls = new MutatorControls(processes, overlays, () -> List.of("tree"),
                () -> List.of("metal"), () -> { });

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
    }
}
