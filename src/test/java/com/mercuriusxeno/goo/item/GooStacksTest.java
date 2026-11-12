package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Tests for GooStacks pure utility: constants, volume math, the output rule and
 * the legacy goo volume; the goo factory is mocked, so no registry is touched.
 */
class GooStacksTest {

    // -- Constants --

    /**
     * One goo is 1000 mB.
     */
    @Test
    void mbPerGooIsOneThousand() {
        assertEquals(1000, GooStacks.THOUSAND);
    }

    // -- absorbedVolume (sink-into-goo math) --

    /**
     * Absorbing into a non-empty goo sums the two volumes.
     */
    @Test
    void absorbedVolume_withExistingVolume() {
        assertEquals(5500, GooStacks.absorbedVolume(500, 5000));
    }

    // -- createForOutput (decision one-goo-item-at-every-amount) --

    /**
     * A volume that is no whole goo count, 1500 mB, comes out as the goo carrying
     * exactly that volume, the one goo item at every volume.
     */
    @Test
    void createForOutputAnswersTheGooCarryingThatVolume() {
        ItemStack goo = mock(ItemStack.class);
        try (MockedStatic<GooItem> factory = mockStatic(GooItem.class)) {
            factory.when(() -> GooItem.createWithVolume(GooTypes.ROCK, 1_500)).thenReturn(goo);
            assertSame(goo, GooStacks.createForOutput(GooTypes.ROCK, 1_500));
        }
    }
}
