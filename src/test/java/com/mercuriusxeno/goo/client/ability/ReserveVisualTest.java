package com.mercuriusxeno.goo.client.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reserve's motes leave the chest and bow on their way to the glove, and the bar's drained and banked halves cue the heartbeat and the pulse (decision reserve-hearts-sit-behind-the-bar). */
class ReserveVisualTest {

    private static final double DELTA = 1e-9;

    @Nested
    class Cues {

        @Test
        void eachHalfTheDrainTakesThumpsAndEachBankedHalfPulses() {
            assertEquals(new ReserveVisual.Cues(1, 1), ReserveVisual.cuesFor(20, 19, 2, 3, true));
        }

        @Test
        void aDropWhileFlinchingCuesNothing() {
            assertEquals(new ReserveVisual.Cues(0, 0), ReserveVisual.cuesFor(20, 16, 2, 2, false));
        }

        @Test
        void healthGainedAndReserveSpentCueNothing() {
            assertEquals(new ReserveVisual.Cues(0, 0), ReserveVisual.cuesFor(16, 18, 4, 2, true));
        }
    }

    @Nested
    class Path {

        @Test
        void aMoteLeavesFromTheChest() {
            assertEquals(new Vec3(1, 2 + 1.8 * ReserveVisual.CHEST_HEIGHT, 3),
                    ReserveVisual.chestOf(new Vec3(1, 2, 3), 1.8));
        }

        @Test
        void aMoteBowsOutFromTheMiddleOfItsLine() {
            Vec3 bow = ReserveVisual.bowPoint(new Vec3(0, 1, 0), new Vec3(2, 1, 0), new Vec3(0, 1, 0));
            assertEquals(1, bow.x, DELTA);
            assertEquals(1 + ReserveVisual.BOW, bow.y, DELTA);
        }
    }
}
