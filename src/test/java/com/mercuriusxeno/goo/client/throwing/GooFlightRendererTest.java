package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A flight's morph into its travel form follows the share of the flight the
 * delivery names, so the timing is ability data and a different share moves
 * it (decisions traveling-form-transforms-in-flight,
 * dart-transform-point-is-ability-data); Dart's javelin forms in the hand, so
 * its blob flies whole from the start
 * (decision shriek-bloom-portal-mitosis-and-dart-still-throw-a-blob).
 */
class GooFlightRendererTest {

    private static final float TOLERANCE = 1e-5f;
    private static final float LONG_THROW = 12f;
    private static final float SHORT_THROW = 1f;
    /** Dart's shipped transform point, the morph in the hand. */
    private static final String IN_THE_HAND = "\"transform_at\": 0,";
    /** A transform point a quarter of the way out, the morph in flight. */
    private static final String IN_FLIGHT = "\"transform_at\": 0.25,";

    @Test
    void theDartFormsInTheHandAndFliesWholeFromTheStart() {
        Delivery dart = AbilityJson.decode("metal_dart").delivery();

        assertTrue(dart.morphsInHand(), "the dart should take its form in the hand, transform_at "
                + dart.transformAt());
        assertEquals(1f, GooFlightRenderer.morphFraction(0f, LONG_THROW, (float) dart.transformAt()), 0f);
    }

    @Test
    void aDartFormNamedInFlightIsTheBlobBeforeItsTransformPointAndTheDartAfter() {
        String shipped = AbilityJson.read("metal_dart");
        assertTrue(shipped.contains(IN_THE_HAND), "metal_dart.json should name " + IN_THE_HAND);
        Delivery inFlight = AbilityJson.decodeText("metal_dart", shipped.replace(IN_THE_HAND, IN_FLIGHT)).delivery();
        float transformAt = (float) inFlight.transformAt();

        assertEquals(TravelForm.DART, inFlight.form());
        assertFalse(inFlight.morphsInHand(),"a form named at transform_at " + transformAt + " morphs in flight");
        assertEquals(0f, GooFlightRenderer.morphFraction(0f, LONG_THROW, transformAt), 0f);
        assertEquals(0.5f, GooFlightRenderer.morphFraction(transformAt / 2, LONG_THROW, transformAt), TOLERANCE);
        assertEquals(1f, GooFlightRenderer.morphFraction(transformAt, LONG_THROW, transformAt), TOLERANCE);
        assertEquals(1f, GooFlightRenderer.morphFraction(1f, LONG_THROW, transformAt), 0f);
    }

    @Test
    void aLaterTransformPointMovesTheMorphLater() {
        float progress = 0.1f;

        assertEquals(1f, GooFlightRenderer.morphFraction(progress, LONG_THROW, 0.1f), TOLERANCE);
        assertEquals(0.25f, GooFlightRenderer.morphFraction(progress, LONG_THROW, 0.4f), TOLERANCE);
    }

    @Test
    void theGlowBeamsBillboardIsScaledToItsOwnHalfWidth() {
        Vec3 lateral = GooFlightRenderer.computeGlowLateral(Vec3.ZERO, new Vec3(0, 0, 4), new Vec3(3, 0, 2));

        assertEquals(0.08, lateral.length(), TOLERANCE);
    }

    @Test
    void aShortThrowFliesAsTheDartFromTheStart() {
        assertEquals(1f, GooFlightRenderer.morphFraction(0f, SHORT_THROW, (float) Delivery.DEFAULT_TRANSFORM_AT), 0f);
    }

    /**
     * The blob's core and shell draw in one shape, so a pressed shell keeps
     * its core inside it (decision palm-blob-rests-pressed-and-stands-into-the-cube).
     */
    @Nested
    class CoreAndShellTakeOneShape {

        private static final BlobShape PRESSED = new BlobShape(1.3f, 1.2f, 0.6f);
        /** The shell's alpha, the opacity the core draws at too. */
        private static final int SHELL_ALPHA = 0x60;
        /** A quarter of the core's pulse, in ticks: its sine's quarter turn over its pace. */
        private static final float QUARTER_PULSE = (float) (Math.PI / 2 / 0.3);

        @Test
        void theShellsBoxStandsInTheShapesProportions() {
            Extents shell = drawn(c -> GooFlightRenderer.emitShell(new PoseStack().last(), c, GooTypes.ZOO, PRESSED));

            assertEquals(GooFlightRenderer.SHELL_HW * 2 * PRESSED.width(), shell.width(), TOLERANCE);
            assertEquals(GooFlightRenderer.SHELL_HW * 2 * PRESSED.depth(), shell.depth(), TOLERANCE);
            assertEquals(GooFlightRenderer.SHELL_HW * 2 * PRESSED.height(), shell.height(), TOLERANCE);
        }

        @Test
        void theCoresBoxKeepsTheShapesProportionsThroughItsPulse() {
            for (float time : new float[] {0f, QUARTER_PULSE}) {
                Extents core = drawn(c -> GooFlightRenderer.emitCore(new PoseStack().last(), c, GooTypes.ZOO, time,
                        PRESSED));

                assertEquals(PRESSED.width() / PRESSED.height(), core.width() / core.height(), TOLERANCE,
                        "the core's width to height at time " + time);
                assertEquals(PRESSED.depth() / PRESSED.height(), core.depth() / core.height(), TOLERANCE,
                        "the core's depth to height at time " + time);
            }
        }

