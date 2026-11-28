package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import java.util.Optional;

/**
 * Lays one of the warrens' armed sculk mechanisms at an open spot on a warren tunnel's floor, its layout picked fresh:
 * the sensor at the spot, the emitter beside it, and each crystal and the resonator settled onto the tunnel's uneven
 * floor near its own place, within the warrens' band and touching no monolith marker. A mechanism whose emitter has no
 * room, or none of whose crystals finds floor, is not laid. It runs in some chunks only, so plenty of the warrens'
 * sculk stands unarmed and a player cannot tell which patch is.
 * decision sculk-sensors-wire-to-pulse-emitter-crystals-that-set-off-volatile-crystals
 */
public final class WarrenMechanismFeature implements Feature {

    /** The feature type's codec, which reads no settings. */
    public static final MapCodec<WarrenMechanismFeature> CODEC = MapCodec.unit(WarrenMechanismFeature::new);

    /** The heights a floor-standing part tries from its place, nearest first: its own, then one and two up and down. */
    static final int[] SETTLE_STEPS = {0, -1, 1, -2, 2};
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;

    /** Creates the feature, which takes no configuration. */
    public WarrenMechanismFeature() {
    }

    @Override
    public MapCodec<WarrenMechanismFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        BlockPos sensor = WarrenFloors.findNear(level, random, origin);
        if (sensor == null) {
            return false;
        }
        return WarrenMechanism.pick(random).plan(sensor, cell -> open(level, cell), cell -> settle(level, cell))
                .map(plan -> {
                    WarrenMechanism.lay(level, plan, PLACE_FLAGS);
                    return true;
                }).orElse(false);
    }

    /**
     * Whether a cell is open for a part: air or a block that gives way, such as a sculk vein, in the warrens' band,
     * touching no monolith marker.
     *
     * @param level the level being generated
     * @param cell  the cell
     * @return whether it is open
     */
    private static boolean open(WorldGenLevel level, BlockPos cell) {
        return level.getBlockState(cell).canBeReplaced() && VolatileCrystalClusterFeature.inWarrens(cell.getY())
                && !WarrenFloors.touchesAMarker(level, cell);
    }

    /**
     * Where a floor-standing part near a cell settles: the nearest open cell within two up or down over a floor sturdy
     * on top, the cell's own height first.
     *
     * @param level the level being generated
     * @param cell  the part's place
     * @return where it stands, or empty where no floor is near
     */
    private static Optional<BlockPos> settle(WorldGenLevel level, BlockPos cell) {
        for (int dy : SETTLE_STEPS) {
            BlockPos at = cell.above(dy);
            BlockPos floor = at.below();
            if (open(level, at) && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                return Optional.of(at);
            }
        }
        return Optional.empty();
    }
}
