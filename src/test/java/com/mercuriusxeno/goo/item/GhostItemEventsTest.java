package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * An item entity carrying a ghost never joins the level, and one carrying any other stack joins
 * (decision ghost-is-one-item-type-whose-entity-self-destroys).
 */
class GhostItemEventsTest {

    private static ItemStack holding(Item item) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        return stack;
    }

    @Test
    void ghostStackIsCancelled() {
        assertTrue(GhostItemEvents.cancelsJoin(holding(mock(GhostItem.class))));
    }

    @Test
    void otherStackJoins() {
        assertFalse(GhostItemEvents.cancelsJoin(holding(mock(Item.class))));
    }
}
