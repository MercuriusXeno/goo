package com.mercuriusxeno.goo.ability.end;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers where the End gate takes the player channelling it, read from the level they stand in and the way back
 * they keep.
 */
class EndDestinationTest {

    /** Where a vanilla end portal sets a player down: the bottom centre of (100, 50, 0), a block lower for a player. */
    private static final Vec3 VANILLA_PLAYER_ARRIVAL = new Vec3(100.5, 49, 0.5);
    /** The way a vanilla end portal turns its arriver: west. */
    private static final float FACING_WEST = 90f;
    /** Where a player stood in the overworld as the gate took them. */
    private static final Vec3 PORTALLED_FROM = new Vec3(-321.5, 72, 1204.25);
    private static final float FACED = -37.5f;

    @Nested
    class ToTheEnd {

        @Test
        void aPlayerInTheOverworldGoesToTheEndsEntrancePlatform() {
            assertEquals(Optional.of(new EndDestination(Level.END, VANILLA_PLAYER_ARRIVAL, FACING_WEST)),
                    EndDestination.from(Level.OVERWORLD, Optional.empty()));
        }
    }

    /** decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in */
    @Nested
    class BackFromTheEnd {

        @Test
        void aPlayerCarriedFromTheOverworldKeepsWhereTheyPortalledInFrom() {
            EndDestination wayBack = EndDestination.wayBackFrom(Level.OVERWORLD, PORTALLED_FROM, FACED);
            assertAll(() -> assertEquals(Level.OVERWORLD, wayBack.dimension()),
                    () -> assertEquals(PORTALLED_FROM, wayBack.feet()));
        }

        @Test
        void aPlayerInTheEndHoldingAWayBackGoesWhereTheyPortalledInFrom() {
            EndDestination wayBack = EndDestination.wayBackFrom(Level.OVERWORLD, PORTALLED_FROM, FACED);
            assertEquals(Optional.of(wayBack), EndDestination.from(Level.END, Optional.of(wayBack)));
        }
    }
}
