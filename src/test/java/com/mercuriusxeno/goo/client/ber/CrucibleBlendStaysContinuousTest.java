package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.MingleNoiseModel;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that the mingle on a crucible surface advances continuously while one
 * type's volume changes (decisions diagnose-then-fix-blend-jump and
 * mingle-noise-keyed-by-type): each frame submits the surface through
 * renderMingledSurface, blends the layers in submission order at the opacity
 * goo_fluid_surface.fsh draws, and measures how much of the surface changed
 * type since the frame before. The operator's recording was a log melting
 * leaf into 1000 mB of blaze; while bands ranked types by volume, the frame
 * leaf passed blaze swapped their layers and inverted the pattern.
 */
class CrucibleBlendStaysContinuousTest {

    private static final int FRAMES = 100;
    private static final int MB_PER_FRAME = 2;
    /** Ticks per day: GameTime is the fraction of the day. */
    private static final float TICKS_PER_DAY = 24000f;
    private static final long START_TICK = 6000L;
    private static final int SAMPLES_PER_SIDE = 40;
    /** The frames before a frame whose largest change bounds that frame's. */
    private static final int WINDOW = 10;
    /** A frame changing more than this many times the window's largest change jumped. */
    private static final float JUMP_RATIO = 3f;
    private static final float WORLD_OFFSET = 7f;
    /**
     * A start volume that holds the moving type off the surface until twice
     * the window has passed, so its arrival is a frame the assertions check.
     */
    private static final int ARRIVES_AFTER_WINDOW = -2 * WINDOW * MB_PER_FRAME;
    /** The reservoir the frames sample: its footprint and height, held fixed. */
    private static final long VOLUME = 2000L;

    /**
     * The goo standing still, the type whose volume changes, its volume on
     * the first frame and its change each frame.
     */
    record Scenario(Map<ResourceKey<GooTypeDefinition>, Integer> still, ResourceKey<GooTypeDefinition> moving,
                    int start, int perFrame) {

        Map<ResourceKey<GooTypeDefinition>, Integer> volumesAt(int frame) {
            Map<ResourceKey<GooTypeDefinition>, Integer> volumes = new HashMap<>(still);
            int moving = Math.max(0, start + frame * perFrame);
            if (moving > 0) {
                volumes.put(this.moving, moving);
            }
            return volumes;
        }

        /**
         * The types present, largest volume first: the ranking a layer-numbered
         * surface drew by, so a frame where it changes is the event under test.
         */
        List<ResourceKey<GooTypeDefinition>> rankingAt(int frame) {
            return volumesAt(frame).entrySet().stream()
                .sorted(Map.Entry.<ResourceKey<GooTypeDefinition>, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey(GooTypes.ORDER)))
                .map(Map.Entry::getKey)
                .toList();
        }

