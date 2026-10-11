package com.mercuriusxeno.goo.ability.zone;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teleportitis turns aside every hit but /kill, and a fall out of the world
 * only once there is ground to return to
 * (decision teleportitis-blinks-along-the-cursor-on-hit).
 */
class TeleportitisTest {

    private static final long NOW = 1000L;
    private static final int HOUR = 72000;
    private static final Vec3 FLOOR = new Vec3(0.5, 64, 0.5);
    private static final Teleportitis GROUNDED = new Teleportitis(8f, NOW + HOUR, Optional.of(FLOOR));
    private static final Teleportitis UNGROUNDED = new Teleportitis(8f, NOW + HOUR, Optional.empty());

    private static DamageSource hitOf(ResourceKey<DamageType> type) {
        DamageSource source = mock(DamageSource.class);
        when(source.is(any(ResourceKey.class))).thenAnswer(call -> type.equals(call.getArgument(0)));
        return source;
    }

    @Nested
    class Escapes {

        @Test
        void aGenericHit() {
            assertTrue(TeleportitisEvents.escapes(hitOf(DamageTypes.GENERIC), UNGROUNDED));
        }

        @Test
        void aFallWithGroundToReturnTo() {
            assertTrue(TeleportitisEvents.escapes(hitOf(DamageTypes.FELL_OUT_OF_WORLD), GROUNDED));
        }

        @Test
        void notAFallWithNoGroundToReturnTo() {
            assertFalse(TeleportitisEvents.escapes(hitOf(DamageTypes.FELL_OUT_OF_WORLD), UNGROUNDED));
        }

        @Test
        void notAKill() {
            assertFalse(TeleportitisEvents.escapes(hitOf(DamageTypes.GENERIC_KILL), GROUNDED));
        }
    }

    @Nested
    class Clock {

        @Test
        void aBrewStandsForItsHour() {
            Teleportitis brewed = Teleportitis.NONE.brew(8f, HOUR, NOW);
            assertTrue(brewed.standsAt(NOW + HOUR - 1));
            assertFalse(brewed.standsAt(NOW + HOUR));
        }

        @Test
        void aHeldCastNeverFades() {
            assertEquals(Teleportitis.NEVER_EXPIRES, Teleportitis.NONE.hold(8f).expiresAt());
        }

        @Test
        void standingKeepsTheGround() {
            assertEquals(Optional.of(FLOOR), Teleportitis.NONE.standingOn(FLOOR).hold(8f).safeGround());
        }
    }
}
