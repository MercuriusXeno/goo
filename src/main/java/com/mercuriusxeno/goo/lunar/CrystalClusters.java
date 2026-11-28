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
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * A small cluster of a lunar crystal on rock: from an open spot, each open block close by that borders rock may grow a
 * crystal, facing out from the rock it grows on, up to a handful. Each crystal's cluster names where a spot is open.
 * decision astral-crystal-is-lunars-scarce-rich-block
 * decision void-crystals-spawn-on-the-moon
 */
public final class CrystalClusters implements Feature {

    /** {@code goo:astral_crystal_cluster}: astral crystal on the bare rock of the middle caverns, within their band. */
    public static final CrystalClusters ASTRAL = new CrystalClusters(GooBlocks.ASTRAL_CRYSTAL,
            CrystalClusters::opensInMiddleZone);
    /** {@code goo:void_crystal_cluster}: void crystal on any open rock, the dark side's surface where it places. */
    public static final CrystalClusters VOID = new CrystalClusters(GooBlocks.VOID_CRYSTAL,
            (level, spot) -> level.getBlockState(spot).isAir());

    /** How far from the cluster's centre a crystal may grow, each way. */
    static final int REACH = 2;
    /** The most crystals one cluster grows. */
    static final int MOST_CRYSTALS = 6;
    /** The chance an open block bordering rock grows a crystal. */
    static final float GROWTH_CHANCE = 0.4F;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    private final Supplier<? extends Block> crystal;
    private final BiPredicate<WorldGenLevel, BlockPos> open;
    private final MapCodec<CrystalClusters> codec;

    /**
     * @param crystal the crystal the cluster grows
     * @param open    whether a spot is open for a crystal to grow in
     */
    private CrystalClusters(Supplier<? extends Block> crystal, BiPredicate<WorldGenLevel, BlockPos> open) {
        this.crystal = crystal;
        this.open = open;
        this.codec = MapCodec.unit(this);
    }

    /**
     * Whether a height lies in the middle zone's band, where the bare rock caverns open.
     *
     * @param y the height
     * @return whether the middle zone's band holds it
     */
    static boolean inMiddleZone(int y) {
        return y >= LunarDepths.WARREN_ZONE_TOP_Y && y < LunarDepths.CAVERN_ZONE_TOP_Y;
    }

    private static boolean opensInMiddleZone(WorldGenLevel level, BlockPos spot) {
        return inMiddleZone(spot.getY()) && level.getBlockState(spot).isAir();
    }

    /** The feature type's codec, which reads no settings and answers this cluster. */
    @Override
    public MapCodec<CrystalClusters> codec() {
        return codec;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        if (!open.test(level, origin)) {
            return false;
        }
        int grown = 0;
        for (BlockPos spot : BlockPos.betweenClosed(origin.offset(-REACH, -REACH, -REACH),
                origin.offset(REACH, REACH, REACH))) {
            if (grown >= MOST_CRYSTALS) {
                break;
            }
            if (growCrystal(level, spot, random)) {
                grown++;
            }
        }
        return grown > 0;
    }

    private boolean growCrystal(WorldGenLevel level, BlockPos spot, RandomSource random) {
        if (!open.test(level, spot) || random.nextFloat() >= GROWTH_CHANCE) {
            return false;
        }
        Direction facing = facingFromRock(level, spot, random);
        if (facing == null) {
            return false;
        }
        level.setBlock(spot, crystal.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, facing),
                PLACE_FLAGS);
        return true;
    }

    /**
     * A random side of the spot whose neighbour is rock with a sturdy face toward it, as the facing away from it.
     *
     * @param level  the level being generated
     * @param spot   the open spot
     * @param random the feature's random
     * @return the facing away from the rock, or null where no side borders rock
     */
    private Direction facingFromRock(WorldGenLevel level, BlockPos spot, RandomSource random) {
        List<Direction> sides = new ArrayList<>(Arrays.asList(Direction.values()));
        for (int i = sides.size() - 1; i > 0; i--) {
            sides.set(i, sides.set(random.nextInt(i + 1), sides.get(i)));
        }
        for (Direction facing : sides) {
            BlockPos root = spot.relative(facing.getOpposite());
            BlockState rock = level.getBlockState(root);
            if (!rock.is(crystal.get()) && rock.isFaceSturdy(level, root, facing)) {
                return facing;
            }
        }
        return null;
    }
}
