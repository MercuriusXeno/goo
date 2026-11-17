package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The goo drawn in a block is the volume it holds, divided between the
 * surfaces it is attached to: on a floor each attached side takes one layer,
 * never more than an equal share, and the floor the rest; with no floor the
 * attached sides divide the volume equally; the shares always sum to the
 * volume (decision attached-goo-draws-per-attachment-and-divides-its-volume).
 */
class GooSharesTest {

    private static final double SAME = 1e-9;

    @Test
    void onAFloorEachAttachedSideTakesOneLayerAndTheFloorTheRest() {
        GooShares shares = GooShares.of(8, true, Set.of(Direction.NORTH, Direction.WEST));

        assertEquals(6, shares.floor(), SAME);
        assertEquals(Map.of(Direction.NORTH, 1.0, Direction.WEST, 1.0), shares.sides());
    }

    @Test
    void withNoFloorTheAttachedSidesDivideTheVolumeEqually() {
        GooShares shares = GooShares.of(8, false, Set.of(Direction.NORTH, Direction.SOUTH));

        assertEquals(0, shares.floor(), SAME);
        assertEquals(Map.of(Direction.NORTH, 4.0, Direction.SOUTH, 4.0), shares.sides());
    }

    @Test
    void withNoFloorOneAttachedSideTakesTheWholeVolume() {
        assertEquals(Map.of(Direction.EAST, 15.0), GooShares.of(15, false, Set.of(Direction.EAST)).sides());
    }

    @Test
    void anAttachedSideOnAFloorTakesNoMoreThanAnEqualShare() {
        GooShares shares = GooShares.of(2, true, Set.of(Direction.NORTH, Direction.EAST, Direction.SOUTH));

        assertEquals(0.5, shares.sides().get(Direction.NORTH), SAME);
        assertEquals(0.5, shares.floor(), SAME);
    }

    @Test
    void theSharesSumToTheVolume() {
        for (int volume = 1; volume <= 15; volume++) {
            for (boolean onAFloor : new boolean[]{true, false}) {
                GooShares shares = GooShares.of(volume, onAFloor, Set.of(Direction.NORTH, Direction.EAST));
                double sum = shares.floor() + shares.sides().values().stream().mapToDouble(Double::doubleValue).sum();
                assertEquals(volume, sum, SAME, "volume " + volume + (onAFloor ? " on a floor" : " with no floor"));
            }
        }
    }
}
