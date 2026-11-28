package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The rilles winding through lunar's basalt basins, as the height in blocks they cut from the ground at a column:
 * long steep-walled channels like dry riverbeds, following the zero line of their own noise, cut only in maria. The
 * biome source places the biome whose climate point lies nearest, so where the eldritch noise reads past its floor
 * by more than the maria noise reads past zero, eldritch ground wins the column; a rille reads maria as the maria
 * noise less that eldritch excess, and cuts only where it stays above zero, so it cuts in maria alone.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 * decision moon-is-288-tall-half-of-today
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 *
 * @param line     the noise whose zero line a rille follows
 * @param maria    the noise reading basin above zero
 * @param eldritch the noise reading eldritch ground past {@link #ELDRITCH_FLOOR}
 */
public record RilleField(Noise line, Noise maria, Noise eldritch) implements DensitySampler {

    /**
     * The data codec of the {@code goo:rilles} density function type, naming its line noise, the maria noise and the
     * eldritch noise.
     */
    public static final MapCodec<Definition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NormalNoise.CODEC.fieldOf("line").forGetter(Definition::line),
            NormalNoise.CODEC.fieldOf("maria").forGetter(Definition::maria),
            NormalNoise.CODEC.fieldOf("eldritch").forGetter(Definition::eldritch)
    ).apply(instance, Definition::new));
    /** The noise reading lunar's eldritch ground, which the biome source reads as temperature. */
    public static final ResourceKey<NormalNoise> ELDRITCH_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_eldritch_ground"));
    /** The eldritch reading from which the dimension's biome source places eldritch ground. */
    static final double ELDRITCH_FLOOR = 0.45;
    /** A rille keeps this far inside maria's edge with eldritch ground, clear of the biome source's rounding. */
    static final double EDGE_MARGIN = 0.001;
    /** The noise lunar's rilles follow the zero line of. */
    public static final ResourceKey<NormalNoise> LINE_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_rilles"));
    /** The noise reading lunar's basins above zero. */
    public static final ResourceKey<NormalNoise> MARIA_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_maria"));

    /** How deep a rille cuts on its line, in blocks. */
    static final double DEPTH = 5.0;
    /** How far the line noise strays from zero before the channel's walls reach the ground. */
    static final double HALF_WIDTH = 0.04;
    /** The maria reading past which a rille cuts its full depth; it fades in from the basin's edge at zero. */
    static final double BASIN_DEPTH = 0.05;
    /** The power the channel's wall rises at; a high one keeps the floor flat and the walls steep. */
    private static final int WALL_STEEPNESS = 6;

    /**
     * The height a rille cuts at a column, from the column's line and maria readings.
     *
     * @param lineValue  the line noise at the column
     * @param mariaValue the maria noise at the column
     * @return the cut, at most zero
     */
    static double profile(double lineValue, double mariaValue) {
        double across = Math.abs(lineValue) / HALF_WIDTH;
        if (across >= 1.0 || mariaValue <= 0.0) {
            return 0.0;
        }
        double basin = Math.min(1.0, mariaValue / BASIN_DEPTH);
        return -DEPTH * basin * (1.0 - Math.pow(across, WALL_STEEPNESS));
    }

    /**
     * The height a rille cuts at a column, from the column's line, maria and eldritch readings: the maria reading,
     * less however far the eldritch reading passes its floor, stands as the maria reading the channel cuts by.
     *
     * @param lineValue     the line noise at the column
     * @param mariaValue    the maria noise at the column
     * @param eldritchValue the eldritch noise at the column
     * @return the cut, at most zero
     */
    static double profile(double lineValue, double mariaValue, double eldritchValue) {
        return profile(lineValue, mariaValue - Math.max(0.0, eldritchValue - ELDRITCH_FLOOR) - EDGE_MARGIN);
    }

    /**
     * The rille field a world's seeded noises draw, as its random state wires {@link #LINE_NOISE},
     * {@link #MARIA_NOISE} and {@link #ELDRITCH_NOISE}.
     *
     * @param line     the seeded line noise
     * @param maria    the seeded maria noise
     * @param eldritch the seeded eldritch noise
     * @return the field those noises draw
     */
    public static RilleField seeded(Noise line, Noise maria, Noise eldritch) {
        return new RilleField(line, maria, eldritch);
    }

    /**
     * The height the rilles cut at a column in the world this field's noises are seeded for.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the cut, at most zero
     */
    public double cutAt(int x, int z) {
        return profile(line.get(x, 0.0, z), maria.get(x, 0.0, z), eldritch.get(x, 0.0, z));
    }

    /**
     * Whether a column lies on a rille's line, cut to its full depth.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true on a rille's floor
     */
    public boolean isOnLineAt(int x, int z) {
        return cutAt(x, z) <= -DEPTH;
    }

    @Override
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return (float) densityAt(blockX, blockY, blockZ);
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
    }

    private double densityAt(int blockX, int blockY, int blockZ) {
        return cutAt(blockX, blockZ);
    }

    /**
     * The density function lunar's noise settings name, compiling to the field its noises draw once the world's
     * random state seeds them.
     *
     * @param line     the noise whose zero line a rille follows
     * @param maria    the noise reading basin above zero
     * @param eldritch the noise reading eldritch ground past its floor
     */
    public record Definition(Holder<NormalNoise> line, Holder<NormalNoise> maria, Holder<NormalNoise> eldritch)
            implements DensityFunction {

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return new RilleField(context.createNoiseSampler(line), context.createNoiseSampler(maria),
                    context.createNoiseSampler(eldritch));
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return this;
        }

        @Override
        public Interval range() {
            return Interval.of((float) (-DEPTH), (float) (0.0));
        }

        @Override
        public @DensityFunction.Axes int domainAxes() {
            return DensityFunction.ALL_AXES;
        }

        @Override
        public MapCodec<Definition> codec() {
            return DATA_CODEC;
        }
    }
}
