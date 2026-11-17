package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The white Lunar's whiteout draws at each frame of a trip: full from the flood before the carry, through the
 * frames after the server removed the fixation and before the next client tick, and over the crossing, on the
 * moon trip and the blue star's return alike; a fixation let go of early runs the white back with no crossing.
 * decision diagnose-and-correct-the-whiteout-blink-before-the-carry
 */
class LunarWhiteoutTest {

    private static final double EXACT = 1e-6;
    private static final long CAST_AT = 1000L;
    private static final int FIXATION_TICKS = 100;
    private static final long RAN_OUT_AT = CAST_AT + FIXATION_TICKS;
    private static final float MIDFRAME = 0.5F;

    private static LunarFixation fixationOn(LunarSight.Sky sky) {
        return LunarFixation.held(sky, new Vec3(0, 1, 0), Vec3.ZERO, CAST_AT, FIXATION_TICKS);
    }

    /**
     * Walks a full trip's frames from the flood to the crossing, in the order the client meets them, asserting
     * each draws full white: the fixation standing at full progress at the last client tick, the frame after the
     * server removed it and before the next client tick, and the crossing that tick starts.
     *
     * @param sky what carries the caster
     */
    private static void assertFullWhiteAcrossTheCarry(LunarSight.Sky sky) {
        LunarFixation standing = fixationOn(sky);
        LunarWhiteout.Stage standingStage = LunarWhiteout.stageTheFixationSets(standing, true);
        float flooded = LunarWhiteout.whiteAt(standing, true, standingStage, 0, RAN_OUT_AT, MIDFRAME);
        // The server removes the fixation before the carry; a frame drawn before the next client tick sees it gone
        // with the stage still where the standing fixation left it.
        float removedBeforeTheTick = LunarWhiteout.whiteAt(LunarFixation.NONE, true, standingStage, 0, RAN_OUT_AT,
                MIDFRAME);
        LunarWhiteout.Stage crossingStage = LunarWhiteout.stageTheFixationSets(LunarFixation.NONE, true);
        float crossing = LunarWhiteout.whiteAt(LunarFixation.NONE, false, crossingStage, 1, RAN_OUT_AT + 1, MIDFRAME);
        assertAll(
                () -> assertEquals(1.0, flooded, EXACT, "the flood at full progress"),
                () -> assertEquals(1.0, removedBeforeTheTick, EXACT,
                        "the frame after the fixation's removal and before the next client tick"),
                () -> assertEquals(1.0, crossing, EXACT, "the crossing"));
    }

    @Nested
    class MoonTrip {
        @Test
        void whiteStaysFullFromTheFloodThroughTheCrossing() {
            assertFullWhiteAcrossTheCarry(LunarSight.Sky.MOON);
        }
    }

    @Nested
    class StarReturn {
        @Test
        void whiteStaysFullFromTheFloodThroughTheCrossing() {
            assertFullWhiteAcrossTheCarry(LunarSight.Sky.OVERWORLD_STAR);
        }
    }

    @Nested
    class EarlyRelease {
        private static final long LET_GO_AT = CAST_AT + FIXATION_TICKS * 9 / 10;

        @Test
        void whiteRunsBackDownWithNoCrossing() {
            LunarFixation released = fixationOn(LunarSight.Sky.MOON).releaseAt(LET_GO_AT);
            LunarWhiteout.Stage after = LunarWhiteout.stageTheFixationSets(released, true);
            float white = LunarWhiteout.whiteAt(released, true, after, 0, LET_GO_AT + 1, MIDFRAME);
            float expected = LunarTrip.whiteBeforeCarry(LunarTrip.progress(released, LET_GO_AT + 1, MIDFRAME));
            assertAll(
                    () -> assertEquals(expected, white, EXACT, "the white running back"),
                    () -> assertTrue(white < 1F, "the white running back stands at " + white + ", not below full"),
                    () -> assertEquals(LunarWhiteout.Stage.CLEAR, after, "the stage after the release"));
        }
    }
}
