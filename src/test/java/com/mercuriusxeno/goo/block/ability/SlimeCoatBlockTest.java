package com.mercuriusxeno.goo.block.ability;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Neighbouring slime coats read as one film: a coat hides its face toward
 * another coat, as a slime block does toward a slime block, and draws it
 * toward anything else (decision coat-is-a-green-slime-film-over-the-blocks-own-texture).
 */
class SlimeCoatBlockTest {

    @Test
    void aCoatHidesItsFaceTowardAnotherCoat() {
        BlockState coat = mock(BlockState.class);
        when(coat.getBlock()).thenReturn(mock(SlimeCoatBlock.class));

        assertTrue(SlimeCoatBlock.hidesFaceToward(coat));
    }

    @Test
    void aCoatDrawsItsFaceTowardAnythingElse() {
        BlockState stone = mock(BlockState.class);
        when(stone.getBlock()).thenReturn(mock(Block.class));

        assertFalse(SlimeCoatBlock.hidesFaceToward(stone));
    }
}
