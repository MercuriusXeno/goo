package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.AbilityBlockRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MarkerOrbVisual's orb geometry, emitted through a recording consumer at the
 * pose the renderer places it with, and the orb drawing only while the
 * marker's program runs.
 */
class MarkerOrbVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);
    private static final int WHITE = 0xFFFFFFFF;

    /** Every scale the orb modifier takes at rest and at the crystal ebb's peak. */
    private static final float[] MODIFIERS = {
        1f, 1f + MarkerOrbVisual.CRYSTAL_EBB_AMPLITUDE,
    };

    /**
     * One orb layer to emit.
     *
     * @param face       the placed face
     * @param shape      the orb shape
     * @param modifier   the orb modifier
     * @param shell      true for the shell layer, false for the core
     */
    record OrbCase(Direction face, MarkerOrbVisual.OrbShape shape, float modifier, boolean shell) {
    }

    static Stream<Arguments> everyOrbLayer() {
        List<Arguments> cases = new ArrayList<>();
        for (Direction face : Direction.values()) {
            for (MarkerOrbVisual.OrbShape shape : MarkerOrbVisual.OrbShape.values()) {
                for (float modifier : MODIFIERS) {
                    cases.add(Arguments.of(new OrbCase(face, shape, modifier, false)));
                    cases.add(Arguments.of(new OrbCase(face, shape, modifier, true)));
                }
            }
        }
        return cases.stream();
    }

    private static boolean isGlow(MarkerOrbVisual.OrbShape shape) {
        return shape == MarkerOrbVisual.OrbShape.GLOW_BUMP;
    }

    /** The layer's lateral half-size by the resting arithmetic, before any pose scale. */
    private static float restingHalf(OrbCase orb) {
        if (isGlow(orb.shape())) {
            GlowCrystalBlock.CrystalSize size = GlowCrystalBlock.CrystalSize.TINY;
            return (float) ((size.max - size.min) / 2);
        }
        float core = MarkerOrbVisual.CORE_BASE;
        return orb.shell() ? core + MarkerOrbVisual.SHELL_MARGIN : core;
    }

    /** The pose scale the renderer lays across the face, per shape. */
    private static float lateralScale(OrbCase orb) {
        return switch (orb.shape()) {
            case GOO -> orb.modifier();
            case GLOW_BUMP -> 1f;
        };
    }

    /** How far the layer reaches off the face plane after the pose scale, per shape. */
    private static float outwardDepth(OrbCase orb) {
        return switch (orb.shape()) {
            case GOO -> restingHalf(orb) * orb.modifier();
            case GLOW_BUMP -> (float) GlowCrystalBlock.BUMP_DEPTH;
        };
    }

    private static List<RecordingVertexConsumer.Vertex> emit(OrbCase orb) {
        PoseStack poseStack = new PoseStack();
        MarkerOrbVisual.placeOrb(poseStack, orb.face(), orb.shape(), orb.modifier());
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(poseStack.last(), consumer, 0);
        MarkerOrbVisual.emitOrbLayer(ctx, WHITE, restingHalf(orb), orb.face(), orb.shape(), UV);
        return consumer.vertices();
    }

    private static float coordinate(RecordingVertexConsumer.Vertex vertex, Direction.Axis axis) {
        return switch (axis) {
            case X -> vertex.x();
            case Y -> vertex.y();
            case Z -> vertex.z();
        };
    }

    /** The face plane's coordinate along its axis in block space: the side of the marker's block it rests on. */
    private static float facePlane(Direction face) {
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0f : 1f;
    }

    /** The face plane's center across it, where the orb centers laterally. */
    private static final float BLOCK_CENTER = 0.5f;

    @Nested
    class OrbSitsOnTheFace {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.ability.MarkerOrbVisualTest#everyOrbLayer")
        void noVertexCrossesTheFacePlane(OrbCase orb) {
            List<RecordingVertexConsumer.Vertex> vertices = emit(orb);
            Direction.Axis axis = orb.face().getAxis();
            float plane = facePlane(orb.face());
            int step = orb.face().getAxisDirection().getStep();

            assertFalse(vertices.isEmpty());
            float farthest = 0f;
            for (RecordingVertexConsumer.Vertex vertex : vertices) {
                float outward = (coordinate(vertex, axis) - plane) * step;
                assertFalse(outward < -TOLERANCE, orb + " reaches " + outward + " into the block it rests on");
                farthest = Math.max(farthest, outward);
            }
            assertEquals(outwardDepth(orb), farthest, TOLERANCE, orb + " outward depth");
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.ability.MarkerOrbVisualTest#everyOrbLayer")
        void lateralExtentsMatchTheRestingArithmetic(OrbCase orb) {
            List<RecordingVertexConsumer.Vertex> vertices = emit(orb);
            float expectedHalf = restingHalf(orb) * lateralScale(orb);
            for (Direction.Axis lateral : Direction.Axis.values()) {
                if (lateral == orb.face().getAxis()) {
                    continue;
                }
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                for (RecordingVertexConsumer.Vertex vertex : vertices) {
                    min = Math.min(min, coordinate(vertex, lateral));
                    max = Math.max(max, coordinate(vertex, lateral));
                }
                assertEquals(BLOCK_CENTER - expectedHalf, min, TOLERANCE, orb + " " + lateral + " min");
                assertEquals(BLOCK_CENTER + expectedHalf, max, TOLERANCE, orb + " " + lateral + " max");
            }
        }
    }

    /** A game time far from zero, as a live level's clock reads. */
    private static final float GAME_TIME = 48_213f;

    // decision splat-runs-the-program-no-fuse
    @Test
    void orbDrawsNothingWhileNoProgramRuns() {
        AbilityBlockRenderState state = new AbilityBlockRenderState();
        state.behaviorActive = false;
        SubmitNodeCollector collector = mock(SubmitNodeCollector.class);

        MarkerOrbVisual.submit(state, new PoseStack(), collector);

        verifyNoInteractions(collector);
    }

    @Nested
    class CrystalMarkerEbbs {

        private static final int SAMPLES = 40;
        private static final int SLOW_PERIOD_TICKS = 60;
        private static final float FAINT_AMPLITUDE = 0.05f;

        @Test
        void ebbIsSlowFaintAndReturnsAfterOnePeriod() {
            float period = MarkerOrbVisual.CRYSTAL_EBB_PERIOD;
            assertTrue(period >= SLOW_PERIOD_TICKS, "period " + period + " is under three seconds");
            assertEquals(MarkerOrbVisual.crystalEbb(true, GAME_TIME),
                    MarkerOrbVisual.crystalEbb(true, GAME_TIME + period), TOLERANCE);

            float lowest = Float.MAX_VALUE;
            float highest = -Float.MAX_VALUE;
            for (int i = 0; i < SAMPLES; i++) {
                float ebb = MarkerOrbVisual.crystalEbb(true, GAME_TIME + i * period / SAMPLES);
                assertTrue(Math.abs(ebb - 1f) <= FAINT_AMPLITUDE, "ebb " + ebb + " is not faint");
                lowest = Math.min(lowest, ebb);
                highest = Math.max(highest, ebb);
            }
            assertTrue(highest - lowest > MarkerOrbVisual.CRYSTAL_EBB_AMPLITUDE, "the orb does not ebb");
        }

        @Test
        void orbRestsWithoutACloud() {
            for (int i = 0; i < SAMPLES; i++) {
                assertEquals(1f, MarkerOrbVisual.crystalEbb(false, GAME_TIME + i), 0f);
            }
        }
    }
}
