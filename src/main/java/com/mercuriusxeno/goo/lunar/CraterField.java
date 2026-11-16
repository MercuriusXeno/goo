package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

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
    /** The noise lunar's {@code goo:craters} density function rolls its cells from. */
    public static final ResourceKey<NormalNoise.NoiseParameters> NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_craters"));

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
    /** A crater's floor reaches half its radius from its centre, the flat of the bowl where impacts melt the ground. */
    static final double FLOOR_REACH = 0.5;
    /**
     * The radius past which a crater is one of the deepest, whose floor holds crater ice; lunar's noise settings name
     * it in their crater ice rule.
     */
    public static final double DEEP_CRATER_RADIUS = 16.0;
    /** Crater ice fills a deep crater's floor out to this many radii from its centre, inside the impact glass. */
    public static final double ICE_REACH = 0.35;

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
        double radius = radius(cellX, cellZ, rolls);
        return profile(reach(x, z, cellX, cellZ, radius, rolls), radius);
    }

    private static double radius(int cellX, int cellZ, CellRolls rolls) {
        double size = rolls.roll(cellX, cellZ, SIZE);
        return MIN_RADIUS + size * size * RADIUS_SPREAD;
    }

    /** A column's distance from a cell's crater centre, in that crater's radii. */
    private static double reach(int x, int z, int cellX, int cellZ, double radius, CellRolls rolls) {
        double centerX = (cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL;
        double centerZ = (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL;
        return Math.hypot(x - centerX, z - centerZ) / radius;
    }

    /**
     * Whether some crater reaches a column within a band of its radii, measured from its centre at zero; its rim's
     * fall ends at one plus {@link #RIM_REACH}.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     *
     * @param x        the column's x
     * @param z        the column's z
     * @param rolls    each cell's rolls
     * @param minReach the band's inner edge, in radii, inclusive
     * @param maxReach the band's outer edge, in radii, exclusive
     * @return true where a crater's reach at the column falls in the band
     */
    static boolean reachesBand(int x, int z, CellRolls rolls, double minReach, double maxReach) {
        return reachesBand(x, z, rolls, minReach, maxReach, 0.0);
    }

    /**
     * Whether some crater of at least a radius reaches a column within a band of its radii.
     *
     * @param x         the column's x
     * @param z         the column's z
     * @param rolls     each cell's rolls
     * @param minReach  the band's inner edge, in radii, inclusive
     * @param maxReach  the band's outer edge, in radii, exclusive
     * @param minRadius the least radius a crater counts at, in blocks
     * @return true where a crater that large reaches the column within the band
     */
    static boolean reachesBand(int x, int z, CellRolls rolls, double minReach, double maxReach, double minRadius) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                if (craterReachFallsInBand(x, z, cellX + dx, cellZ + dz, rolls, minReach, maxReach, minRadius)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean craterReachFallsInBand(int x, int z, int cellX, int cellZ, CellRolls rolls,
            double minReach, double maxReach, double minRadius) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= CRATER_CHANCE) {
            return false;
        }
        double radius = radius(cellX, cellZ, rolls);
        if (radius < minRadius) {
            return false;
        }
        double reach = reach(x, z, cellX, cellZ, radius, rolls);
        return reach >= minReach && reach < maxReach;
    }

    /**
     * Whether a column lies on a crater's floor, within half a radius of some crater's centre.
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls each cell's rolls
     * @return true on a crater floor
     */
    static boolean isFloor(int x, int z, CellRolls rolls) {
        return reachesBand(x, z, rolls, 0.0, FLOOR_REACH);
    }

    /**
     * Whether a column lies outside every crater, past each one's rim.
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls each cell's rolls
     * @return true on ground no crater reaches
     */
    static boolean isOpenGround(int x, int z, CellRolls rolls) {
        return !reachesBand(x, z, rolls, 0.0, 1.0 + RIM_REACH);
    }

    /**
     * The crater field a world's seeded crater noise draws, as its random state wires {@link #NOISE}.
     *
     * @param noise the seeded crater noise
     * @return the field that noise draws
     */
    public static CraterField seeded(NormalNoise noise) {
        return new CraterField(new DensityFunction.NoiseHolder(Holder.direct(noise.parameters()), noise));
    }

    /**
     * Whether some crater in this field reaches a column within a band of its radii.
     *
     * @param x        the column's x
     * @param z        the column's z
     * @param minReach the band's inner edge, in radii, inclusive
     * @param maxReach the band's outer edge, in radii, exclusive
     * @return true where a crater's reach at the column falls in the band
     */
    public boolean reachesBandAt(int x, int z, double minReach, double maxReach) {
        return reachesBand(x, z, this::roll, minReach, maxReach);
    }

    /**
     * Whether some crater of at least a radius in this field reaches a column within a band of its radii.
     *
     * @param x         the column's x
     * @param z         the column's z
     * @param minReach  the band's inner edge, in radii, inclusive
     * @param maxReach  the band's outer edge, in radii, exclusive
     * @param minRadius the least radius a crater counts at, in blocks
     * @return true where a crater that large reaches the column within the band
     */
    public boolean reachesBandAt(int x, int z, double minReach, double maxReach, double minRadius) {
        return reachesBand(x, z, this::roll, minReach, maxReach, minRadius);
    }

    /**
     * Whether a column lies on a crater's floor in this field.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true on a crater floor
     */
    public boolean isFloorAt(int x, int z) {
        return isFloor(x, z, this::roll);
    }

    /**
     * Whether a column lies outside every crater in this field.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true on ground no crater reaches
     */
    public boolean isOpenGroundAt(int x, int z) {
        return isOpenGround(x, z, this::roll);
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
