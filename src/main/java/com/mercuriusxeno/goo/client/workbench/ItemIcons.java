package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Each item the manager draws, drawn from its own ItemStack, with no capture and no image route in-game; a tag draws
 * no icon (decision goo-workbench-opens-the-in-game-manager-screen).
 */
final class ItemIcons {
    private static final String TAG_PREFIX = "#";
    private static final String DEFAULT_NAMESPACE = "minecraft:";
    private static final String NAMESPACE_SEPARATOR = ":";
    private static final String AMOUNT_SEPARATOR = ": ";

    // A hover key no prefab name spells, since a name holds no colon.
    private static final String HOVER_PREFIX = "goo-item:";

    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final Function<String, Map<String, String>> amounts;

    /**
     * @param amounts each item's goo value by type, empty for an item nothing values
     */
    ItemIcons(Function<String, Map<String, String>> amounts) {
        this.amounts = amounts;
    }

    /**
     * @param id an item id
     * @return the hover key whose tooltip names the item
     */
    static String hoverKey(String id) {
        return HOVER_PREFIX + id;
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the item's name and value where the area is an item icon's, empty otherwise
     */
    Optional<List<Component>> tooltipFor(String hoverKey) {
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        String id = hoverKey.substring(HOVER_PREFIX.length());
        return Optional.of(tooltipLines(nameOf(id), amounts.apply(id)));
    }

    /**
     * The tooltip an item icon shows (decision used-by-icons-link-to-items-and-hover-their-value).
     *
     * @param name    the item's name
     * @param amounts each goo type the item is worth with its amount, empty where nothing values it
     * @return the name, then one line per goo type with its amount
     */
    static List<Component> tooltipLines(String name, Map<String, String> amounts) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name));
        amounts.forEach((type, amount) -> lines.add(Component.empty().append(GooTypeIcons.nameOf(type))
                .append(Component.literal(AMOUNT_SEPARATOR + amount))));
        return lines;
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
