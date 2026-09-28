package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.item.ChrysmTier;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each chrysm tier's crystal fills the item box (operator ruling: each tier fills the
 * slot): its scaled reach or its scaled height touches the box's edge.
 */
class ChrysmSpecialRendererTest {

    private static final double BOX_HALF_WIDTH = 7;
    private static final double BOX_HEIGHT = 14;

    @ParameterizedTest
    @EnumSource(ChrysmTier.class)
    void eachTierFillsTheItemBox(ChrysmTier tier) {
        double[] reach = CrystalCluster.reach(tier.volume());
        float scale = ChrysmSpecialRenderer.fitScale(reach);
        double fill = Math.max(reach[0] * scale / BOX_HALF_WIDTH, reach[1] * scale / BOX_HEIGHT);
        assertEquals(1.0, fill, 1e-6, tier.name());
    }
}
