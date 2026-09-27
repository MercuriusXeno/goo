package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Chamber;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Step;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The crystallizer's arithmetic (decision crystallizer-emits-chrysm): goo
 * crystallizes as it arrives at 10% crystal, pauses without crystal, stops at
 * the knob's tier, and the item inside is the highest tier reached.
 */
class CrystallizerPhasesTest {

    private static final int CHRYSM = 1_000;
    private static final int KILO = 1_000_000;

    private static GooContents ender(int ender, int crystal) {
        return GooContents.EMPTY.withAdded(GooTypes.ENDER, ender).withAdded(GooTypes.CRYSTAL, crystal);
    }

    private static Chamber chamber(GooContents held, long crystallized, ChrysmTier knob) {
        return new Chamber(held, crystallized, crystallized > 0 ? GooTypes.ENDER : null, knob);
    }

    @Nested
    class Step10Percent {

        @Test
        void goodCrystalCrystallizesAllTheGooAtOneTenth() {
            assertEquals(new Step(GooTypes.ENDER, CHRYSM, 100),
                    CrystallizerPhases.step(chamber(ender(CHRYSM, 100), 0, ChrysmTier.CHRYSM)));
        }

        @Test
        void shortCrystalCrystallizesWhatItPaysFor() {
            assertEquals(new Step(GooTypes.ENDER, 500, 50),
                    CrystallizerPhases.step(chamber(ender(CHRYSM, 50), 0, ChrysmTier.CHRYSM)));
        }

        @Test
        void noCrystalPauses() {
            assertNull(CrystallizerPhases.step(chamber(ender(CHRYSM, 0), 0, ChrysmTier.CHRYSM)));
        }

        @Test
        void gooUnderOneStepWaits() {
            assertNull(CrystallizerPhases.step(chamber(ender(9, 100), 0, ChrysmTier.CHRYSM)));
        }

        @Test
        void crystallizingStopsAtTheKnobTier() {
            assertEquals(new Step(GooTypes.ENDER, 400, 40),
                    CrystallizerPhases.step(chamber(ender(CHRYSM, 100), 600, ChrysmTier.CHRYSM)));
            assertNull(CrystallizerPhases.step(chamber(ender(CHRYSM, 100), CHRYSM, ChrysmTier.CHRYSM)));
        }

        @Test
        void crystalCrystallizesItselfAtElevenPerTen() {
            GooContents crystal = GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, 1_100);
            assertEquals(new Step(GooTypes.CRYSTAL, CHRYSM, 100),
                    CrystallizerPhases.step(new Chamber(crystal, 0, null, ChrysmTier.CHRYSM)));
        }
    }

    @Nested
    class Capacity {

        @Test
        void theTypeTakesTheRoomToTheKnobTier() {
            assertEquals(CHRYSM, CrystallizerPhases.capacityFor(chamber(ender(1, 0), 0, ChrysmTier.CHRYSM), GooTypes.ENDER));
            assertEquals(KILO - 500, CrystallizerPhases.capacityFor(
                    chamber(GooContents.EMPTY, 500, ChrysmTier.KILOCHRYSM), GooTypes.ENDER));
        }

        @Test
        void crystalTakesATenthOfTheRoom() {
            assertEquals(100, CrystallizerPhases.capacityFor(chamber(ender(1, 0), 0, ChrysmTier.CHRYSM), GooTypes.CRYSTAL));
        }

        @Test
        void aKnobTierReachedTakesNothing() {
            Chamber full = chamber(GooContents.EMPTY, CHRYSM, ChrysmTier.CHRYSM);
            assertEquals(0, CrystallizerPhases.capacityFor(full, GooTypes.ENDER));
            assertEquals(0, CrystallizerPhases.capacityFor(full, GooTypes.CRYSTAL));
        }

        @Test
        void aThirdTypeIsRefused() {
            assertEquals(0, CrystallizerPhases.capacityFor(chamber(ender(1, 0), 0, ChrysmTier.CHRYSM), GooTypes.ROCK));
            assertEquals(0, CrystallizerPhases.capacityFor(
                    chamber(GooContents.EMPTY, 500, ChrysmTier.CHRYSM), GooTypes.ROCK));
        }

        @Test
        void anEmptyChamberTakesAnyTypeAndCrystalForItself() {
            Chamber empty = new Chamber(GooContents.EMPTY, 0, null, ChrysmTier.CHRYSM);
            assertEquals(CHRYSM, CrystallizerPhases.capacityFor(empty, GooTypes.ROCK));
            assertEquals(1_100, CrystallizerPhases.capacityFor(empty, GooTypes.CRYSTAL));
        }
    }

    @Test
    void theItemInsideIsTheHighestTierReached() {
        assertNull(CrystallizerPhases.reachedTier(CHRYSM - 1));
        assertEquals(ChrysmTier.CHRYSM, CrystallizerPhases.reachedTier(KILO - 1));
        assertEquals(ChrysmTier.KILOCHRYSM, CrystallizerPhases.reachedTier(KILO));
        assertEquals(ChrysmTier.MEGACHRYSM, CrystallizerPhases.reachedTier(1_000_000_000L));
    }

    @Test
    void theKnobWrapsFromThreeToOne() {
        assertEquals(2, CrystallizerPhases.nextKnob(1));
        assertEquals(3, CrystallizerPhases.nextKnob(2));
        assertEquals(1, CrystallizerPhases.nextKnob(3));
    }
}
