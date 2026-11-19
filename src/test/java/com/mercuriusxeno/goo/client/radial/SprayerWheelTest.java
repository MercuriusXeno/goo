package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.SprayerFlow;
import com.mercuriusxeno.goo.item.SprayerNozzle;
import com.mercuriusxeno.goo.item.SprayerSetting;
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

    /**
     * The hub holds a three by three grid, every column aligned: Mist, Wide and Line on the top row, Spray, Thin and
     * Jet under them, and under a divider the flow steps, low to high, each under its column.
     * operator ruling 2026-11-19: put the icons in alignment and arrange them three by three
     */
    @Test
    void hubHoldsTheNozzlesAndFlowThreeByThreeInAlignment() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME),
                Set.of(GooTypes.BLAZE)::contains);
        int columns = wheel.flows().size();

        for (int nozzle = 0; nozzle < wheel.nozzles().size(); nozzle++) {
            double[] at = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE, nozzle));
            double[] first = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE,
                    nozzle % columns));
            double[] flow = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.FLOW, nozzle % columns));
            assertEquals(flow[0], at[0], 1e-9, "nozzle " + (nozzle + 1) + " in its flow's column");
            assertEquals(first[1] + (nozzle / columns), at[1], 1e-9, "nozzle " + (nozzle + 1) + " in its row");
            assertTrue(flow[1] > at[1] && wheel.dividerRow() > at[1] && wheel.dividerRow() < flow[1],
                    "the divider runs between nozzle " + (nozzle + 1) + " and the flow");
        }
        double[] middle = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.FLOW, columns / 2));
        assertEquals(0, middle[0], 1e-9, "the grid centered across the hub");
    }

    /**
     * Letting go over a petal sets that one setting and keeps the other two.
     * operator ruling 2026-11-18: a petal or an icon sets that one setting
     */
    @Test
    void releasingOverAPetalSetsThatSettingAlone() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME)
                .withVolume(goo.resource(GooTypes.ROCK), VOLUME), Set.of(GooTypes.BLAZE, GooTypes.ROCK)::contains);
        SprayerSetting kept = new SprayerSetting(java.util.Optional.of(GooTypes.BLAZE), SprayerFlow.MEDIUM,
                SprayerNozzle.SPRAY);

        SprayerSetting fan = wheel.picked(kept, new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE,
                wheel.nozzles().indexOf(SprayerNozzle.LINE)));
        SprayerSetting high = wheel.picked(kept, new SprayerWheel.RingPetal(SprayerWheel.Group.FLOW,
                wheel.flows().indexOf(SprayerFlow.HIGH)));
        SprayerSetting rock = wheel.picked(kept, new SprayerWheel.RingPetal(SprayerWheel.Group.GOO,
                wheel.types().indexOf(GooTypes.ROCK)));

        assertEquals(new SprayerSetting(kept.pickedType(), SprayerFlow.MEDIUM, SprayerNozzle.LINE), fan);
        assertEquals(new SprayerSetting(kept.pickedType(), SprayerFlow.HIGH, SprayerNozzle.SPRAY), high);
        assertEquals(new SprayerSetting(java.util.Optional.of(GooTypes.ROCK), SprayerFlow.MEDIUM,
                SprayerNozzle.SPRAY), rock);
    }

    /** decision mister-draws-mist-and-lays-one-layer-on-bare-spots */
    @Test
    void wheelListsTheMister() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.WEIRD), VOLUME),
                GooTypes.WEIRD::equals);

        assertTrue(wheel.nozzles().contains(SprayerNozzle.MIST), "the wheel lists " + wheel.nozzles());
    }

    /** decision spray-item-takes-different-nozzles-doing-different-things */
    @Test
    void wheelListsTheNozzles() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.WEIRD), VOLUME),
                GooTypes.WEIRD::equals);

        assertTrue(wheel.nozzles().containsAll(List.of(SprayerNozzle.THIN, SprayerNozzle.JET,
                SprayerNozzle.WIDE, SprayerNozzle.LINE)), "the wheel lists " + wheel.nozzles());
    }
}
