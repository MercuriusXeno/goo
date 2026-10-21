package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The plexer fabricates at the ceiling of each type, read through a fake goo supply
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class PlexerBlockEntityTest {

    @Test
    void aThirdsTargetIsRefusedAtTheFloor() {
        MapGooSupply supply = MapGooSupply.holding(GooTypes.LEAF, 21);

        assertFalse(PlexerBlockEntity.fabricateFrom(fraction(GooTypes.LEAF, 64, 3), supply));
        assertEquals(21, supply.available(GooTypes.LEAF));
    }

    @Test
    void aThirdsTargetIsMadeAtTheCeilingAndConsumesIt() {
        MapGooSupply supply = MapGooSupply.holding(GooTypes.LEAF, 22);

        assertTrue(PlexerBlockEntity.fabricateFrom(fraction(GooTypes.LEAF, 64, 3), supply));
        assertEquals(0, supply.available(GooTypes.LEAF));
    }
}
