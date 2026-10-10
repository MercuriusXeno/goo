package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispsStep: a cell is dark enough for a wisp only under the threshold, and
 * a holder's flood starts over when none is under way, when the hold begins
 * and when the eyes move on from its origin; a flood started over as the
 * holder walks keeps its reach and the wisps already placed, so it goes on
 * lighting fresh dark air while the holder moves.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 */
class WispsStepTest {

    private static final BlockPos EYES = new BlockPos(10, 64, -5);
    private static final int THRESHOLD = 8;
    private static final int LATER_TICK = 40;
    private static final WispsStep RADIANT = new WispsStep(64, 4000, 1200, 0, true, true, 0.8);
    private static final int TICK_BUDGET = 4000;
    private static final int TICKS_BEFORE_THE_MOVE = 10;
    private static final int STEPS_WALKED = WispsStep.RESTART_STEPS + 1;

    @Test
    void aCellIsDarkOnlyUnderTheThreshold() {
        assertTrue(WispsStep.isDark(THRESHOLD - 1, THRESHOLD));
        assertFalse(WispsStep.isDark(THRESHOLD, THRESHOLD));
    }

    @Test
    void aFloodStartsWhenNoneIsUnderWay() {
        assertTrue(WispsStep.startsOver(null, EYES, LATER_TICK));
    }

    @Test
    void aFloodStartsOverWhenTheHoldBegins() {
        assertTrue(WispsStep.startsOver(new WispFlood(EYES, 64, 0), EYES, ChannelAim.FIRST_TICK));
    }

    @Test
    void aFloodGoesOnWhileTheEyesStayNear() {
        WispFlood under = new WispFlood(EYES, 64, 0);
        assertFalse(WispsStep.startsOver(under, EYES, LATER_TICK));
        assertFalse(WispsStep.startsOver(under, EYES.east(WispsStep.RESTART_STEPS), LATER_TICK));
    }

    @Test
    void aFloodStartsOverWhenTheEyesMoveOn() {
        WispFlood under = new WispFlood(EYES, 64, 0);
        assertTrue(WispsStep.startsOver(under, EYES.east(WispsStep.RESTART_STEPS + 1), LATER_TICK));
    }

    @Test
    void aFloodStartedOverAsTheHolderWalksPlacesWispsOnItsFirstTick() {
        OpenDark world = new OpenDark();
        WispFlood under = RADIANT.floodFor(null, EYES, ChannelAim.FIRST_TICK, WispFlood.LIT_STEPS);
        for (int tick = 0; tick < TICKS_BEFORE_THE_MOVE; tick++) {
            under.walk(TICK_BUDGET, world);
        }
        int before = world.placed.size();
        BlockPos walkedTo = EYES.east(STEPS_WALKED);
        WispFlood moved = RADIANT.floodFor(under, walkedTo, LATER_TICK, WispFlood.LIT_STEPS);
        assertEquals(under.edge(), moved.edge(), 1e-9, "The flood should keep its reach as the holder walks");
        assertTrue(moved.walk(TICK_BUDGET, world) > 0, "The first tick after the move should place wisps");
        for (BlockPos fresh : world.placed.subList(before, world.placed.size())) {
            for (BlockPos earlier : world.placed.subList(0, before)) {
                assertTrue(fresh.distManhattan(earlier) > WispFlood.LIT_STEPS,
                        fresh + " sits in the light of " + earlier + ", placed before the move");
            }
        }
    }

    @Test
    void aFloodStartsFromTheEyesWhenTheHoldBegins() {
        WispFlood under = RADIANT.floodFor(null, EYES, ChannelAim.FIRST_TICK, WispFlood.LIT_STEPS);
        under.walk(TICK_BUDGET, new OpenDark());
        WispFlood fresh = RADIANT.floodFor(under, EYES, ChannelAim.FIRST_TICK, WispFlood.LIT_STEPS);
        assertEquals(0, fresh.edge(), 1e-9);
        assertTrue(fresh.placed().isEmpty());
    }

    /** Open dark air everywhere, all of it seen; a cell holding a wisp reads lit, its neighbors still dark. */
    private static final class OpenDark implements WispFlood.Cells {
        private final List<BlockPos> placed = new ArrayList<>();

        @Override
        public boolean open(BlockPos cell) {
            return true;
        }

        @Override
        public boolean dark(BlockPos cell) {
            return !placed.contains(cell);
        }

        @Override
        public boolean seen(BlockPos cell) {
            return true;
        }

        @Override
        public void place(BlockPos cell) {
            placed.add(cell.immutable());
        }
    }
}
