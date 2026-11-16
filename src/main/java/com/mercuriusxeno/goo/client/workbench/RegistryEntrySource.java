package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.Comparator;
import java.util.List;

/**
 * The client's own item registry and the item tags the server synced to it on login: every item with its
 * default-instance display name, which the Items tab orders by that name ignoring case, ties by id, then every item
 * tag sorted by id with its members (decisions client-builds-items-and-tags-from-its-own-registries,
 * items-tab-lists-items-by-translated-name), and every block with no item form, such as lava, for the Groups tab's
 * member picker (decision this-becomes-that-is-two-parallel-groups-in-the-goo-workbench).
 */
public final class RegistryEntrySource implements ManagerEntrySource {

    @Override
    public List<ItemName> items() {
        return BuiltInRegistries.ITEM.keySet().stream().map(RegistryEntrySource::itemName).toList();
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

    @Override
    public List<BlockName> itemlessBlocks() {
        return BuiltInRegistries.BLOCK.stream().filter(block -> block != Blocks.AIR && block.asItem() == Items.AIR)
                .map(RegistryEntrySource::blockName).sorted(Comparator.comparing(BlockName::id)).toList();
    }

    private static BlockName blockName(Block block) {
        return new BlockName(BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString());
    }
}
