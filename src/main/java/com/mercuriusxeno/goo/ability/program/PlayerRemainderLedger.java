package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import java.util.OptionalInt;

/**
 * A player's remainder ledger, held in their remainders attachment, which unmake's
 * payout credits and ability costs debit.
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 *
 * @param holder the player holding the attachment
 */
public record PlayerRemainderLedger(IAttachmentHolder holder) implements IRemainderLedger {

    @Override
    public int creditExact(ResourceKey<GooTypeDefinition> type, GooFraction exact) {
        RemainderLedger exchange = new RemainderLedger(remainders());
        int whole = exchange.creditExact(type, exact);
        holder.setData(GooAttachments.REMAINDERS, exchange.remainders());
        return whole;
    }

    @Override
    public OptionalInt debitExact(ResourceKey<GooTypeDefinition> type, GooFraction exact, int availableWhole) {
        RemainderLedger exchange = new RemainderLedger(remainders());
        OptionalInt whole = exchange.debitExact(type, exact, availableWhole);
        holder.setData(GooAttachments.REMAINDERS, exchange.remainders());
        return whole;
    }

    @Override
    public GooRemainders remainders() {
        return holder.getData(GooAttachments.REMAINDERS);
    }
}
