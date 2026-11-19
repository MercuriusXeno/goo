package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A sprayer held in use keeps its hold and its place in the hand while its canister drains, though each drip that
 * lands rewrites the canister the sprayer carries; another item in the hand, or another slot, still ends it.
 * operator report 2026-11-18: the sprayer kept resetting in the hand while in use
 * decision spray-item-sprays-residuals-beside-the-glove
 */
class SprayerHoldTest {

    private final SprayerItem sprayer = mock(SprayerItem.class, CALLS_REAL_METHODS);
    private final ItemStack beforeTheDrip = mock(ItemStack.class);
    private final ItemStack afterTheDrip = mock(ItemStack.class);
    private final ItemStack somethingElse = mock(ItemStack.class);

    SprayerHoldTest() {
        when(beforeTheDrip.getItem()).thenReturn(sprayer);
        when(afterTheDrip.getItem()).thenReturn(sprayer);
        when(somethingElse.getItem()).thenReturn(mock(GooGloveItem.class));
    }

    @Test
    void aDrainingCanisterKeepsTheHold() {
        assertTrue(sprayer.canContinueUsing(beforeTheDrip, afterTheDrip));
        assertFalse(sprayer.canContinueUsing(beforeTheDrip, somethingElse));
    }

    @Test
    void aDrainingCanisterReplaysNoEquip() {
        assertFalse(sprayer.shouldCauseReequipAnimation(beforeTheDrip, afterTheDrip, false));
        assertTrue(sprayer.shouldCauseReequipAnimation(beforeTheDrip, afterTheDrip, true));
        assertTrue(sprayer.shouldCauseReequipAnimation(beforeTheDrip, somethingElse, false));
    }
}
