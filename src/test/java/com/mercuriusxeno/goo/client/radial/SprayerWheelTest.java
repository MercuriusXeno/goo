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
     * The wheel is one ring of three arcs, goo types, then flow, then nozzles, each petal under the cursor where it
     * stands. operator ruling 2026-11-18: layout A, one ring of three arcs
     */
    @Test
    void ringRunsGooThenFlowThenNozzlesEachUnderTheCursor() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME)
                .withVolume(goo.resource(GooTypes.ROCK), VOLUME), Set.of(GooTypes.BLAZE, GooTypes.ROCK)::contains);
        List<SprayerWheel.RingPetal> ring = wheel.ring();

        assertEquals(wheel.types().size() + wheel.flows().size() + wheel.nozzles().size(), ring.size());
        assertEquals(List.of(SprayerWheel.Group.GOO, SprayerWheel.Group.GOO, SprayerWheel.Group.FLOW),
                ring.subList(0, 3).stream().map(SprayerWheel.RingPetal::group).toList());
        assertEquals(SprayerWheel.Group.NOZZLE, ring.getLast().group());
        for (int petal = 0; petal < ring.size(); petal++) {
            double middle = wheel.startOf(petal) + wheel.petalArc() / 2;
            assertEquals(petal, wheel.petalAt(middle), "the middle of petal " + petal);
        }
    }

    /**
     * Letting go over a petal sets that one setting and keeps the other two.
     * operator ruling 2026-11-18: layout A, one ring of three arcs
     */
    @Test
    void releasingOverAPetalSetsThatSettingAlone() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME)
                .withVolume(goo.resource(GooTypes.ROCK), VOLUME), Set.of(GooTypes.BLAZE, GooTypes.ROCK)::contains);
        SprayerSetting kept = new SprayerSetting(java.util.Optional.of(GooTypes.BLAZE), SprayerFlow.MEDIUM,
                SprayerNozzle.OPEN);

        SprayerSetting fan = wheel.picked(kept, new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE,
                wheel.nozzles().indexOf(SprayerNozzle.FAN)));
        SprayerSetting high = wheel.picked(kept, new SprayerWheel.RingPetal(SprayerWheel.Group.FLOW,
                wheel.flows().indexOf(SprayerFlow.HIGH)));
        SprayerSetting rock = wheel.picked(kept, new SprayerWheel.RingPetal(SprayerWheel.Group.GOO,
                wheel.types().indexOf(GooTypes.ROCK)));

        assertEquals(new SprayerSetting(kept.pickedType(), SprayerFlow.MEDIUM, SprayerNozzle.FAN), fan);
        assertEquals(new SprayerSetting(kept.pickedType(), SprayerFlow.HIGH, SprayerNozzle.OPEN), high);
        assertEquals(new SprayerSetting(java.util.Optional.of(GooTypes.ROCK), SprayerFlow.MEDIUM,
                SprayerNozzle.OPEN), rock);
    }

    /** decision mister-draws-mist-and-lays-one-layer-on-bare-spots */
    @Test
    void wheelListsTheMister() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.WEIRD), VOLUME),
                GooTypes.WEIRD::equals);

        assertTrue(wheel.nozzles().contains(SprayerNozzle.ATOMIZER), "the wheel lists " + wheel.nozzles());
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
