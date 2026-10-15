package com.mercuriusxeno.goo.client.sound;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // broil-heat-wave-ring-cooks-ahead: a holder's own loop sits at the ears, so tick-stepped feeding never re-pans it
    @Test
    void aLoopAtTheListenerStaysUnpannedWhereverItIsFed() {
        FadingLoopSound loop = FadingLoopSound.atListener(
                SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("goo", "effects.heat_hum")),
                SoundSource.PLAYERS, FULL, 1f);
        loop.feed(new Vec3(12.3, 64.6, -7.9));
        assertTrue(loop.isRelative());
        assertEquals(SoundInstance.Attenuation.NONE, loop.getAttenuation());
        assertEquals(0, loop.getX(), EPSILON);
        assertEquals(0, loop.getY(), EPSILON);
        assertEquals(0, loop.getZ(), EPSILON);
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
