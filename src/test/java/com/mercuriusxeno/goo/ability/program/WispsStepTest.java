package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispsStep: a cell is dark enough for a wisp only under the threshold, and
 * a holder's flood starts over when none is under way, when the hold begins
 * and when the eyes move on from its origin.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 */
class WispsStepTest {

    private static final BlockPos EYES = new BlockPos(10, 64, -5);
    private static final int THRESHOLD = 8;
    private static final int LATER_TICK = 40;

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
}
