package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Listed goo amounts read the highest first, an exact fraction compared exactly, a tie
 * broken by type id ascending (decision value-lists-order-highest-first-ties-by-type-key).
 */
class GooAmountOrderTest {

    @Test
    void amountsReadHighestFirstAFractionComparedExactlyTiesByTypeId() {
        List<Map.Entry<ResourceKey<GooTypeDefinition>, GooFraction>> amounts = new ArrayList<>(List.of(
                Map.entry(GooTypes.METAL, GooFraction.of(5, 2)),
                Map.entry(GooTypes.WEIRD, GooFraction.of(3)),
                Map.entry(GooTypes.PULSE, GooFraction.of(3))));

        amounts.sort(GooAmountOrder.highestFirst());

        assertEquals(List.of(GooTypes.PULSE, GooTypes.WEIRD, GooTypes.METAL),
                amounts.stream().map(Map.Entry::getKey).toList());
    }
}
