package com.mercuriusxeno.goo.block.canister;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The goo surface rises from a floor clear of the bottom cap to the body top
 * (decision one-fluid-surface-clears-the-cap).
 */
class CanisterGeometryTest {

    private static final float BODY_BOTTOM = 3f / 16f;
    private static final float BODY_TOP = 13f / 16f;
    private static final float TOLERANCE = 1e-6f;

    private final CanisterGeometry geometry = CanisterGeometry.at(BODY_BOTTOM, BODY_TOP);

    @Test
    void emptySurfaceSitsTheFluidFloorAboveTheBodyBottom() {
        assertEquals(BODY_BOTTOM + CanisterGeometry.FLUID_FLOOR, geometry.fluidSurface(0f), TOLERANCE);
    }

    @Test
    void fullSurfaceMeetsTheBodyTop() {
        assertEquals(BODY_TOP, geometry.fluidSurface(1f), TOLERANCE);
    }

    @Test
    void halfSurfaceSitsMidwayBetweenTheFloorAndTheBodyTop() {
        float floor = BODY_BOTTOM + CanisterGeometry.FLUID_FLOOR;
        assertEquals((floor + BODY_TOP) / 2f, geometry.fluidSurface(0.5f), TOLERANCE);
    }
}
