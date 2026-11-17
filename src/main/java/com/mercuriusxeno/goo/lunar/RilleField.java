package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The rilles winding through lunar's basalt basins, as the height in blocks they cut from the ground at a column:
 * long steep-walled channels like dry riverbeds, following the zero line of their own noise, cut only where the maria
 * noise reads basin.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 *
 * @param line  the noise whose zero line a rille follows
 * @param maria the noise reading basin above zero
 */
public record RilleField(DensityFunction.NoiseHolder line, DensityFunction.NoiseHolder maria) implements DensityFunction {

    /** The data codec of the {@code goo:rilles} density function type, naming its line noise and the maria noise. */
    public static final MapCodec<RilleField> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("line").forGetter(RilleField::line),
            DensityFunction.NoiseHolder.CODEC.fieldOf("maria").forGetter(RilleField::maria)
    ).apply(instance, RilleField::new));
    private static final KeyDispatchDataCodec<RilleField> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    /** The noise lunar's rilles follow the zero line of. */
    public static final ResourceKey<NormalNoise.NoiseParameters> LINE_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_rilles"));
    /** The noise reading lunar's basins above zero. */
    public static final ResourceKey<NormalNoise.NoiseParameters> MARIA_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_maria"));

    /** How deep a rille cuts on its line, in blocks. */
    static final double DEPTH = 10.0;
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
     * The rille field a world's seeded noises draw, as its random state wires {@link #LINE_NOISE} and
     * {@link #MARIA_NOISE}.
     *
     * @param line  the seeded line noise
     * @param maria the seeded maria noise
     * @return the field those noises draw
     */
    public static RilleField seeded(NormalNoise line, NormalNoise maria) {
        return new RilleField(new DensityFunction.NoiseHolder(Holder.direct(line.parameters()), line),
                new DensityFunction.NoiseHolder(Holder.direct(maria.parameters()), maria));
    }

    /**
     * The height the rilles cut at a column in the world this field's noises are seeded for.
     *
     * @param x the column's x
     * @param z the column's z
     * @return the cut, at most zero
     */
    public double cutAt(int x, int z) {
        return profile(line.getValue(x, 0.0, z), maria.getValue(x, 0.0, z));
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
    public double compute(DensityFunction.FunctionContext context) {
        return cutAt(context.blockX(), context.blockZ());
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new RilleField(visitor.visitNoise(line), visitor.visitNoise(maria)));
    }

    @Override
    public double minValue() {
        return -DEPTH;
    }

    @Override
    public double maxValue() {
        return 0.0;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
