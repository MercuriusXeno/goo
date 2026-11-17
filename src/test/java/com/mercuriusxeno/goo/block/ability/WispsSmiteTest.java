package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.WispsStep;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Wisps goo's smite: undead touching it burn and take holy damage rising
 * with the cell's fill, at glow_wisps.json's per layer tunable, and the
 * living are spared.
 * undead-touching-wisps-goo-erupt-in-flames-and-take-holy-damage-by-fill
 */
class WispsSmiteTest {

    private static final int FULL = 15;

    @Nested
    class HolyDamage {

        private final double perLayer = WispsStep.in(AbilityJson.decode("glow_wisps").behaviors())
                .orElseThrow().holyPerLayer();

        @Test
        void aOneLayerCellDealsThePerLayerTunable() {
            assertEquals(perLayer, WispsSmite.holyDamage(1, perLayer), 1e-9);
        }

        @Test
        void aFullCellDealsFifteenTimesAOneLayerCell() {
            assertEquals(FULL * WispsSmite.holyDamage(1, perLayer), WispsSmite.holyDamage(FULL, perLayer), 1e-9);
        }
    }

    @Nested
    class Undead {

        @Test
        void aZombieBurns() {
            Zombie zombie = mock(Zombie.class);
            when(zombie.isInvertedHealAndHarm()).thenReturn(true);

            assertTrue(WispsSmite.smites(zombie));
        }

        @Test
        void aCowIsSpared() {
            assertFalse(WispsSmite.smites(mock(Cow.class)));
        }
    }
}
