package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Melts a stack at its exact value: each type's exact amount times the count credits
 * the store's ledger, the whole mB the credit answers arrive and the part under 1 mB
 * stays in the ledger. The stacks it answers carry every arriving mB, each item its
 * floor and the last item the whole mB the remainder made up, so a melt queue clocks
 * every item and drains all that arrived.
 * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
 */
public final class ExactMelt {

    private ExactMelt() {
    }

    /**
     * The goo a melt pays and the stacks that carry it.
     *
     * @param arriving the whole mB the melt pays
     * @param stacks   the stacks carrying it, together holding the melted count
     */
    public record Melt(GooContents arriving, List<ValuedStack> stacks) {
    }

    /**
     * Credits a stack's exact value through a ledger.
     *
     * @param itemId the item's registry id
     * @param count  the number of items melted
     * @param value  one item's goo value
     * @param ledger the store's remainder ledger
     * @return the whole mB that arrive and the stacks carrying them
     */
    public static Melt credit(Identifier itemId, int count, GooValue value, IRemainderLedger ledger) {
        GooContents perItem = value.meltPayout();
        if (count <= 0) {
            return new Melt(GooContents.EMPTY, List.of());
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> arriving = new HashMap<>();
        Map<ResourceKey<GooTypeDefinition>, Integer> lastItem = new HashMap<>(perItem.getAll());
        value.exactAll().forEach((type, exact) -> {
            if (!exact.isPositive()) {
                return;
            }
            int whole = ledger.creditExact(type, exact.times(count));
            arriving.put(type, whole);
            int madeUp = whole - Math.multiplyExact(perItem.getVolume(type), count);
            lastItem.merge(type, madeUp, Integer::sum);
        });
        return new Melt(new GooContents(arriving), stacksFor(itemId, count, perItem, new GooContents(lastItem)));
    }

    /**
     * The most whole mB of each type one item's melt may pay, its exact amount
     * rounded up, so a store counting room per item never overruns its cap.
     *
     * @param value one item's goo value
     * @return each positive type's ceiling
     */
    public static GooContents roomPerItem(GooValue value) {
        Map<ResourceKey<GooTypeDefinition>, Integer> room = new HashMap<>();
        value.exactAll().forEach((type, exact) -> room.put(type, exact.ceiling()));
        return new GooContents(room);
    }

    /**
     * The stacks carrying a melt: every item at its floor, the last one carrying what the remainder made up.
     *
     * @param itemId   the item's registry id
     * @param count    the number of items melted
     * @param perItem  one item's floor
     * @param lastItem the last item's goo
     * @return one stack where the last item matches the rest, else the rest and the last apart
     */
    private static List<ValuedStack> stacksFor(Identifier itemId, int count, GooContents perItem, GooContents lastItem) {
        if (lastItem.equals(perItem)) {
            return List.of(new ValuedStack(itemId, count, perItem));
        }
        if (count == 1) {
            return List.of(new ValuedStack(itemId, 1, lastItem));
        }
        return List.of(new ValuedStack(itemId, count - 1, perItem), new ValuedStack(itemId, 1, lastItem));
    }
}
