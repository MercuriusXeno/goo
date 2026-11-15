package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Where a held or cursor stack's goo goes: takes a type and a volume and answers the volume it kept.
 */
@FunctionalInterface
public interface GooSink {
    /**
     * Accepts up to the given volume of one goo type.
     *
     * @param type   the goo type offered
     * @param volume the volume offered, in mB
     * @return the volume accepted
     */
    int accept(ResourceKey<GooTypeDefinition> type, int volume);

    /**
     * The store's own remainder ledger, which a poured stack's part under 1 mB is
     * credited to; a sink keeping none leaves the part on the stack.
     * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
     *
     * @return the ledger, or null where the sink keeps none
     */
    default @Nullable IRemainderLedger ledger() {
        return null;
    }

    /**
     * A sink that banks a poured part in a store's ledger.
     *
     * @param ledger the store's ledger, or null where it keeps none
     * @param accept how the store takes whole mB
     * @return the sink
     */
    static GooSink banking(@Nullable IRemainderLedger ledger, GooSink accept) {
        return new GooSink() {
            @Override
            public int accept(ResourceKey<GooTypeDefinition> type, int volume) {
                return accept.accept(type, volume);
            }

            @Override
            public @Nullable IRemainderLedger ledger() {
                return ledger;
            }
        };
    }
}
