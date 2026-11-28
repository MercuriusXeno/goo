package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.Noise;
import java.util.Optional;

/**
 * The sites of the frozen pools lying in the lows of lunar's shadowed basins, apart from any crater: the ground is cut
 * into square cells, each holding one pool site or none, its place and its reach rolled from the world's seeded crater
 * noise, so every pool takes its own size; a site holds its pool only where the basins noise reads the basin sunk to
 * its floor, the lows the terrain sinks deepest.
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 * decision craters-are-rare-giant-landmarks-only
 * decision no-two-lunar-features-look-alike
 *
 * @param noise  the seeded crater noise each cell's rolls read
 * @param basins the seeded basins noise, reading the basin's sunk floor from {@link #BASIN_LOW_FLOOR} up
 */
public record BasinPools(Noise noise, Noise basins) {

    /** A cell's side in blocks; a pool's reach stays inside its cell, so a chunk reads the cells it overlaps alone. */
    public static final int CELL = 96;
    /** The share of cells holding a pool site. */
    static final double POOL_CHANCE = 0.6;
    /** A pool reaches this far from its site at the least, and up to this much further. */
    static final double MIN_REACH = 8.0;
    static final double REACH_SPREAD = 14.0;
    /** A site stands this far inside its cell every way, past the farthest a pool reaches. */
    static final double CELL_MARGIN = 24.0;
    /**
     * The basins reading from which lunar's terrain has sunk the basin to its floor (terrain.json's
     * clamp(5 * basins - 1) reads one from here), the lows a pool lies in.
     */
    public static final double BASIN_LOW_FLOOR = 0.4;

    /** Basin pools roll on indices clear of every other feature reading the crater noise. */
    private static final int PRESENCE = 400;
    private static final int OFFSET_X = 401;
    private static final int OFFSET_Z = 402;
    private static final int SIZE = 403;

    /**
     * One pool site: where it stands and how far its pool reaches.
     *
     * @param centerX the site's x
     * @param centerZ the site's z
     * @param reach   how far from the site its pool reaches, in blocks
     */
    public record Site(double centerX, double centerZ, double reach) {
    }

    /** Reads the basins noise at a spot. */
    @FunctionalInterface
    interface BasinsReading {
        /**
         * @param x the spot's x
         * @param z the spot's z
         * @return the basins noise there
         */
        double basinsAt(double x, double z);
    }

    /**
     * The pool site a cell holds, if it holds one: its presence roll falls under the pool chance and the basins noise
     * at its place reads the basin's sunk floor.
     *
     * @param cellX  the cell's x
     * @param cellZ  the cell's z
     * @param rolls  each cell's rolls
     * @param basins the basins noise
     * @return the site, or empty
     */
    static Optional<Site> siteIn(int cellX, int cellZ, LunarRolls.CellRolls rolls, BasinsReading basins) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= POOL_CHANCE) {
            return Optional.empty();
        }
        double span = CELL - CELL_MARGIN - CELL_MARGIN;
        double centerX = cellX * CELL + CELL_MARGIN + rolls.roll(cellX, cellZ, OFFSET_X) * span;
        double centerZ = cellZ * CELL + CELL_MARGIN + rolls.roll(cellX, cellZ, OFFSET_Z) * span;
        if (basins.basinsAt(centerX, centerZ) < BASIN_LOW_FLOOR) {
            return Optional.empty();
        }
        return Optional.of(new Site(centerX, centerZ, MIN_REACH + rolls.roll(cellX, cellZ, SIZE) * REACH_SPREAD));
    }

    /**
     * The pool site a cell of this field holds, if it holds one.
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @return the site, or empty
     */
    public Optional<Site> siteInCell(int cellX, int cellZ) {
        return siteIn(cellX, cellZ, (x, z, roll) -> LunarRolls.rollFrom(noise, CELL, x, z, roll),
                (x, z) -> basins.get(x, 0.0, z));
    }

    /**
     * The basin pool sites a world's seeded crater noise draws, in the basins its random state seeds.
     *
     * @param noise       the seeded crater noise
     * @param randomState the world's random state
     * @return the sites that noise draws
     */
    public static BasinPools seeded(Noise noise, RandomState randomState) {
        return new BasinPools(noise, randomState.getOrCreateNoise(GiantCraterField.BASINS_NOISE));
    }
}
