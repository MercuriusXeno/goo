package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One goo amount a machine offers its light loop, measured against the
 * capacity its fill is read from.
 *
 * @param type     the goo type
 * @param amount   the amount present, in mB
 * @param capacity the capacity the fill is measured against, in mB
 */
public record GooLightEntry(ResourceKey<GooTypeDefinition> type, long amount, long capacity) {

    /**
     * Reads every type in a reservoir against one shared capacity.
     *
     * @param contents the reservoir contents
     * @param capacity the capacity each type's fill is measured against
     * @return one entry per type held
     */
    public static List<GooLightEntry> ofContents(GooContents contents, long capacity) {
        List<GooLightEntry> entries = new ArrayList<>(contents.contents().size());
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : contents.contents().entrySet()) {
            entries.add(new GooLightEntry(entry.getKey(), entry.getValue(), capacity));
        }
        return entries;
    }
}
