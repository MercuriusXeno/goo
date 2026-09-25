package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The crucible's insert gate read through a mocked {@link IGooValueLookup}, the fake that
 * keeps the lookup an interface (decision delete-dead-fold-mirrors).
 */
class CrucibleInsertionTest {

    private static final Identifier STONE = Identifier.fromNamespaceAndPath("minecraft", "stone");

    private final IGooValueLookup lookup = mock(IGooValueLookup.class);

    @Test
    void itemWithGooValueIsInsertable() {
        when(lookup.lookup(STONE)).thenReturn(new GooValue(Map.of(GooTypes.ROCK, 16)));

        assertTrue(CrucibleInsertion.canInsertItem(STONE, lookup));
        verify(lookup).lookup(STONE);
    }

    @Test
    void itemWithoutGooValueIsRefused() {
        when(lookup.lookup(STONE)).thenReturn(null);

        assertFalse(CrucibleInsertion.canInsertItem(STONE, lookup));
    }

    @Test
    void itemWithEmptyGooValueIsRefused() {
        when(lookup.lookup(STONE)).thenReturn(GooValue.EMPTY);

        assertFalse(CrucibleInsertion.canInsertItem(STONE, lookup));
    }
}
