package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.SprayerFlow;
import com.mercuriusxeno.goo.item.SprayerNozzle;
import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sprayer wheel's model: a petal for each goo type of the fitted canister that has a residual, and the flow
 * steps from low to high (decisions sprayer-wheel-picks-nozzle-flow-and-goo-type,
 * spray-item-sprays-more-or-less-per-tick). StampedGoo stands the goo resources.
 */
class SprayerWheelTest {

    private static final int VOLUME = 300;

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
    void wheelListsTheCanistersSprayableTypesAndTheFlowSteps() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME * 3)
                .withVolume(goo.resource(GooTypes.WEIRD), VOLUME)
                .withVolume(goo.resource(GooTypes.KINETIC), VOLUME * 2);

        SprayerWheel wheel = SprayerWheel.of(content, Set.of(GooTypes.WEIRD, GooTypes.KINETIC)::contains);

        assertEquals(List.of(GooTypes.KINETIC, GooTypes.WEIRD), wheel.types());
        List<SprayerFlow> lowToHigh = wheel.flows().stream().sorted(Comparator.comparingDouble(SprayerFlow::rate))
                .toList();
        assertEquals(lowToHigh, wheel.flows());
        assertEquals(List.of(SprayerFlow.values()).size(), wheel.flows().size());
    }

    /** decision mister-draws-mist-and-lays-one-layer-on-bare-spots */
    @Test
    void wheelListsTheMister() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.WEIRD), VOLUME),
                GooTypes.WEIRD::equals);

        assertTrue(wheel.nozzles().contains(SprayerNozzle.MISTER), "the wheel lists " + wheel.nozzles());
    }

    /** decision spray-item-takes-different-nozzles-doing-different-things */
    @Test
    void wheelListsTheNozzles() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.WEIRD), VOLUME),
                GooTypes.WEIRD::equals);

        assertTrue(wheel.nozzles().containsAll(List.of(SprayerNozzle.CHOKE, SprayerNozzle.JET,
                SprayerNozzle.SPRINKLER, SprayerNozzle.FAN)), "the wheel lists " + wheel.nozzles());
    }
}
