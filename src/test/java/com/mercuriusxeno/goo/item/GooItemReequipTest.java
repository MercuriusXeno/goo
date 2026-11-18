package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Raw goo held in a hand replays the equip animation only when the hand's goo changes type or the slot changes, never
 * when a bare-handed cast drains its volume, as a glove's stack never changes under a cast.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
class GooItemReequipTest {

    private final GooItem goo = mock(GooItem.class, CALLS_REAL_METHODS);
    private final ItemStack before = mock(ItemStack.class);
    private final ItemStack drained = mock(ItemStack.class);
    private final ItemStack otherType = mock(ItemStack.class);
    private MockedStatic<GooStacks> stacks;

    @BeforeEach
    void holdAstralThenDrainIt() {
        when(before.getItem()).thenReturn(goo);
        when(drained.getItem()).thenReturn(goo);
        when(otherType.getItem()).thenReturn(goo);
        stacks = mockStatic(GooStacks.class);
        stacks.when(() -> GooStacks.keyOf(before)).thenReturn(GooTypes.ASTRAL);
        stacks.when(() -> GooStacks.keyOf(drained)).thenReturn(GooTypes.ASTRAL);
        stacks.when(() -> GooStacks.keyOf(otherType)).thenReturn(GooTypes.ZOO);
    }

    @AfterEach
    void restoreStacks() {
        stacks.close();
    }

    @Test
    void aCastDrainingTheHeldGooReplaysNoEquip() {
        assertFalse(goo.shouldCauseReequipAnimation(before, drained, false),
                "a drained stack of the same goo in the same slot should not replay the equip animation");
    }

    @Test
    void gooOfAnotherTypeInTheHandReplaysTheEquip() {
        assertTrue(goo.shouldCauseReequipAnimation(before, otherType, false));
    }

    @Test
    void aChangedSlotReplaysTheEquip() {
        assertTrue(goo.shouldCauseReequipAnimation(before, drained, true));
    }
}
