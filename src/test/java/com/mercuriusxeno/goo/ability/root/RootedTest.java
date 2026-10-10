package com.mercuriusxeno.goo.ability.root;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vines on a rooted mob: a throw latching or stacking onto standing
 * vines, hits spending them down to their release, and the share they cover
 * as they unpack and fall away (decision vines-unpack-root-and-thorn).
 */
class RootedTest {

    private static final long NOW = 1_000L;
    private static final Rooted.Throw THROW = new Rooted.Throw(4, 1f, 1.5f, 60);
    private static final Vec3 ROOT = new Vec3(3, 64, 3);
    private static final Vec3 ELSEWHERE = new Vec3(9, 64, 9);
    private static final float EPSILON = 1e-6f;

    private static Rooted latched() {
        return Rooted.NONE.latch(THROW, ROOT, NOW);
    }

    @Nested
    class Latch {

        @Test
        void aThrowOnAFreeMobRootsItWhereItStandsForTheThrowsHold() {
            Rooted rooted = latched();
            assertEquals(ROOT, rooted.anchor());
            assertTrue(rooted.holds(NOW + THROW.duration() - 1));
            assertFalse(rooted.holds(NOW + THROW.duration()));
        }

        @Test
        void aSecondThrowStacksHitsAndThornsKeepsTheRootAndHoldsAfresh() {
            long later = NOW + 30;
            Rooted stacked = latched().latch(THROW, ELSEWHERE, later);
            assertEquals(ROOT, stacked.anchor());
            assertEquals(2 * THROW.hits(), stacked.hitsLeft());
            assertEquals(2 * THROW.thorns(), stacked.thorns(), EPSILON);
            assertEquals(NOW, stacked.rootedAt());
            assertEquals(later + THROW.duration(), stacked.endsAt());
        }

        @Test
        void aThrowAfterTheVinesReleasedLatchesNewVinesWhereTheMobStands() {
            long later = NOW + 100;
            Rooted fresh = latched().releasedAt(NOW + 10).latch(THROW, ELSEWHERE, later);
            assertEquals(ELSEWHERE, fresh.anchor());
            assertEquals(THROW.hits(), fresh.hitsLeft());
            assertEquals(later, fresh.rootedAt());
        }
    }

    @Nested
    class Struck {

        @Test
        void hitsShortOfTheLastLeaveTheVinesHolding() {
            Rooted rooted = latched();
            for (int hit = 1; hit < THROW.hits(); hit++) {
                rooted = rooted.struck(NOW);
            }
            assertTrue(rooted.holds(NOW));
        }

        @Test
        void theLastHitReleasesTheMobAndStartsTheFade() {
            Rooted rooted = latched();
            for (int hit = 0; hit < THROW.hits(); hit++) {
                rooted = rooted.struck(NOW + 5);
            }
            assertFalse(rooted.holds(NOW + 5));
            assertFalse(rooted.fadedBy(NOW + 5 + Rooted.FADE_TICKS - 1));
            assertTrue(rooted.fadedBy(NOW + 5 + Rooted.FADE_TICKS));
        }
    }

    @Nested
    class Cover {

        @Test
        void theVinesSpreadOverTheMobAcrossTheUnpack() {
            Rooted rooted = latched();
            assertEquals(0f, rooted.coverAt(NOW), EPSILON);
            assertEquals(0.5f, rooted.coverAt(NOW + Rooted.UNPACK_TICKS / 2f), EPSILON);
            assertEquals(1f, rooted.coverAt(NOW + Rooted.UNPACK_TICKS), EPSILON);
        }

        @Test
        void releasedVinesFallAwayAcrossTheFade() {
            long released = NOW + 20;
            Rooted rooted = latched().releasedAt(released);
            assertEquals(1f, rooted.coverAt(released), EPSILON);
            assertEquals(0.5f, rooted.coverAt(released + Rooted.FADE_TICKS / 2f), EPSILON);
            assertEquals(0f, rooted.coverAt(released + Rooted.FADE_TICKS), EPSILON);
        }
    }
}
