package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.EndStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.TartarusStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.ability.program.ZoneStep;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.network.StreamHolds;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pace of the conjured gates' trip, read from the shipped End, Zone and Tartarus JSON: each channel
 * takes two seconds, draining its cost once over them, and the gate on arrival takes two seconds to close.
 */
class GateTripPaceTest {

    /** The operator's ruling: the whole trip twice as slow, two seconds of channel. */
    private static final int CHANNEL_TICKS = 40;

    @ParameterizedTest
    @ValueSource(strings = {"ender_end", "void_zone", "nether_tartarus"})
    void theChannelTakesTwoSeconds(String id) {
        assertEquals(CHANNEL_TICKS, chargeOf(AbilityJson.decode(id)), id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ender_end", "nether_tartarus"})
    void theChannelDrainsItsCostOnceOverTheTwoSeconds(String id) {
        AbilityDefinition ability = AbilityJson.decode(id);
        int cost = ability.cost().in(GooValueTable.EMPTY);
        int drained = 0;
        for (int held = 1; held <= CHANNEL_TICKS; held++) {
            drained += StreamHolds.shareAt(cost, ability.delivery().ticksPerCharge(), held);
        }
        assertEquals(cost, drained, id);
    }

    @Test
    void theGateOnArrivalStillStandsAfterASecondAndAHalfAndIsGoneAfterTwo() {
        assertTrue(ConjuredDoor.releasing(Vec3.ZERO, 0f, 30).unzipped() > 0, "standing 30 ticks in");
        assertEquals(0, ConjuredDoor.releasing(Vec3.ZERO, 0f, CHANNEL_TICKS).unzipped(), 1e-9, "gone at 40");
    }

    private static int chargeOf(AbilityDefinition ability) {
        Expr charge = ability.behaviors().stream().map(step -> switch (step) {
            case EndStep end -> end.charge();
            case ZoneStep zone -> zone.charge();
            case TartarusStep tartarus -> tartarus.charge();
            default -> null;
        }).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        return (int) charge.evaluate(Variables.NONE);
    }
}
