package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's terrain repeats every 15,000,000 blocks on x and z: every noise its generation reads, built from the shipped
 * noise files and repeating as lunar's random state hands them out, and every goo field its terrain and caves add,
 * reads the same just past +7,500,000 as just past -7,500,000, and runs as smoothly across the seam as beside it.
 * Lunar's terrain composes these column by column through vanilla's pointwise operations, so it repeats with them;
 * LunarTerrainTests reads the live terrain itself.
 * decision moon-wraps-at-its-border-like-the-zone
 */
class LunarTerrainRepeatsTest {

    private static final int SEAM = 7_500_000;
    private static final long SEED = 20_261_127L;
    private static final List<String> NOISES = List.of("lunar_basins", "lunar_craters", "lunar_crystal_caverns",
            "lunar_eldritch_ground", "lunar_highlands", "lunar_lava_tubes", "lunar_maria", "lunar_passes",
            "lunar_range_mask", "lunar_ranges", "lunar_ridges", "lunar_rilles", "lunar_rubble", "lunar_rubble_caves",
            "lunar_sea_floor", "lunar_sea_pillars", "lunar_sea_roof", "lunar_warrens_first", "lunar_warrens_second");
    /** Steps past the seam, spread over several of every field's cells. */
    private static final int[] PAST_THE_SEAM = {0, 1, 7, 40, 333, 641, 1_919, 4_096};
    private static final int[] ACROSS = {0, 2_345, -SEAM + 3, SEAM - 12};
    private static final int[] HEIGHTS = {-110, -40, 0, 60, 120};
    /** A field is sampled this far past each seam, every this many blocks, through the moon's whole height. */
    private static final int FIELD_REACH_PAST = 1_300;
    private static final int FIELD_STEP = 13;
    private static final int LOWEST_Y = -128;
    private static final int HIGHEST_Y = 160;
    private static final int HEIGHT_STEP = 8;
    /** Giant craters stand hundreds of blocks apart, so their field is sampled further out and more coarsely. */
    private static final int GIANT_CRATER_REACH_PAST = 6_000;
    private static final int GIANT_CRATER_STEP = 37;
    /** The columns beside the seam whose steps the step across it is held to. */
    private static final int BESIDE = 64;
    private static final float SMOOTHNESS_HEADROOM = 3.0F;

