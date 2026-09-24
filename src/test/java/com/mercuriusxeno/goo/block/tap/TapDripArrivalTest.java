package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A drip of a type carrying no tap-tagged ability lands with nothing
 * further: no program loads, so nothing can be refused or logged.
 */
class TapDripArrivalTest {

    private static final BlockPos TAP_POS = new BlockPos(0, 64, 0);
    private static final BlockPos LANDING = new BlockPos(0, 62, 0);

    @Test
    void dripOfATypeWithNoTapAbilityRunsNoProgram() {
        assertNull(AbilityRegistry.tapAbilityFor(GooTypes.ROCK));

        TapDripScheduler.PendingDrip drip = new TapDripScheduler.PendingDrip(
                null, TAP_POS, LANDING, Direction.UP, GooTypes.ROCK, 0);

        assertEquals(0, TapDripScheduler.land(drip));
    }
}
