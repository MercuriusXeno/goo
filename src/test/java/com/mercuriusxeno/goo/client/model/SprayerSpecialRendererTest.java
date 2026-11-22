package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sprayer's renderer draws its fitted canister on its side with every goo type it holds inside, settled as a
 * canister turned sideways settles, and nothing past the frame for an empty sprayer
 * (decisions sprayer-holds-a-canister-sideways-like-a-caulk-gun,
 * sideways-canister-goo-settles-as-if-turned-sideways). StampedGoo stands the goo resources.
 */
class SprayerSpecialRendererTest {

    private static final int CAPACITY = 1000;
    private static final int ROCK_VOLUME = 200;
    private static final int BLAZE_VOLUME = 300;

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
    void fittedCanisterDrawsSidewaysWithItsGoo() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.ROCK), ROCK_VOLUME)
                .withVolume(goo.resource(GooTypes.BLAZE), BLAZE_VOLUME);
        CanisterSpecialRenderer.GooData data = CanisterSpecialRenderer.dataOf(content, CAPACITY, false, false);
        RecordingParts parts = new RecordingParts();

        SprayerSpecialRenderer.submitFitted(data, parts);

        assertEquals(1, parts.bodies);
        assertEquals(List.of(SprayerSpecialRenderer.sidewaysFluidBounds(data.fill())), parts.fluidBounds);
        CanisterGeometry.FluidBox sideways = CanisterGeometry.STANDING.fluidBox(CanisterGeometry.Settling.SIDEWAYS,
                8f / 16f, data.fill());
        assertEquals(sideways.minX(), parts.fluidBounds.getFirst().x0(), 1e-6f);
        assertEquals(sideways.maxY(), parts.fluidBounds.getFirst().yTop(), 1e-6f);
        List<Object> typesDrawn = parts.bands.stream().flatMap(List::stream).map(band -> (Object) band.type())
                .toList();
        assertTrue(typesDrawn.containsAll(List.of(GooTypes.ROCK, GooTypes.BLAZE)),
                "both goo types should be drawn, drawn " + typesDrawn);
    }

    /**
     * The fitted canister's goo is submitted before its body, since the GUI's item atlas sorts no quad under
     * improved transparency and the body, drawn first, would hide the goo behind the depth it writes (defect row 7).
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    @Test
    void fittedCanisterSubmitsItsGooBeforeItsBody() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.ROCK), ROCK_VOLUME);
        CanisterSpecialRenderer.GooData data = CanisterSpecialRenderer.dataOf(content, CAPACITY, false, false);
        RecordingParts parts = new RecordingParts();

        SprayerSpecialRenderer.submitFitted(data, parts);

        assertEquals(List.of("fluid", "body"), parts.order);
    }

    @Test
    void emptySprayerDrawsItsFrameAlone() {
        RecordingParts parts = new RecordingParts();

        SprayerSpecialRenderer.submitFitted(null, parts);

        assertEquals(0, parts.bodies);
        assertTrue(parts.fluidBounds.isEmpty());
    }

    /** Records what the renderer submits past the frame. */
    private static final class RecordingParts implements SprayerSpecialRenderer.FittedParts {
        private int bodies;
        private final List<CuboidBounds> fluidBounds = new ArrayList<>();
        private final List<List<TypeBand>> bands = new ArrayList<>();
        private final List<String> order = new ArrayList<>();

        @Override
        public void body(CanisterSpecialRenderer.GooData data) {
            bodies++;
            order.add("body");
        }

        @Override
        public void fluid(CanisterSpecialRenderer.GooData data, CuboidBounds bounds) {
            order.add("fluid");
            fluidBounds.add(bounds);
            bands.add(data.bands());
        }
    }
}
