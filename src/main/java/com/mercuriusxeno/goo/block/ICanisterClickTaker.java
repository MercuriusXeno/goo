package com.mercuriusxeno.goo.block;

import net.minecraft.world.item.ItemStack;

/**
 * A machine whose own standing click moves goo with a held canister, so that click places no
 * canister block against it (decision preview-runs-the-placement-validator).
 */
public interface ICanisterClickTaker {

    /**
     * Whether a standing click with this canister is the machine's own.
     *
     * @param canister the held canister stack
     * @return true when the machine's click takes the canister use
     */
    boolean takesCanisterClick(ItemStack canister);
}
