package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import java.util.Comparator;
import java.util.List;

/**
 * The client's own item registry and the item tags the server synced to it on login, listed as the dumped
 * inventory lists them: every item in sorted key order with its default-instance display name, then every item tag
 * sorted by id with its members (decision client-builds-items-and-tags-from-its-own-registries).
 */
public final class RegistryEntrySource implements ManagerEntrySource {

    @Override
    public List<ItemName> items() {
        return BuiltInRegistries.ITEM.keySet().stream().sorted().map(RegistryEntrySource::itemName).toList();
    }

    private static ItemName itemName(Identifier id) {
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return new ItemName(id.toString(), item.getName(item.getDefaultInstance()).getString());
    }

    @Override
    public List<ItemTag> itemTags() {
        return BuiltInRegistries.ITEM.listTags()
                .map(tag -> new ItemTag(tag.key().location().toString(), tag.stream()
                        .map(holder -> BuiltInRegistries.ITEM.getKey(holder.value()).toString()).toList()))
                .sorted(Comparator.comparing(ItemTag::id)).toList();
    }
}
