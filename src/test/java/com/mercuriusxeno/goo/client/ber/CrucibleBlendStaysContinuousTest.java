package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.MingleNoiseModel;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the one-pass blend on a crucible surface gives each type its
 * volume ratio (decisions mingle-noise-keyed-by-type and one-pass-blends-every-type):
 * the bands a mix lays, blended as goo_fluid_surface.fsh blends them, cover each
 * type's share of the surface. TypeBandsTest holds that a type keeps its place and
 * seed while another type's volume passes it.
 */
class CrucibleBlendStaysContinuousTest {

    /** Ticks per day: GameTime is the fraction of the day. */
    private static final float TICKS_PER_DAY = 24000f;
    private static final long START_TICK = 6000L;
    private static final float WORLD_OFFSET = 7f;
    /** Blocks along each side of the plane the coverage is measured over, one sample per world texel. */
    private static final int COVERAGE_BLOCKS = 24;
    private static final int WORLD_TEXELS = 16;
    /** How far a type's measured coverage may stand from its volume ratio over that plane. */
    private static final double COVERAGE_TOLERANCE = 0.04;

    static Stream<Arguments> mixes() {
        return Stream.of(
            Arguments.of(Map.of(GooTypes.BLAZE, 250, GooTypes.FROST, 750)),
            Arguments.of(Map.of(GooTypes.ASTRAL, 100, GooTypes.BLAZE, 600, GooTypes.FROST, 300)),
            Arguments.of(Map.of(GooTypes.CRYSTAL, 500, GooTypes.LEAF, 400, GooTypes.BLAZE, 100)));
    }

    @ParameterizedTest
    @MethodSource("mixes")
    void eachTypeCoversItsVolumeRatio(Map<ResourceKey<GooTypeDefinition>, Integer> volumes) {
        List<TypeBand> bands = TypeBands.over(new GooContents(volumes));
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
}
