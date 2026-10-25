package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

/**
 * Each item the manager draws, drawn from its own ItemStack, with no capture and no image route in-game; a tag draws
 * no icon (decision goo-workbench-opens-the-in-game-manager-screen).
 */
final class ItemIcons {
    private static final String TAG_PREFIX = "#";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String NAMESPACE_SEPARATOR = ":";

    private final Map<String, ItemStack> stacks = new HashMap<>();

    /**
     * @param id an item id, bare in the minecraft namespace, or a #tag
     * @return the item's stack, empty for a tag or an unknown id
     */
    ItemStack of(String id) {
        return stacks.computeIfAbsent(id, key -> {
            if (key.startsWith(TAG_PREFIX)) {
                return ItemStack.EMPTY;
            }
            Identifier parsed = Identifier.tryParse(key.contains(NAMESPACE_SEPARATOR) ? key : DEFAULT_NAMESPACE + key);
            return parsed == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getValue(parsed).getDefaultInstance();
        });
    }
}
