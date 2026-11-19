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

    /**
     * A canister lying on its side settles its goo along the side that is down, which in the canister's own frame
     * is its low X side: at half fill the goo spans the body's whole length from that side and holds half the body.
     * decision sideways-canister-goo-settles-as-if-turned-sideways
     */
    @Test
    void sidewaysFluidFillsFromTheLowSideHoldingItsVolume() {
        float center = 8f / 16f;
        float low = center - CanisterGeometry.HALF_WIDTH + CanisterGeometry.FLUID_INSET;
        float across = 2 * (CanisterGeometry.HALF_WIDTH - CanisterGeometry.FLUID_INSET);
        float bodyVolume = (BODY_TOP - BODY_BOTTOM) * across * across;

        CanisterGeometry.FluidBox half = geometry.fluidBox(CanisterGeometry.Settling.SIDEWAYS, center, 0.5f);

        assertEquals(BODY_BOTTOM, half.minY(), TOLERANCE);
        assertEquals(BODY_TOP, half.maxY(), TOLERANCE);
        assertEquals(low, half.minX(), TOLERANCE);
        float volume = (half.maxX() - half.minX()) * (half.maxY() - half.minY()) * (half.maxZ() - half.minZ());
        assertEquals(0.5f, volume / bodyVolume, 0.01f);
    }
}
