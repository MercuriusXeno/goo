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
     * Absorbing into an empty goo yields the source volume.
     */
    @Test
    void absorbedVolume_emptyGoo() {
        assertEquals(1000, GooStacks.absorbedVolume(1000, 0));
    }

    /**
     * Absorbing into a non-empty goo sums the two volumes.
     */
    @Test
    void absorbedVolume_withExistingVolume() {
        assertEquals(5500, GooStacks.absorbedVolume(500, 5000));
    }

    /**
     * Absorbing 64,000 mB into a partial goo.
     */
    @Test
    void absorbedVolume_maxGooStackIntoPartial() {
        assertEquals(64_500, GooStacks.absorbedVolume(64_000, 500));
    }

    /**
     * Zero source leaves the goo unchanged.
     */
    @Test
    void absorbedVolume_zeroSource() {
        assertEquals(5000, GooStacks.absorbedVolume(0, 5000));
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
