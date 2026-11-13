package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.network.ConjuredDoorPayload;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
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
 * Covers the door a channel toward the pocket conjures: it unzips before the
 * caster with the charge, widens as it moves into them over its last ticks,
 * and its state reaches the caster and every player tracking them
 * (decisions channeling-to-the-pocket-conjures-a-door-that-envelops-the-player,
 * door-unzips-from-a-starfield-pixel-then-widens-into-you).
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
        void itUnzipsWithTheChargeBeforeTheCasterTheWayTheyFace() {
            ConjuredDoor first = ConjuredDoor.standing(FEET, FACING_SOUTH, 1, CHARGE);
            ConjuredDoor half = ConjuredDoor.standing(FEET, FACING_SOUTH, CHARGE / 2, CHARGE);

            assertTrue(first.unzipped() > 0 && first.unzipped() < half.unzipped(),
                    "unzipped " + first.unzipped() + ", " + half.unzipped() + " should rise");
            assertEquals(0, FEET.add(0, 0, ConjuredDoor.STANDS_OFF).distanceTo(half.at()), EXACT,
                    "the door should stand before the caster's facing");
            assertEquals(ConjuredDoor.Phase.GROWING, half.phase());
        }

        @Test
        void itUnzipsWhereItStandsThenWidensOnlyWhileItMovesIn() {
            int growing = CHARGE - ConjuredDoor.ENVELOP_TICKS;
            for (int held = 0; held <= CHARGE; held++) {
                ConjuredDoor door = ConjuredDoor.standing(FEET, FACING_SOUTH, held, CHARGE);
                if (held <= growing) {
                    assertEquals(0, door.crossed(), EXACT, "standing off at held " + held);
                    assertEquals(0, door.widened(), EXACT, "a slash at held " + held);
                } else {
                    assertEquals(1, door.unzipped(), EXACT, "unzipped at held " + held);
                    assertEquals(door.crossed(), door.widened(), EXACT, "widening as it crosses at held " + held);
                }
            }
            assertEquals(1, ConjuredDoor.standing(FEET, FACING_SOUTH, growing, CHARGE).unzipped(), EXACT,
                    "unzipped to the floor as it starts to move");
            ConjuredDoor done = ConjuredDoor.standing(FEET, FACING_SOUTH, CHARGE, CHARGE);
            assertEquals(FEET, done.at(), "on the caster's feet as the charge completes");
            assertEquals(1, done.widened(), EXACT, "full width on the caster's feet");
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

    @Nested
    class TheReleasingDoor {

        @Test
        void itStartsFullSizeOnTheArriversFeet() {
            ConjuredDoor first = ConjuredDoor.releasing(FEET, FACING_SOUTH, 0, ConjuredDoor.RELEASE_TICKS);

            assertEquals(ConjuredDoor.Phase.RELEASING, first.phase());
            assertEquals(FEET, first.at(), "the release should start on the arriver's feet");
            assertEquals(1, first.widened(), EXACT, "the release should start at full width");
        }

        @Test
        void itMovesOffTheWayTheArriverFacesAndNarrows() {
            ConjuredDoor first = ConjuredDoor.releasing(FEET, FACING_SOUTH, 0, ConjuredDoor.RELEASE_TICKS);
            ConjuredDoor later = ConjuredDoor.releasing(FEET, FACING_SOUTH, ConjuredDoor.RELEASE_TICKS / 2,
                    ConjuredDoor.RELEASE_TICKS);

            assertTrue(later.at().z() > first.at().z() && later.at().x() == FEET.x(),
                    "the door should move south, the way the arriver faces, stands at " + later.at());
            assertTrue(later.widened() < first.widened(), "the door should narrow, widened " + later.widened());
        }
    }

    @Test
    void bothSharesTravelWithTheDoor() {
        ConjuredDoor unzipping = new ConjuredDoor(FEET, FACING_SOUTH, 0.4, 0, 0, ConjuredDoor.Phase.GROWING);
        ConjuredDoor widening = new ConjuredDoor(FEET, FACING_SOUTH, 1, 0.4, 0.4, ConjuredDoor.Phase.ENVELOPING);

        ConjuredDoor unzippingSent = sent(unzipping);
        ConjuredDoor wideningSent = sent(widening);

        assertEquals(0.4, unzippingSent.unzipped(), EXACT, "unzipped sent");
        assertEquals(0, unzippingSent.widened(), EXACT, "widened sent");
        assertEquals(1, wideningSent.unzipped(), EXACT, "unzipped sent");
        assertEquals(0.4, wideningSent.widened(), EXACT, "widened sent");
    }

    private static ConjuredDoor sent(ConjuredDoor door) {
        ByteBuf buffer = Unpooled.buffer();
        ConjuredDoor.STREAM_CODEC.encode(buffer, door);
        return ConjuredDoor.STREAM_CODEC.decode(buffer);
    }

    @Test
    void theReleasingDoorReachesTheArriverAndEveryPlayerTrackingThem() {
        ServerPlayer arriver = mock(ServerPlayer.class);
        when(arriver.getId()).thenReturn(CASTER_ID);
        when(arriver.getUUID()).thenReturn(java.util.UUID.randomUUID());
        when(arriver.position()).thenReturn(FEET);
        when(arriver.getYRot()).thenReturn(FACING_SOUTH);
        ConjuredDoor first = ConjuredDoor.releasing(FEET, FACING_SOUTH, 0, ConjuredDoor.RELEASE_TICKS);
        try (MockedStatic<EntityVisuals> visuals = mockStatic(EntityVisuals.class)) {
            ConjuredDoors.releaseAt(arriver, 0);

            visuals.verify(() -> EntityVisuals.sendToWatchers(arriver, new ConjuredDoorPayload(CASTER_ID, first)));
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
