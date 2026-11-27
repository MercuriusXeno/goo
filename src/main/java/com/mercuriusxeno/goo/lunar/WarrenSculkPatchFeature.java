package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SculkPatchFeature;

/**
 * A sculk patch in the warrens, after vanilla's deep dark patch: from an open spot on a tunnel floor, sculk and its
 * veins spread over the warrens' anorthosite and regolith, growing sculk sensors as they go, and a sculk catalyst
 * stands on the floor at the patch's heart by the deep dark's chance. The warrens home sensors and catalysts alone, so
 * every shrieker the spread grows becomes a sensor, and no sensor stands over the warrens' band.
 * decision warren-sculk-homes-sensors-and-catalysts-and-grows-in-lunar-rock
 */
public final class WarrenSculkPatchFeature implements Feature {

    /** The feature type's codec, which reads no settings. */
    public static final MapCodec<WarrenSculkPatchFeature> CODEC = MapCodec.unit(WarrenSculkPatchFeature::new);

    /** The deep dark patch's spread: its charges, the charge each carries, its spread attempts and rounds. */
    private static final int CHARGE_COUNT = 10;
    private static final int AMOUNT_PER_CHARGE = 32;
    private static final int SPREAD_ATTEMPTS = 64;
    private static final int GROWTH_ROUNDS = 0;
    private static final int SPREAD_ROUNDS = 1;
    private static final SculkPatchFeature DEEP_DARK_SPREAD = new SculkPatchFeature(CHARGE_COUNT, AMOUNT_PER_CHARGE,
            SPREAD_ATTEMPTS, GROWTH_ROUNDS, SPREAD_ROUNDS);
    /** The deep dark's chance a patch's heart grows a catalyst. */
    static final float CATALYST_CHANCE = 0.5F;
    /** How far from the heart a patch's growths are swept, sideways and up and down. */
    static final int SWEEP_REACH = 15;
    static final int SWEEP_HEIGHT = 10;
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Creates the feature, which takes no configuration. */
    public WarrenSculkPatchFeature() {
    }

    @Override
    public MapCodec<WarrenSculkPatchFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        BlockPos heart = WarrenFloors.findNear(level, random, origin);
        if (heart == null || !DEEP_DARK_SPREAD.place(level, chunkGenerator, random, heart)) {
            return false;
        }
        BlockPos floor = heart.below();
        if (random.nextFloat() < CATALYST_CHANCE && level.getBlockState(heart).isAir()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
            level.setBlock(heart, Blocks.SCULK_CATALYST.defaultBlockState(), PLACE_FLAGS);
        }
        sweepGrowths(level, heart);
        return true;
    }

    /**
     * Turns every shrieker around a patch's heart into a sensor, and clears every growth standing over the warrens'
     * band and every growth or vein standing on a monolith marker, so each marker keeps open air over it; it reaches
     * only the chunks the feature may write.
     *
     * @param level the level being generated
     * @param heart the patch's heart
     */
    private static void sweepGrowths(WorldGenLevel level, BlockPos heart) {
        for (BlockPos at : BlockPos.betweenClosed(heart.offset(-SWEEP_REACH, -SWEEP_HEIGHT, -SWEEP_REACH),
                heart.offset(SWEEP_REACH, SWEEP_HEIGHT, SWEEP_REACH))) {
            if (!level.hasChunk(SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ()))) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (clears(level, at, state)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
            } else if (state.is(Blocks.SCULK_SHRIEKER)) {
                level.setBlock(at, Blocks.SCULK_SENSOR.defaultBlockState(), PLACE_FLAGS);
            }
        }
    }

    /**
     * Whether the sweep clears a block: a growth over the warrens' band, or a growth or vein on a monolith marker.
     *
     * @param level the level being generated
     * @param at    the block's position
     * @param state the block
     * @return whether it is cleared to air
     */
    private static boolean clears(WorldGenLevel level, BlockPos at, BlockState state) {
        boolean growth = state.is(Blocks.SCULK_SHRIEKER) || state.is(Blocks.SCULK_SENSOR);
        boolean onMarker = (growth || state.is(Blocks.SCULK_VEIN))
                && level.getBlockState(at.below()).is(GooBlocks.MONOLITH_MARKER.get());
        return growth && at.getY() >= LunarDepths.WARREN_ZONE_TOP_Y || onMarker;
    }
}
