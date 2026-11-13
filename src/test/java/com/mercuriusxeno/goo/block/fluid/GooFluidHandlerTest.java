package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.block.crucible.CrucibleCapacity;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A crucible or vat reservoir holds a remainder ledger saved and loaded beside its
 * volumes (decision every-producer-consumer-and-container-holds-a-remainder-buffer).
 * StampedGoo stands the vanilla bootstrap the handler's tanks read.
 */
class GooFluidHandlerTest {

    private static final int CAPACITY = 1000;

    private StampedGoo goo;

    @BeforeEach
    void bootstrap() {
        goo = new StampedGoo();
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
    }

    @Test
    void aCreditedRemainderSurvivesASaveAndLoad() {
        GooFluidHandler saved = GooFluidHandler.withCapacityPerType(CAPACITY, () -> { });
        saved.ledger().creditExact(GooTypes.LEAF, GooFraction.of(1, 3));

        GooContents persisted = GooContents.CODEC.parse(JsonOps.INSTANCE,
                GooContents.CODEC.encodeStart(JsonOps.INSTANCE, saved.toGooContents()).getOrThrow()).getOrThrow();
        GooFluidHandler loaded = GooFluidHandler.withCapacityPerType(CAPACITY, () -> { });
        loaded.loadFrom(persisted);

        assertEquals(GooFraction.of(1, 3), loaded.ledger().remainders().of(GooTypes.LEAF));
    }

    /**
     * A reservoir built per type, the crucible's, holds the whole capacity in each of its tanks, so one type
     * filling takes no room from another (decision crucible-refuses-past-two-billion).
     */
    @Test
    void aReservoirBuiltPerTypeHoldsTheWholeCapacityInEachTank() {
        GooFluidHandler reservoir = GooFluidHandler.withCapacityPerType(CrucibleCapacity.TYPE_CAPACITY, () -> { });

        assertEquals(List.of(CrucibleCapacity.TYPE_CAPACITY, CrucibleCapacity.TYPE_CAPACITY),
                List.of(reservoir.getCapacity(0, null), reservoir.getCapacity(1, null)));
    }
}