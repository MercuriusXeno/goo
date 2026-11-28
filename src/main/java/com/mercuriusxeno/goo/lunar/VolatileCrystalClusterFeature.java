package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import java.util.ArrayList;
import java.util.List;

/**
 * A small volatile crystal structure in the warrens: a first crystal grows up from an open spot on a tunnel floor,
 * and the rest grow one by one into open blocks beside the crystals already grown, each from whichever rock face it
 * borders, floor, wall or ceiling, so the cluster creeps along the tunnel's rock as one touching growth. Nothing grows
 * outside the warrens' band.
 * decision volatile-crystals-are-rich-in-crystal-and-unstable
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
public final class VolatileCrystalClusterFeature implements Feature {

    /** The feature type's codec, which reads no settings. */
    public static final MapCodec<VolatileCrystalClusterFeature> CODEC =
            MapCodec.unit(VolatileCrystalClusterFeature::new);

    /** The fewest and most crystals one cluster grows. */
    static final int FEWEST_CRYSTALS = 3;
    static final int MOST_CRYSTALS = 7;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Creates the feature, which takes no configuration. */
    public VolatileCrystalClusterFeature() {
    }

    @Override
    public MapCodec<VolatileCrystalClusterFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        BlockPos seed = WarrenFloors.findNear(level, random, origin);
        if (seed == null) {
            return false;
        }
        List<BlockPos> grown = new ArrayList<>();
        grow(level, seed, Direction.UP, grown);
        int wanted = FEWEST_CRYSTALS + random.nextInt(MOST_CRYSTALS - FEWEST_CRYSTALS + 1);
        while (grown.size() < wanted) {
            List<BlockPos> open = openBesideCluster(level, grown);
            if (open.isEmpty()) {
                break;
            }
            BlockPos next = open.get(random.nextInt(open.size()));
            grow(level, next, facingFromRock(level, next, random), grown);
        }
        return true;
    }

    private static void grow(WorldGenLevel level, BlockPos at, Direction facing, List<BlockPos> grown) {
        BlockState crystal = GooBlocks.VOLATILE_CRYSTAL.get().defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, facing);
        level.setBlock(at, crystal, PLACE_FLAGS);
        grown.add(at);
    }

    /**
     * The open blocks beside the cluster a crystal may grow in: air in the warrens' band touching no monolith marker,
     * bordering rock with a sturdy face toward it.
     *
     * @param level the level being generated
     * @param grown the crystals grown so far
     * @return each open block a crystal may grow in
     */
    private static List<BlockPos> openBesideCluster(WorldGenLevel level, List<BlockPos> grown) {
        List<BlockPos> open = new ArrayList<>();
        for (BlockPos crystal : grown) {
            for (Direction side : Direction.values()) {
                BlockPos next = crystal.relative(side);
                if (!open.contains(next) && mayGrowIn(level, next)) {
                    open.add(next);
                }
            }
        }
        return open;
    }

    /**
     * Whether a crystal may grow in a block: open air in the warrens' band, touching no monolith marker, bordering
     * rock with a sturdy face toward it.
     *
     * @param level the level being generated
     * @param spot  the block
     * @return whether a crystal may grow there
     */
    private static boolean mayGrowIn(WorldGenLevel level, BlockPos spot) {
        boolean open = level.getBlockState(spot).isAir() && inWarrens(spot.getY());
        return open && !WarrenFloors.touchesAMarker(level, spot) && !rockFacesOf(level, spot).isEmpty();
    }

    /**
     * Whether a height lies in the warrens' band.
     *
     * @param y the height
     * @return whether the band holds it
     */
    static boolean inWarrens(int y) {
        return y >= LunarDepths.SEA_ZONE_TOP_Y && y < LunarDepths.WARREN_ZONE_TOP_Y;
    }

    /**
     * Each facing away from a rock face a spot borders, the rock no crystal and sturdy toward the spot.
     *
     * @param level the level being generated
     * @param spot  the spot
     * @return each facing a crystal there may grow along
     */
    private static List<Direction> rockFacesOf(WorldGenLevel level, BlockPos spot) {
        List<Direction> facings = new ArrayList<>();
        for (Direction facing : Direction.values()) {
            BlockPos root = spot.relative(facing.getOpposite());
            BlockState rock = level.getBlockState(root);
            if (!rock.is(GooBlocks.VOLATILE_CRYSTAL.get()) && rock.isFaceSturdy(level, root, facing)) {
                facings.add(facing);
            }
        }
        return facings;
    }

    private static Direction facingFromRock(WorldGenLevel level, BlockPos spot, RandomSource random) {
        List<Direction> facings = rockFacesOf(level, spot);
        return facings.get(random.nextInt(facings.size()));
    }
}
