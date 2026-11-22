package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The vat item submits its goo before its glass shell, since the GUI's item atlas sorts no quad under improved
 * transparency and the glass, drawn first, would hide the goo behind the depth it writes (defect row 7).
 * decision goo-translucency-draws-through-order-independent-transparency
 */
class VatSpecialRendererTest {

    private static final float HALF_FULL = 0.5f;
    private static final int ROCK_VOLUME = 200;
    private static final int BLAZE_VOLUME = 600;

    @Test
    void aFilledVatSubmitsItsGooBeforeItsShell() {
        RecordingParts parts = new RecordingParts();

        VatSpecialRenderer.submitParts(new VatSpecialRenderer.VatData(GooTypes.ROCK, HALF_FULL, List.of()), parts);

        assertEquals(List.of("goo", "shell"), parts.order);
    }

    /**
     * A vat item holding several goo types carries a band per type by volume, which it draws as one mingled
     * surface, as the placed vat and the canister item draw theirs.
     * decision one-pass-blends-every-type
     */
    @Test
    void aVatHoldingSeveralTypesCarriesABandPerType() {
        GooContents contents = new GooContents(Map.of(GooTypes.ROCK, ROCK_VOLUME, GooTypes.BLAZE, BLAZE_VOLUME));

        VatSpecialRenderer.VatData data = VatSpecialRenderer.dataOf(contents);

        assertNotNull(data);
        assertEquals(TypeBands.over(contents), data.bands());
    }

    @Test
    void anEmptyVatSubmitsItsShellAlone() {
        RecordingParts parts = new RecordingParts();

        VatSpecialRenderer.submitParts(null, parts);

        assertEquals(List.of("shell"), parts.order);
    }

    /** Records the parts the vat submits, in order. */
    private static final class RecordingParts implements VatSpecialRenderer.VatParts {
        private final List<String> order = new ArrayList<>();

        @Override
        public void goo(VatSpecialRenderer.VatData data) {
            order.add("goo");
        }

        @Override
        public void shell() {
            order.add("shell");
        }
    }
}
