package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.type.GooTypes;
import java.util.Map;
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

    // -- exactDrops (decision goo-item-carries-its-exact-value-as-a-mixed-remainder) --

    /**
     * A store's contents drop as exact values: each type's whole mB plus its remainder,
     * and a type holding only a remainder drops that part alone.
     */
    @Test
    void exactDropsCarryEachTypesRemainder() {
        GooContents contents = new GooContents(Map.of(GooTypes.ROCK, 10),
                GooRemainders.EMPTY.with(GooTypes.ROCK, GooFraction.of(1, 3)).with(GooTypes.LEAF, GooFraction.of(1, 2)));

        assertEquals(Map.of(GooTypes.ROCK, GooFraction.of(31, 3), GooTypes.LEAF, GooFraction.of(1, 2)),
                GooStacks.exactDrops(contents));
    }
}
