package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * The fence round the chunks a feature may read and write while one chunk generates: the origin's chunk, which the
 * placement keeps the origin inside, and one chunk out each way. Past it a read logs an unsafe terrain read.
 * decision warren-sculk-homes-sensors-and-catalysts-and-grows-in-lunar-rock
 */
final class WritableFence {

    /** How many chunks out from the chunk being generated a feature may read and write. */
    static final int WRITE_RADIUS = 1;

    private WritableFence() {
    }

    /**
     * The lowest block coordinate along one axis a feature may reach from an origin: the edge of the chunk one out from
     * the origin's.
     *
     * @param origin the origin's coordinate along the axis
     * @return the lowest coordinate it may reach
     */
    static int writableMin(int origin) {
        return SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin) - WRITE_RADIUS);
    }

    /**
     * The highest block coordinate along one axis a feature may reach from an origin: the far edge of the chunk one out
     * from the origin's.
     *
     * @param origin the origin's coordinate along the axis
     * @return the highest coordinate it may reach
     */
    static int writableMax(int origin) {
        return SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin) + WRITE_RADIUS + 1) - 1;
    }

    /**
     * Whether a position lies inside the fence around an origin.
     *
     * @param origin the placed origin, inside the chunk being generated
     * @param at     the position
     * @return whether a feature may reach it
     */
    static boolean inside(BlockPos origin, BlockPos at) {
        return at.getX() >= writableMin(origin.getX()) && at.getX() <= writableMax(origin.getX())
                && at.getZ() >= writableMin(origin.getZ()) && at.getZ() <= writableMax(origin.getZ());
    }
}
