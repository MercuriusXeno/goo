package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * How a goo block's volume divides between the surfaces it is attached to,
 * the one division its drawing and its box read: on a floor each attached
 * side takes one layer, never more than an equal share, and the floor the
 * rest; with no floor the attached sides divide the volume equally; a block
 * with no floor and no attached side, its goo on its way down, keeps it as
 * fill. The shares always sum to the volume.
 * attached-goo-draws-per-attachment-and-divides-its-volume
 *
 * @param floor the layers the floor draws
 * @param sides the layers each attached side draws, keyed by the side
 */
public record GooShares(double floor, Map<Direction, Double> sides) {

    /** The layers an attached side of a block on a floor takes, short of an equal share. */
    private static final double SIDE_ON_A_FLOOR = 1.0;

    /**
     * @param volume   the block's volume, 1 to 15 layers
     * @param onAFloor whether the block stands on a floor
     * @param attached the sides it is attached to
     * @return each surface's share of the volume
     */
    public static GooShares of(int volume, boolean onAFloor, Set<Direction> attached) {
        if (attached.isEmpty()) {
            return new GooShares(volume, Map.of());
        }
        double side = onAFloor ? Math.min(SIDE_ON_A_FLOOR, (double) volume / (attached.size() + 1))
                : (double) volume / attached.size();
        Map<Direction, Double> sides = new EnumMap<>(Direction.class);
        attached.forEach(face -> sides.put(face, side));
        return new GooShares(onAFloor ? volume - side * attached.size() : 0, Map.copyOf(sides));
    }
}