        List<ResourceKey<GooTypeDefinition>> types() {
            List<ResourceKey<GooTypeDefinition>> types = new ArrayList<>(still.keySet());
            types.add(moving);
            return types;
        }
    }

    static Stream<Arguments> scenarios() {
        return Stream.of(
            Arguments.of("leaf melts past 1000 mB of blaze",
                new Scenario(Map.of(GooTypes.BLAZE, 1000), GooTypes.LEAF, 900, MB_PER_FRAME)),
            Arguments.of("crystal arrives between blaze and leaf in the type order",
                new Scenario(Map.of(GooTypes.BLAZE, 1000, GooTypes.LEAF, 1000), GooTypes.CRYSTAL,
                    ARRIVES_AFTER_WINDOW, MB_PER_FRAME)),
            Arguments.of("blaze burns away under crystal and leaf",
                new Scenario(Map.of(GooTypes.CRYSTAL, 500, GooTypes.LEAF, 1000), GooTypes.BLAZE, 100, -MB_PER_FRAME)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void blendAdvancesContinuouslyAsOneTypeChanges(String event, Scenario scenario) {
        CrucibleBasin.PuddleFootprint footprint = CrucibleBasin.footprintForVolume(VOLUME);
        float surfaceY = CrucibleBasin.surfaceYForVolume(VOLUME);
        assertEventFallsPastTheWindow(event, scenario);
        List<ResourceKey<GooTypeDefinition>> types = scenario.types();
        float[][] previous = null;
        List<Float> changes = new ArrayList<>();
        for (int frame = 0; frame < FRAMES; frame++) {
            float gameTime = (START_TICK + frame) / TICKS_PER_DAY;
            float[][] shown = shownTypes(submittedLayers(scenario.volumesAt(frame), footprint, surfaceY),
                types, footprint, surfaceY, gameTime);
            if (previous != null) {
                changes.add(meanChange(previous, shown));
            }
            previous = shown;
        }
        for (int at = WINDOW; at < changes.size(); at++) {
            float bound = 0f;
            for (int before = at - WINDOW; before < at; before++) {
                bound = Math.max(bound, changes.get(before));
            }
            float change = changes.get(at);
            assertTrue(change <= JUMP_RATIO * bound, event + ": frame " + (at + 1) + " at "
                + scenario.volumesAt(at + 1) + " changed " + change + " of the surface, against at most " + bound
                + " in each of the " + WINDOW + " frames before it");
        }
    }

    /** A frame the assertions skip cannot hold the event, or the case passes without testing it. */
    private static void assertEventFallsPastTheWindow(String event, Scenario scenario) {
        List<ResourceKey<GooTypeDefinition>> first = scenario.rankingAt(0);
        int eventFrame = 1;
        while (eventFrame < FRAMES && scenario.rankingAt(eventFrame).equals(first)) {
            eventFrame++;
        }
        assertTrue(eventFrame > WINDOW && eventFrame < FRAMES, event + ": the ranking changes on frame " + eventFrame
            + ", outside the frames " + (WINDOW + 1) + " to " + (FRAMES - 1) + " the assertions check");
    }

    /** One submitted layer: the type its sprite shows and the share and seed its vertices carry. */
    private record Layer(ResourceKey<GooTypeDefinition> type, float share, int seed) {
    }

    private static List<Layer> submittedLayers(Map<ResourceKey<GooTypeDefinition>, Integer> volumes,
                                               CrucibleBasin.PuddleFootprint footprint, float surfaceY) {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        CrucibleRenderState state = mock(CrucibleRenderState.class);
        state.typeBands = TypeBands.over(new GooContents(volumes));
        state.rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;
        List<Layer> layers = new ArrayList<>();
        CrucibleBlockEntityRenderer.renderMingledSurface((band, emitter) -> {
            RecordingVertexConsumer recorder = new RecordingVertexConsumer();
            emitter.accept(RenderContext.banded(new PoseStack().last(), recorder,
                GooRenderUtil.OPAQUE_WHITE, band), sprite());
            RecordingVertexConsumer.Vertex vertex = recorder.vertices().getFirst();
            layers.add(new Layer(band.type(), (float) vertex.uv2U() / TypeBand.SHARE_UNITS, vertex.uv2V()));
        }, state, footprint, surfaceY);
        return layers;
    }

    /**
     * How much each sample point shows each type, 0 to 1, the layers blended
     * over each other in submission order at the opacity goo_fluid_surface.fsh draws.
     */
    private static float[][] shownTypes(List<Layer> layers, List<ResourceKey<GooTypeDefinition>> types,
                                        CrucibleBasin.PuddleFootprint footprint, float surfaceY, float gameTime) {
        float[][] shown = new float[SAMPLES_PER_SIDE * SAMPLES_PER_SIDE][types.size()];
        float span = footprint.max() - footprint.min();
        for (int i = 0; i < SAMPLES_PER_SIDE; i++) {
            for (int j = 0; j < SAMPLES_PER_SIDE; j++) {
                float x = WORLD_OFFSET + footprint.min() + span * (i + 0.5f) / SAMPLES_PER_SIDE;
                float z = WORLD_OFFSET + footprint.min() + span * (j + 0.5f) / SAMPLES_PER_SIDE;
                float[] point = shown[i * SAMPLES_PER_SIDE + j];
                for (Layer layer : layers) {
                    float opacity = MingleNoiseModel.opacity(x, surfaceY, z, gameTime, layer.share(), layer.seed());
                    int drawn = types.indexOf(layer.type());
                    for (int t = 0; t < point.length; t++) {
                        point[t] += ((t == drawn ? 1f : 0f) - point[t]) * opacity;
                    }
                }
            }
        }
        return shown;
    }

    /** The share of the surface that changed type: half the summed per-type change, averaged over the points. */
    private static float meanChange(float[][] before, float[][] after) {
        float sum = 0f;
        for (int k = 0; k < before.length; k++) {
            for (int t = 0; t < before[k].length; t++) {
                sum += Math.abs(after[k][t] - before[k][t]);
            }
        }
        return sum / 2f / before.length;
    }

    private static TextureAtlasSprite sprite() {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.getU1()).thenReturn(1f);
        when(sprite.getV1()).thenReturn(1f);
        return sprite;
    }
}
