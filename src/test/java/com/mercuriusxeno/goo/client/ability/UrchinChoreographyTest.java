package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Urchin's JSON names a shink as its spikes shoot out and the same shink at
 * another pitch on the frame its visual starts retracting
 * (decision urchin-spikes-shink-out-and-shink-back).
 */
class UrchinChoreographyTest {

    @Test
    void urchinShinksOutOnLandingAndBackAsItsSpikesRetract() {
        LingerStep linger = (LingerStep) AbilityJson.decode("metal_spikes").behaviors().getFirst();
        List<SoundStep> shinks = linger.steps().stream().filter(SoundStep.class::isInstance)
                .map(SoundStep.class::cast).toList();

        assertEquals(2, shinks.size());
        SoundStep out = shinks.get(0);
        SoundStep back = shinks.get(1);
        assertEquals(out.sound(), back.sound());
        assertNotEquals(out.pitch().evaluateFloat(Variables.NONE), back.pitch().evaluateFloat(Variables.NONE));
        assertEquals(0, out.frame().evaluateInt(Variables.NONE));
        assertEquals(MetalExplosionVisual.ARM_TICKS + MetalExplosionVisual.HOLD_TICKS,
                back.frame().evaluateInt(Variables.NONE));
    }
}
