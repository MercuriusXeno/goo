package com.mercuriusxeno.goo.lunar;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * Lunar's seeded noises built from the shipped noises' settings and a fixed seed, with no registry, for tests that
 * read the fields a world draws.
 */
final class LunarTestNoises {

    private static final long SEED = 20_261_126L;

    private LunarTestNoises() {
    }

    /** The crater noise, as data/goo/worldgen/noise/lunar_craters.json sets it. */
    static Noise crater() {
        return NormalNoise.builder().setBaseOctave(0).setBaseAmplitude(0.955388882960065).build()
                .create(RandomSource.create(SEED));
    }

    /** The maria noise, as data/goo/worldgen/noise/lunar_maria.json sets it. */
    static Noise maria() {
        return NormalNoise.builder().setBaseOctave(-9).setOctaveCount(2).setAmplitudeModifier(1, 0.5)
                .setBaseAmplitude(1.0504451407960578).build().create(RandomSource.create(SEED + 1));
    }

    /** The basins noise, as data/goo/worldgen/noise/lunar_basins.json sets it. */
    static Noise basins() {
        return NormalNoise.builder().setBaseOctave(-10).setOctaveCount(2).setAmplitudeModifier(1, 0.5)
                .setBaseAmplitude(1.0).build().create(RandomSource.create(SEED + 2));
    }
}