    private static Noise lunarNoise(String name) {
        String path = "/data/goo/worldgen/noise/" + name + ".json";
        try (InputStream stream = LunarTerrainRepeatsTest.class.getResourceAsStream(path)) {
            JsonElement json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            NormalNoise noise = NormalNoise.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            return RepeatingNoise.repeating(noise.create(RandomSource.create(SEED + name.hashCode())),
                    LunarSeam.PERIOD);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Stream<String> noises() {
        return NOISES.stream();
    }

    /**
     * A goo field under test, sampled every step blocks out to its reach past each seam.
     *
     * @param name    the field's name
     * @param sampler the field
     * @param reach   how far past each seam it is sampled, in blocks
     * @param step    how far apart its samples stand, in blocks
     */
    record Field(String name, DensitySampler sampler, int reach, int step) {

        Field(String name, DensitySampler sampler) {
            this(name, sampler, FIELD_REACH_PAST, FIELD_STEP);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Field> fields() {
        Noise craters = lunarNoise("lunar_craters");
        Noise maria = lunarNoise("lunar_maria");
        return Stream.of(
                new Field("giant craters", new GiantCraterField(craters, maria, lunarNoise("lunar_basins")),
                        GIANT_CRATER_REACH_PAST, GIANT_CRATER_STEP),
                new Field("rilles", new RilleField(lunarNoise("lunar_rilles"), maria,
                        lunarNoise("lunar_eldritch_ground"))),
                new Field("lava tubes", new LavaTubes(lunarNoise("lunar_lava_tubes"))),
                new Field("sea cavern", new SeaCavern(lunarNoise("lunar_sea_floor"), lunarNoise("lunar_sea_roof"),
                        lunarNoise("lunar_sea_pillars"))),
                new Field("warrens", new WarrenTunnels(lunarNoise("lunar_warrens_first"),
                        lunarNoise("lunar_warrens_second"))),
                new Field("rubble caves", new RubbleCaves(lunarNoise("lunar_rubble_caves"),
                        lunarNoise("lunar_rubble"))),
                new Field("crystal caverns", new CrystalCaverns(lunarNoise("lunar_crystal_caverns"))));
    }

    @Nested
    class Noises {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.lunar.LunarTerrainRepeatsTest#noises")
        void aNoiseReadsTheSameJustPastEitherEdgeOnX(String name) {
            Noise noise = lunarNoise(name);
            for (int past : PAST_THE_SEAM) {
                for (int z : ACROSS) {
                    for (int y : HEIGHTS) {
                        assertEquals(noise.get(-SEAM + past, y, z), noise.get(SEAM + past, y, z),
                                name + " at " + past + " past the seam, y " + y + ", z " + z);
                    }
                }
            }
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.lunar.LunarTerrainRepeatsTest#noises")
        void aNoiseReadsTheSameJustPastEitherEdgeOnZ(String name) {
            Noise noise = lunarNoise(name);
            for (int past : PAST_THE_SEAM) {
                for (int x : ACROSS) {
                    assertEquals(noise.get(x, 0.0, -SEAM + past), noise.get(x, 0.0, SEAM + past),
                            name + " at " + past + " past the seam, x " + x);
                }
            }
        }

        /** The step from the last column before the seam to the first past it is one more step of the same noise. */
        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.lunar.LunarTerrainRepeatsTest#noises")
        void aNoiseRunsAsSmoothlyAcrossTheSeamAsBesideIt(String name) {
            Noise noise = lunarNoise(name);
            float largestStepBeside = 0.0F;
            for (int i = 1; i <= BESIDE; i++) {
                largestStepBeside = Math.max(largestStepBeside,
                        Math.abs(noise.get(SEAM - i, 0.0, 0.0) - noise.get(SEAM - i - 1, 0.0, 0.0)));
                largestStepBeside = Math.max(largestStepBeside,
                        Math.abs(noise.get(-SEAM + i, 0.0, 0.0) - noise.get(-SEAM + i - 1, 0.0, 0.0)));
            }
            float stepAcross = Math.abs(noise.get(-SEAM, 0.0, 0.0) - noise.get(SEAM - 1, 0.0, 0.0));
            assertTrue(stepAcross <= largestStepBeside * SMOOTHNESS_HEADROOM,
                    name + " steps " + stepAcross + " across the seam against " + largestStepBeside + " beside it");
        }
    }

    @Nested
    class Fields {

        /** The field is read through the moon's whole height, so each cave opens somewhere in the sample. */
        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.lunar.LunarTerrainRepeatsTest#fields")
        void aFieldReadsTheSameJustPastEitherEdgeOnX(Field field) {
            Set<Float> read = new HashSet<>();
            for (int past = 0; past < field.reach(); past += field.step()) {
                for (int z : ACROSS) {
                    for (int y = LOWEST_Y; y <= HIGHEST_Y; y += HEIGHT_STEP) {
                        float west = sample(field, -SEAM + past, y, z);
                        read.add(west);
                        assertEquals(west, sample(field, SEAM + past, y, z),
                                field + " at " + past + " past the seam, y " + y + ", z " + z);
                    }
                }
            }
            assertTrue(read.size() > 1, field + " reads one value over the whole sample, so it proves nothing");
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.lunar.LunarTerrainRepeatsTest#fields")
        void aFieldReadsTheSameJustPastEitherEdgeOnZ(Field field) {
            Set<Float> read = new HashSet<>();
            for (int past = 0; past < field.reach(); past += field.step()) {
                for (int x : ACROSS) {
                    for (int y = LOWEST_Y; y <= HIGHEST_Y; y += HEIGHT_STEP) {
                        float north = sample(field, x, y, -SEAM + past);
                        read.add(north);
                        assertEquals(north, sample(field, x, y, SEAM + past),
                                field + " at " + past + " past the seam, y " + y + ", x " + x);
                    }
                }
            }
            assertTrue(read.size() > 1, field + " reads one value over the whole sample, so it proves nothing");
        }

        private static float sample(Field field, int x, int y, int z) {
            return field.sampler().sampleValue(SamplerContext.EMPTY_UNCACHED, x, y, z);
        }
    }
}
