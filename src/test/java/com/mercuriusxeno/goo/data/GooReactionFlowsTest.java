package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The REACTIONS index names, under each fluid, the reactions yielding it and the reactions spending it.
 */
class GooReactionFlowsTest {

    private static final Identifier FIRST = Identifier.fromNamespaceAndPath("goo", "c_from_a_b");
    private static final Identifier SECOND = Identifier.fromNamespaceAndPath("goo", "d_from_c_water");

    @Test
    void eachFluidNamesTheReactionsYieldingAndSpendingIt() {
        GooReactionFlows flows = GooReactionFlows.index(List.of(
                new GooReactionFlows.Flow(FIRST, List.of("a", "b"), List.of("c")),
                new GooReactionFlows.Flow(SECOND, List.of("c", "minecraft:water"), List.of("d"))));
        assertEquals(Map.of("c", Set.of(FIRST), "d", Set.of(SECOND)), flows.yieldedBy());
        assertEquals(Map.of("a", Set.of(FIRST), "b", Set.of(FIRST), "c", Set.of(SECOND),
                "minecraft:water", Set.of(SECOND)), flows.spentBy());
    }
}
