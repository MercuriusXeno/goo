package com.mercuriusxeno.goo.client.ber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The beam's reach: the box a beam draws within and how a beam widens with the camera's distance, as BeaconBeams
 * answers them for a beacon and for a combined prism.
 * decision bulb-one-model-max-light-beacon-combo
 */
class PrismRendererTest {

    private static final BlockPos PRISM = new BlockPos(10, 64, -3);
    private static final int REACH = 2048;
    private static final float TOLERANCE = 1e-6f;

    @Test
    void plainPrismDrawsWithinItsCell() {
        assertEquals(new AABB(PRISM), BeaconBeams.drawnBounds(PRISM, Direction.UP, 0));
    }

    @Test
    void beamBoundsStretchUpFromAFloorPrism() {
        assertEquals(new AABB(10, 64, -3, 11, 65 + REACH, -2), BeaconBeams.drawnBounds(PRISM, Direction.UP, REACH));
    }

    @Test
    void beamBoundsStretchAlongAWallPrismsFace() {
        assertEquals(new AABB(10 - REACH, 64, -3, 11, 65, -2), BeaconBeams.drawnBounds(PRISM, Direction.WEST, REACH));
    }

    @Test
    void beamKeepsItsWidthWithinTheWidenDistance() {
        assertEquals(1f, BeaconBeams.radiusScale(BeaconBeams.BEAM_WIDEN_DISTANCE / 2), TOLERANCE);
    }

    @Test
    void beamWidensInStepWithDistancePastIt() {
        assertEquals(3f, BeaconBeams.radiusScale(BeaconBeams.BEAM_WIDEN_DISTANCE * 3), TOLERANCE);
    }
}
