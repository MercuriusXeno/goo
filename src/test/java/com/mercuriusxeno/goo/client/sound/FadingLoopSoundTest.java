package com.mercuriusxeno.goo.client.sound;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fading loop swells to its full volume while fed and fades to silence
 * once not, never jumping (decision decay-gnats-degrade-each-block-once).
 */
class FadingLoopSoundTest {

    private static final float FULL = 0.12f;
    private static final float EPSILON = 1e-6f;

    @Test
    void aFedLoopSwellsToFullOverTheFadeInAndStaysThere() {
        float volume = 0f;
        for (int tick = 0; tick < FadingLoopSound.FADE_IN_TICKS; tick++) {
            volume = FadingLoopSound.nextVolume(volume, FULL, true);
        }
        assertEquals(FULL, volume, EPSILON);
        assertEquals(FULL, FadingLoopSound.nextVolume(volume, FULL, true), EPSILON);
    }

    @Test
    void anUnfedLoopFadesFromFullToSilenceOverTheFadeOut() {
        float volume = FULL;
        for (int tick = 1; tick < FadingLoopSound.FADE_OUT_TICKS; tick++) {
            volume = FadingLoopSound.nextVolume(volume, FULL, false);
        }
        assertEquals(FULL / FadingLoopSound.FADE_OUT_TICKS, volume, EPSILON);
        volume = FadingLoopSound.nextVolume(volume, FULL, false);
        assertEquals(0f, volume, EPSILON);
        assertEquals(0f, FadingLoopSound.nextVolume(volume, FULL, false), EPSILON);
    }
}
