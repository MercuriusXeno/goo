package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The giant craters lunar holds rarely, hundreds of blocks wide, as the height in blocks they add to the ground at a
 * column: a broad floor sunk below the ground with a central peak rising from it, an inner wall stepping up in
 * terraces to a raised rim, and the rim falling back to the ground outside. The ground is cut into cells far larger
 * than {@link CraterField}'s, each holding one giant crater or none, rolled from the same seeded crater noise on rolls
 * of their own. Each crater reads the biome noises at its centre: one falling in maria is rare there, one in the
 * highlands is old and worn, throwing no rays, and one in the shadowed basins is a fresh impact.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 * decision moon-is-288-tall-half-of-today
 * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
 *
 * @param noise     the seeded noise each cell's rolls read
 * @param maria     the seeded maria noise, reading maria above zero
 * @param basins    the seeded basins noise, reading the shadowed basins from {@link #BASINS_FLOOR} up
 */
public record GiantCraterField(Noise noise, Noise maria, Noise basins) implements DensitySampler,
        CentreGround {

    /**
     * The data codec of the {@code goo:giant_craters} density function type, naming the noise its rolls read and the
     * biome noises its craters' centres read.
     */
    public static final MapCodec<Definition> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NormalNoise.CODEC.fieldOf("noise").forGetter(Definition::noise),
            NormalNoise.CODEC.fieldOf("maria").forGetter(Definition::maria),
            NormalNoise.CODEC.fieldOf("basins").forGetter(Definition::basins)
    ).apply(instance, Definition::new));

    /** The basins noise, which the biome source reads to split the shadowed basins from the highlands. */
    public static final ResourceKey<NormalNoise> BASINS_NOISE = ResourceKey.create(Registries.NOISE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_basins"));
    /** The basins reading from which the biome source places the shadowed basins over the highlands. */
    static final double BASINS_FLOOR = 0.25;
    /** A giant crater centred in maria keeps this share of the crater chance, so maria holds few. */
    static final double MARIA_CRATER_SHARE = 0.25;

    /**
     * A cell's side in blocks; a giant crater's farthest reach stays under it, and it divides the moon's way around,
     * so the seam falls on a cell's edge.
     * decision moon-wraps-at-its-border-like-the-zone
     */
    public static final int CELL = 625;
    /** The share of cells holding a giant crater. */
    static final double CRATER_CHANCE = 0.35;
    static final double MIN_RADIUS = 110.0;
    static final double RADIUS_SPREAD = 130.0;
    /** The floor sinks this share of the radius; large craters run shallow for their width. */
    static final double DEPTH_PER_RADIUS = 0.075;
    static final double RIM_PER_RADIUS = 0.03;
    /** The central peak rises this share of the radius over the floor, short of the rim. */
    static final double PEAK_PER_RADIUS = 0.065;
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
    private static final int FRESH = 60;
    /** A giant crater's rays roll on their own indices from here, three to a ray. */
    private static final int RAY_ROLLS = 61;
    /** A giant crater throws six to twelve boulders, each rolled on three rolls of its own past these. */
    private static final int BOULDER_COUNT = 14;
    private static final int BOULDER_ROLLS = 15;
    private static final int ROLLS_PER_BOULDER = 3;
    static final int MIN_BOULDERS = 6;
    static final int BOULDER_SPREAD = 7;
    /** A rim boulder lies between the rim crest and a third of a radius past it. */
    static final double RIM_BOULDER_REACH = 0.95;
    static final double RIM_BOULDER_SPREAD = 0.3;
    /** A ray boulder lies out along one of the crater's rays, up to two radii from its centre. */
    static final double RAY_BOULDER_REACH = 1.15;
    static final double RAY_BOULDER_SPREAD = 0.85;
    /** How far a ray boulder strays either side of its ray's line, in radians, and the width of that stray. */
    static final double RAY_BOULDER_STRAY = 0.04;
    static final double RAY_BOULDER_STRAY_SPAN = RAY_BOULDER_STRAY + RAY_BOULDER_STRAY;
    /** A boulder's rolls in order: where round the crater, how far out, how far it strays from its ray. */
    private static final int WHERE_TO_OUT = 1;
    private static final int WHERE_TO_STRAY = 2;
    /** Boulders alternate between the crater's rays and its rim. */
    private static final int ALTERNATION = 2;
    /** A crater thrown boulders' farthest reach, in radii, past which no chunk reads them. */
    static final double BOULDER_REACH = RAY_BOULDER_REACH + RAY_BOULDER_SPREAD;
    private static final double MAX_RADIUS = MIN_RADIUS + RADIUS_SPREAD;
    private static final int NEIGHBOUR_REACH = 1;
    private static final double FULL_TURN = 2.0 * Math.PI;
    /** Nine cells' giant craters at their deepest and their highest bound the field. */
    private static final int NEIGHBOURHOOD = 9;

    /**
     * Whether a cell holds its giant crater: a crater centred in maria keeps a share of the chance a crater centred
     * anywhere else holds.
     *
     * @param presenceRoll  the cell's presence roll, in [0, 1)
     * @param mariaAtCentre the maria noise at the crater's centre
     * @return true where the cell holds its crater
     */
    static boolean holdsCrater(double presenceRoll, double mariaAtCentre) {
        double chance = mariaAtCentre > 0.0 ? CRATER_CHANCE * MARIA_CRATER_SHARE : CRATER_CHANCE;
        return presenceRoll < chance;
    }

    /**
     * Whether a giant crater is fresh, throwing its rays: in maria as its roll falls, in the highlands never, the
     * crater old and worn, and in the shadowed basins always, a fresh impact.
     *
     * @param freshRoll      the cell's freshness roll, in [0, 1)
     * @param mariaAtCentre  the maria noise at the crater's centre
     * @param basinsAtCentre the basins noise at the crater's centre
     * @return true for a fresh crater
     */
    static boolean isFresh(double freshRoll, double mariaAtCentre, double basinsAtCentre) {
        if (mariaAtCentre > 0.0) {
            return freshRoll < EjectaRays.FRESH_CHANCE;
        }
        return basinsAtCentre >= BASINS_FLOOR;
    }

    /**
     * One giant crater: its centre and its radius, in blocks, the lines its ejecta run out along, and the boulders it
     * threw.
     *
     * @param centerX the centre's x
     * @param centerZ the centre's z
     * @param radius  the radius, rim crest to centre
     * @param fresh        whether it is fresh enough to paint its rays on the ground
     * @param rays         the lines its ejecta run out along, painted only where it is fresh; empty where the crater
     *                     was read only for its shape
     * @param boulderSpots the columns its thrown boulders lie on, half on its rim and half out along its rays; empty
     *                     where the crater was read only for its shape
     */
    public record Crater(double centerX, double centerZ, double radius, boolean fresh, List<EjectaRays.Ray> rays,
            List<BlockPos> boulderSpots) {

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
            return fresh && EjectaRays.isOnRayOf(x, z, centerX, centerZ, radius, rays);
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
    static Optional<Crater> craterIn(int cellX, int cellZ, CraterField.CellRolls rolls, CentreGround ground) {
        return craterIn(cellX, cellZ, rolls, ground, false);
    }

    private static Optional<Crater> craterIn(int cellX, int cellZ, CraterField.CellRolls rolls, CentreGround ground,
            boolean withBoulders) {
        double presence = rolls.roll(cellX, cellZ, PRESENCE);
        if (presence >= CRATER_CHANCE) {
            return Optional.empty();
        }
        double centerX = (cellX + rolls.roll(cellX, cellZ, OFFSET_X)) * CELL;
        double centerZ = (cellZ + rolls.roll(cellX, cellZ, OFFSET_Z)) * CELL;
        double mariaAtCentre = ground.mariaAt(centerX, centerZ);
        if (!holdsCrater(presence, mariaAtCentre)) {
            return Optional.empty();
        }
        double radius = radius(cellX, cellZ, rolls);
        boolean fresh = isFresh(rolls.roll(cellX, cellZ, FRESH), mariaAtCentre, ground.basinsAt(centerX, centerZ));
        if (!withBoulders) {
            return Optional.of(new Crater(centerX, centerZ, radius, fresh, List.of(), List.of()));
        }
        List<EjectaRays.Ray> rays = rays(cellX, cellZ, rolls);
        return Optional.of(new Crater(centerX, centerZ, radius, fresh, rays,
                boulderSpots(cellX, cellZ, rolls, centerX, centerZ, radius, rays)));
    }

    private static List<EjectaRays.Ray> rays(int cellX, int cellZ, CraterField.CellRolls rolls) {
        return EjectaRays.rays(rolls.roll(cellX, cellZ, RAY_COUNT), rolls.roll(cellX, cellZ, RAY_PHASE) * FULL_TURN,
                ray -> rolls.roll(cellX, cellZ, RAY_ROLLS + ray));
    }

    /**
     * The columns a giant crater throws its boulders onto: six to twelve, the even ones out along its rays between
     * 1.15 and 2 radii, the odd ones on its rim between the crest and a third of a radius past it.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     */
    private static List<BlockPos> boulderSpots(int cellX, int cellZ, CraterField.CellRolls rolls, double centerX,
            double centerZ, double radius, List<EjectaRays.Ray> rays) {
        int count = MIN_BOULDERS + (int) (rolls.roll(cellX, cellZ, BOULDER_COUNT) * BOULDER_SPREAD);
        List<BlockPos> spots = new ArrayList<>(count);
        for (int boulder = 0; boulder < count; boulder++) {
            int base = BOULDER_ROLLS + boulder * ROLLS_PER_BOULDER;
            double where = rolls.roll(cellX, cellZ, base);
            double out = rolls.roll(cellX, cellZ, base + WHERE_TO_OUT);
            double stray = rolls.roll(cellX, cellZ, base + WHERE_TO_STRAY) * RAY_BOULDER_STRAY_SPAN - RAY_BOULDER_STRAY;
            boolean alongARay = Math.floorMod(boulder, ALTERNATION) == 0;
            double angle = alongARay ? rays.get((int) (where * rays.size())).angle() + stray : where * FULL_TURN;
            double reach = alongARay ? RAY_BOULDER_REACH + out * RAY_BOULDER_SPREAD
                    : RIM_BOULDER_REACH + out * RIM_BOULDER_SPREAD;
            spots.add(BlockPos.containing(centerX + Math.cos(angle) * reach * radius, 0.0,
                    centerZ + Math.sin(angle) * reach * radius));
        }
        return spots;
    }

    /**
     * The height every giant crater reaching a column adds to its ground, in blocks.
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls  each cell's rolls
     * @param ground the biome noises each crater's centre reads
     * @return the height the giant craters add
     */
    static double heightAt(int x, int z, CraterField.CellRolls rolls, CentreGround ground) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        double height = 0.0;
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                height += craterIn(cellX + dx, cellZ + dz, rolls, ground)
                        .map(crater -> profile(crater.reach(x, z), crater.radius())).orElse(0.0);
            }
        }
        return height;
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
     * @param ground    the biome noises each crater's centre reads
     * @return true where a giant crater that large reaches the column within the band
     */
    static boolean reachesBand(int x, int z, CraterField.CellRolls rolls, double minReach, double maxReach,
            double minRadius, CentreGround ground) {
        for (Crater crater : cratersNear(x, z, rolls, ground)) {
            double reach = crater.reach(x, z);
            if (crater.radius() >= minRadius && reach >= minReach && reach < maxReach) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a column lies flecked on an ejecta ray of some fresh giant crater.
     * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
     *
     * @param x     the column's x
     * @param z     the column's z
     * @param rolls  each cell's rolls
     * @param ground the biome noises each crater's centre reads
     * @return true on a giant crater's ray
     */
    static boolean isOnRay(int x, int z, CraterField.CellRolls rolls, CentreGround ground) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                if (isOnRayOfCell(x, z, cellX + dx, cellZ + dz, rolls, ground)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOnRayOfCell(int x, int z, int cellX, int cellZ, CraterField.CellRolls rolls,
            CentreGround ground) {
        Optional<Crater> crater = craterIn(cellX, cellZ, rolls, ground);
        if (crater.isEmpty()) {
            return false;
        }
        double reach = crater.get().reach(x, z);
        if (reach < 1.0 || reach >= EjectaRays.REACH || !crater.get().fresh()) {
            return false;
        }
        return EjectaRays.isOnRayOf(x, z, crater.get().centerX(), crater.get().centerZ(), crater.get().radius(),
                rays(cellX, cellZ, rolls));
    }

    private static Iterable<Crater> cratersNear(int x, int z, CraterField.CellRolls rolls, CentreGround ground) {
        int cellX = Math.floorDiv(x, CELL);
        int cellZ = Math.floorDiv(z, CELL);
        List<Crater> near = new ArrayList<>();
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                craterIn(cellX + dx, cellZ + dz, rolls, ground).ifPresent(near::add);
            }
        }
        return near;
    }

    /**
     * The giant crater field a world's seeded crater noise draws, its craters' centres reading the maria and basins
     * noises the world's random state seeds.
     *
     * @param noise       the seeded crater noise
     * @param randomState the world's random state
     * @return the field that noise draws
     */
    public static GiantCraterField seeded(Noise noise, RandomState randomState) {
        return new GiantCraterField(noise, randomState.getOrCreateNoise(RilleField.MARIA_NOISE),
                randomState.getOrCreateNoise(BASINS_NOISE));
    }

    @Override
    public double mariaAt(double x, double z) {
        return maria.get(x, 0.0, z);
    }

    @Override
    public double basinsAt(double x, double z) {
        return basins.get(x, 0.0, z);
    }

    /**
     * The giant crater a cell of this field holds, if it holds one.
     *
     * @param cellX the cell's x
     * @param cellZ the cell's z
     * @return the crater, or empty
     */
    public Optional<Crater> craterInCell(int cellX, int cellZ) {
        return craterIn(cellX, cellZ, this::roll, this, true);
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
        return reachesBand(x, z, this::roll, minReach, maxReach, minRadius, this);
    }

    /**
     * Whether a column lies on an ejecta ray of some giant crater in this field.
     *
     * @param x the column's x
     * @param z the column's z
     * @return true on a giant crater's ray
     */
    public boolean isOnRayAt(int x, int z) {
        return isOnRay(x, z, this::roll, this);
    }

    private double roll(int cellX, int cellZ, int roll) {
        return CraterField.rollFrom(noise, CELL, cellX, cellZ, roll);
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
        return heightAt(blockX, blockZ, this::roll, this);
    }

    /**
     * The density function lunar's noise settings name, compiling to the field its noises draw once the world's
     * random state seeds them.
     *
     * @param noise     the noise each cell's rolls read
     * @param maria     the maria noise each crater's centre reads
     * @param basins    the basins noise each crater's centre reads
     */
    public record Definition(Holder<NormalNoise> noise, Holder<NormalNoise> maria, Holder<NormalNoise> basins)
            implements DensityFunction {

        @Override
        public DensitySampler compileSampler(DensityFunction.CompileContext context) {
            return new GiantCraterField(context.createNoiseSampler(noise), context.createNoiseSampler(maria),
                    context.createNoiseSampler(basins));
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return this;
        }

        @Override
        public Interval range() {
            return Interval.of((float) (-NEIGHBOURHOOD * MAX_RADIUS * DEPTH_PER_RADIUS), (float) (NEIGHBOURHOOD * MAX_RADIUS * RIM_PER_RADIUS));
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
