package com.mercuriusxeno.goo.ability.hex;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers Enchant's draw: any entry of the pool can come up, a curse as
 * readily as any other, and an empty pool draws nothing.
 */
class RandomEnchantmentTest {

    private static final String SHARPNESS = "minecraft:sharpness";
    private static final String BINDING_CURSE = "minecraft:binding_curse";
    private static final List<String> POOL = List.of(SHARPNESS, BINDING_CURSE);

    @Test
    void theDrawCanYieldACurse() {
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(POOL.size())).thenReturn(POOL.indexOf(BINDING_CURSE));

        assertEquals(Optional.of(BINDING_CURSE), RandomEnchantment.pick(POOL, random));
    }

    @Test
    void theDrawFollowsTheRoll() {
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(POOL.size())).thenReturn(POOL.indexOf(SHARPNESS));

        assertEquals(Optional.of(SHARPNESS), RandomEnchantment.pick(POOL, random));
    }

    @Test
    void anEmptyPoolDrawsNothing() {
        assertEquals(Optional.empty(), RandomEnchantment.pick(List.of(), mock(RandomSource.class)));
    }
}
