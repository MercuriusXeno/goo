package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A container's contents melt at the floor of each type, as the crucible pays
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class ContainerEvaluatorTest {

    private static final Identifier CARPET = Identifier.withDefaultNamespace("white_carpet");

    @Test
    void aFractionalStackIsValuedAtTheFloorOfEachType() {
        IGooValueLookup lookup = mock(IGooValueLookup.class);
        when(lookup.lookup(CARPET)).thenReturn(fraction(GooTypes.LEAF, 64, 3, GooTypes.JELLY, 1, 2));

        ValuedStack valued = ContainerEvaluator.valuedStack(CARPET, 1, lookup);

        assertEquals(new GooContents(Map.of(GooTypes.LEAF, 21)), valued.unit());
    }
}
