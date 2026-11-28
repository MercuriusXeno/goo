package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.network.LeechPayload;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the wisps a mob in Leech goo sheds and the drained-life burst every
 * leech by Drain or Leech goo plays, read from its schedule.
 */
class LeechWispsTest {

    /**
     * A living thing sheds a hex wisp while it stands in Leech goo each time
     * the shed interval hex_leech.json ships comes due on its tick count.
     * hex-wisps-drift-off-mobs-standing-in-leech-goo
     */
    @Nested
    class Shed {

        private static final int INTERVAL = LeechGooStep.in(AbilityJson.decode("hex_leech").behaviors())
                .orElseThrow().wispShedTicks();
        /** A tick count the interval comes due on, a few intervals into the thing's life. */
        private static final int DUE_TICK = 3 * INTERVAL;

        @Test
        void aMobInTheGooShedsOnADueTick() {
            assertTrue(LeechWisps.sheds(true, DUE_TICK, INTERVAL));
        }

        @Test
        void theSameMobShedsNoneOnATickBetween() {
            assertFalse(LeechWisps.sheds(true, DUE_TICK + 1, INTERVAL));
        }

        @Test
        void aMobOutsideTheGooShedsNoneOnADueTick() {
            assertFalse(LeechWisps.sheds(false, DUE_TICK, INTERVAL));
        }
    }

    /**
     * Every leech, Drain's or Leech goo's, bursts one fixed set of wisps at
     * once that hovers, sets off home a tick or so apart and lands within
     * Drain's strike interval.
     * drained-mobs-show-stream-burst-and-tether
     * drained-life-arcs-home-speeding-up
     */
    @Nested
    class Burst {

        /** Drain's strike interval at invulnerable_ticks 10, which a burst ends inside. */
        private static final int STRIKE_INTERVAL = 10;
        private static final float[] HEALS = {0.5f, 3f, 40f};
        private static final boolean[] SOURCES = {false, true};

        private List<LeechWisps.BurstWisp> burstOf(float healed, boolean splash) {
            return LeechWisps.burstOf(new LeechPayload(1, 2, healed, splash));
        }

        @Test
        void everyLeechBurstsOneCountAllOnItsArrivalTick() {
            Set<Integer> counts = new HashSet<>();
            for (boolean splash : SOURCES) {
                for (float healed : HEALS) {
                    List<LeechWisps.BurstWisp> burst = burstOf(healed, splash);
                    counts.add(burst.size());
                    for (LeechWisps.BurstWisp wisp : burst) {
                        assertEquals(0, wisp.spawnDelay(), "splash " + splash + " healed " + healed);
                    }
                }
            }
            assertEquals(1, counts.size(), counts::toString);
            assertTrue(counts.iterator().next() > 1, counts::toString);
        }

        @Test
        void everyWispHoversBeforeItSetsOff() {
            for (LeechWisps.BurstWisp wisp : burstOf(3f, false)) {
                assertTrue(wisp.hoverTicks() >= 1, wisp::toString);
            }
        }

        @Test
        void theWispsSetOffOverMoreThanOneTick() {
            Set<Integer> setOffs = new HashSet<>();
            for (LeechWisps.BurstWisp wisp : burstOf(3f, false)) {
                setOffs.add(wisp.scatterTicks() + wisp.hoverTicks());
            }
            assertTrue(setOffs.size() >= 2, setOffs::toString);
        }

        @Test
        void theLastWispLandsWithinAStrike() {
            for (LeechWisps.BurstWisp wisp : burstOf(3f, true)) {
                assertTrue(wisp.spawnDelay() + wisp.scatterTicks() + wisp.hoverTicks() + wisp.flightTicks()
                        <= STRIKE_INTERVAL, wisp::toString);
            }
        }
    }
}
