package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import java.util.ArrayList;
import java.util.List;

/**
 * An air geyser: a round hole dug into lunar's level ground, walled and floored in its lining stone wherever the
 * ground around it stands open, with whole blocks of typhoon's Updraft goo at its bottom, and trace Updraft cells of a layer or two on the ground around it. Updraft
 * never depletes, so the geyser has no vent and no cycle: it stands for good, lifting what steps over it and
 * refilling its air, and the geysers recur across the landscape as the feature's spacing places them.
 * decision air-geysers-of-updraft-goo-dot-lunar
 * decision updraft-goo-is-semi-permanent-and-does-not-deplete
 *
 * @param lining      the stone the hole is walled and floored in where its ground stands open, never a dust that
 *                    falls, so no regolith is left over air
 * @param radius      the hole's radius, in blocks
 * @param depth       how deep the hole's goo sits under the ground's top, in blocks
 * @param traces      how many trace cells the geyser tries to lay around its hole
 * @param traceReach  the farthest a trace cell lies from the hole's middle, in blocks
 * @param traceLayers the layers a trace cell holds
 */
public record AirGeyserFeature(BlockStateProvider lining, IntProvider radius, IntProvider depth, IntProvider traces,
                               int traceReach, IntProvider traceLayers) implements Feature {

    /** The feature type's codec, naming the hole's size and the trace goo around it. */
    public static final MapCodec<AirGeyserFeature> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockStateProvider.DIRECT_CODEC.fieldOf("lining").forGetter(AirGeyserFeature::lining),
            IntProviders.codec(0, 3).fieldOf("radius").forGetter(AirGeyserFeature::radius),
            IntProviders.codec(1, 4).fieldOf("depth").forGetter(AirGeyserFeature::depth),
            IntProviders.codec(0, 16).fieldOf("traces").forGetter(AirGeyserFeature::traces),
            Codec.intRange(1, 8).fieldOf("trace_reach").forGetter(AirGeyserFeature::traceReach),
            IntProviders.codec(1, VolumeCellBlock.MAX_LAYERS).fieldOf("trace_layers")
                    .forGetter(AirGeyserFeature::traceLayers)
    ).apply(instance, AirGeyserFeature::new));

    /** A trace cell lies at least this far past the hole's radius, so it never borders the hole's wall. */
    static final int TRACE_CLEARANCE = 2;
    /** A trace cell's ground is sought this far over and under the geyser's height. */
    static final int TRACE_SEARCH = 4;
    private static final double FULL_TURN = 2.0 * Math.PI;
    /** A cell laid in generation ticks this soon once its chunk ticks, so its column lifts from the start. */
    private static final int FIRST_TICK = 1;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    @Override
    public MapCodec<AirGeyserFeature> codec() {
        return CODEC;
    }

    /**
     * The columns a hole of a radius takes, as offsets from its middle: a round footprint, a single column at radius
     * zero and a three by three square at radius one.
     *
     * @param radius the hole's radius
     * @return the footprint's offsets, each as {dx, dz}
     */
    static List<int[]> footprint(int radius) {
        List<int[]> columns = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (inFootprint(dx, dz, radius)) {
                    columns.add(new int[]{dx, dz});
                }
            }
        }
        return columns;
    }

    static boolean inFootprint(int dx, int dz, int radius) {
        return dx * dx + dz * dz <= radius * radius + radius;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        BlockPos under = origin.below();
        if (!level.getBlockState(origin).isAir() || !level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)) {
            return false;
        }
        int holeRadius = radius.sample(random);
        List<int[]> hole = footprint(holeRadius);
        if (!opensLevel(level, origin, hole)) {
            return false;
        }
        BlockState ground = lining.getState(level, random, origin);
        int holeDepth = depth.sample(random);
        for (int[] column : hole) {
            digColumn(level, origin, column, holeRadius, holeDepth, ground);
        }
        int count = traces.sample(random);
        for (int trace = 0; trace < count; trace++) {
            layTrace(level, random, origin, holeRadius);
        }
        return true;
    }

    /**
     * Whether every column of the hole stands open at the geyser's height, so the hole is dug under open sky alone and
     * leaves no ground hanging over it.
     *
     * @param level  the level being generated
     * @param origin the hole's middle at the ground's top height
     * @param hole   the hole's footprint
     * @return true where the ground is level enough to dig
     */
    private static boolean opensLevel(WorldGenLevel level, BlockPos origin, List<int[]> hole) {
        for (int[] column : hole) {
            if (!level.getBlockState(origin.offset(column[0], 0, column[1])).isAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Digs one of the hole's columns down to its goo, lays a whole block of Updraft at its bottom, and walls and
     * floors it in the ground wherever the ground around it stands open, so the goo is held where it lies.
     *
     * @param level      the level being generated
     * @param origin     the hole's middle at the ground's top height
     * @param column     the column's offset from the middle, as {dx, dz}
     * @param holeRadius the hole's radius
     * @param holeDepth  how deep the goo sits under the ground's top
     * @param ground     the lining stone
     */
    private static void digColumn(WorldGenLevel level, BlockPos origin, int[] column, int holeRadius, int holeDepth,
                                  BlockState ground) {
        BlockPos cell = origin.offset(column[0], -holeDepth, column[1]);
        for (int dy = 1; dy < holeDepth; dy++) {
            level.setBlock(cell.above(dy), Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
        }
        holdWith(level, cell.below(), ground);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (inFootprint(column[0] + side.getStepX(), column[1] + side.getStepZ(), holeRadius)) {
                continue;
            }
            for (int dy = 0; dy < holeDepth; dy++) {
                holdWith(level, cell.relative(side).above(dy), ground);
            }
        }
        layUpdraft(level, cell, VolumeCellBlock.MAX_LAYERS);
    }

    private static void holdWith(WorldGenLevel level, BlockPos at, BlockState ground) {
        if (!level.getBlockState(at).isFaceSturdy(level, at, Direction.UP)) {
            level.setBlock(at, ground, PLACE_FLAGS);
        }
    }

    /**
     * Lays a trace Updraft cell on the open ground top nearest the geyser's height, at a random spot around the hole
     * clear of its wall; a spot with no open ground top in the search lays none.
     *
     * @param level      the level being generated
     * @param random     the feature's random
     * @param origin     the geyser's middle at the ground's top height
     * @param holeRadius the hole's radius
     */
    private void layTrace(WorldGenLevel level, RandomSource random, BlockPos origin, int holeRadius) {
        int nearest = holeRadius + TRACE_CLEARANCE;
        if (nearest > traceReach) {
            return;
        }
        double angle = random.nextDouble() * FULL_TURN;
        double reach = nearest + random.nextDouble() * (traceReach - nearest);
        BlockPos column = origin.offset((int) Math.round(Math.cos(angle) * reach), 0,
                (int) Math.round(Math.sin(angle) * reach));
        int layers = traceLayers.sample(random);
        for (int dy = TRACE_SEARCH; dy >= -TRACE_SEARCH; dy--) {
            BlockPos spot = column.above(dy);
            BlockPos below = spot.below();
            if (level.getBlockState(spot).isAir() && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                layUpdraft(level, spot, layers);
                return;
            }
        }
    }

    /**
     * Lays an Updraft cell of some layers and schedules its first tick, which a cell laid in generation takes from
     * no placement of its own, so its column lifts once its chunk ticks.
     *
     * @param level  the level being generated
     * @param cell   where the cell lies
     * @param layers its layers
     */
    private static void layUpdraft(WorldGenLevel level, BlockPos cell, int layers) {
        Block updraft = GooBlocks.TYPHOON_UPDRAFT.get();
        level.setBlock(cell, updraft.defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers), PLACE_FLAGS);
        level.scheduleTick(cell, updraft, FIRST_TICK);
    }
}
