package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A body touches the mycelial block it stands on, in or presses against, and
 * not one a step away (decision transmit-reaches-only-connected-mycelial-blocks-you-touch).
 */
class MycelialTouchTest {

    private static final double HALF_WIDTH = 0.3;
    private static final double HEIGHT = 1.8;

    /** A player's box standing at a point. */
    private static AABB standingAt(double x, double y, double z) {
        return new AABB(x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + HEIGHT, z + HALF_WIDTH);
    }

    @Test
    void theBlockStoodOnIsTouched() {
        BlockPos under = new BlockPos(0, 0, 0);
        assertEquals(List.of(under), MycelialTouch.touched(standingAt(0.5, 1, 0.5), Set.of(under)::contains));
    }

    @Test
    void aBlockPressedAgainstIsTouched() {
        BlockPos wall = new BlockPos(1, 1, 0);
        assertEquals(List.of(wall), MycelialTouch.touched(standingAt(1 - HALF_WIDTH, 1, 0.5), Set.of(wall)::contains));
    }

    @Test
    void aBlockBesideTheOneStoodOnIsNotTouched() {
        BlockPos beside = new BlockPos(1, 0, 0);
        assertEquals(List.of(), MycelialTouch.touched(standingAt(0.5, 1, 0.5), Set.of(beside)::contains));
    }
}
