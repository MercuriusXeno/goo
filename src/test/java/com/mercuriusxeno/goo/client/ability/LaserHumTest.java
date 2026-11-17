package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LaserHum's pitch: it starts pitched up as the hold begins, glides down
 * fast then slow, and settles at the low thrum it holds after.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
class LaserHumTest {

    private static final float TOLERANCE = 1e-6f;

    @Test
    void startsPitchedUp() {
        assertEquals(LaserHum.START_PITCH, LaserHum.pitchAt(0), TOLERANCE);
    }

    @Test
    void settlesAtTheThrumAndHolds() {
        assertEquals(LaserHum.THRUM_PITCH, LaserHum.pitchAt(LaserHum.GLIDE_TICKS), TOLERANCE);
        assertEquals(LaserHum.THRUM_PITCH, LaserHum.pitchAt(LaserHum.GLIDE_TICKS * 10), TOLERANCE);
    }

    @Test
    void glidesDownFastThenSlow() {
        float quarter = LaserHum.pitchAt(LaserHum.GLIDE_TICKS / 4);
        float half = LaserHum.pitchAt(LaserHum.GLIDE_TICKS / 2);
        assertTrue(LaserHum.START_PITCH - quarter > quarter - half, "the glide does not ease out");
    }

    // goo-sound-at-the-listener-reaches-both-ears: a caster's own hum sits at their head
    @Nested
    class WhereTheHumPlays {

        private static final double EPSILON = 1e-9;

        private Entity casterAt(double x, double eyeY, double z) {
            Entity caster = mock(Entity.class);
            when(caster.getX()).thenReturn(x);
            when(caster.getEyeY()).thenReturn(eyeY);
            when(caster.getZ()).thenReturn(z);
            return caster;
        }

        @Test
        void theLocalCastersHumPlaysAtTheListenerUnattenuated() {
            LaserHum hum = new LaserHum(casterAt(12.3, 65.62, -7.9), true);
            assertTrue(hum.isRelative());
            assertEquals(SoundInstance.Attenuation.NONE, hum.getAttenuation());
            assertEquals(0, hum.getX(), EPSILON);
            assertEquals(0, hum.getY(), EPSILON);
            assertEquals(0, hum.getZ(), EPSILON);
        }

        @Test
        void anotherPlayersHumPlaysAtTheirEye() {
            LaserHum hum = new LaserHum(casterAt(12.3, 65.62, -7.9), false);
            assertFalse(hum.isRelative());
            assertEquals(12.3, hum.getX(), EPSILON);
            assertEquals(65.62, hum.getY(), EPSILON);
            assertEquals(-7.9, hum.getZ(), EPSILON);
        }
    }
}
