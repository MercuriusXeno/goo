package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Covers the door a channel toward the pocket conjures: it grows before the
 * caster with the charge, moves into them over its last ticks, and its state
 * reaches the caster and every player tracking them
 * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player).
 */
class ConjuredDoorsTest {

    private static final int CHARGE = 20;
    private static final int CASTER_ID = 41;
    private static final Vec3 FEET = new Vec3(10.5, 64, -3.5);
    /** Facing south, the way yaw 0 looks. */
    private static final float FACING_SOUTH = 0f;
    private static final double EXACT = 1.0E-9;

    @Nested
    class TheDoor {

        @Test
        void itGrowsWithTheChargeBeforeTheCasterTheWayTheyFace() {
            ConjuredDoor first = ConjuredDoor.standing(FEET, FACING_SOUTH, 1, CHARGE);
            ConjuredDoor half = ConjuredDoor.standing(FEET, FACING_SOUTH, CHARGE / 2, CHARGE);
            ConjuredDoor full = ConjuredDoor.standing(FEET, FACING_SOUTH, CHARGE, CHARGE);

            assertTrue(first.size() > 0 && first.size() < half.size() && half.size() < full.size(),
                    "sizes " + first.size() + ", " + half.size() + ", " + full.size() + " should rise");
            assertEquals(0, FEET.add(0, 0, ConjuredDoor.STANDS_OFF).distanceTo(half.at()), EXACT,
                    "the door should stand before the caster's facing");
            assertEquals(ConjuredDoor.Phase.GROWING, half.phase());
        }

        @Test
        void itReachesTheCastersFeetEnvelopingThemAsTheChargeCompletes() {
            ConjuredDoor moving = ConjuredDoor.standing(FEET, FACING_SOUTH, CHARGE - 1, CHARGE);
            ConjuredDoor done = ConjuredDoor.standing(FEET, FACING_SOUTH, CHARGE, CHARGE);

            assertEquals(ConjuredDoor.Phase.ENVELOPING, moving.phase());
            assertTrue(moving.at().distanceTo(FEET) < ConjuredDoor.STANDS_OFF, "the door should be moving in");
            assertEquals(ConjuredDoor.Phase.ENVELOPING, done.phase());
            assertEquals(FEET, done.at(), "the door should stand on the caster as the charge completes");
        }
    }

    @Test
    void theDoorsStateReachesTheCasterAndEveryPlayerTrackingThem() {
        ServerPlayer caster = mock(ServerPlayer.class);
        when(caster.getId()).thenReturn(CASTER_ID);
        ConjuredDoor door = ConjuredDoor.standing(FEET, FACING_SOUTH, CHARGE / 2, CHARGE);
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            ConjuredDoors.sendDoor(caster, door);

            visuals.verify(() -> EntityVisuals.sendToWatchers(caster, new ConjuredDoorPayload(CASTER_ID, door)));
        }
    }
}
