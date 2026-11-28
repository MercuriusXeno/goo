package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The rolls lunar's scattered features draw from a world's seeded noise: each feature cuts the ground into cells of
 * its own and rolls each cell's values on indices of its own, so one cell always rolls the same, neighbouring cells
 * and rolls read unrelated values, and every world draws its own.
 * decision lunar-generates-from-its-own-noise-and-surface-rules
 * decision no-two-lunar-features-look-alike
 */
public final class LunarRolls {

    /** The noise lunar's giant craters, basin pools and boulder fields roll their cells from. */
    public static final ResourceKey<NormalNoise> CRATER_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_craters"));

    /** Spreads one cell's rolls apart in the noise, so neighbouring cells and rolls read unrelated values. */
    private static final double CELL_STRIDE = 7.31;
    private static final double ROLL_STRIDE = 101.7;
    /** Amplifies the noise before its fraction is read, so the fraction spreads near evenly over [0, 1). */
    private static final double ROLL_SPREAD = 4096.0;

    private LunarRolls() {
    }

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

    /**
     * Rolls one value in [0, 1) for a cell from a seeded noise, each cell and roll reading the noise apart. A cell
     * past the seam rolls as the cell the same distance in from the other edge, so a feature reaching across the seam
     * is the feature standing on the other side.
     * decision moon-wraps-at-its-border-like-the-zone
     *
     * @param noise    the seeded noise
     * @param cellSide the side of the cells rolled, in blocks, a divisor of the moon's way around
     * @param cellX    the cell's x
     * @param cellZ    the cell's z
     * @param roll     which of the cell's rolls
     * @return the roll, in [0, 1)
     */
    static double rollFrom(Noise noise, int cellSide, int cellX, int cellZ, int roll) {
        double value = noise.get(LunarSeam.wrapCell(cellX, cellSide) * CELL_STRIDE + roll * ROLL_STRIDE, roll,
                LunarSeam.wrapCell(cellZ, cellSide) * CELL_STRIDE);
        return Mth.frac(value * ROLL_SPREAD);
    }
}
