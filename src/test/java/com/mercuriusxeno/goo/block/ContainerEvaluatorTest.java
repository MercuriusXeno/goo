package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A container's contents melt at their exact value through the crucible's ledger
 * (decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer).
 */
class ContainerEvaluatorTest {

    private static final Identifier CARPET = Identifier.withDefaultNamespace("white_carpet");

    private final IGooValueLookup lookup = mock(IGooValueLookup.class);

    @Test
    void threeItemsWorthSixtyFourThirdsCreditSixtyFourAndLeaveNoRemainder() {
        when(lookup.lookup(CARPET)).thenReturn(fraction(GooTypes.LEAF, 64, 3));
        RemainderLedger ledger = new RemainderLedger();

        ContainerEvaluator.ContainerEvaluation evaluation = ContainerEvaluator.creditWalk(
                List.of(ContainerEvaluator.valuedStack(CARPET, 3, lookup)), List.of(), lookup, ledger);

        assertEquals(new GooContents(Map.of(GooTypes.LEAF, 64)), evaluation.goo());
        assertTrue(ledger.remainders().isEmpty());
    }

    @Test
    void theValuedStacksCarryEveryMbThatArrives() {
        when(lookup.lookup(CARPET)).thenReturn(fraction(GooTypes.LEAF, 64, 3));

        ContainerEvaluator.ContainerEvaluation evaluation = ContainerEvaluator.creditWalk(
                List.of(ContainerEvaluator.valuedStack(CARPET, 3, lookup)), List.of(), lookup, new RemainderLedger());

        assertEquals(64L, evaluation.valued().stream().mapToLong(ValuedStack::volume).sum());
    }
}
