package com.mercuriusxeno.goo.ability.end;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers where the End gate takes the player channelling it, read from the level they stand in.
 */
class EndDestinationTest {

    /** Where a vanilla end portal sets a player down: the bottom centre of (100, 50, 0), a block lower for a player. */
    private static final Vec3 VANILLA_PLAYER_ARRIVAL = new Vec3(100.5, 49, 0.5);
    /** The way a vanilla end portal turns its arriver: west. */
    private static final float FACING_WEST = 90f;

    @Test
    void aPlayerInTheOverworldGoesToTheEndsEntrancePlatform() {
        assertEquals(Optional.of(new EndDestination(Level.END, VANILLA_PLAYER_ARRIVAL, FACING_WEST)),
                EndDestination.from(Level.OVERWORLD));
    }
}
