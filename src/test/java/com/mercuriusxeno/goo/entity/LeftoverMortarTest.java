package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.entity.LeftoverMortar.Riding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Mortar a Grab lift leaves riding its piece: it quivers for the first half second, then sheds evenly over the
 * next two seconds, a layer at a time from the rider holding the most.
 * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
 */
class LeftoverMortarTest {

    private static final int QUIVER_TICKS = 10;
    private static final int LAST_STILL_TICK = 60;
    private static final int SHED_START = 10;
    private static final int SHED_END = 50;
    private static final int ELEVEN_LAYERS = 11;
    private static final int HALFWAY = 30;

    @Test
    void drawnMortarQuiversInTheFirstHalfSecondAndIsStillFromThenOn() {
        assertTrue(IntStream.range(1, QUIVER_TICKS).anyMatch(age -> LeftoverMortar.quiverOffset(age) != 0),
                "The Mortar should quiver at some tick from 1 to 9");
        IntStream.rangeClosed(QUIVER_TICKS, LAST_STILL_TICK).forEach(age ->
                assertEquals(0, LeftoverMortar.quiverOffset(age), "The Mortar should be still at tick " + age));
    }

    @Test
    void ridingLayersShedEvenlyFromTickTenToTickFifty() {
        assertEquals(0, LeftoverMortar.shedBy(SHED_START - 1, ELEVEN_LAYERS));
        assertEquals(0, LeftoverMortar.shedBy(SHED_START, ELEVEN_LAYERS));
        assertEquals(ELEVEN_LAYERS / 2, LeftoverMortar.shedBy(HALFWAY, ELEVEN_LAYERS));
        assertEquals(ELEVEN_LAYERS, LeftoverMortar.shedBy(SHED_END, ELEVEN_LAYERS));
        assertEquals(ELEVEN_LAYERS, LeftoverMortar.shedBy(SHED_END + 1, ELEVEN_LAYERS));
    }

    @Test
    void eachShedLayerLeavesTheRiderHoldingTheMost() {
        Riding small = new Riding(BlockPos.ZERO, Direction.UP, 1);
        Riding large = new Riding(BlockPos.ZERO.east(), Direction.UP, 3);

        assertEquals(List.of(small, new Riding(large.offset(), Direction.UP, 1)),
                LeftoverMortar.ridingAfter(List.of(small, large), 2));
        assertEquals(0, LeftoverMortar.layersOf(LeftoverMortar.ridingAfter(List.of(small, large), 4)));
    }
}
