package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.Optional;

/**
 * The craters pocking lunar's surface, as the height in blocks they add to the ground at a column: a bowl sunk
 * below the ground, ringed by a raised rim that falls back to the ground outside it. The ground is cut into square
 * cells, each holding one crater or none, its place and size rolled from the world's seeded noise, so every world
 * draws its own craters and a column reads the same craters on every call.
 * decision lunar-generates-from-its-own-noise-and-surface-rules
 * decision moon-is-288-tall-half-of-today
 *
 * @param noise the seeded noise each cell's rolls read
 */
public record CraterField(Noise noise) implements DensitySampler {

    /** The data codec of the {@code goo:craters} density function type, naming the noise its rolls read. */
    public static final MapCodec<Definition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NormalNoise.CODEC.fieldOf("noise").forGetter(Definition::noise)
    ).apply(instance, Definition::new));
    /** The noise lunar's {@code goo:craters} density function rolls its cells from. */
    public static final ResourceKey<NormalNoise> NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_craters"));

    /** A cell's side in blocks; a crater's farthest reach stays under it, so a column reads its own cell's
     * neighbours alone. */
    public static final int CELL = 40;
    /** The share of cells holding a crater. */
    static final double CRATER_CHANCE = 0.55;
    static final double MIN_RADIUS = 5.0;
    /** The radius a cell adds over the least, at its largest; squared rolls make small craters common. */
    static final double RADIUS_SPREAD = 17.0;
    /** A bowl sinks a third of its radius, and its rim rises an eighth, the shape fresh lunar craters keep. */
    static final double DEPTH_PER_RADIUS = 0.175;
    static final double RIM_PER_RADIUS = 0.06;
    /** The rim falls back to the ground at 1.6 radii. */
    static final double RIM_REACH = 0.6;
    /** A crater's floor reaches half its radius from its centre, the flat of the bowl where impacts melt the ground. */
    public static final double FLOOR_REACH = 0.5;
    /**
     * The radius past which a crater is one of the deepest, whose floor holds crater ice; lunar's noise settings name
     * it in their crater ice rule.
     */
    public static final double DEEP_CRATER_RADIUS = 16.0;
    /** Crater ice fills a deep crater's floor out to this many radii from its centre, inside the impact glass. */
    public static final double ICE_REACH = 0.35;

    /**
     * How much an old crater has worn: its bowl sinks this share less, its rim stands this share lower, and its floor
     * fills flatter by this much more of a power, at full wear.
     * decision no-two-lunar-features-look-alike
     */
    static final double WEAR_DEPTH_LOSS = 0.6;
    static final double WEAR_RIM_LOSS = 0.7;
    static final double WEAR_FILL = 2.0;
    /**
     * A crater's rim swells and pinches round its circle by up to these shares of its radius, on two and three lobes,
     * and stands up to this share higher on one side than the other; the lobes stay small enough that the rim's fall
     * ends inside the cells a column reads.
     */
    static final double TWO_LOBE_SHARE = 0.07;
    static final double THREE_LOBE_SHARE = 0.05;
    static final double TILT_SHARE = 0.5;
    /** The lobes a rim swells on round its circle, two and three to a turn. */
    private static final double TWO_LOBES = 2.0;
    private static final double THREE_LOBES = 3.0;
    /** A fresh bowl rises from its floor to its rim on a square, the power a worn one's floor fills flatter past. */
    private static final double FRESH_BOWL_POWER = 2.0;

    private static final int PRESENCE = 0;
    private static final int OFFSET_X = 1;
    private static final int OFFSET_Z = 2;
    private static final int SIZE = 3;
    private static final int RAY_COUNT = 4;
    private static final int RAY_PHASE = 5;
    private static final int FRESH = 6;
    /** A crater's rays roll on their own indices from here, three to a ray. */
    private static final int RAY_ROLLS = 7;
    /** A crater's outline rolls on indices past every ray's, so its shape reads apart from its rays. */
    private static final int TWO_LOBE = 200;
    private static final int TWO_LOBE_PHASE = 201;
    private static final int THREE_LOBE = 202;
    private static final int THREE_LOBE_PHASE = 203;
    private static final int TILT = 204;
    private static final int TILT_PHASE = 205;
    /** The farthest a crater's outline reaches past its radius, as a share of it. */
    private static final double MAX_LOBE = TWO_LOBE_SHARE + THREE_LOBE_SHARE;
    private static final double FULL_TURN = 2.0 * Math.PI;
    /** A large crater's rays reach two and a half radii, past one cell, so a ray search reads two cells around. */
    private static final int RAY_NEIGHBOUR_REACH = 2;
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
    public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
        return (float) densityAt(blockX, blockY, blockZ);
    }

    @Override
    public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
        DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
    }

    private double densityAt(int blockX, int blockY, int blockZ) {
        return heightAt(blockX, blockZ, this::roll);
    }

    private double roll(int cellX, int cellZ, int roll) {
        return rollFrom(noise, cellX, cellZ, roll);
    }

    /**
     * Rolls one value in [0, 1) for a cell from a seeded noise, each cell and roll reading the noise apart.
     *
     * @param noise the seeded noise
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @param roll  which of the cell's rolls
     * @return the roll, in [0, 1)
     */
    static double rollFrom(Noise noise, int cellX, int cellZ, int roll) {
        double value = noise.get(cellX * CELL_STRIDE + roll * ROLL_STRIDE, roll, cellZ * CELL_STRIDE);
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
        return craterReaching(x, z, cellX, cellZ, rolls).map(crater -> crater.heightAt(x, z)).orElse(0.0);
    }

    private static double radius(int cellX, int cellZ, CellRolls rolls) {
        double size = rolls.roll(cellX, cellZ, SIZE);
        return MIN_RADIUS + size * size * RADIUS_SPREAD;
    }

    /**
     * The crater a cell holds, if it holds one: its place, size and wear, and its rim's outline and tilt, all rolled
     * from the cell's own rolls, so one cell always draws one shape and no two cells draw the same.
     * decision no-two-lunar-features-look-alike
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @param rolls each cell's rolls
     * @return the crater, or empty
     */
    static Optional<Crater> craterIn(int cellX, int cellZ, CellRolls rolls) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= CRATER_CHANCE) {
            return Optional.empty();
        }
        return Optional.of(rollCrater(cellX, cellZ, rolls, (cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL,
                (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL, radius(cellX, cellZ, rolls)));
    }

    /**
     * A cell's crater where some part of it reaches a column, skipping the outline's rolls for a crater too far off to
     * reach it.
     */
    private static Optional<Crater> craterReaching(int x, int z, int cellX, int cellZ, CellRolls rolls) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= CRATER_CHANCE) {
            return Optional.empty();
        }
        double centerX = (cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL;
        double centerZ = (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL;
        double radius = radius(cellX, cellZ, rolls);
        if (Math.hypot(x - centerX, z - centerZ) >= radius * (1.0 + MAX_LOBE) * (1.0 + RIM_REACH)) {
            return Optional.empty();
        }
        return Optional.of(rollCrater(cellX, cellZ, rolls, centerX, centerZ, radius));
    }

    private static Crater rollCrater(int cellX, int cellZ, CellRolls rolls, double centerX, double centerZ,
            double radius) {
        return new Crater(centerX, centerZ, radius, rolls.roll(cellX, cellZ, FRESH),
                rolls.roll(cellX, cellZ, TWO_LOBE) * TWO_LOBE_SHARE, rolls.roll(cellX, cellZ, TWO_LOBE_PHASE) * FULL_TURN,
                rolls.roll(cellX, cellZ, THREE_LOBE) * THREE_LOBE_SHARE,
                rolls.roll(cellX, cellZ, THREE_LOBE_PHASE) * FULL_TURN, rolls.roll(cellX, cellZ, TILT) * TILT_SHARE,
                rolls.roll(cellX, cellZ, TILT_PHASE) * FULL_TURN);
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
        Optional<Crater> crater = craterReaching(x, z, cellX, cellZ, rolls);
        if (crater.isEmpty() || crater.get().radius() < minRadius) {
            return false;
        }
        double reach = crater.get().reach(x, z);
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
     * Whether a column lies on an ejecta ray of some crater past {@link #DEEP_CRATER_RADIUS}, the craters large enough
     * to throw rays.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls each cell's rolls
     * @return true on a large crater's ray
     */
    static boolean isOnRay(int x, int z, CellRolls rolls) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        for (int dx = -RAY_NEIGHBOUR_REACH; dx <= RAY_NEIGHBOUR_REACH; dx++) {
            for (int dz = -RAY_NEIGHBOUR_REACH; dz <= RAY_NEIGHBOUR_REACH; dz++) {
                if (isOnRayOfCell(x, z, cellX + dx, cellZ + dz, rolls)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOnRayOfCell(int x, int z, int cellX, int cellZ, CellRolls rolls) {
        if (rolls.roll(cellX, cellZ, PRESENCE) >= CRATER_CHANCE) {
            return false;
        }
        double radius = radius(cellX, cellZ, rolls);
        if (radius < DEEP_CRATER_RADIUS || !isFresh(rolls.roll(cellX, cellZ, FRESH))) {
            return false;
        }
        double centerX = (cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL;
        double centerZ = (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL;
        double reach = Math.hypot(x - centerX, z - centerZ) / radius;
        if (reach < 1.0 + MAX_LOBE || reach >= EjectaRays.REACH) {
            return false;
        }
        return EjectaRays.isOnRayOf(x, z, centerX, centerZ, radius,
                EjectaRays.rays(rolls.roll(cellX, cellZ, RAY_COUNT), rolls.roll(cellX, cellZ, RAY_PHASE) * FULL_TURN,
                        ray -> rolls.roll(cellX, cellZ, RAY_ROLLS + ray)));
    }

    /**
     * Whether a crater of a wear is fresh, sharp enough to throw rays.
     *
     * @param wear the crater's wear, in [0, 1)
     * @return true for a fresh crater
     */
    static boolean isFresh(double wear) {
        return wear < EjectaRays.FRESH_CHANCE;
    }

    /**
     * One small crater: its centre, its mean radius, how worn it is, and the lobes and tilt its rim takes round its
     * circle.
     * decision no-two-lunar-features-look-alike
     *
     * @param centerX        the centre's x
     * @param centerZ        the centre's z
     * @param radius         the mean radius, rim crest to centre
     * @param wear           how old and worn it is, in [0, 1): sharp and deep at zero, soft, shallow and filled near one
     * @param twoLobe        how far its rim swells and pinches on two lobes, as a share of its radius
     * @param twoLobePhase   where round the circle the two lobes stand, in radians
     * @param threeLobe      how far its rim swells and pinches on three lobes, as a share of its radius
     * @param threeLobePhase where round the circle the three lobes stand, in radians
     * @param tilt           how much higher its rim stands on one side, as a share of the rim's height
     * @param tiltPhase      the side its rim stands highest on, in radians
     */
    public record Crater(double centerX, double centerZ, double radius, double wear, double twoLobe,
            double twoLobePhase, double threeLobe, double threeLobePhase, double tilt, double tiltPhase) {

        /**
         * The rim crest's distance from the centre at an angle round the circle.
         *
         * @param angle the angle, in radians
         * @return the rim's radius there, in blocks
         */
        public double rimRadiusAt(double angle) {
            return radius * (1.0 + twoLobe * Math.cos(TWO_LOBES * angle + twoLobePhase)
                    + threeLobe * Math.cos(THREE_LOBES * angle + threeLobePhase));
        }

        /**
         * A column's distance from the centre, in the rim's radius toward it.
         *
         * @param x the column's x
         * @param z the column's z
         * @return the reach
         */
        public double reach(int x, int z) {
            double angle = Math.atan2(z - centerZ, x - centerX);
            return Math.hypot(x - centerX, z - centerZ) / rimRadiusAt(angle);
        }

        /**
         * The height this crater adds to a column's ground.
         *
         * @param x the column's x
         * @param z the column's z
         * @return the height, in blocks
         */
        public double heightAt(int x, int z) {
            double angle = Math.atan2(z - centerZ, x - centerX);
            return profile(reach(x, z), radius, wear, 1.0 + tilt * Math.cos(angle - tiltPhase));
        }
    }

    /**
     * The crater a cell of this field holds, if it holds one.
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @return the crater, or empty
     */
    public Optional<Crater> craterInCell(int cellX, int cellZ) {
        return craterIn(cellX, cellZ, this::roll);
    }

    /**
     * Whether a column lies on an ejecta ray of some large crater in this field.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true on a large crater's ray
     */
    public boolean isOnRayAt(int x, int z) {
        return isOnRay(x, z, this::roll);
    }

    /**
     * The crater field a world's seeded crater noise draws, as its random state wires {@link #NOISE}.
     *
     * @param noise the seeded crater noise
     * @return the field that noise draws
     */
    public static CraterField seeded(Noise noise) {
        return new CraterField(noise);
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
        return profile(reach, radius, 0.0, 1.0);
    }

    /**
     * A crater's height at a distance from its centre as it wears: an old crater's bowl sinks less and fills flatter,
     * and its rim stands lower, by its wear; its rim stands higher or lower than its mean by a factor round its tilt.
     * decision no-two-lunar-features-look-alike
     *
     * @param reach     the distance from the centre, in the rim's radii
     * @param radius    the crater's mean radius in blocks
     * @param wear      how worn the crater is, in [0, 1)
     * @param rimFactor the rim's height there as a share of its mean
     * @return the height the crater adds there
     */
    static double profile(double reach, double radius, double wear, double rimFactor) {
        double depth = radius * DEPTH_PER_RADIUS * (1.0 - WEAR_DEPTH_LOSS * wear);
        double rim = radius * RIM_PER_RADIUS * (1.0 - WEAR_RIM_LOSS * wear) * rimFactor;
        if (reach < 1.0) {
            return (depth + rim) * Math.pow(reach, FRESH_BOWL_POWER + WEAR_FILL * wear) - depth;
        }
        double fall = 1.0 - (reach - 1.0) / RIM_REACH;
        return fall > 0.0 ? rim * fall * fall : 0.0;
    }

    /**
     * The density function lunar's noise settings name, compiling to the field its noises draw once the world's
     * random state seeds them.
     *
     * @param noise the noise each cell's rolls read
     */
    public record Definition(Holder<NormalNoise> noise) implements DensityFunction {

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return new CraterField(context.createNoiseSampler(noise));
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return this;
        }

        @Override
        public Interval range() {
            return Interval.of((float) (-NEIGHBOURHOOD * MAX_RADIUS * DEPTH_PER_RADIUS),
                    (float) (NEIGHBOURHOOD * MAX_RADIUS * RIM_PER_RADIUS * (1.0 + TILT_SHARE)));
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
