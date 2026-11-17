package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.world.item.ItemStack;

/**
 * Whether Rime freezes a living thing standing on it, read from what it
 * wears on its feet: anything worn there spares it, and an empty feet slot,
 * which is what a mob with no feet slot holds, leaves it to freeze.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 */
public enum RimeFeet {

    /** Something is worn on the feet, so Rime leaves it be. */
    SPARED,
    /** Nothing is worn on the feet, so Rime freezes and hurts it. */
    FROZEN;

    /**
     * Whether Rime freezes a thing wearing this on its feet.
     *
     * @param feet what is worn in the feet slot, empty for nothing
     * @return frozen for an empty feet slot, spared for anything worn
     */
    public static RimeFeet of(ItemStack feet) {
        return feet.isEmpty() ? FROZEN : SPARED;
    }
}
