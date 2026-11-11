package com.mercuriusxeno.goo.ability.zone;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Elude lands a player only on a safe column: a sturdy dry floor, two air blocks to stand in, inside the world.
 * decision elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage
 */
class StandingColumnTest {

    private static final BlockState STONE = block(false, false);
    private static final BlockState AIR = block(true, false);
    private static final BlockState LAVA = block(false, true);
    private static final BlockState WATER = block(false, true);

    private static BlockState block(boolean air, boolean fluid) {
        BlockState state = mock(BlockState.class);
        FluidState fluidState = mock(FluidState.class);
        when(fluidState.isEmpty()).thenReturn(!fluid);
        when(state.isAir()).thenReturn(air);
        when(state.getFluidState()).thenReturn(fluidState);
        return state;
    }

    @Test
    void aSturdyFloorUnderTwoAirIsSafe() {
        assertTrue(new StandingColumn(STONE, true, AIR, AIR, true).safe());
    }

    @Test
    void lavaBelowIsUnsafe() {
        assertFalse(new StandingColumn(LAVA, true, AIR, AIR, true).safe());
    }

    @Test
    void aFloorThatHoldsNothingIsUnsafe() {
        assertFalse(new StandingColumn(STONE, false, AIR, AIR, true).safe());
    }

    @Test
    void aOneBlockGapIsUnsafe() {
        assertFalse(new StandingColumn(STONE, true, AIR, STONE, true).safe());
    }

    @Test
    void waterAtTheFeetIsUnsafe() {
        assertFalse(new StandingColumn(STONE, true, WATER, AIR, true).safe());
    }

    @Test
    void outsideTheWorldIsUnsafe() {
        assertFalse(new StandingColumn(STONE, true, AIR, AIR, false).safe());
    }
}
