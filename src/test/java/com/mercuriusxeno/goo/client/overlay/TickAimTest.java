package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tick's overlay draws only on the block the server last named as one it
 * hastens, and only while that word is fresh
 * (decision tick-channel-marches-squares-on-the-face).
 */
class TickAimTest {

    private static final BlockPos FURNACE = new BlockPos(1, 2, 3);
    private static final BlockPos CHEST = new BlockPos(4, 2, 3);
    private static final long NOW = 100L;

    @Test
    void drawsOnTheBlockTheServerNamed() {
        TickAim aim = new TickAim();
        aim.name(FURNACE, NOW);
        assertTrue(aim.drawsOn(FURNACE, NOW));
    }

    @Test
    void drawsOnNoOtherBlock() {
        TickAim aim = new TickAim();
        aim.name(FURNACE, NOW);
        assertFalse(aim.drawsOn(CHEST, NOW));
    }

    @Test
    void drawsNothingWhereTheServerNamedNone() {
        TickAim aim = new TickAim();
        aim.name(null, NOW);
        assertFalse(aim.drawsOn(CHEST, NOW));
    }

    @Test
    void drawsNothingBeforeTheServerNamesABlock() {
        assertFalse(new TickAim().drawsOn(FURNACE, NOW));
    }

    @Test
    void ridesOutALatePacket() {
        TickAim aim = new TickAim();
        aim.name(FURNACE, NOW);
        assertTrue(aim.drawsOn(FURNACE, NOW + TickAim.FRESH_TICKS));
    }

    @Test
    void dropsAStaleName() {
        TickAim aim = new TickAim();
        aim.name(FURNACE, NOW);
        assertFalse(aim.drawsOn(FURNACE, NOW + TickAim.FRESH_TICKS + 1));
    }
}
