package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooOmniblobItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests which held items the crucible claims on the client: a canister is any other item and
 * falls through to the empty-hand drain (decision canister-click-is-any-other-click-on-crucible-and-vat).
 */
class CrucibleInteractionTest {

    private static ItemStack stackOf(Item item) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(item);
        return stack;
    }

    @Test
    void canisterFallsThroughToTheEmptyHandDrain() {
        assertFalse(CrucibleInteraction.wouldHandleItem(stackOf(mock(CanisterItem.class))));
    }

    @Test
    void omniblobIsHandled() {
        assertTrue(CrucibleInteraction.wouldHandleItem(stackOf(mock(GooOmniblobItem.class))));
    }

    @Test
    void flintAndSteelIsHandled() {
        ItemStack flint = stackOf(mock(Item.class));
        when(flint.is(Items.FLINT_AND_STEEL)).thenReturn(true);
        assertTrue(CrucibleInteraction.wouldHandleItem(flint));
    }
}
