package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.item.ChrysmTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each cluster tier's crystal fills the item box (operator ruling: each tier fills the
 * slot), its scaled reach or its scaled height touching the box's edge, then steps
 * past it by its ruled share; materia draws as an orb half the slot wide (decision
 * chrysm-tiers-in-32x-steps), and every tier stands centered.
 */
class ChrysmSpecialRendererTest {

    private static final double BOX_HALF_WIDTH = 7;
    private static final double BOX_HEIGHT = 14;
    private static final double[] TIER_SCALES = {1.1, 1.44, 1.728};
    private static final double SLOT = 16;

    @ParameterizedTest
    @EnumSource(value = ChrysmTier.class, names = "MATERIA", mode = EnumSource.Mode.EXCLUDE)
    void eachClusterTierFillsTheItemBox(ChrysmTier tier) {
        double[] reach = CrystalCluster.reach(tier.volume());
        float scale = ChrysmSpecialRenderer.fitScale(reach);
        double fill = Math.max(reach[0] * scale / BOX_HALF_WIDTH, reach[1] * scale / BOX_HEIGHT);
        assertEquals(1.0, fill, 1e-6, tier.name());
    }

    @ParameterizedTest
    @EnumSource(value = ChrysmTier.class, names = "MATERIA", mode = EnumSource.Mode.EXCLUDE)
    void eachClusterTierDrawsItsRuledScalePastTheFit(ChrysmTier tier) {
        double[] reach = CrystalCluster.reach(tier.volume());
        float scale = ChrysmSpecialRenderer.tierScale(tier);
        double fill = Math.max(reach[0] * scale / BOX_HALF_WIDTH, reach[1] * scale / BOX_HEIGHT);
        assertEquals(TIER_SCALES[tier.ordinal()], fill, 1e-5, tier.name());
    }

    @ParameterizedTest
    @EnumSource(ChrysmTier.class)
    void eachTierStandsCenteredInTheItemBox(ChrysmTier tier) {
        float scale = ChrysmSpecialRenderer.tierScale(tier);
        double height = ChrysmSpecialRenderer.drawnHeight(tier) * scale;
        double middle = ChrysmSpecialRenderer.baseHeight(tier, scale) + height / 2;
        assertEquals(8.0, middle, 1e-4, tier.name());
    }

    @Test
    void materiaDrawsAnOrbHalfTheSlotWide() {
        assertTrue(ChrysmSpecialRenderer.drawsAsOrb(ChrysmTier.MATERIA));
        double diameter = ChrysmSpecialRenderer.drawnHeight(ChrysmTier.MATERIA)
                * ChrysmSpecialRenderer.tierScale(ChrysmTier.MATERIA);
        assertEquals(0.5, diameter / SLOT, 1e-6);
    }
}
