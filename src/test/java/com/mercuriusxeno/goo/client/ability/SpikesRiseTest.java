package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.SpikesStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spikes' rise through a strike, on the retract frame metal_spikes.json
 * ships: flat before it, shot up and held until the retract frame, sinking
 * after it, and flat again once retracted
 * (decision urchin-rests-bristled-and-spears).
 */
class SpikesRiseTest {

    private static final int RETRACT_FRAME = SpikesStep.in(AbilityJson.decode("metal_spikes").behaviors())
            .orElseThrow().retractFrame();
    private static final float HALF_TICK = 0.5f;

    @Test
    void theGooLiesFlatBeforeTheStrike() {
        assertEquals(0f, SpikesRise.extension(0f, RETRACT_FRAME), 0f);
    }

    @Test
    void theSpikesStandFullFromTheShootToTheRetractFrame() {
        assertEquals(1f, SpikesRise.extension(SpikesRise.SHOOT_TICKS, RETRACT_FRAME), 0f);
        assertEquals(1f, SpikesRise.extension(RETRACT_FRAME - HALF_TICK, RETRACT_FRAME), 0f);
    }

    @Test
    void theSpikesSinkPartWayThroughTheRetract() {
        float sinking = SpikesRise.extension(RETRACT_FRAME + SpikesRise.RETRACT_TICKS / 2f, RETRACT_FRAME);
        assertTrue(sinking > 0f && sinking < 1f, "half way through the retract the spikes stand " + sinking);
    }

    @Test
    void theGooLiesFlatOnceTheSpikesRetract() {
        assertEquals(0f, SpikesRise.extension(RETRACT_FRAME + SpikesRise.RETRACT_TICKS, RETRACT_FRAME), 0f);
    }
}
