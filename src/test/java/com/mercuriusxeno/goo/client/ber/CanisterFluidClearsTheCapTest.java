package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A canister's goo surface stands clear of the bottom cap's top face at any fill, so a
 * nearly empty canister's goo never shares the cap's plane and z-fights with it.
 */
class CanisterFluidClearsTheCapTest {

    /** A gap the depth buffer resolves a few blocks out: a tenth of a pixel. */
    private static final float CLEAR = 0.1f / 16f;

    @Test
    void aNearlyEmptyCanistersSurfaceStandsClearOfTheCap() {
        CanisterGeometry geometry = CanisterGeometry.STANDING;
        SlotFluidGeometry.SlotGeometry fluid = CanisterSlotRenderer.fluidGeometry(geometry);
        for (float fill : new float[] {1e-6f, 1e-3f, 0.01f}) {
            float surface = SlotFluidGeometry.computeBounds(fluid, 0.5f, 0.5f, fill).yTop();
            assertTrue(surface - geometry.bodyBottom() >= CLEAR - 1e-7f,
                    "at fill " + fill + " the surface sits " + (surface - geometry.bodyBottom())
                            + " above the cap, inside z-fighting range");
        }
    }
}
