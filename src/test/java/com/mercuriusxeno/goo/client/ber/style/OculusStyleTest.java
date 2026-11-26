package com.mercuriusxeno.goo.client.ber.style;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A combo's transformation runs once from when it took and holds whole, and an oculus prism's eye hovers low off
 * the face the prism grew from (decision oculus-prism-becomes-a-hovering-eye).
 */
class OculusStyleTest {

    private static final float EPSILON = 1e-4f;
    private static final long SINCE = 1000L;

    @Test
    void theTransformationRunsFromTheComboAndHoldsWhole() {
        assertEquals(0f, OculusStyle.transformationShare(SINCE, SINCE), EPSILON);
        assertEquals(0.5f, OculusStyle.transformationShare(SINCE + OculusStyle.TRANSFORM_TICKS / 2, SINCE), EPSILON);
        assertEquals(1f, OculusStyle.transformationShare(SINCE + 10 * OculusStyle.TRANSFORM_TICKS, SINCE), EPSILON);
    }

    @Test
    void aFloorPrismsEyeHoversLowOffTheFloor() {
        assertEquals(new Vec3(0.5, 4.0 / 16, 0.5), OculusStyle.eyeInCell(Direction.UP));
    }
}
