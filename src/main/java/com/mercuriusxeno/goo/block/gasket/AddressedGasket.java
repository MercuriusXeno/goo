package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.item.gasket.GasketRole;

/**
 * The installed gasket a click addresses on a holder: its face role and the
 * canister slot carrying it, or {@link com.mercuriusxeno.goo.GooConstants#NO_SLOT}
 * for a gasket on the block itself. {@link IGasketHolder#addressedGasket} answers it.
 *
 * @param role the gasket's face role
 * @param slot the canister slot carrying it, or NO_SLOT for a block-level gasket
 */
public record AddressedGasket(GasketRole role, int slot) {
}
