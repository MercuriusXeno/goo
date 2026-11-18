package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.SpikeTrapFixture;
import com.mercuriusxeno.goo.ability.ChargeSize;
import com.mercuriusxeno.goo.item.GooFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each goo type's held ghost names its dome radius, ring direction and ring
 * radius from the ability's synced area and program
 * (decision held-visual-ghosts-the-landing-in-two-passes).
 */
class HeldGhostTest {

    private static final float TOLERANCE = 1e-6f;
    /** unstable_blast.json's cost, the price of the reference radius. */
    private static final int BLAST_COST = 1000;

    private static HeldGhost ghostOf(HeldGhostVisual visual, AbilityDefinition definition) {
        return visual.ghost(definition.area(), definition.behaviors());
    }

    /** prismatic-voronoi-code-stays-and-is-unplugged-from-shatter: the unplugged prism dome still ghosts a field */
    @Test
    void thePrismDomeRingsOutwardToAFieldsDome() {
        HeldGhost ghost = ghostOf(CrystalExplosionVisual.INSTANCE, SpikeTrapFixture.definition());
        float synced = (float) SpikeTrapFixture.definition().area().size();
        assertEquals(synced, ghost.domeRadius(), TOLERANCE);
        assertEquals(ghost.domeRadius(), ghost.ringRadius(), TOLERANCE);
    }

    @Test
    void theSpikeTrapRingsOutwardToItsDome() {
        HeldGhost ghost = ghostOf(MetalExplosionVisual.INSTANCE, SpikeTrapFixture.definition());
        assertEquals((float) SpikeTrapFixture.definition().area().size(), ghost.domeRadius(), TOLERANCE);
        assertEquals(ghost.domeRadius(), ghost.ringRadius(), TOLERANCE);
    }

    // decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob: the dome follows the charge, the rings run out to it
    @Test
    void aSizedBlastRingsOutwardToItsChargedDomeAndPricesByVolume() {
        HeldGhost ghost = UnstableExplosionVisual.INSTANCE.sizedGhost(6f);
        assertEquals(6f, ghost.domeRadius(), TOLERANCE);
        assertEquals(6f, ghost.ringRadius(), TOLERANCE);
        assertEquals(GooFormat.formatAmount(ChargeSize.costAt(BLAST_COST, 6)), ChargeSize.priceLabel(BLAST_COST, 6));
        assertEquals(GooFormat.formatAmount(8000), ChargeSize.priceLabel(BLAST_COST, 6));
    }

    @Test
    void anUnstableGhostRingsOutwardToItsDome() {
        HeldGhost ghost = ghostOf(UnstableExplosionVisual.INSTANCE, SpikeTrapFixture.definition());
        assertEquals(ghost.domeRadius(), ghost.ringRadius(), TOLERANCE);
    }
}
