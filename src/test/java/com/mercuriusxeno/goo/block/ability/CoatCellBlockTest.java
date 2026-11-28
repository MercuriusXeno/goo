package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A coated face keeps a block from a blast on its side and from no other.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
class CoatCellBlockTest {

    private static final Vec3 BLOCK = new Vec3(0.5, 0.5, 0.5);
    private static final Vec3 EAST_BLAST = new Vec3(2.5, 0.6, 0.5);

    @Test
    void aBlastOnTheCoatedSideIsKeptOff() {
        assertTrue(CoatCellBlock.shieldsFrom(BLOCK, EAST_BLAST, Set.of(Direction.EAST)));
    }

    @Test
    void aBlastOnABareSideIsNot() {
        assertFalse(CoatCellBlock.shieldsFrom(BLOCK, EAST_BLAST, Set.of(Direction.WEST, Direction.DOWN)));
    }
}
