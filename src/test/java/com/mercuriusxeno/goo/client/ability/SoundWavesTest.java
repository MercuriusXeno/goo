package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Click's trailing echo sounds at most four times on the way, at least four
 * ticks apart, the first as it leaves the glove, as the operator ruled it.
 */
class SoundWavesTest {

    /** The ticks each echo first falls due on, read tick by tick. */
    private static List<Integer> echoTicks(int ticks) {
        List<Integer> echoes = new ArrayList<>();
        int due = 0;
        for (int age = 0; age <= ticks; age++) {
            int now = SoundWaves.echoesDue(age, ticks);
            if (now > due) {
                echoes.add(age);
            }
            due = now;
        }
        return echoes;
    }

    @Test
    void aFullWaveEchoesFourTimesSpreadToTheTarget() {
        assertEquals(List.of(0, 5, 11, 16), echoTicks(16));
    }

    /**
     * The client's game time can jump past a tick when the server re-syncs
     * it, and a wave's first tick may come a tick late: every echo still
     * sounds, each once, when the ages it is read at skip the echo's own tick.
     */
    @Test
    void everyEchoSoundsOnceWhenTheClockSkipsTicks() {
        int sounded = 0;
        int due = 0;
        for (int age = 1; age <= 17; age += 2) {
            int now = SoundWaves.echoesDue(age, 16);
            sounded += now - due;
            due = now;
        }
        assertEquals(SoundWaves.MOST_ECHOES, sounded);
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

    // goo-sound-at-the-listener-reaches-both-ears: the first echo leaves the local player's own glove hand
    @Nested
    class WhereAnEchoPlays {

        private static final double EPSILON = 1e-9;
        private final Vec3 eye = new Vec3(12.3, 65.62, -7.9);
        private final SoundEvent click = SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath("goo", "effects.click"));

        @Test
        void theFirstEchoFromTheLocalHandPlaysAtTheListenerUnattenuated() {
            SimpleSoundInstance echo = SoundWaves.echoOf(click, eye.add(0.3, -0.3, 0.4), 0f, eye);
            assertTrue(echo.isRelative());
            assertEquals(SoundInstance.Attenuation.NONE, echo.getAttenuation());
        }

        @Test
        void anEchoAnotherPlayerCausedPlaysAtItsPoint() {
            Vec3 elsewhere = eye.add(6, -0.3, 1);
            SimpleSoundInstance echo = SoundWaves.echoOf(click, elsewhere, 0f, eye);
            assertFalse(echo.isRelative());
            assertEquals(elsewhere.x, echo.getX(), EPSILON);
            assertEquals(elsewhere.y, echo.getY(), EPSILON);
            assertEquals(elsewhere.z, echo.getZ(), EPSILON);
        }
    }
}
