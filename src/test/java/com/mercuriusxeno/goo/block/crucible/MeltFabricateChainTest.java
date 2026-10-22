package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.block.plexer.MapGooSupply;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Melting pays the floor and fabricating costs the ceiling, so no melt and fabricate
 * chain on a fractional value ends with more goo than it started with
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class MeltFabricateChainTest {

    private static final Identifier CARPET = Identifier.withDefaultNamespace("white_carpet");
    private static final GooValue CARPET_VALUE = fraction(GooTypes.LEAF, 64, 3);
    private static final int CEILING_PLUS_SPARE = 30;

    private final IGooValueLookup lookup = mock(IGooValueLookup.class);

    private int meltOneCarpet() {
        when(lookup.lookup(CARPET)).thenReturn(CARPET_VALUE);
        return CrucibleInsertion.meltArrival(GooContents.EMPTY, CARPET, 1, lookup).arriving()
                .getVolume(GooTypes.LEAF);
    }

    @Test
    void meltingThenFabricatingGainsNoLeaf() {
        int melted = meltOneCarpet();
        MapGooSupply pool = MapGooSupply.holding(GooTypes.LEAF, melted);

        boolean remade = PlexerBlockEntity.fabricateFrom(CARPET_VALUE, pool);

        assertFalse(remade);
        GooFraction before = CARPET_VALUE.exact(GooTypes.LEAF);
        assertTrue(GooFraction.of(pool.available(GooTypes.LEAF)).compareTo(before) <= 0);
    }

    @Test
    void fabricatingThenMeltingGainsNoLeaf() {
        MapGooSupply pool = MapGooSupply.holding(GooTypes.LEAF, CEILING_PLUS_SPARE);

        assertTrue(PlexerBlockEntity.fabricateFrom(CARPET_VALUE, pool));
        int after = pool.available(GooTypes.LEAF) + meltOneCarpet();

        assertTrue(after <= CEILING_PLUS_SPARE, "the chain ended with " + after + " leaf");
    }
}
