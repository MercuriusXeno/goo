package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import java.util.ArrayList;
import java.util.List;

/**
 * A native iron lode set into the face of a rubble cave: from an open spot in a cave, it takes a breccia block on the
 * cave's surface and grows a few blocks of native iron through the breccia beside it, favouring blocks that also face
 * the cave, so a player in the cave sees the lode rather than breccia alone. Nothing grows outside the shallow zone.
 * decision native-iron-lodes-are-worth-whole-iron-blocks
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
public final class NativeIronLodeFeature extends Feature<NoneFeatureConfiguration> {

    /** How far from the open spot the lode's first block may sit, each way. */
    static final int REACH = 2;
    /** How many blocks of native iron one lode holds. */
    static final int LODE_SIZE = 3;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Creates the feature, which takes no configuration. */
    public NativeIronLodeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        if (!inShallowZone(origin.getY()) || !level.getBlockState(origin).isAir()) {
            return false;
        }
        List<BlockPos> face = caveFace(level, origin);
        if (face.isEmpty()) {
            return false;
        }
        growLode(level, face.get(random.nextInt(face.size())), random);
        return true;
    }

    /**
     * The breccia blocks near an open spot that face the cave's air.
     *
     * @param level the level being generated
     * @param origin the open spot
     * @return the cave's face around it
     */
    private static List<BlockPos> caveFace(WorldGenLevel level, BlockPos origin) {
        List<BlockPos> face = new ArrayList<>();
        for (BlockPos spot : BlockPos.betweenClosed(origin.offset(-REACH, -REACH, -REACH),
                origin.offset(REACH, REACH, REACH))) {
            if (isBreccia(level, spot) && touchesAir(level, spot)) {
                face.add(spot.immutable());
            }
        }
        return face;
    }

    /**
     * Sets native iron at the lode's first block and grows it through the breccia beside it to the lode's size.
     *
     * @param level the level being generated
     * @param first the lode's first block, on the cave's face
     * @param random the feature's random
     */
    private static void growLode(WorldGenLevel level, BlockPos first, RandomSource random) {
        List<BlockPos> lode = new ArrayList<>();
        BlockPos next = first;
        while (next != null && lode.size() < LODE_SIZE) {
            level.setBlock(next, GooBlocks.NATIVE_IRON.get().defaultBlockState(), PLACE_FLAGS);
            lode.add(next);
            next = nextLodeBlock(level, lode, random);
        }
    }

    /**
     * Whether a height lies in the shallow zone's band, where the rubble caves open.
     *
     * @param y the height
     * @return whether the shallow zone's band holds it
     */
    static boolean inShallowZone(int y) {
        return y >= LunarDepths.CAVERN_ZONE_TOP_Y && y < LunarDepths.SHALLOW_ZONE_TOP_Y;
    }

    /**
     * The breccia beside the lode it grows into next, a block that faces the cave where one does.
     *
     * @param level the level being generated
     * @param lode the lode's blocks so far
     * @param random the feature's random
     * @return the next block, or null where no breccia borders the lode
     */
    private static BlockPos nextLodeBlock(WorldGenLevel level, List<BlockPos> lode, RandomSource random) {
        List<BlockPos> facing = new ArrayList<>();
        List<BlockPos> buried = new ArrayList<>();
        for (BlockPos block : lode) {
            for (Direction side : Direction.values()) {
                BlockPos beside = block.relative(side);
                if (!isBreccia(level, beside) || lode.contains(beside)) {
                    continue;
                }
                (touchesAir(level, beside) ? facing : buried).add(beside);
            }
        }
        List<BlockPos> pool = facing.isEmpty() ? buried : facing;
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    private static boolean isBreccia(WorldGenLevel level, BlockPos spot) {
        return inShallowZone(spot.getY()) && level.getBlockState(spot).is(GooBlocks.BRECCIA.get());
    }

    private static boolean touchesAir(WorldGenLevel level, BlockPos spot) {
        for (Direction side : Direction.values()) {
            if (level.getBlockState(spot.relative(side)).isAir()) {
                return true;
            }
        }
        return false;
    }
}
