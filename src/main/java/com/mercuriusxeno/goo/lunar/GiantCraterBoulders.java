package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooPlacementModifiers;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import java.util.stream.Stream;

/**
 * The {@code goo:giant_crater_boulders} placement modifier: it turns a chunk's origin into the spots in that chunk
 * where a giant crater threw its boulders, read from the same seeded crater noise the terrain sinks its giant craters
 * from, so every giant crater has its boulders and no open ground does.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
public final class GiantCraterBoulders extends PlacementModifier {

    private static final GiantCraterBoulders INSTANCE = new GiantCraterBoulders();
    /** The data codec of the modifier, which takes no fields. */
    public static final MapCodec<GiantCraterBoulders> CODEC = MapCodec.unit(() -> INSTANCE);
    private static final int CHUNK_SIDE = 16;
    /** A chunk reads the giant craters of its own cell and the cells one step round it, all a boulder reaches from. */
    private static final int NEIGHBOUR_REACH = 1;

    private GiantCraterBoulders() {
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
        ServerLevel level = context.getLevel().getLevel();
        GiantCraterField giants = GiantCraterField.seeded(level.getChunkSource().randomState()
                .getOrCreateNoise(CraterField.NOISE));
        int minX = Math.floorDiv(origin.getX(), CHUNK_SIDE) * CHUNK_SIDE;
        int minZ = Math.floorDiv(origin.getZ(), CHUNK_SIDE) * CHUNK_SIDE;
        int cellX = Math.floorDiv(minX, GiantCraterField.CELL);
        int cellZ = Math.floorDiv(minZ, GiantCraterField.CELL);
        Stream.Builder<BlockPos> spots = Stream.builder();
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                giants.craterInCell(cellX + dx, cellZ + dz).ifPresent(crater -> crater.boulderSpots().stream()
                        .filter(spot -> spot.getX() >= minX && spot.getX() < minX + CHUNK_SIDE
                                && spot.getZ() >= minZ && spot.getZ() < minZ + CHUNK_SIDE)
                        .forEach(spot -> spots.add(spot.atY(origin.getY()))));
            }
        }
        return spots.build();
    }

    @Override
    public PlacementModifierType<?> type() {
        return GooPlacementModifiers.GIANT_CRATER_BOULDERS.get();
    }
}
