package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    // decision black-hole-leaves-a-compression-sphere: the dome follows the drag, the rings its pull
    @Test
    void aSizedBlackHoleRingsInwardFromItsPullToItsDraggedDome() {
        HeldGhost ghost = HeldDomeRenderer.sizedGhost(5f);
        assertEquals(5f, ghost.domeRadius(), TOLERANCE);
        assertEquals(HeldGhost.RingDirection.INWARD, ghost.rings());
        assertEquals(15f, ghost.ringRadius(), TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({"unstable_timed_bomb, 2.0", "unstable_explode, 3.0", "unstable_lurker, 2.5"})
    void fireballRingsOutwardToItsBlastReach(String name, float power) {
        HeldGhost ghost = ghostOf(UnstableExplosionVisual.INSTANCE, name);
        float reach = (float) ExplosionMarch.maxReach(power);
        assertEquals(reach, ghost.domeRadius(), TOLERANCE);
        assertEquals(HeldGhost.RingDirection.OUTWARD, ghost.rings());
        assertEquals(reach, ghost.ringRadius(), TOLERANCE);
    }
}
