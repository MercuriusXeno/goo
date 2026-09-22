package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Utility for inserting goo into a NeoForge Transfer API fluid handler.
 * Decouples the goo type key/volume domain from the FluidResource/Transaction wire format.
 */
public final class GooFluidTransfer {

    private GooFluidTransfer() {}

    /**
     * Inserts goo of the given type and volume into the handler using a root transaction.
     * The handler's tank index is the goo type's ordinal (one tank per goo type).
     *
     * @param handler the target fluid handler
     * @param type    the goo type to insert
     * @param volume  volume in microblobs
     * @return the amount actually inserted
     */
    public static int insert(ResourceHandler<FluidResource> handler, ResourceKey<GooTypeDefinition> type, int volume) {
        int index = GooTypes.indexOf(type);
        FluidResource resource = GooFluids.resource(type);
        int amount = Math.min(volume, Integer.MAX_VALUE);
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(index, resource, amount, tx);
            tx.commit();
            return inserted;
        }
    }
}
