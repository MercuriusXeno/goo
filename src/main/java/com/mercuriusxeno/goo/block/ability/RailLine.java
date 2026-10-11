package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The cells a straight line between two blocks' centers passes through,
 * the ends left out: where a light rail runs between two reflector prisms
 * linked in any direction.
 * decision reflector-rails-carry-the-brightest-light
 */
public final class RailLine {

    /** Samples per block of the line's length, enough that no cell it crosses is skipped. */
    private static final int SAMPLES_PER_BLOCK = 4;
    private static final double HALF = 0.5;

    private RailLine() {
    }

    /**
     * The cells between two blocks, in order from the first, neither end included.
     *
     * @param from one end
     * @param to   the other end
     * @return the cells between, empty for neighbors
     */
    public static List<BlockPos> between(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        double length = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
        int samples = (int) Math.ceil(length * SAMPLES_PER_BLOCK);
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int sample = 1; sample < samples; sample++) {
            double t = (double) sample / samples;
            BlockPos cell = BlockPos.containing(from.getX() + HALF + dx * t, from.getY() + HALF + dy * t,
                    from.getZ() + HALF + dz * t);
            if (!cell.equals(from) && !cell.equals(to)) {
                cells.add(cell);
            }
        }
        return new ArrayList<>(cells);
    }
}
