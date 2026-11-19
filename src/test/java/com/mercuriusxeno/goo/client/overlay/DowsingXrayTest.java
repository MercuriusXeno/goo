package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.ability.GooShares;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Dowsing cell's window is the goo the cell draws, where its volume lies:
 * a slab on each wall or ceiling it hangs from, and a fill on its floor, so
 * the ore behind the goo shows across the whole goo and the goo draws over
 * none of it.
 * dowsing-goo-is-a-window-onto-the-ore-behind-it
 * astral-goo-hangs-and-is-loose
 * attached-goo-draws-per-attachment-and-divides-its-volume
 */
class DowsingXrayTest {

    private static final BlockPos CELL = new BlockPos(4, 7, -2);
    private static final double THREE_LAYERS = 3;
    private static final double SLAB = THREE_LAYERS / 15;

    @Test
    void gooHangingOnAnEastWallWindowsTheSlabAgainstThatWall() {
        GooShares onTheWall = new GooShares(0, Map.of(Direction.EAST, THREE_LAYERS));

        assertEquals(List.of(new AABB(5 - SLAB, 7, -2, 5, 8, -1)), DowsingXray.gooOf(CELL, onTheWall));
    }

    @Test
    void gooHangingFromACeilingWindowsTheSlabUnderIt() {
        GooShares underTheCeiling = new GooShares(0, Map.of(Direction.UP, THREE_LAYERS));

        assertEquals(List.of(new AABB(4, 8 - SLAB, -2, 5, 8, -1)), DowsingXray.gooOf(CELL, underTheCeiling));
    }
}
