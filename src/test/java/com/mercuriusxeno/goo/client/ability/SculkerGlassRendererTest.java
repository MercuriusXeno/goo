package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The glass field draws a block's side only where two different block types
 * meet, never where the same block carries on past the field's edge
 * (decision sculker-shows-blocks-translucent-close-up).
 */
class SculkerGlassRendererTest {

    private final BlockState stone = mock(BlockState.class);
    private final BlockState ore = mock(BlockState.class);

    @Test
    void drawsWhereTwoDifferentBlocksMeet() {
        assertTrue(SculkerGlassRenderer.whereTypesMeet(stone, ore));
    }

    @Test
    void drawsNothingWhereTheSameBlockCarriesOn() {
        assertFalse(SculkerGlassRenderer.whereTypesMeet(stone, stone));
    }
}
