package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nova's ring bursts out fast: it has spread to its reach within its few
 * spread ticks, far sooner than frost's burnout drifts out, then fades.
 */
class NovaRingsTest {

    private static final float EPSILON = 1e-6f;
    private static final float SPREAD_DONE = (float) NovaRings.SPREAD_TICKS / NovaRings.DURATION_TICKS;

    @Test
    void theRingHasSpreadWithinItsSpreadTicks() {
        assertEquals(1f, NovaRings.spread(SPREAD_DONE), EPSILON);
        assertTrue(NovaRings.SPREAD_TICKS < FrostExplosionVisual.SPREAD_TICKS);
    }

    @Test
    void theFogHoldsWhileTheRingSpreadsThenFades() {
        assertEquals(1f, NovaRings.fog(SPREAD_DONE), EPSILON);
        assertEquals(0f, NovaRings.fog(1f), EPSILON);
    }

    // novas-rush-with-air
    @Test
    void theRushGrowsLouderWithTheRingsReachWithinItsBounds() {
        assertEquals(NovaRings.RUSH_FLOOR, NovaRings.rushVolume(0f), EPSILON);
        assertTrue(NovaRings.rushVolume(6f) > NovaRings.rushVolume(2f));
        assertEquals(NovaRings.RUSH_CEILING, NovaRings.rushVolume(40f), EPSILON);
    }

    @Test
    void theSnowflakesBurstSlowerThanTheyDid() {
        assertEquals(1.6f, NovaRings.SNOWFLAKE_BURST, EPSILON);
    }

    // goo-sound-at-the-listener-reaches-both-ears: a nova bursting about the local player sits at their head
    @Nested
    class WhereTheRushPlays {

        private static final double EPSILON = 1e-9;
        private final Vec3 eye = new Vec3(12.3, 65.62, -7.9);

        @Test
        void aNovaCentredOnTheLocalPlayerRushesAtTheListenerUnattenuated() {
            SimpleSoundInstance rush = NovaRings.rushOf(eye.add(0, -0.72, 0), 4f, eye);
            assertTrue(rush.isRelative());
            assertEquals(SoundInstance.Attenuation.NONE, rush.getAttenuation());
        }

        @Test
        void anotherPlayersNovaRushesAtItsCentre() {
            Vec3 elsewhere = eye.add(5, -0.72, 2);
            SimpleSoundInstance rush = NovaRings.rushOf(elsewhere, 4f, eye);
            assertFalse(rush.isRelative());
            assertEquals(elsewhere.x, rush.getX(), EPSILON);
            assertEquals(elsewhere.y, rush.getY(), EPSILON);
            assertEquals(elsewhere.z, rush.getZ(), EPSILON);
        }
    }
}
