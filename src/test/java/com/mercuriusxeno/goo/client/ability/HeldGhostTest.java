package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DragSize;
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

    private static HeldGhost ghostOf(HeldGhostVisual visual, String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return visual.ghost(definition.area(), definition.behaviors());
    }

    @Test
    void razorRingsOutwardToItsDome() {
        HeldGhost ghost = ghostOf(CrystalExplosionVisual.INSTANCE, "crystal_cloud");
        float synced = (float) AbilityJson.decode("crystal_cloud").area().size();
        assertEquals(synced, ghost.domeRadius(), TOLERANCE);
        assertEquals(ghost.domeRadius(), ghost.ringRadius(), TOLERANCE);
    }

    @Test
    void urchinRingsOutwardToItsDome() {
        HeldGhost ghost = ghostOf(MetalExplosionVisual.INSTANCE, "metal_urchin");
        assertEquals((float) AbilityJson.decode("metal_urchin").area().size(), ghost.domeRadius(), TOLERANCE);
        assertEquals(ghost.domeRadius(), ghost.ringRadius(), TOLERANCE);
    }

    // decision blast-is-drag-sized-like-the-black-hole: the dome follows the drag, the rings run out to it
    @Test
    void aSizedBlastRingsOutwardToItsDraggedDomeAndPricesByVolume() {
        HeldGhost ghost = UnstableExplosionVisual.INSTANCE.sizedGhost(6f);
        assertEquals(6f, ghost.domeRadius(), TOLERANCE);
        assertEquals(6f, ghost.ringRadius(), TOLERANCE);
        assertEquals(GooFormat.formatAmount(DragSize.costAt(BLAST_COST, 6)), DragSize.priceLabel(BLAST_COST, 6));
        assertEquals(GooFormat.formatAmount(8000), DragSize.priceLabel(BLAST_COST, 6));
    }

    @Test
    void fireballRingsOutwardToItsBlastReach() {
        HeldGhost ghost = ghostOf(UnstableExplosionVisual.INSTANCE, "unstable_lurker");
        assertEquals(ghost.domeRadius(), ghost.ringRadius(), TOLERANCE);
    }
}
