package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The first-person hand draws the stack it last took, which lags the stack the server sent while a bare-handed cast
 * drains raw goo every tick: the drawn copy of the held goo still stands for the player's hand and caster, so the palm
 * blob holds steady as a glove's, whose stack never changes, does. Another stack, the off hand's or goo of another
 * type, never stands for the caster.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
class GooCastersDrawnTest {

    private final Player player = mock(Player.class);
    /** The astral goo the renderer last took into the hand. */
    private final ItemStack drawnAstral = gooStack();
    /** The astral goo the server sent after the cast drained it. */
    private final ItemStack heldAstral = gooStack();
    private final ItemStack offHandZombie = gooStack();
    private MockedStatic<GooStacks> stacks;

    private static ItemStack gooStack() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getItem()).thenReturn(mock(GooItem.class));
        return stack;
    }

    @BeforeEach
    void holdAstralInTheMainHandAndZombieInTheOff() {
        when(player.getMainHandItem()).thenReturn(heldAstral);
        when(player.getOffhandItem()).thenReturn(offHandZombie);
        stacks = mockStatic(GooStacks.class);
        stacks.when(() -> GooStacks.keyOf(drawnAstral)).thenReturn(GooTypes.ASTRAL);
        stacks.when(() -> GooStacks.keyOf(heldAstral)).thenReturn(GooTypes.ASTRAL);
        stacks.when(() -> GooStacks.keyOf(offHandZombie)).thenReturn(GooTypes.ZOMBIE);
    }

    @AfterEach
    void restoreStacks() {
        stacks.close();
    }

    @Test
    void theDrawnCopyOfDrainedGooStandsForTheCaster() {
        assertTrue(GooCasters.drawnAsCaster(player, drawnAstral));
    }

    @Test
    void theDrawnCopyOfDrainedGooStandsForTheHand() {
        assertTrue(GooCasters.drawnInHand(player, drawnAstral));
    }

    /** The arm drawn under held goo is the main hand's for the drawn copy, so the skin's arm never swaps sides. */
    @Test
    void theDrawnCopyOfDrainedGooStandsForTheMainHand() {
        assertTrue(GooCasters.drawnInMainHand(player, drawnAstral));
    }

    @Test
    void theOffHandsGooNeverStandsForTheMainHand() {
        assertFalse(GooCasters.drawnInMainHand(player, offHandZombie));
    }

    @Test
    void theOffHandsGooNeverStandsForTheCaster() {
        assertFalse(GooCasters.drawnAsCaster(player, offHandZombie));
    }

    @Test
    void gooOfAnotherTypeNeverStandsForTheCaster() {
        ItemStack drawnZombie = gooStack();
        stacks.when(() -> GooStacks.keyOf(drawnZombie)).thenReturn(GooTypes.ZOMBIE);

        assertFalse(GooCasters.drawnAsCaster(player, drawnZombie));
    }
}
