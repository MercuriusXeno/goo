package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bio's toxin climbs one amplitude per application and stops at two: a
 * fresh mob takes amplifier 0, a toxined one 1, and one at the cap stays there.
 * bio-toxin-stacks-to-amplitude-two
 */
class ToxinStepTest {

    @ParameterizedTest
    @CsvSource({"-1, 2, 0", "0, 2, 1", "1, 2, 1", "5, 2, 1"})
    void eachApplicationRaisesTheAmplifierOnceUpToTheCap(int standing, int maxAmplitude, int applied) {
        assertEquals(applied, ToxinStep.nextAmplifier(standing, maxAmplitude));
    }
}
