package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ChainFootprint;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * FuseOrbVisual's orb geometry, emitted through a recording consumer at the
 * pose the renderer places it with.
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
}
