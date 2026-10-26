package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Each item the manager draws, drawn from its own ItemStack, with no capture and no image route in-game; a tag draws
 * no icon (decision goo-workbench-opens-the-in-game-manager-screen).
 */
final class ItemIcons {
    private static final String TAG_PREFIX = "#";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String NAMESPACE_SEPARATOR = ":";

    // A hover key no prefab name spells, since a name holds no colon.
    private static final String HOVER_PREFIX = "goo-item:";

    private final Map<String, ItemStack> stacks = new HashMap<>();

    /**
     * @param id an item id
     * @return the hover key whose tooltip names the item
     */
    static String hoverKey(String id) {
        return HOVER_PREFIX + id;
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the item's name where the area is an item icon's, empty otherwise
     */
    Optional<Component> tooltipFor(String hoverKey) {
        return hoverKey.startsWith(HOVER_PREFIX)
                ? Optional.of(Component.literal(nameOf(hoverKey.substring(HOVER_PREFIX.length()))))
                : Optional.empty();
    }

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

    /**
     * @param id an item id, bare in the minecraft namespace, or a #tag
     * @return the item's display name, or the id's path where no item answers it, never its namespace
     *         (decision curated-view-shows-no-brace-quote-or-raw-id)
     */
    String nameOf(String id) {
        ItemStack stack = of(id);
        if (!stack.isEmpty()) {
            return stack.getHoverName().getString();
        }
        int separator = id.indexOf(NAMESPACE_SEPARATOR);
        return separator < 0 ? id : id.substring(separator + 1);
    }
}
