package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.BeamSamples;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BeaconBeams: the light beacon's column leaves the block's center along the facing the block holds, up from a
 * floor and away from a wall, its layers breathe in alpha and shrink slightly at the ease, never past their own
 * width, and the box it draws within and its widening with the camera's distance follow its reach.
 * decision bulb-one-model-max-light-beacon-combo
 * decision every-glow-beam-breathes-in-brightness
 */
class BeaconBeamsTest {

    private static final float RADIUS_SCALE = 1.5f;
    private static final float TOLERANCE = 1e-4f;
    private static final Vector3f CENTER = new Vector3f(0.5f, 0.5f, 0.5f);
    private static final BlockPos BEACON = new BlockPos(10, 64, -3);
    private static final int REACH = 2048;

    /** Where a point along the column, at a height up its +y, lands in the cell for a facing. */
    private static Vector3f alongColumn(Direction facing, float height) {
        PoseStack poseStack = new PoseStack();
        BeaconBeams.standAtFoot(poseStack, facing);
        return poseStack.last().pose().transformPosition(new Vector3f(0f, height, 0f));
    }

    private static void assertNear(Vector3f expected, Vector3f actual, String message) {
        assertTrue(expected.distance(actual) < TOLERANCE, message + ": expected " + expected + ", was " + actual);
    }

    @Nested
    class TheColumnLeavesAlongTheFacing {

        @Test
        void aFloorBeaconsColumnRisesFromItsCenter() {
            assertNear(CENTER, alongColumn(Direction.UP, 0f), "the column's foot");
            assertNear(new Vector3f(0.5f, 1.5f, 0.5f), alongColumn(Direction.UP, 1f), "a block up the column");
        }

        @Test
        void aWestWallBeaconsColumnRunsEastFromItsCenter() {
            assertNear(CENTER, alongColumn(Direction.EAST, 0f), "the column's foot");
            assertNear(new Vector3f(1.5f, 0.5f, 0.5f), alongColumn(Direction.EAST, 1f), "a block along the column");
        }
    }

    private static List<RecordingVertexConsumer.Vertex> emitBeacon(float breath) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            BeaconBeams.emitBeaconLayer(new PoseStack().last(), consumer, layer, RADIUS_SCALE, 0f, breath);
        }
        return consumer.vertices();
    }

    @Test
    void theBeaconBreathesInAlphaAndShrinksSlightlyAtTheEase() {
        List<RecordingVertexConsumer.Vertex> peak = emitBeacon(GlowBeamMesh.breath(0));
        List<RecordingVertexConsumer.Vertex> trough = emitBeacon(GlowBeamMesh.breath(GlowBeamMesh.BREATH_PERIOD_TICKS / 2));

        assertNotEquals(BeamSamples.alphas(peak), BeamSamples.alphas(trough), "the beacon's alpha breathes");
        assertEquals(GlowBeamMesh.LAYERS.getLast().radius() * RADIUS_SCALE, BeamSamples.widest(peak), TOLERANCE,
                "the beacon is never fatter than its own width");
        assertTrue(BeamSamples.widest(trough) < BeamSamples.widest(peak), "the beacon shrinks slightly at the ease");
    }

    @Nested
    class TheBeamsReach {

        @Test
        void aBeamOfNoReachDrawsWithinItsCell() {
            assertEquals(new AABB(BEACON), BeaconBeams.drawnBounds(BEACON, Direction.UP, 0));
        }

        @Test
        void beamBoundsStretchUpFromAFloorBeacon() {
            assertEquals(new AABB(10, 64, -3, 11, 65 + REACH, -2), BeaconBeams.drawnBounds(BEACON, Direction.UP, REACH));
        }

        @Test
        void beamBoundsStretchAlongAWallBeaconsFace() {
            assertEquals(new AABB(10 - REACH, 64, -3, 11, 65, -2),
                    BeaconBeams.drawnBounds(BEACON, Direction.WEST, REACH));
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
}
