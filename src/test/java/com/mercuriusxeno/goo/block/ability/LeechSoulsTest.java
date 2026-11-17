package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the souls that pop off mobs standing in Leech goo: a standing mob
 * pops one when the draw falls under the rate hex_leech.json ships, a mob
 * outside the goo pops none, and each soul rises from a point on the mob's
 * body, inside its bounding box.
 * soul-particles-pop-from-leech-goo-now-and-then
 */
class LeechSoulsTest {

    private static final double RATE = LeechGooStep.in(AbilityJson.decode("hex_leech").behaviors())
            .orElseThrow().soulPopChance();

    @Nested
    class Pops {

        @Test
        void aMobInTheGooPopsASoulOnADrawUnderTheRate() {
            assertTrue(LeechSouls.pops(true, RATE / 2, RATE));
        }

        @Test
        void theSameMobPopsNoneOnADrawOverTheRate() {
            assertFalse(LeechSouls.pops(true, (1 + RATE) / 2, RATE));
        }

        @Test
        void aMobOutsideTheGooPopsNoneOnADrawUnderTheRate() {
            assertFalse(LeechSouls.pops(false, RATE / 2, RATE));
        }
    }

    @Nested
    class BodyPoint {

        /** A pig's box, short and wide, standing on a cell's top. */
        private static final AABB SHORT_MOB = new AABB(4.05, 71.0, -3.45, 4.95, 71.9, -2.55);
        /** A zombie's box, tall and narrow. */
        private static final AABB TALL_MOB = new AABB(-8.3, 64.0, 12.2, -7.7, 65.95, 12.8);
        private static final double LOWEST_DRAW = 0.0;
        /** The highest draw a random source hands back, just under one. */
        private static final double HIGHEST_DRAW = Math.nextDown(1.0);

        @Test
        void theLowestAndHighestDrawsFallInsideAShortMobsBox() {
            assertInside(SHORT_MOB, LeechSouls.bodyPoint(SHORT_MOB, LOWEST_DRAW, LOWEST_DRAW, LOWEST_DRAW));
            assertInside(SHORT_MOB, LeechSouls.bodyPoint(SHORT_MOB, HIGHEST_DRAW, HIGHEST_DRAW, HIGHEST_DRAW));
        }

        @Test
        void theLowestAndHighestDrawsFallInsideATallMobsBox() {
            assertInside(TALL_MOB, LeechSouls.bodyPoint(TALL_MOB, LOWEST_DRAW, LOWEST_DRAW, LOWEST_DRAW));
            assertInside(TALL_MOB, LeechSouls.bodyPoint(TALL_MOB, HIGHEST_DRAW, HIGHEST_DRAW, HIGHEST_DRAW));
        }

        private static void assertInside(AABB box, Vec3 point) {
            assertTrue(box.contains(point), point + " lies outside " + box);
        }
    }
}
