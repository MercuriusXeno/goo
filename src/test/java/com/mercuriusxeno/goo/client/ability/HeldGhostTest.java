package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each goo type's held ghost names its dome radius, ring direction and ring
 * radius from the ability's synced area and program
 * (decision held-visual-ghosts-the-landing-in-two-passes).
 */
class HeldGhostTest {

    private static final float TOLERANCE = 1e-6f;

    private static HeldGhost ghostOf(HeldGhostVisual visual, String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return visual.ghost(definition.area(), definition.behaviors());
    }

    @Test
    void razorRingsOutwardToItsDome() {
        HeldGhost ghost = ghostOf(CrystalExplosionVisual.INSTANCE, "crystal_cloud");
        float synced = (float) AbilityJson.decode("crystal_cloud").area().size();
        assertEquals(synced, ghost.domeRadius(), TOLERANCE);
        assertEquals(HeldGhost.RingDirection.OUTWARD, ghost.rings());
        assertEquals(ghost.domeRadius(), ghost.ringRadius(), TOLERANCE);
    }

    @Test
    void urchinRingsOutwardToItsDome() {
        HeldGhost ghost = ghostOf(MetalExplosionVisual.INSTANCE, "metal_spikes");
        assertEquals((float) AbilityJson.decode("metal_spikes").area().size(), ghost.domeRadius(), TOLERANCE);
        assertEquals(3.75f, ghost.domeRadius(), TOLERANCE);
        assertEquals(HeldGhost.RingDirection.OUTWARD, ghost.rings());
        assertEquals(3.75f, ghost.ringRadius(), TOLERANCE);
    }
}
