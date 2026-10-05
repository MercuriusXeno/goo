package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.client.ability.Afterimages.GROWTH_BLOCKS;
import static com.mercuriusxeno.goo.client.ability.Afterimages.PULSES;
import static com.mercuriusxeno.goo.client.ability.Afterimages.PULSE_GAP_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An afterimage plays as a ripple of four silhouettes leaving a beat apart,
 * each growing outward and fading over its life, and draws through its own
 * shader pair (decision afterimage-is-one-shared-effect).
 */
class AfterimagesTest {

    private static final long LEFT_AT = 500;
    private static final int LIFE = 12;
    private static final int ENDER = 0x2A9D8F;
    private static final float EPSILON = 1e-4f;

    private static Afterimages.Afterimage<String> ripple() {
        return new Afterimages.Afterimage<>("player", Vec3.ZERO, ENDER, LEFT_AT, LIFE);
    }

    @Nested
    class Pulses {

        @Test
        void rippleSendsFourSilhouettes() {
            assertEquals(4, PULSES);
        }

        @Test
        void oneSilhouetteStandsWhenTheRippleIsLeft() {
            List<Afterimages.Pulse> pulses = ripple().pulses(LEFT_AT);

            assertEquals(List.of(new Afterimages.Pulse(0f, 0xFF)), pulses);
        }

        @Test
        void eachSilhouetteLeavesAGapAfterTheOneBefore() {
            Afterimages.Afterimage<String> longLived = new Afterimages.Afterimage<>("player", Vec3.ZERO, ENDER, LEFT_AT,
                    PULSES * PULSE_GAP_TICKS);

            assertEquals(1, longLived.pulses(LEFT_AT + PULSE_GAP_TICKS - 0.5f).size());
            assertEquals(2, longLived.pulses(LEFT_AT + PULSE_GAP_TICKS).size());
            assertEquals(PULSES, longLived.pulses(LEFT_AT + (PULSES - 1) * PULSE_GAP_TICKS).size());
            assertEquals(PULSES, longLived.pulses(LEFT_AT + PULSES * PULSE_GAP_TICKS - 0.5f).size(),
                    "a ninth silhouette left");
        }

        @Test
        void theLastSilhouetteLeavesFromTheBodyAGapAfterTheOneBefore() {
            List<Afterimages.Pulse> pulses = ripple().pulses(LEFT_AT + (PULSES - 1) * PULSE_GAP_TICKS);

            assertEquals(0f, pulses.getLast().growth());
            assertEquals(0xFF, pulses.getLast().alpha());
        }

        @Test
        void olderSilhouettesStandFurtherOutAndFainter() {
            List<Afterimages.Pulse> pulses = ripple().pulses(LEFT_AT + (PULSES - 1) * PULSE_GAP_TICKS);

            for (int older = 0; older < pulses.size() - 1; older++) {
                assertTrue(pulses.get(older).growth() > pulses.get(older + 1).growth(), "growth at " + older);
                assertTrue(pulses.get(older).alpha() < pulses.get(older + 1).alpha(), "alpha at " + older);
            }
        }

        @Test
        void aSilhouetteGrowsOnAnEaseOutAndFadesLinearly() {
            Afterimages.Pulse halfway = ripple().pulses(LEFT_AT + LIFE / 2f).getFirst();

            assertEquals(GROWTH_BLOCKS * 0.75f, halfway.growth(), EPSILON);
            assertEquals(Math.round(0xFF / 2f), halfway.alpha());
        }
    }

    @Nested
    class Life {

        @Test
        void rippleStandsUntilItsLastSilhouetteHasFaded() {
            Afterimages<String> afterimages = new Afterimages<>();
            afterimages.add("source", new Vec3(1, 64, 1), ENDER, LEFT_AT, LIFE);
            long lastFaded = LEFT_AT + (long) (PULSES - 1) * PULSE_GAP_TICKS + LIFE;

            assertEquals(1, afterimages.live(lastFaded - 1).size());
            assertTrue(ripple().pulses(lastFaded - 1).size() == 1);
            assertTrue(afterimages.live(lastFaded).isEmpty());
        }

        @Test
        void clearDropsEveryRipple() {
            Afterimages<String> afterimages = new Afterimages<>();
            afterimages.add("source", Vec3.ZERO, ENDER, LEFT_AT, LIFE);

            afterimages.clear();

            assertTrue(afterimages.live(LEFT_AT).isEmpty());
        }
    }

    @Nested
    class Pipelines {

        @Test
        void eachSilhouetteFillsAChannelOfItsOwn() {
            assertEquals(PULSES, GooRenderTypes.GOO_RIPPLE_MASKS.size());
            assertEquals(List.of(ColorTargetState.WRITE_RED, ColorTargetState.WRITE_GREEN, ColorTargetState.WRITE_BLUE,
                            ColorTargetState.WRITE_ALPHA),
                    GooRenderTypes.GOO_RIPPLE_MASKS.stream().map(mask -> mask.getColorTargetState().writeMask())
                            .toList());
            for (RenderPipeline mask : GooRenderTypes.GOO_RIPPLE_MASKS) {
                assertTrue(mask.getColorTargetState().blendFunction().isEmpty(), "a mask blends into another");
                assertFalse(mask.getDepthStencilState().writeDepth(), "a mask writes the world's depth");
                PipelineShaders.assertExist(mask);
            }
        }

        @Test
        void maskChannelsMatchTheEdgePassChannels() {
            assertEquals(AfterimageRenderer.CHANNELS, GooRenderTypes.GOO_RIPPLE_MASK_TYPES.size());
        }

        @Test
        void edgePassReadsTheMasksOverTheWholeScreen() {
            RenderPipeline edge = GooRenderTypes.GOO_RIPPLE_EDGE;

            assertEquals("core/screenquad", edge.getVertexShader().getPath());
            assertEquals("core/goo_ripple_edge", edge.getFragmentShader().getPath());
            PipelineShaders.assertExist(edge);
        }
    }
}
