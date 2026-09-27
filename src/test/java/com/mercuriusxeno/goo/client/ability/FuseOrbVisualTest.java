package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FuseOrbVisual's orb geometry, emitted through a recording consumer at the
 * pose the renderer places it with, and the scale its fuse expiry takes.
 */
class FuseOrbVisualTest {

    private static final float TOLERANCE = 1e-5f;
    private static final GooRenderUtil.UvRect UV = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);
    private static final int WHITE = 0xFFFFFFFF;

    /** Every scale the orb modifier takes at rest, at the shrink minimum and at the pulse peak. */
    private static final float[] MODIFIERS = {
        1f, FuseOrbVisual.IMPLOSION_MIN, 1f + FuseOrbVisual.PULSE_AMPLITUDE,
    };
    private static final int[] STACK_COUNTS = {1, ChainFootprint.MAX_STACKS};

    /**
     * One orb layer to emit.
     *
     * @param face       the placed face
     * @param shape      the orb shape
     * @param stackCount the marker's stack count
     * @param modifier   the orb modifier
     * @param shell      true for the shell layer, false for the core
     */
    record OrbCase(Direction face, FuseOrbVisual.OrbShape shape, int stackCount,
                   float modifier, boolean shell) {
    }

    static Stream<Arguments> everyOrbLayer() {
        List<Arguments> cases = new ArrayList<>();
        for (Direction face : Direction.values()) {
            for (FuseOrbVisual.OrbShape shape : FuseOrbVisual.OrbShape.values()) {
                for (int stacks : STACK_COUNTS) {
                    for (float modifier : MODIFIERS) {
                        cases.add(Arguments.of(new OrbCase(face, shape, stacks, modifier, false)));
                        cases.add(Arguments.of(new OrbCase(face, shape, stacks, modifier, true)));
                    }
                }
            }
        }
        return cases.stream();
    }

    private static boolean isGlow(FuseOrbVisual.OrbShape shape) {
        return shape == FuseOrbVisual.OrbShape.GLOW_BUMP || shape == FuseOrbVisual.OrbShape.GLOW_FLAT;
    }

    /** The layer's lateral half-size by the resting arithmetic, before any pose scale. */
    private static float restingHalf(OrbCase orb) {
        if (isGlow(orb.shape())) {
            GlowCrystalBlock.CrystalSize size = GlowCrystalBlock.CrystalSize.fromStacks(orb.stackCount());
            return (float) ((size.max - size.min) / 2);
        }
        float core = FuseOrbVisual.CORE_BASE + (orb.stackCount() - 1) * FuseOrbVisual.CORE_GROWTH;
        return orb.shell() ? core + FuseOrbVisual.SHELL_MARGIN : core;
    }

    /** The pose scale the renderer lays across the face, per shape. */
    private static float lateralScale(OrbCase orb) {
        return switch (orb.shape()) {
            case BLOB -> orb.modifier();
            case SPLAT -> FuseOrbVisual.SPLAT_WIDTH * orb.modifier();
            case GLOW_BUMP, GLOW_FLAT -> 1f;
        };
    }

    /** How far the layer reaches off the face plane after the pose scale, per shape. */
    private static float outwardDepth(OrbCase orb) {
        return switch (orb.shape()) {
            case BLOB -> restingHalf(orb) * orb.modifier();
            case SPLAT -> restingHalf(orb) * FuseOrbVisual.SPLAT_HEIGHT * orb.modifier();
            case GLOW_BUMP -> (float) GlowCrystalBlock.BUMP_DEPTH;
            case GLOW_FLAT -> (float) GlowCrystalBlock.FLAT_DEPTH;
        };
    }

    private static List<RecordingVertexConsumer.Vertex> emit(OrbCase orb) {
        PoseStack poseStack = new PoseStack();
        FuseOrbVisual.placeOrb(poseStack, orb.face(), orb.shape(), orb.modifier());
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        RenderContext ctx = new RenderContext(poseStack.last(), consumer, 0);
        FuseOrbVisual.emitOrbLayer(ctx, WHITE, restingHalf(orb), orb.face(), orb.shape(), UV);
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
        @MethodSource("com.mercuriusxeno.goo.client.ability.FuseOrbVisualTest#everyOrbLayer")
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
        @MethodSource("com.mercuriusxeno.goo.client.ability.FuseOrbVisualTest#everyOrbLayer")
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

    /** The shrink's scale at each whole tick of its window, first tick to last. */
    private static float[] shrinkAtEachTick() {
        float[] scales = new float[FuseOrbVisual.SHRINK_TICKS + 1];
        for (int tick = 0; tick <= FuseOrbVisual.SHRINK_TICKS; tick++) {
            int remaining = FuseOrbVisual.FUSE_EXPIRY_TICKS - tick;
            scales[tick] = FuseOrbVisual.computeImplosionScale(remaining, 0f, GAME_TIME + tick);
        }
        return scales;
    }

    private static float largestShrinkDelta() {
        float[] scales = shrinkAtEachTick();
        float largest = 0f;
        for (int i = 1; i < scales.length; i++) {
            largest = Math.max(largest, scales[i - 1] - scales[i]);
        }
        return largest;
    }

    @Nested
    class ShrinkEasesThenJitters {

        @Test
        void shrinkFallsSlowFastSlowFromRestToTheMinimum() {
            float[] scales = shrinkAtEachTick();
            int last = scales.length - 1;

            assertEquals(1f, scales[0], TOLERANCE);
            assertEquals(FuseOrbVisual.IMPLOSION_MIN, scales[last], TOLERANCE);
            for (int i = 1; i <= last; i++) {
                assertFalse(scales[i] > scales[i - 1], "the shrink rises at tick " + i);
            }
            float firstDelta = scales[0] - scales[1];
            float middleDelta = scales[last / 2 - 1] - scales[last / 2];
            float lastDelta = scales[last - 1] - scales[last];
            assertTrue(firstDelta < middleDelta,
                    "first delta " + firstDelta + " not below middle " + middleDelta);
            assertTrue(lastDelta < middleDelta,
                    "last delta " + lastDelta + " not below middle " + middleDelta);
        }

        @Test
        void orbJittersInItsBandAcrossTheLastFuseTick() {
            Set<Float> distinct = new HashSet<>();
            for (int step = 0; step < 10; step++) {
                float partial = step / 10f;
                float scale = FuseOrbVisual.computeImplosionScale(1, partial, GAME_TIME + partial);
                assertTrue(scale > 0f, "scale " + scale + " at or below zero");
                assertTrue(
                        scale <= FuseOrbVisual.IMPLOSION_MIN + FuseOrbVisual.JITTER_AMPLITUDE + TOLERANCE,
                        "scale " + scale + " above the band");
                assertTrue(
                        scale >= FuseOrbVisual.IMPLOSION_MIN - FuseOrbVisual.JITTER_AMPLITUDE - TOLERANCE,
                        "scale " + scale + " below the band");
                distinct.add(scale);
            }
            assertTrue(distinct.size() >= 2, "the orb holds still at its minimum");
        }

        @Test
        void handoffStartsWithinTheJitterBandAndNeverJumps() {
            float partial = 0.9f;
            float lastFuseFrame = FuseOrbVisual.computeImplosionScale(1, partial, GAME_TIME + partial);
            float firstActiveFrame = FuseOrbVisual.computeHandoffScale(0f);
            assertEquals(lastFuseFrame, firstActiveFrame, FuseOrbVisual.JITTER_AMPLITUDE + TOLERANCE);

            float bound = largestShrinkDelta() + TOLERANCE;
            float previous = firstActiveFrame;
            for (int tick = 1; tick <= FuseOrbVisual.SHRINK_TICKS + 2; tick++) {
                float scale = FuseOrbVisual.computeHandoffScale(tick);
                assertTrue(Math.abs(scale - previous) <= bound,
                        "handoff jumps " + (scale - previous) + " at tick " + tick);
                previous = scale;
            }
            assertEquals(1f, previous, TOLERANCE);
        }

        @Test
        void syncThresholdCoversTheWholeShrinkWindow() {
            assertTrue(
                    ChainMarkerBlockEntity.IMPLOSION_SYNC_THRESHOLD
                            >= FuseOrbVisual.FUSE_EXPIRY_TICKS);
        }
    }

    @Nested
    class CrystalMarkerEbbs {

        private static final int SAMPLES = 40;
        private static final int SLOW_PERIOD_TICKS = 60;
        private static final float FAINT_AMPLITUDE = 0.05f;

        @Test
        void ebbIsSlowFaintAndReturnsAfterOnePeriod() {
            float period = FuseOrbVisual.CRYSTAL_EBB_PERIOD;
            assertTrue(period >= SLOW_PERIOD_TICKS, "period " + period + " is under three seconds");
            assertEquals(FuseOrbVisual.crystalEbb(true, GAME_TIME),
                    FuseOrbVisual.crystalEbb(true, GAME_TIME + period), TOLERANCE);

            float lowest = Float.MAX_VALUE;
            float highest = -Float.MAX_VALUE;
            for (int i = 0; i < SAMPLES; i++) {
                float ebb = FuseOrbVisual.crystalEbb(true, GAME_TIME + i * period / SAMPLES);
                assertTrue(Math.abs(ebb - 1f) <= FAINT_AMPLITUDE, "ebb " + ebb + " is not faint");
                lowest = Math.min(lowest, ebb);
                highest = Math.max(highest, ebb);
            }
            assertTrue(highest - lowest > FuseOrbVisual.CRYSTAL_EBB_AMPLITUDE, "the orb does not ebb");
        }

        @Test
        void orbRestsWithoutACloud() {
            for (int i = 0; i < SAMPLES; i++) {
                assertEquals(1f, FuseOrbVisual.crystalEbb(false, GAME_TIME + i), 0f);
            }
        }
    }

    @Nested
    class MiningMarkerBeats {

        private static final long LAST_LAYER_TICK = 48_200L;

        @Test
        void miningBeatsFasterThanTheNetherPulse() {
            assertTrue(FuseOrbVisual.MINING_BEAT_PERIOD < BlackHolePhases.HOLE_PULSE_PERIOD);
        }

        @Test
        void layerStrikeRestartsTheBeat() {
            assertEquals(0f, FuseOrbVisual.miningBeatPhase(LAST_LAYER_TICK, LAST_LAYER_TICK), 0f);
            assertEquals(1f, FuseOrbVisual.miningBeat(true, LAST_LAYER_TICK, LAST_LAYER_TICK), 0f);
            float midBeat = LAST_LAYER_TICK + FuseOrbVisual.MINING_BEAT_PERIOD / 2;
            assertEquals(1f + FuseOrbVisual.MINING_BEAT_AMPLITUDE,
                    FuseOrbVisual.miningBeat(true, midBeat, LAST_LAYER_TICK), TOLERANCE);
            float nextBeat = LAST_LAYER_TICK + FuseOrbVisual.MINING_BEAT_PERIOD;
            assertEquals(0f, FuseOrbVisual.miningBeatPhase(nextBeat, LAST_LAYER_TICK), TOLERANCE);
        }

        @Test
        void orbRestsWhileNoProgramRuns() {
            for (int i = 0; i < FuseOrbVisual.MINING_BEAT_PERIOD * 2; i++) {
                assertEquals(1f, FuseOrbVisual.miningBeat(false, LAST_LAYER_TICK + i + 0.5f, LAST_LAYER_TICK), 0f);
            }
        }
    }
}