        /** goo-cuboid-core-is-three-quarters-of-its-shell */
        @Test
        void theCoreIsThreeQuartersOfItsShellAtTheMiddleOfItsPulse() {
            for (BlobShape shape : new BlobShape[] {BlobShape.CUBE, PRESSED}) {
                Extents shell = drawn(c -> GooFlightRenderer.emitShell(new PoseStack().last(), c, GooTypes.ZOO, shape));
                Extents core = drawn(c -> GooFlightRenderer.emitCore(new PoseStack().last(), c, GooTypes.ZOO, 0f,
                        shape));

                assertEquals(0.75f * shell.width(), core.width(), TOLERANCE, "the core's width in " + shape);
                assertEquals(0.75f * shell.depth(), core.depth(), TOLERANCE, "the core's depth in " + shape);
                assertEquals(0.75f * shell.height(), core.height(), TOLERANCE, "the core's height in " + shape);
            }
        }

        @Test
        void theCoreStaysInsideItsShellAtThePeakOfItsPulse() {
            for (BlobShape shape : new BlobShape[] {BlobShape.CUBE, PRESSED}) {
                Extents shell = drawn(c -> GooFlightRenderer.emitShell(new PoseStack().last(), c, GooTypes.ZOO, shape));
                Extents peak = drawn(c -> GooFlightRenderer.emitCore(new PoseStack().last(), c, GooTypes.ZOO,
                        QUARTER_PULSE, shape));

                assertTrue(peak.width() < shell.width(), "the swollen core's width in " + shape);
                assertTrue(peak.depth() < shell.depth(), "the swollen core's depth in " + shape);
                assertTrue(peak.height() < shell.height(), "the swollen core's height in " + shape);
                assertEquals(0.825f * shell.height(), peak.height(), TOLERANCE, "the swollen core's height in "
                        + shape);
            }
        }

        @Test
        void theCoresPulseChangesItsSizeAQuarterPulseOn() {
            Extents still = drawn(c -> GooFlightRenderer.emitCore(new PoseStack().last(), c, GooTypes.ZOO, 0f,
                    PRESSED));
            Extents swollen = drawn(c -> GooFlightRenderer.emitCore(new PoseStack().last(), c, GooTypes.ZOO,
                    QUARTER_PULSE, PRESSED));

            assertTrue(swollen.height() > still.height(), "the core should swell a quarter pulse on, from "
                    + still.height() + " to " + swollen.height());
        }

        /** slime-core-draws-translucent-at-the-shells-opacity */
        @Test
        void theCoreDrawsAtTheShellsAlphaInItsSpritesOwnColors() {
            List<RecordingVertexConsumer.Vertex> core = recorded(c -> GooFlightRenderer.emitCore(new PoseStack().last(),
                    c, GooTypes.ZOO, 0f, BlobShape.CUBE));
            List<RecordingVertexConsumer.Vertex> shell = recorded(c -> GooFlightRenderer.emitShell(
                    new PoseStack().last(), c, GooTypes.ZOO, BlobShape.CUBE));

            assertTrue(shell.stream().allMatch(vertex -> ARGB.alpha(vertex.color()) == SHELL_ALPHA));
            for (RecordingVertexConsumer.Vertex vertex : core) {
                assertEquals(SHELL_ALPHA, ARGB.alpha(vertex.color()), "the core's alpha");
                assertEquals(0xFFFFFF, vertex.color() & 0xFFFFFF, "the core's untinted red, green and blue");
            }
        }

        private static Extents drawn(Consumer<VertexConsumer> emitter) {
            return Extents.of(recorded(emitter));
        }

        private static List<RecordingVertexConsumer.Vertex> recorded(Consumer<VertexConsumer> emitter) {
            Minecraft minecraft = mock(Minecraft.class);
            try (MockedStatic<Minecraft> client = mockStatic(Minecraft.class);
                 MockedStatic<GooRenderUtil> util = mockStatic(GooRenderUtil.class, CALLS_REAL_METHODS)) {
                client.when(Minecraft::getInstance).thenReturn(minecraft);
                util.when(() -> GooRenderUtil.lookupFluidSprite(any())).thenReturn(mock(TextureAtlasSprite.class));
                RecordingVertexConsumer recorder = new RecordingVertexConsumer();
                emitter.accept(recorder);
                return recorder.vertices();
            }
        }
    }

    /** The box a set of vertices spans along x (width), z (depth) and y (height). */
    record Extents(float width, float depth, float height) {

        static Extents of(List<RecordingVertexConsumer.Vertex> vertices) {
            return new Extents(span(vertices, RecordingVertexConsumer.Vertex::x),
                    span(vertices, RecordingVertexConsumer.Vertex::z), span(vertices, RecordingVertexConsumer.Vertex::y));
        }

        private static float span(List<RecordingVertexConsumer.Vertex> vertices,
                                  ToDoubleFunction<RecordingVertexConsumer.Vertex> axis) {
            return (float) (vertices.stream().mapToDouble(axis).max().orElseThrow()
                    - vertices.stream().mapToDouble(axis).min().orElseThrow());
        }
    }
}
