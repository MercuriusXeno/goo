package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleParticleHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The crucible's drawn surface and bubble spawn surface follow the reservoir alone,
 * so a loaded pool over an empty reservoir shows the basin floor (decision reservoir-volume-drives-fill).
 */
class CrucibleSurfaceFromReservoirTest {

    private static final long FULL_POOL = 5_000;
    private static final long MELTED = 2_000;

    /**
     * Builds a render state holding the given volumes.
     *
     * @param reservoir the reservoir volume in mB
     * @param pool      the pool volume in mB
     * @return the render state
     */
    private static CrucibleRenderState stateHolding(long reservoir, long pool) {
        CrucibleRenderState state = new CrucibleRenderState();
        state.volumes = new CrucibleBasin.Volumes(reservoir, pool);
        return state;
    }

    /** A full pool over an empty reservoir draws no surface, and spawns no bubbles from one. */
    @Test
    void fullPoolEmptyReservoirDrawsNoSurface() {
        assertNull(CrucibleBlockEntityRenderer.surfaceOf(stateHolding(0, FULL_POOL)));
        assertNull(CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(0, FULL_POOL)));
    }

    /** A melted reservoir draws the surface its volume alone raises, whatever the pool holds. */
    @Test
    void surfaceHeightFollowsReservoirAlone() {
        CrucibleBasin.DrawnSurface withPool = CrucibleBlockEntityRenderer.surfaceOf(stateHolding(MELTED, FULL_POOL));
        CrucibleBasin.DrawnSurface withoutPool = CrucibleBlockEntityRenderer.surfaceOf(stateHolding(MELTED, 0));

        assertNotNull(withPool);
        assertEquals(withoutPool, withPool);
        assertEquals(CrucibleParticleHelper.computeSurfaceY(MELTED), withPool.surfaceY());
        assertEquals(CrucibleBasin.footprintForVolume(MELTED), withPool.footprint());
    }

    /** The bubbles' spawn surface is the drawn surface, so bubbles rise from the goo shown. */
    @Test
    void bubbleSurfaceMatchesDrawnSurface() {
        CrucibleBasin.Volumes volumes = new CrucibleBasin.Volumes(MELTED, FULL_POOL);

        assertEquals(CrucibleBlockEntityRenderer.surfaceOf(stateHolding(MELTED, FULL_POOL)),
                CrucibleBasin.drawnSurface(volumes));
    }
}
