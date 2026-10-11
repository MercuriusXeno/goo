package com.mercuriusxeno.goo.client.model;

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

/**
 * The canister item renderer builds its fill from every type the canister holds:
 * the vat's type bands, one per type by volume, so a two-type canister draws the
 * mingled fill (decisions canisters-hold-more-than-one-goo-type and
 * noise-mingled-type-textures). StampedGoo stands the goo resources.
 */
class CanisterSpecialRendererTest {

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
    void aTwoTypeCanisterCarriesTheVatsBandsForBothTypes() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.ROCK), ROCK_VOLUME)
                .withVolume(goo.resource(GooTypes.BLAZE), BLAZE_VOLUME);

        CanisterSpecialRenderer.GooData data = CanisterSpecialRenderer.dataOf(content, CAPACITY, false, false);

        assertEquals(TypeBands.over(new GooContents(Map.of(GooTypes.ROCK, ROCK_VOLUME, GooTypes.BLAZE, BLAZE_VOLUME))),
                data.bands());
        assertEquals(GooTypes.BLAZE, data.gooType());
        assertEquals((float) (ROCK_VOLUME + BLAZE_VOLUME) / CAPACITY, data.fill());
    }
}
