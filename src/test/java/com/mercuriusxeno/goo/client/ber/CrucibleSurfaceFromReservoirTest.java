package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleParticleHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The crucible's drawn surface follows the reservoir alone, so a loaded pool over an
 * empty reservoir shows the basin floor (decision reservoir-volume-drives-fill).
 */
class CrucibleSurfaceFromReservoirTest {

    private static final long FULL_POOL = 5_000;
    private static final long MELTED = 2_000;

    /** A full pool over an empty reservoir draws no surface. */
    @Test
    void fullPoolEmptyReservoirDrawsNoSurface() {
        assertNull(CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(0, FULL_POOL)));
    }

    /** A melted reservoir draws the surface its volume alone raises, whatever the pool holds. */
    @Test
    void surfaceHeightFollowsReservoirAlone() {
        assertEquals(new CrucibleBasin.DrawnSurface(CrucibleBasin.footprintForVolume(MELTED),
                        CrucibleParticleHelper.computeSurfaceY(MELTED)),
                CrucibleBasin.drawnSurface(new CrucibleBasin.Volumes(MELTED, FULL_POOL)));
    }

    /** The renderer draws the surface the render state's reservoir raises. */
    @Test
    void theRendererDrawsTheSurfaceItsReservoirRaises() {
        CrucibleRenderState state = new CrucibleRenderState();
        state.volumes = new CrucibleBasin.Volumes(MELTED, FULL_POOL);

        assertEquals(CrucibleBasin.drawnSurface(state.volumes), CrucibleBlockEntityRenderer.surfaceOf(state));
    }
}