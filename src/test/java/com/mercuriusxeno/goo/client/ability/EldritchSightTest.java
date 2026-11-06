package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a frame draws an entity: a thing Quantum's phase put out of phase draws
 * as a ghost to every viewer, and an eldritch being, out of phase and eldritch
 * combined, goes undrawn to a plain viewer and warped to an eldritch one
 * (decision phased-things-draw-as-a-ghostly-image).
 */
class EldritchSightTest {

    @Test
    void phasedMobDrawsAsAGhostToAPlainViewer() {
        assertEquals(EldritchSight.Phase.GHOST, EldritchSight.drawOf(true, false, false));
    }

    @Test
    void phasedMobDrawsAsAGhostToAnEldritchViewer() {
        assertEquals(EldritchSight.Phase.GHOST, EldritchSight.drawOf(true, false, true));
    }

    @Test
    void eldritchBeingGoesUndrawnToAPlainViewer() {
        assertEquals(EldritchSight.Phase.UNSEEN, EldritchSight.drawOf(true, true, false));
    }

    @Test
    void eldritchBeingDrawsWarpedToAnEldritchViewer() {
        assertEquals(EldritchSight.Phase.WARPED, EldritchSight.drawOf(true, true, true));
    }

    @Test
    void inPhaseMobDrawsAsItIs() {
        assertEquals(EldritchSight.Phase.IN_PHASE, EldritchSight.drawOf(false, false, false));
    }
}
