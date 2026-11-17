package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.RimeStep;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a Rime cell's layers pay for: the sources in its reach, nearest first,
 * each at the price Rime's step names, until the layers run short; the
 * shipped Rime prices a source at one layer
 * (decision rime-goo-depletes-on-freezing).
 */
class RimeChillTest {

    private static final BlockPos CELL = new BlockPos(0, 64, 0);
    /** Five sources at rising distances from the cell, listed farthest first so the order is the seam's. */
    private static final List<BlockPos> FIVE_SOURCES = List.of(CELL.offset(3, 0, 0), CELL.offset(0, 0, -3),
            CELL.offset(2, -1, 0), CELL.offset(1, 0, 1), CELL.offset(0, -1, 0));
    private static final BlockPos NEAREST = CELL.offset(0, -1, 0);
    private static final BlockPos SECOND_NEAREST = CELL.offset(1, 0, 1);

    @Test
    void twoLayersAtPriceOneFreezeTheTwoNearestAndLeaveNone() {
        assertEquals(new RimeChill.Paid(List.of(NEAREST, SECOND_NEAREST), 0),
                RimeChill.paidFor(CELL, 2, 1, FIVE_SOURCES));
    }

    @Test
    void threeLayersAtPriceTwoFreezeTheNearestAndLeaveOne() {
        assertEquals(new RimeChill.Paid(List.of(NEAREST), 1), RimeChill.paidFor(CELL, 3, 2, FIVE_SOURCES));
    }

    @Test
    void shippedRimePricesASourceAtOneLayer() {
        assertEquals(1, RimeStep.in(AbilityJson.decode("frost_rime").behaviors()).orElseThrow().layersPerFreeze());
    }
}
