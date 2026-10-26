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
}
