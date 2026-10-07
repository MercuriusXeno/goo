package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.platform.CompareOp;
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

    /** The spikes extend over six ticks, slow enough to see, and retract over ten. */
    @Test
    void spikesExtendOverSixTicksAndRetractOverTen() {
        assertTrue(atTick(5f) < MetalExplosionVisual.SPIKE_REACH - TOLERANCE, "the spikes are out before tick 6");
        assertEquals(MetalExplosionVisual.SPIKE_REACH, atTick(6f), TOLERANCE);
        assertTrue(atTick(6 + 5 + 9) > 0f, "the spikes are gone before tick 21");
        assertEquals(0f, atTick(6 + 5 + 10), TOLERANCE);
    }

    @Test
    void spikesExtendFastThenSlowThenHoldAtFullReach() {
        assertEquals(0f, atTick(0f), 0f);
        float early = atTick(1f);
        float late = atTick(MetalExplosionVisual.ARM_TICKS) - atTick(MetalExplosionVisual.ARM_TICKS - 1f);
        assertTrue(early > late, "the spikes do not ease out");
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

    /**
     * Metal's held ghost: every spike out to the dome's radius, at the held opacity
     * (decision held-visual-ghosts-the-landing-in-two-passes).
     */
    @Test
    void heldUrchinReachesTheDomeRadiusAtTheHeldOpacity() {
        float opacity = 0.4f;
        MetalExplosionVisual.Urchin urchin = MetalExplosionVisual.heldUrchin(HeldGhost.outwardTo(3.75f), Direction.UP,
                opacity, 0);
        assertEquals(3.75f, urchin.length(), 0f);
        assertEquals(NetherDiscMesh.toByte(opacity), urchin.alpha());
        assertEquals(0.06f * 3.75f, urchin.baseRadius(), 1e-6f);
    }

    @Test
    void heldUrchinsBandSweepsWithTheClock() {
        HeldGhost ghost = HeldGhost.outwardTo(3.75f);
        int start = MetalExplosionVisual.heldUrchin(ghost, Direction.UP, 1f, 0).progressByte();
        int half = MetalExplosionVisual.heldUrchin(ghost, Direction.UP, 1f,
                MetalExplosionVisual.HELD_SWEEP_SECONDS / 2).progressByte();
        assertEquals(0, start);
        assertEquals(NetherDiscMesh.toByte(0.5f), half);
    }

    @Test
    void throughBlocksPipelineIgnoresDepthOverTheMetalShader() {
        PipelineShaders.assertExist(GooRenderTypes.METAL_EXPLOSION_THROUGH_BLOCKS);
        assertEquals(CompareOp.ALWAYS_PASS,
                GooRenderTypes.METAL_EXPLOSION_THROUGH_BLOCKS.getDepthStencilState().depthTest());
    }
}
