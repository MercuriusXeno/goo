package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Click's trailing echo sounds at most four times on the way, at least four
 * ticks apart, the first as it leaves the glove, as the operator ruled it.
 */
class SoundWavesTest {

    private static List<Integer> echoTicks(int ticks) {
        List<Integer> echoes = new ArrayList<>();
        for (int age = 0; age <= ticks; age++) {
            if (SoundWaves.echoesAt(age, ticks)) {
                echoes.add(age);
            }
        }
        return echoes;
    }

    @Test
    void aFullWaveEchoesFourTimesSpreadToTheTarget() {
        assertEquals(List.of(0, 5, 11, 16), echoTicks(16));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 8, 11, 16, 30})
    void echoesAtMostFourTimesAtLeastFourTicksApart(int ticks) {
        List<Integer> echoes = echoTicks(ticks);
        assertTrue(echoes.size() <= SoundWaves.MOST_ECHOES, echoes.toString());
        assertEquals(0, echoes.getFirst());
        for (int i = 1; i < echoes.size(); i++) {
            assertTrue(echoes.get(i) - echoes.get(i - 1) >= SoundWaves.LEAST_ECHO_GAP, echoes.toString());
        }
    }
}
