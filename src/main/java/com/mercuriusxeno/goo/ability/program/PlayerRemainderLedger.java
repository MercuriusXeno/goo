package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.StoredRemainderLedger;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/**
 * The remainder ledger a player serves through GooCapabilities.REMAINDER_ENTITY,
 * held in their remainders attachment, which unmake's payout credits and ability
 * costs debit.
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 * decision one-remainder-capability-injected-into-every-fluid-capability
 */
public final class PlayerRemainderLedger {

    private PlayerRemainderLedger() {
    }

    /**
     * The ledger over a holder's remainders attachment.
     *
     * @param holder the player holding the attachment
     * @return the ledger
     */
    public static IRemainderLedger of(IAttachmentHolder holder) {
        return new StoredRemainderLedger(() -> holder.getData(GooAttachments.REMAINDERS),
                remainders -> holder.setData(GooAttachments.REMAINDERS, remainders));
    }
}
