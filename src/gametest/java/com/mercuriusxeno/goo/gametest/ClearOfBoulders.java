package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.function.Predicate;

/**
 * Passes a column no giant crater's thrown boulder covers, read through the seeded giant crater field, where a test
 * reads ground a boulder's stone would stand in for.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
final class ClearOfBoulders implements Predicate<BlockPos> {

    /** A boulder's farthest reach from its spot: its widest radius and a knob past it. */
    private static final double BOULDER_REACH = 16.0;

    private final GiantCraterField giants;

    private ClearOfBoulders(GiantCraterField giants) {
        this.giants = giants;
    }

    /**
     * The predicate for a level's seeded giant craters.
     *
     * @param lunar the level
     * @return the predicate
     */
    static ClearOfBoulders in(ServerLevel lunar) {
        return new ClearOfBoulders(GiantCraterField.seeded(lunar.getChunkSource().randomState()
                .getOrCreateNoise(CraterField.NOISE)));
    }

    @Override
    public boolean test(BlockPos column) {
        int cellX = Math.floorDiv(column.getX(), GiantCraterField.CELL);
        int cellZ = Math.floorDiv(column.getZ(), GiantCraterField.CELL);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean covered = giants.craterInCell(cellX + dx, cellZ + dz).map(crater -> crater.boulderSpots()
                        .stream().anyMatch(spot -> Math.hypot(spot.getX() - column.getX(), spot.getZ() - column.getZ())
                                <= BOULDER_REACH)).orElse(false);
                if (covered) {
                    return false;
                }
            }
        }
        return true;
    }
}
