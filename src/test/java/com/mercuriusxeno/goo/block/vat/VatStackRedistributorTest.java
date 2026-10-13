package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A vat stack settles void into each vat first and figures that vat's capacity
 * from the void it took, so the stack never loses room (decision
 * each-void-mb-adds-n-plus-one-capacity).
 */
class VatStackRedistributorTest {

    private static final int BASE = 1_000;
    private static final int VOID_HELD = 100;
    private static final int ROCK_POOLED = 1_000_000;

    @Test
    void theBottomVatTakesEveryVoidThenFillsTheCapacityItGrows() {
        Map<ResourceKey<GooTypeDefinition>, Integer> pool = new HashMap<>(
                Map.of(GooTypes.VOID, VOID_HELD, GooTypes.ROCK, ROCK_POOLED));

        GooContents bottom = VatStackRedistributor.takeSlice(pool, BASE);

        int rockTaken = ContainerCapacity.capacity(BASE, VOID_HELD) - VOID_HELD;
        assertEquals(Map.of(GooTypes.VOID, VOID_HELD, GooTypes.ROCK, rockTaken), bottom.getAll());
        assertEquals(Map.of(GooTypes.ROCK, ROCK_POOLED - rockTaken), pool);
    }

    @Test
    void aVatAboveTheVoidFillsItsBase() {
        Map<ResourceKey<GooTypeDefinition>, Integer> pool = new HashMap<>(Map.of(GooTypes.ROCK, ROCK_POOLED));

        assertEquals(Map.of(GooTypes.ROCK, BASE), VatStackRedistributor.takeSlice(pool, BASE).getAll());
    }
}
