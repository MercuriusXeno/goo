package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.data.GooValueTable;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the price a caster pays for an ability by the level they stand in, read from the shipped JSON.
 */
class AbilityPriceTest {

    /** decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in */
    @Nested
    class EndInTheEnd {

        private final AbilityDefinition end = AbilityJson.decode("ender_end");

        @Test
        void endCostsNothingForACasterInTheEnd() {
            assertEquals(0, end.cost().forCasterIn(Level.END, end.behaviors()).in(GooValueTable.EMPTY));
        }

        @Test
        void endCostsItsJsonCostForACasterInTheOverworld() {
            assertEquals(end.cost().in(GooValueTable.EMPTY),
                    end.cost().forCasterIn(Level.OVERWORLD, end.behaviors()).in(GooValueTable.EMPTY));
        }
    }
}
