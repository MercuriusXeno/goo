package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The craters pocking lunar's surface, as the height in blocks they add to the ground at a column: a bowl sunk
 * below the ground, ringed by a raised rim that falls back to the ground outside it. The ground is cut into square
 * cells, each holding one crater or none, its place and size rolled from the world's seeded noise, so every world
 * draws its own craters and a column reads the same craters on every call.
 * decision lunar-generates-from-its-own-noise-and-surface-rules
 *
 * @param noise the seeded noise each cell's rolls read
 */
public record CraterField(DensityFunction.NoiseHolder noise) implements DensityFunction {

    /** The data codec of the {@code goo:craters} density function type, naming the noise its rolls read. */
    public static final MapCodec<CraterField> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(CraterField::noise)
    ).apply(instance, CraterField::new));
    private static final KeyDispatchDataCodec<CraterField> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    /** A cell's side in blocks; a crater's farthest reach stays under it, so a column reads its own cell's
     * neighbours alone. */
    static final int CELL = 40;
    /** The share of cells holding a crater. */
    static final double CRATER_CHANCE = 0.55;
    static final double MIN_RADIUS = 5.0;
    /** The radius a cell adds over the least, at its largest; squared rolls make small craters common. */
    static final double RADIUS_SPREAD = 17.0;
    /** A bowl sinks a third of its radius, and its rim rises an eighth, the shape fresh lunar craters keep. */
    static final double DEPTH_PER_RADIUS = 0.35;
    static final double RIM_PER_RADIUS = 0.12;
    /** The rim falls back to the ground at 1.6 radii. */
    static final double RIM_REACH = 0.6;

    private static final int PRESENCE = 0;
    private static final int OFFSET_X = 1;
    private static final int OFFSET_Z = 2;
    private static final int SIZE = 3;
    /** Spreads one cell's rolls apart in the noise, so neighbouring cells and rolls read unrelated values. */
    private static final double CELL_STRIDE = 7.31;
    private static final double ROLL_STRIDE = 101.7;
    /** Amplifies the noise before its fraction is read, so the fraction spreads near evenly over [0, 1). */
    private static final double ROLL_SPREAD = 4096.0;
    private static final double MAX_RADIUS = MIN_RADIUS + RADIUS_SPREAD;
    /** A column reads its own cell and the cells one step around it. */
    private static final int NEIGHBOUR_REACH = 1;
    /** Nine cells' craters at their deepest and their highest bound the field. */
    private static final int NEIGHBOURHOOD = 9;

    /**
     * Rolls one value in [0, 1) for a cell.
     */
    @FunctionalInterface
    interface CellRolls {
        /**
         * @param cellX the cell's x
         * @param cellZ the cell's z
         * @param roll  which of the cell's rolls
         * @return the roll, in [0, 1)
         */
        double roll(int cellX, int cellZ, int roll);
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return heightAt(context.blockX(), context.blockZ(), this::roll);
    }

    private double roll(int cellX, int cellZ, int roll) {
        double value = noise.getValue(cellX * CELL_STRIDE + roll * ROLL_STRIDE, roll, cellZ * CELL_STRIDE);
        return Mth.frac(value * ROLL_SPREAD);
    }

    /**
     * The height every crater reaching a column adds to its ground, in blocks: below zero in a bowl, above it on a
     * rim, zero on open ground.
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls each cell's rolls
     * @return the height the craters add
     */
    static double heightAt(int x, int z, CellRolls rolls) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        double height = 0.0;
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                height += craterHeight(x, z, cellX + dx, cellZ + dz, rolls);
            }
        }
        return height;
    }

    private static double craterHeight(int x, int z, int cellX, int cellZ, CellRolls rolls) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= CRATER_CHANCE) {
            return 0.0;
        }
        double centerX = (cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL;
        double centerZ = (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL;
        double size = rolls.roll(cellX, cellZ, SIZE);
        double radius = MIN_RADIUS + size * size * RADIUS_SPREAD;
        double reach = Math.hypot(x - centerX, z - centerZ) / radius;
        return profile(reach, radius);
    }

    /**
     * A crater's height at a distance from its center: the bowl rises from its floor to the rim crest at one
     * radius, and the rim falls back to the ground by 1.6 radii.
     *
     * @param reach  the distance from the center, in radii
     * @param radius the crater's radius in blocks
     * @return the height the crater adds there
     */
    static double profile(double reach, double radius) {
        double depth = radius * DEPTH_PER_RADIUS;
        double rim = radius * RIM_PER_RADIUS;
        if (reach < 1.0) {
            return (depth + rim) * reach * reach - depth;
        }
        double fall = 1.0 - (reach - 1.0) / RIM_REACH;
        return fall > 0.0 ? rim * fall * fall : 0.0;
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new CraterField(visitor.visitNoise(noise)));
    }

    @Override
    public double minValue() {
        return -NEIGHBOURHOOD * MAX_RADIUS * DEPTH_PER_RADIUS;
    }

    @Override
    public double maxValue() {
        return NEIGHBOURHOOD * MAX_RADIUS * RIM_PER_RADIUS;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
