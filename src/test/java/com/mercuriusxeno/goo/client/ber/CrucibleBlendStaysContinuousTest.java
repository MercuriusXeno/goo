package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.MingleNoiseModel;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests that the one-pass blend on a crucible surface gives each type its
 * volume ratio and advances continuously while one type's volume changes
 * (decisions diagnose-then-fix-blend-jump, mingle-noise-keyed-by-type and
 * one-pass-blends-every-type): each frame submits the surface through
 * renderMingledSurface, blends its bands as goo_fluid_surface.fsh does, and
 * measures how much of the surface changed type since the frame before. The
 * operator's recording was a log melting leaf into 1000 mB of blaze; while
 * bands ranked types by volume, the frame leaf passed blaze swapped their
 * layers and inverted the pattern.
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
    /** Blocks along each side of the plane the coverage is measured over, one sample per world texel. */
    private static final int COVERAGE_BLOCKS = 24;
    private static final int WORLD_TEXELS = 16;
    /** How far a type's measured coverage may stand from its volume ratio over that plane. */
    private static final double COVERAGE_TOLERANCE = 0.04;

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
            float[][] shown = shownTypes(submittedBands(scenario.volumesAt(frame), footprint, surfaceY),
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

    static Stream<Arguments> mixes() {
        return Stream.of(
            Arguments.of(Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750)),
            Arguments.of(Map.of(GooTypes.ASTRAL, 100, GooTypes.BLAZE, 600, GooTypes.FROST, 300)),
            Arguments.of(Map.of(GooTypes.CRYSTAL, 500, GooTypes.LEAF, 400, GooTypes.BLAZE, 100)));
    }

    @ParameterizedTest
    @MethodSource("mixes")
    void eachTypeCoversItsVolumeRatio(Map<ResourceKey<GooTypeDefinition>, Integer> volumes) {
        List<TypeBand> bands = submittedBands(volumes, CrucibleBasin.footprintForVolume(VOLUME),
            CrucibleBasin.surfaceYForVolume(VOLUME));
        float[] shares = new float[bands.size()];
        float[] seeds = new float[bands.size()];
        for (int k = 0; k < bands.size(); k++) {
            shares[k] = bands.get(k).share();
            seeds[k] = bands.get(k).seed();
        }
        float gameTime = START_TICK / TICKS_PER_DAY;
        double[] covered = new double[bands.size()];
        int texels = COVERAGE_BLOCKS * WORLD_TEXELS;
        for (int i = 0; i < texels; i++) {
            for (int j = 0; j < texels; j++) {
                float[] shown = MingleNoiseModel.blend((i + 0.5f) / WORLD_TEXELS, WORLD_OFFSET,
                    (j + 0.5f) / WORLD_TEXELS, gameTime, shares, seeds);
                for (int k = 0; k < shown.length; k++) {
                    covered[k] += shown[k];
                }
            }
        }
        int total = volumes.values().stream().mapToInt(Integer::intValue).sum();
        for (int k = 0; k < bands.size(); k++) {
            float ratio = (float) volumes.get(bands.get(k).type()) / total;
            double coverage = covered[k] / ((double) texels * texels);
            assertEquals(ratio, coverage, COVERAGE_TOLERANCE, bands.get(k).type() + " covers "
                + coverage + " of the surface, against its volume ratio " + ratio);
        }
    }

    private static List<TypeBand> submittedBands(Map<ResourceKey<GooTypeDefinition>, Integer> volumes,
                                                 CrucibleBasin.PuddleFootprint footprint, float surfaceY) {
        // BlockEntityRenderState's constructor bootstraps Blocks, so the state is built without it.
        CrucibleRenderState state = mock(CrucibleRenderState.class);
        state.typeBands = TypeBands.over(new GooContents(volumes));
        state.rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;
        List<TypeBand> submitted = new ArrayList<>();
        CrucibleBlockEntityRenderer.renderMingledSurface((bands, emitter) -> {
            emitter.accept(new RenderContext(new PoseStack().last(), new RecordingVertexConsumer(), 0));
            submitted.addAll(bands);
        }, state, footprint, surfaceY);
        return submitted;
    }

    /**
     * How much each sample point shows each type, 0 to 1, the submission's
     * bands blended in one pass as goo_fluid_surface.fsh blends them.
     */
    private static float[][] shownTypes(List<TypeBand> bands, List<ResourceKey<GooTypeDefinition>> types,
                                        CrucibleBasin.PuddleFootprint footprint, float surfaceY, float gameTime) {
        float[] shares = new float[bands.size()];
        float[] seeds = new float[bands.size()];
        for (int k = 0; k < bands.size(); k++) {
            shares[k] = bands.get(k).share();
            seeds[k] = bands.get(k).seed();
        }
        float[][] shown = new float[SAMPLES_PER_SIDE * SAMPLES_PER_SIDE][types.size()];
        float span = footprint.max() - footprint.min();
        for (int i = 0; i < SAMPLES_PER_SIDE; i++) {
            for (int j = 0; j < SAMPLES_PER_SIDE; j++) {
                float x = WORLD_OFFSET + footprint.min() + span * (i + 0.5f) / SAMPLES_PER_SIDE;
                float z = WORLD_OFFSET + footprint.min() + span * (j + 0.5f) / SAMPLES_PER_SIDE;
                float[] layers = MingleNoiseModel.blend(x, surfaceY, z, gameTime, shares, seeds);
                float[] point = shown[i * SAMPLES_PER_SIDE + j];
                for (int k = 0; k < layers.length; k++) {
                    point[types.indexOf(bands.get(k).type())] += layers[k];
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
}
