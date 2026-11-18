package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A small cluster of astral crystal on the bare rock of lunar's middle caverns: from an open spot in a cavern, each
 * open block close by that borders rock may grow a crystal, facing out from the rock it grows on, up to a handful.
 * Nothing grows outside the middle zone's band.
 * decision astral-crystal-is-lunars-scarce-rich-block
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
public final class AstralCrystalClusterFeature extends Feature<NoneFeatureConfiguration> {

    /** How far from the cluster's centre a crystal may grow, each way. */
    static final int REACH = 2;
    /** The most crystals one cluster grows. */
    static final int MOST_CRYSTALS = 6;
    /** The chance an open block bordering rock grows a crystal. */
    static final float GROWTH_CHANCE = 0.4F;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Creates the feature, which takes no configuration. */
    public AstralCrystalClusterFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        if (!opensInMiddleZone(level, origin)) {
            return false;
        }
        int grown = 0;
        for (BlockPos spot : BlockPos.betweenClosed(origin.offset(-REACH, -REACH, -REACH),
                origin.offset(REACH, REACH, REACH))) {
            if (grown >= MOST_CRYSTALS) {
                break;
            }
            if (growCrystal(level, spot, context.random())) {
                grown++;
            }
        }
        return grown > 0;
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

    /**
     * Grows a crystal at an open spot bordering rock, by the growth chance.
     *
     * @param level the level being generated
     * @param spot the spot a crystal may grow in
     * @param random the feature's random
     * @return whether a crystal grew
     */
    private static boolean growCrystal(WorldGenLevel level, BlockPos spot, RandomSource random) {
        if (!opensInMiddleZone(level, spot) || random.nextFloat() >= GROWTH_CHANCE) {
            return false;
        }
        Direction facing = facingFromRock(level, spot, random);
        if (facing == null) {
            return false;
        }
        level.setBlock(spot, GooBlocks.ASTRAL_CRYSTAL.get().defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, facing), PLACE_FLAGS);
        return true;
    }

    /**
     * A random side of the spot whose neighbour is rock with a sturdy face toward it, as the facing away from it.
     *
     * @param level the level being generated
     * @param spot the open spot
     * @param random the feature's random
     * @return the facing away from the rock, or null where no side borders rock
     */
    private static Direction facingFromRock(WorldGenLevel level, BlockPos spot, RandomSource random) {
        List<Direction> sides = new ArrayList<>(Arrays.asList(Direction.values()));
        for (int i = sides.size() - 1; i > 0; i--) {
            sides.set(i, sides.set(random.nextInt(i + 1), sides.get(i)));
        }
        for (Direction facing : sides) {
            BlockPos root = spot.relative(facing.getOpposite());
            BlockState rock = level.getBlockState(root);
            if (!rock.is(GooBlocks.ASTRAL_CRYSTAL.get()) && rock.isFaceSturdy(level, root, facing)) {
                return facing;
            }
        }
        return null;
    }
}
