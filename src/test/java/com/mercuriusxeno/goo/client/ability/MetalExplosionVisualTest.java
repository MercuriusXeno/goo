package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import java.util.List;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MetalExplosionVisual's urchin: its spikes' timing and spread, and the
 * shader pair its pipeline names.
 */
class MetalExplosionVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final int FEWEST_SPIKES = 20;
    private static final int MOST_SPIKES = 28;

    private static float atTick(float tick) {
        return MetalExplosionVisual.spikeLength(tick / MetalExplosionVisual.DURATION_TICKS);
    }

    @Test
    void spikesSnapOutFastThenHoldAtFullReach() {
        assertEquals(0f, atTick(0f), 0f);
        assertTrue(atTick(1f) > MetalExplosionVisual.SPIKE_REACH / 2, "the spikes arm slowly");
        int armed = MetalExplosionVisual.ARM_TICKS;
        for (int tick = armed; tick <= armed + MetalExplosionVisual.HOLD_TICKS; tick++) {
            assertEquals(MetalExplosionVisual.SPIKE_REACH, atTick(tick), TOLERANCE, "tick " + tick);
        }
    }

    @Test
    void spikesRetractToNothingByTheEnd() {
        int retractFrom = MetalExplosionVisual.ARM_TICKS + MetalExplosionVisual.HOLD_TICKS;
        float previous = MetalExplosionVisual.SPIKE_REACH;
        for (int tick = retractFrom + 1; tick <= MetalExplosionVisual.DURATION_TICKS; tick++) {
            float length = atTick(tick);
            assertTrue(length < previous, "the spikes stop retracting at tick " + tick);
            previous = length;
        }
        assertEquals(0f, atTick(MetalExplosionVisual.DURATION_TICKS), TOLERANCE);
    }

    @ParameterizedTest
    @EnumSource(Direction.class)
    void aboutTwoDozenSpikesPointOutOfTheWall(Direction face) {
        List<Vector3f> spikes = MetalExplosionVisual.spikeDirections(face);
        assertTrue(spikes.size() >= FEWEST_SPIKES && spikes.size() <= MOST_SPIKES,
                spikes.size() + " spikes on " + face);
        for (Vector3f dir : spikes) {
            float out = dir.x() * face.getStepX() + dir.y() * face.getStepY() + dir.z() * face.getStepZ();
            assertTrue(out >= MetalExplosionVisual.OUTWARD_MIN, "a spike points into the wall on " + face);
            assertEquals(1f, dir.length(), TOLERANCE);
        }
    }

    @Test
    void pipelineShadersResolveOnTheClasspath() {
        PipelineShaders.assertExist(GooRenderTypes.METAL_EXPLOSION);
    }
}
