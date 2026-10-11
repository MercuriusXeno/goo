package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.block.fluid.VesselSourcing.Pour;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * One tick's pour from a vessel's source goo: the rate times the source's mB,
 * into one held non-source type picked by the random index
 * (decision infinity-sources-whatever-shares-its-vessel).
 */
class VesselSourcingTest {

    private static final ToIntFunction<ResourceKey<GooTypeDefinition>> INFINITY_AT_ONE =
            type -> GooTypes.INFINITY.equals(type) ? 1 : 0;
    private static final IntUnaryOperator FIRST = bound -> 0;

    @Test
    void oneMbOfInfinityPoursOneMbOfTheOtherType() {
        GooContents held = new GooContents(Map.of(GooTypes.INFINITY, 1, GooTypes.BLAZE, 100));

        assertEquals(new Pour(GooTypes.BLAZE, 1), VesselSourcing.pickPour(held, INFINITY_AT_ONE, FIRST));
    }

    @Test
    void thePourIsTheRateTimesTheSourceMb() {
        GooContents held = new GooContents(Map.of(GooTypes.INFINITY, 5, GooTypes.BLAZE, 100));
        ToIntFunction<ResourceKey<GooTypeDefinition>> rateThree = type -> GooTypes.INFINITY.equals(type) ? 3 : 0;

        assertEquals(new Pour(GooTypes.BLAZE, 15), VesselSourcing.pickPour(held, rateThree, FIRST));
    }

    @Test
    void theRandomIndexPicksAmongTheOtherTypesInTypeOrder() {
        GooContents held = new GooContents(Map.of(GooTypes.INFINITY, 1, GooTypes.ROCK, 10, GooTypes.BLAZE, 10));

        assertEquals(GooTypes.BLAZE, VesselSourcing.pickPour(held, INFINITY_AT_ONE, bound -> 0).type());
        assertEquals(GooTypes.ROCK, VesselSourcing.pickPour(held, INFINITY_AT_ONE, bound -> bound - 1).type());
    }

    @Test
    void infinityAloneOrNoInfinityPoursNothing() {
        assertNull(VesselSourcing.pickPour(new GooContents(Map.of(GooTypes.INFINITY, 1)), INFINITY_AT_ONE, FIRST));
        assertNull(VesselSourcing.pickPour(new GooContents(Map.of(GooTypes.BLAZE, 100)), INFINITY_AT_ONE, FIRST));
    }
}
