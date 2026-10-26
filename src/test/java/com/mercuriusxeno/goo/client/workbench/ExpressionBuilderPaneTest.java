package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the expression builder's hover hints through their static seams, with no Screen or font.
 */
class ExpressionBuilderPaneTest {

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
}
