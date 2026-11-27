package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import java.util.ArrayList;
import java.util.List;

/**
 * One boulder a crater threw: a lumpy rock of a single stone, 10 to 20 blocks across at the sizes lunar names, a
 * squat ellipsoid with knobs swelling from its upper face, sunk a third of its height into the ground it landed on.
 * Each draws its size, how squat it sits and how many knobs swell from it, and where, from its own position, so no
 * two boulders share a silhouette and one position always throws the same.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 * decision no-two-lunar-features-look-alike
 *
 * @param stone  the stone a boulder is cut from, one per boulder
 * @param radius the boulder's radius east to west, in blocks
 */
public record BoulderFeature(BlockStateProvider stone, IntProvider radius) implements Feature {

    /** The feature type's codec, naming the stone a boulder is cut from and its radius. */
    public static final MapCodec<BoulderFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockStateProvider.DIRECT_CODEC.fieldOf("stone").forGetter(BoulderFeature::stone),
            IntProviders.codec(1, 16).fieldOf("radius").forGetter(BoulderFeature::radius)
    ).apply(instance, BoulderFeature::new));

    /** The boulder's half height and its breadth north to south, as shares of its radius east to west. */
    static final double HEIGHT_SHARE_MIN = 0.45;
    static final double HEIGHT_SHARE_SPREAD = 0.4;
    static final double DEPTH_SHARE_MIN = 0.7;
    static final double DEPTH_SHARE_SPREAD = 0.3;
    /** The share of its full height a boulder sinks into the ground. */
    static final double SUNK_SHARE = 0.35;
    /** How many knobs swell from a boulder's face, two to six, and their size as a share of its radius. */
    static final int KNOBS_MIN = 2;
    static final int KNOBS_SPREAD = 5;
    static final double KNOB_SHARE_MIN = 0.3;
    static final double KNOB_SHARE_SPREAD = 0.25;
    /** A knob's centre stands this share of the boulder's radii out from its centre. */
    static final double KNOB_REACH_SHARE = 0.6;
    /** A knob's centre lifts between these shares of the boulder's half height. */
    static final double KNOB_LIFT_MIN = 0.3;
    static final double KNOB_LIFT_SPREAD = 0.4;
    private static final double FULL_TURN = 2.0 * Math.PI;
    /** Spreads a position's bits through the seed, so neighbouring positions roll unrelated boulders. */
    private static final long POSITION_MIX = 0x9E3779B97F4A7C15L;
    /** Block flags for a feature's placement: tell clients, and skip neighbour updates. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /**
     * A boulder's shape: its squat ellipsoid's radii and the knobs swelling from it.
     *
     * @param radius     its radius east to west
     * @param height     its half height
     * @param depth      its radius north to south
     * @param knobs      the knobs' centres, as offsets from the boulder's centre
     * @param knobRadius the knobs' radius
     */
    record Shape(double radius, double height, double depth, List<Knob> knobs, double knobRadius) {

        /**
         * Whether a block at an offset from the boulder's centre lies inside it: inside its ellipsoid, or inside one
         * of its knobs.
         *
         * @param dx the offset east, in blocks
         * @param dy the offset up
         * @param dz the offset south
         * @return true inside the boulder
         */
        boolean holds(int dx, int dy, int dz) {
            if (sq(dx / radius) + sq(dy / height) + sq(dz / depth) <= 1.0) {
                return true;
            }
            for (Knob knob : knobs) {
                if (sq(dx - knob.x()) + sq(dy - knob.y()) + sq(dz - knob.z()) <= sq(knobRadius)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * A knob's centre, as an offset from its boulder's centre.
     *
     * @param x the offset east
     * @param y the offset up
     * @param z the offset south
     */
    record Knob(double x, double y, double z) {
    }

    private static double sq(double value) {
        return value * value;
    }

    /**
     * Rolls a boulder's shape for a radius.
     *
     * @param radius the boulder's radius east to west
     * @param random the feature's random source
     * @return the shape
     */
    static Shape shape(double radius, RandomSource random) {
        double height = radius * (HEIGHT_SHARE_MIN + random.nextDouble() * HEIGHT_SHARE_SPREAD);
        double depth = radius * (DEPTH_SHARE_MIN + random.nextDouble() * DEPTH_SHARE_SPREAD);
        int count = KNOBS_MIN + random.nextInt(KNOBS_SPREAD);
        double knobRadius = radius * (KNOB_SHARE_MIN + random.nextDouble() * KNOB_SHARE_SPREAD);
        List<Knob> knobs = new ArrayList<>(count);
        for (int knob = 0; knob < count; knob++) {
            double angle = random.nextDouble() * FULL_TURN;
            knobs.add(new Knob(Math.cos(angle) * radius * KNOB_REACH_SHARE,
                    height * (KNOB_LIFT_MIN + random.nextDouble() * KNOB_LIFT_SPREAD),
                    Math.sin(angle) * depth * KNOB_REACH_SHARE));
        }
        return new Shape(radius, height, depth, knobs, knobRadius);
    }

    /**
     * The shape a boulder landing at a position takes, rolled from the world's seed and the position alone: its size
     * a radius the provider draws plus a fraction of a block, and its silhouette.
     *
     * @param origin    where the boulder lands
     * @param worldSeed the world's seed
     * @param radius    the provider of a boulder's whole radius
     * @return the shape
     */
    static Shape shapeAt(BlockPos origin, long worldSeed, IntProvider radius) {
        RandomSource random = RandomSource.create(worldSeed ^ BlockPos.asLong(origin.getX(), 0, origin.getZ()) * POSITION_MIX);
        return shape(radius.sample(random) + random.nextDouble(), random);
    }

    @Override
    public MapCodec<BoulderFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (level.getBlockState(origin.below()).isAir()) {
            return false;
        }
        Shape shape = shapeAt(origin, level.getSeed(), radius);
        BlockState placed = stone.getState(level, random, origin);
        BlockPos centre = origin.below().above((int) Math.round(shape.height() * (1.0 - SUNK_SHARE - SUNK_SHARE)));
        int reach = (int) Math.ceil(shape.radius() + shape.knobRadius());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (shape.holds(dx, dy, dz)) {
                        level.setBlock(at.setWithOffset(centre, dx, dy, dz), placed, PLACE_FLAGS);
                    }
                }
            }
        }
        return true;
    }
}
