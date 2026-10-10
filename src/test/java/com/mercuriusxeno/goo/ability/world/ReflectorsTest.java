package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reflectors: two reflectors link only when they stand on one axis, never
 * on a diagonal (operator ruling 2026-10-09).
 * decision reflector-rails-carry-the-brightest-light
 */
class ReflectorsTest {

    private static final BlockPos FROM = new BlockPos(10, 64, -5);

    @Test
    void cellsOnEachAxisLink() {
        assertTrue(Reflectors.onOneAxis(FROM, FROM.east(12)));
        assertTrue(Reflectors.onOneAxis(FROM, FROM.above(7)));
        assertTrue(Reflectors.onOneAxis(FROM, FROM.north(30)));
    }

    @Test
    void diagonalCellsNeverLink() {
        assertFalse(Reflectors.onOneAxis(FROM, FROM.east(5).north(5)));
        assertFalse(Reflectors.onOneAxis(FROM, FROM.east(5).above(1)));
        assertFalse(Reflectors.onOneAxis(FROM, FROM.offset(5, 5, 5)));
    }

    @Test
    void aCellNeverLinksToItself() {
        assertFalse(Reflectors.onOneAxis(FROM, FROM));
    }
}
