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
     * The goo types are the petals, each under the cursor where it stands; the nozzles sit across the top of the hub,
     * left to right in their order, and the flow steps across its bottom, left to right.
     * operator ruling 2026-11-18: icons inside the hub, nozzles on top, flow on the bottom, each left to right
     */
    @Test
    void gooOnThePetalsNozzlesAcrossTheTopFlowAcrossTheBottom() {
        SprayerWheel wheel = SprayerWheel.of(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME)
                .withVolume(goo.resource(GooTypes.ROCK), VOLUME), Set.of(GooTypes.BLAZE, GooTypes.ROCK)::contains);

        for (int type = 0; type < wheel.types().size(); type++) {
            assertEquals(type, wheel.gooAt(wheel.gooStartOf(type) + wheel.gooArc() / 2), "the middle of petal " + type);
        }
        double lastX = -2;
        for (int nozzle = 0; nozzle < wheel.nozzles().size(); nozzle++) {
            double angle = wheel.hubAngleOf(new SprayerWheel.RingPetal(SprayerWheel.Group.NOZZLE, nozzle));
            assertTrue(Math.cos(angle) > 0 && Math.sin(angle) > lastX, "nozzle " + nozzle + " at " + angle);
            lastX = Math.sin(angle);
        }
        lastX = -2;
        for (int flow = 0; flow < wheel.flows().size(); flow++) {
            double angle = wheel.hubAngleOf(new SprayerWheel.RingPetal(SprayerWheel.Group.FLOW, flow));
            assertTrue(Math.cos(angle) < 0 && Math.sin(angle) > lastX, "flow " + flow + " at " + angle);
            lastX = Math.sin(angle);
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
