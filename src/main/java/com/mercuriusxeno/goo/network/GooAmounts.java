package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes and reads an amount of each goo type on a payload, so a client can
 * mingle what something melts into.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class GooAmounts {

    private GooAmounts() {
    }

    /**
     * Writes each type's short id and amount.
     *
     * @param buf     the buffer
     * @param amounts each type's amount
     */
    public static void write(FriendlyByteBuf buf, Map<ResourceKey<GooTypeDefinition>, Integer> amounts) {
        buf.writeVarInt(amounts.size());
        amounts.forEach((type, amount) -> {
            buf.writeUtf(GooTypes.id(type));
            buf.writeVarInt(amount);
        });
    }

    /**
     * Reads each type's amount, dropping a type this side does not know.
     *
     * @param buf the buffer
     * @return each known type's amount
     */
    public static Map<ResourceKey<GooTypeDefinition>, Integer> read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Map<ResourceKey<GooTypeDefinition>, Integer> amounts = new LinkedHashMap<>();
        for (int entry = 0; entry < count; entry++) {
            ResourceKey<GooTypeDefinition> type = GooTypes.byId(buf.readUtf());
            int amount = buf.readVarInt();
            if (type != null) {
                amounts.merge(type, amount, Integer::sum);
            }
        }
        return amounts;
    }
}
