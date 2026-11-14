package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a frame draws an entity: a thing Quantum's phase put out of phase draws
 * as a ghost to every viewer, and an eldritch being, out of phase and eldritch
 * combined, goes undrawn to a plain viewer and warped to an eldritch one
 * (decision phased-things-draw-as-a-ghostly-image).
 */
class PhasedSightTest {

    @Test
    void phasedMobDrawsAsAGhostToAPlainViewer() {
        assertEquals(PhasedSight.Phase.GHOST, PhasedSight.drawOf(true, false, false));
    }

    @Test
    void phasedMobDrawsAsAGhostToAnEldritchViewer() {
        assertEquals(PhasedSight.Phase.GHOST, PhasedSight.drawOf(true, false, true));
    }

    @Test
    void eldritchBeingGoesUndrawnToAPlainViewer() {
        assertEquals(PhasedSight.Phase.UNSEEN, PhasedSight.drawOf(true, true, false));
    }

    @Test
    void eldritchBeingDrawsWarpedToAnEldritchViewer() {
        assertEquals(PhasedSight.Phase.WARPED, PhasedSight.drawOf(true, true, true));
    }

    @Test
    void inPhaseMobDrawsAsItIs() {
        assertEquals(PhasedSight.Phase.IN_PHASE, PhasedSight.drawOf(false, false, false));
    }
}
