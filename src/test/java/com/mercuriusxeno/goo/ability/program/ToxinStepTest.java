package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.effect.MobEffectInstance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bio's toxin climbs one amplitude per application and stops at two: a
 * fresh mob takes amplifier 0, a toxined one 1, and one at the cap stays there.
 * bio-toxin-stacks-to-amplitude-two
 */
class ToxinStepTest {

    private static final int MAX_AMPLITUDE = 2;

    @ParameterizedTest
    @CsvSource({"-1, 2, 0", "0, 2, 1", "1, 2, 1", "5, 2, 1"})
    void eachApplicationRaisesTheAmplifierOnceUpToTheCap(int standing, int maxAmplitude, int applied) {
        assertEquals(applied, ToxinStep.nextAmplifier(standing, maxAmplitude));
    }

    @Test
    void aSecondStrikeOnAMobHoldingTheToxinAtAmplitudeOneRaisesItToTwo() {
        MobEffectInstance standing = mock(MobEffectInstance.class);
        when(standing.getAmplifier()).thenReturn(0);
        assertEquals(1, ToxinStep.amplifierOver(standing, MAX_AMPLITUDE));
    }

    @Test
    void aFirstStrikeOnAMobTheToxinHasNotReachedLaysAmplitudeOne() {
        assertEquals(0, ToxinStep.amplifierOver(null, MAX_AMPLITUDE));
    }
}
