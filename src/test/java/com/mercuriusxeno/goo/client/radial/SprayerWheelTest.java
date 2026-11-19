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
     * The goo types are the petals, each under the cursor where it stands; inside the hub the nozzles sit in two
     * staggered rows, 1, 3 and 5 below and 2, 4 and 6 above, left to right, and the flow steps in a row under them,
     * each under 2, 4 and 6.
     * operator ruling 2026-11-18: arrange the icons as 2 4 6 over 1 3 5, a divider, then 1 2 3; drop the wheel
     */
    @Test
    void gooOnThePetalsNozzlesStaggeredOverTheFlowInTheHub() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME)
                .withVolume(goo.resource(GooTypes.ROCK), VOLUME), Set.of(GooTypes.BLAZE, GooTypes.ROCK)::contains);

        for (int type = 0; type < wheel.types().size(); type++) {
            assertEquals(type, wheel.gooAt(wheel.gooStartOf(type) + wheel.gooArc() / 2), "the middle of petal " + type);
        }
        double lastX = -Double.MAX_VALUE;
        double lowestNozzle = -Double.MAX_VALUE;
        for (int nozzle = 0; nozzle < wheel.nozzles().size(); nozzle++) {
            double[] at = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE, nozzle));
            double[] next = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE,
                    (nozzle + 1) % wheel.nozzles().size()));
            assertTrue(at[0] > lastX, "nozzle " + nozzle + " right of the one before");
            assertTrue(nozzle % 2 == 0 ? at[1] > next[1] || nozzle == wheel.nozzles().size() - 1 : at[1] < next[1],
                    "nozzles 1, 3, 5 low and 2, 4, 6 high, nozzle " + (nozzle + 1));
            lastX = at[0];
            lowestNozzle = Math.max(lowestNozzle, at[1]);
        }
        for (int flow = 0; flow < wheel.flows().size(); flow++) {
            double[] at = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.FLOW, flow));
            double[] over = wheel.hubOffsetOf(new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE, flow * 2 + 1));
            assertEquals(over[0], at[0], 1e-9, "flow " + (flow + 1) + " under nozzle " + (flow * 2 + 2));
            assertTrue(at[1] > lowestNozzle, "flow " + (flow + 1) + " below every nozzle");
        }
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
