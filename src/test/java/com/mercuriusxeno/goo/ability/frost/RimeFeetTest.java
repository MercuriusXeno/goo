package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a living thing wears on its feet decides whether Rime freezes it:
 * anything worn spares it and an empty feet slot leaves it to freeze
 * (decision rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless).
 */
class RimeFeetTest {

    @Test
    void anEmptyFeetSlotFreezes() {
        ItemStack bareFeet = mock(ItemStack.class);
        when(bareFeet.isEmpty()).thenReturn(true);

        assertEquals(RimeFeet.FROZEN, RimeFeet.of(bareFeet));
    }

    @Test
    void leatherBootsAndIronBootsSpare() {
        ItemStack leatherBoots = mock(ItemStack.class);
        ItemStack ironBoots = mock(ItemStack.class);

        assertEquals(List.of(RimeFeet.SPARED, RimeFeet.SPARED),
                List.of(RimeFeet.of(leatherBoots), RimeFeet.of(ironBoots)));
    }
}
