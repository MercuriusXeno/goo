package com.mercuriusxeno.goo.ability.frost;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a block becomes given whether a Rime cell stands in reach of it:
 * water and lava freeze in reach, Rime ice and obsidian thaw out of it, and
 * nothing else changes
 * (decision rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless).
 */
class RimeFreezeTest {

    @Nested
    class InReach {

        @Test
        void waterFreezesToRimeIce() {
            assertEquals(Optional.of(RimeFreeze.ICE), RimeFreeze.WATER.settled(true));
        }

        @Test
        void lavaFreezesToObsidian() {
            assertEquals(Optional.of(RimeFreeze.OBSIDIAN), RimeFreeze.LAVA.settled(true));
        }

        @Test
        void rimeIceAndObsidianStayFrozen() {
            assertEquals(Optional.empty(), RimeFreeze.ICE.settled(true));
            assertEquals(Optional.empty(), RimeFreeze.OBSIDIAN.settled(true));
        }
    }

    @Nested
    class OutOfReach {

        @Test
        void rimeIceThawsToWater() {
            assertEquals(Optional.of(RimeFreeze.WATER), RimeFreeze.ICE.settled(false));
        }

        @Test
        void obsidianThawsToLava() {
            assertEquals(Optional.of(RimeFreeze.LAVA), RimeFreeze.OBSIDIAN.settled(false));
        }

        @Test
        void waterAndLavaStayWarm() {
            assertEquals(Optional.empty(), RimeFreeze.WATER.settled(false));
            assertEquals(Optional.empty(), RimeFreeze.LAVA.settled(false));
        }
    }
}
