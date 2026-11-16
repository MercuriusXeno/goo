package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The giant craters lunar holds rarely, hundreds of blocks wide, as the height in blocks they add to the ground at a
 * column: a broad floor sunk below the ground with a central peak rising from it, an inner wall stepping up in
 * terraces to a raised rim, and the rim falling back to the ground outside. The ground is cut into cells far larger
 * than {@link CraterField}'s, each holding one giant crater or none, rolled from the same seeded crater noise on rolls
 * of their own.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 *
 * @param noise the seeded noise each cell's rolls read
 */
public record GiantCraterField(DensityFunction.NoiseHolder noise) implements DensityFunction {

    /** The data codec of the {@code goo:giant_craters} density function type, naming the noise its rolls read. */
    public static final MapCodec<GiantCraterField> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.NoiseHolder.CODEC.fieldOf("noise").forGetter(GiantCraterField::noise)
    ).apply(instance, GiantCraterField::new));
    private static final KeyDispatchDataCodec<GiantCraterField> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    /** A cell's side in blocks; a giant crater's farthest reach stays under it. */
    public static final int CELL = 640;
    /** The share of cells holding a giant crater. */
    static final double CRATER_CHANCE = 0.35;
    static final double MIN_RADIUS = 110.0;
    static final double RADIUS_SPREAD = 130.0;
    /** The floor sinks this share of the radius; large craters run shallow for their width. */
    static final double DEPTH_PER_RADIUS = 0.15;
    static final double RIM_PER_RADIUS = 0.06;
    /** The central peak rises this share of the radius over the floor, short of the rim. */
    static final double PEAK_PER_RADIUS = 0.13;
    /** The central peak's foot, in radii from the centre. */
    static final double PEAK_REACH = 0.15;
    /** The flat floor runs out to here, in radii, where the terraced wall begins. */
    static final double FLOOR_REACH = 0.5;
    /** How many terraces step the inner wall up from the floor to the rim. */
    static final int TERRACES = 3;
    /** The share of each terrace that is flat tread before its riser climbs to the next. */
    static final double TREAD = 0.7;
    /** The rim falls back to the ground at 1.4 radii. */
    public static final double RIM_REACH = 0.4;

    /** Giant craters roll on indices clear of CraterField's, so the two scales read unrelated values. */
    private static final int PRESENCE = 8;
    private static final int OFFSET_X = 9;
    private static final int OFFSET_Z = 10;
    private static final int SIZE = 11;
    private static final int RAY_COUNT = 12;
    private static final int RAY_PHASE = 13;
    private static final double MAX_RADIUS = MIN_RADIUS + RADIUS_SPREAD;
    private static final int NEIGHBOUR_REACH = 1;
    /** Nine cells' giant craters at their deepest and their highest bound the field. */
    private static final int NEIGHBOURHOOD = 9;

    /**
     * One giant crater: its centre and its radius, in blocks, and the ejecta rays it throws.
     *
     * @param centerX the centre's x
     * @param centerZ the centre's z
     * @param radius  the radius, rim crest to centre
     * @param rays    how many ejecta rays it throws
     * @param phase   the angle of its first ray, in radians
     */
    public record Crater(double centerX, double centerZ, double radius, int rays, double phase) {

        /**
         * A column's distance from this crater's centre, in its radii.
         *
         * @param x the column's x
         * @param z the column's z
         * @return the reach
         */
        public double reach(int x, int z) {
            return Math.hypot(x - centerX, z - centerZ) / radius;
        }

        /**
         * Whether a column lies on one of this crater's ejecta rays.
         *
         * @param x the column's x
         * @param z the column's z
         * @return true on a ray
         */
        public boolean isOnRay(int x, int z) {
            return EjectaRays.isOnRayOf(x, z, centerX, centerZ, radius, rays, phase);
        }
    }

    /**
     * A giant crater's height at a distance from its centre: a cone of a peak over the floor at the centre, the flat
     * floor, the inner wall climbing in flat terraces joined by steep risers to the rim crest at one radius, and the
     * rim falling back to the ground by 1.4 radii.
     *
     * @param reach  the distance from the centre, in radii
     * @param radius the crater's radius in blocks
     * @return the height the crater adds there
     */
    static double profile(double reach, double radius) {
        double depth = radius * DEPTH_PER_RADIUS;
        double rim = radius * RIM_PER_RADIUS;
        if (reach < PEAK_REACH) {
            return -depth + radius * PEAK_PER_RADIUS * (1.0 - reach / PEAK_REACH);
        }
        if (reach < FLOOR_REACH) {
            return -depth;
        }
        if (reach < 1.0) {
            return -depth + (depth + rim) * terraceClimb((reach - FLOOR_REACH) / (1.0 - FLOOR_REACH));
        }
        double fall = 1.0 - (reach - 1.0) / RIM_REACH;
        return fall > 0.0 ? rim * fall * fall : 0.0;
    }

    /** How far up the wall a share of the way across it stands, flat on each terrace's tread and rising on its riser. */
    private static double terraceClimb(double across) {
        double terraces = across * TERRACES;
        int terrace = (int) Math.floor(terraces);
        double withinTerrace = terraces - terrace;
        double riser = withinTerrace < TREAD ? 0.0 : (withinTerrace - TREAD) / (1.0 - TREAD);
        return (terrace + riser) / TERRACES;
    }

    /**
     * The giant crater a cell holds, if it holds one.
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @param rolls each cell's rolls
     * @return the crater, or empty
     */
    static Optional<Crater> craterIn(int cellX, int cellZ, CraterField.CellRolls rolls) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= CRATER_CHANCE) {
            return Optional.empty();
        }
        return Optional.of(new Crater((cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL,
                (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL, radius(cellX, cellZ, rolls),
                EjectaRays.rayCount(rolls.roll(cellX, cellZ, RAY_COUNT)),
                EjectaRays.phase(rolls.roll(cellX, cellZ, RAY_PHASE))));
    }

    /**
     * The height every giant crater reaching a column adds to its ground, in blocks.
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls each cell's rolls
     * @return the height the giant craters add
     */
    static double heightAt(int x, int z, CraterField.CellRolls rolls) {
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

    private static double craterHeight(int x, int z, int cellX, int cellZ, CraterField.CellRolls rolls) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= CRATER_CHANCE) {
            return 0.0;
        }
        double radius = radius(cellX, cellZ, rolls);
        double centerX = (cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL;
        double centerZ = (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL;
        return profile(Math.hypot(x - centerX, z - centerZ) / radius, radius);
    }

    private static double radius(int cellX, int cellZ, CraterField.CellRolls rolls) {
        return MIN_RADIUS + rolls.roll(cellX, cellZ, SIZE) * RADIUS_SPREAD;
    }

    /**
     * Whether some giant crater of at least a radius reaches a column within a band of its radii.
     *
     * @param x         the column's x
     * @param z         the column's z
     * @param rolls     each cell's rolls
     * @param minReach  the band's inner edge, in radii, inclusive
     * @param maxReach  the band's outer edge, in radii, exclusive
     * @param minRadius the least radius a crater counts at, in blocks
     * @return true where a giant crater that large reaches the column within the band
     */
    static boolean reachesBand(int x, int z, CraterField.CellRolls rolls, double minReach, double maxReach,
            double minRadius) {
        for (Crater crater : cratersNear(x, z, rolls)) {
            double reach = crater.reach(x, z);
            if (crater.radius() >= minRadius && reach >= minReach && reach < maxReach) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a column lies on an ejecta ray of some giant crater; every giant crater throws rays.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls each cell's rolls
     * @return true on a giant crater's ray
     */
    static boolean isOnRay(int x, int z, CraterField.CellRolls rolls) {
        for (Crater crater : cratersNear(x, z, rolls)) {
            if (crater.isOnRay(x, z)) {
                return true;
            }
        }
        return false;
    }

    private static Iterable<Crater> cratersNear(int x, int z, CraterField.CellRolls rolls) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        List<Crater> near = new ArrayList<>();
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                craterIn(cellX + dx, cellZ + dz, rolls).ifPresent(near::add);
            }
        }
        return near;
    }

    /**
     * The giant crater field a world's seeded crater noise draws, as its random state wires {@link CraterField#NOISE}.
     *
     * @param noise the seeded crater noise
     * @return the field that noise draws
     */
    public static GiantCraterField seeded(NormalNoise noise) {
        return new GiantCraterField(new DensityFunction.NoiseHolder(Holder.direct(noise.parameters()), noise));
    }

    /**
     * The giant crater a cell of this field holds, if it holds one.
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @return the crater, or empty
     */
    public Optional<Crater> craterInCell(int cellX, int cellZ) {
        return craterIn(cellX, cellZ, this::roll);
    }

    /**
     * Whether some giant crater of at least a radius in this field reaches a column within a band of its radii.
     *
     * @param x         the column's x
     * @param z         the column's z
     * @param minReach  the band's inner edge, in radii, inclusive
     * @param maxReach  the band's outer edge, in radii, exclusive
     * @param minRadius the least radius a crater counts at, in blocks
     * @return true where a giant crater that large reaches the column within the band
     */
    public boolean reachesBandAt(int x, int z, double minReach, double maxReach, double minRadius) {
        return reachesBand(x, z, this::roll, minReach, maxReach, minRadius);
    }

    /**
     * Whether a column lies on an ejecta ray of some giant crater in this field.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true on a giant crater's ray
     */
    public boolean isOnRayAt(int x, int z) {
        return isOnRay(x, z, this::roll);
    }

    private double roll(int cellX, int cellZ, int roll) {
        return CraterField.rollFrom(noise, cellX, cellZ, roll);
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        return heightAt(context.blockX(), context.blockZ(), this::roll);
    }

    @Override
    public void fillArray(double[] output, DensityFunction.ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(output, this);
    }

    @Override
    public DensityFunction mapAll(DensityFunction.Visitor visitor) {
        return visitor.apply(new GiantCraterField(visitor.visitNoise(noise)));
    }

    /** Every neighbouring cell's crater at its deepest bounds the field below. */
    @Override
    public double minValue() {
        return -NEIGHBOURHOOD * MAX_RADIUS * DEPTH_PER_RADIUS;
    }

    /** Every neighbouring cell's crater at its highest bounds the field above. */
    @Override
    public double maxValue() {
        return NEIGHBOURHOOD * MAX_RADIUS * RIM_PER_RADIUS;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
