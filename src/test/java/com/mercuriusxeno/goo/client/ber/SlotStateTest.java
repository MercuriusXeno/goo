package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every canister slot renderer (canister, hub, tap, reactor) builds its slot's fill
 * from every type the canister holds: the vat's type bands, one per type by volume,
 * so a two-type slot draws the mingled surface (decisions
 * canisters-hold-more-than-one-goo-type and noise-mingled-type-textures).
 * StampedGoo stands the goo resources.
 */
class SlotStateTest {

    private static final int CAPACITY = 1000;
    private static final int ROCK_VOLUME = 200;
    private static final int BLAZE_VOLUME = 600;

    private StampedGoo goo;

    @BeforeEach
    void stampGooTypes() {
        goo = new StampedGoo();
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
    }

    @Test
    void aTwoTypeSlotCarriesTheVatsBandsForBothTypes() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.ROCK), ROCK_VOLUME)
                .withVolume(goo.resource(GooTypes.BLAZE), BLAZE_VOLUME);
        SlotState slot = new SlotState();

        slot.showContent(content, CAPACITY);

        assertEquals(TypeBands.over(new GooContents(Map.of(GooTypes.ROCK, ROCK_VOLUME, GooTypes.BLAZE, BLAZE_VOLUME))),
                slot.bands);
        assertTrue(slot.mingles());
        assertEquals(GooTypes.BLAZE, slot.type);
        assertEquals((float) (ROCK_VOLUME + BLAZE_VOLUME) / CAPACITY, slot.fill);
    }

    @Test
    void aOneTypeSlotDrawsOneSurface() {
        SlotState slot = new SlotState();
        slot.showContent(CanisterFluidContent.of(goo.resource(GooTypes.ROCK), ROCK_VOLUME), CAPACITY);
        assertFalse(slot.mingles());
    }
}
