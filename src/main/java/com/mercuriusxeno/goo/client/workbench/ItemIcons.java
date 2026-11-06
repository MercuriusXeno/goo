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

    // A hover key no prefab name spells, since a name holds no colon.
    private static final String HOVER_PREFIX = "goo-item:";

    private static final String ANCHORS = "anchors";
    // Every anchor shows, so the line leaves none out.
    private static final int NONE_LEFT_OUT = 0;

    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final Function<String, Map<String, String>> amounts;
    private final Function<String, List<String>> anchors;

    /**
     * @param amounts each item's goo value by type, empty for an item nothing values
     * @param anchors each item's anchors, empty for an item holding an entry or traced to none
     */
    ItemIcons(Function<String, Map<String, String>> amounts, Function<String, List<String>> anchors) {
        this.amounts = amounts;
        this.anchors = anchors;
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
    Optional<List<TooltipLine>> tooltipFor(String hoverKey) {
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        String id = hoverKey.substring(HOVER_PREFIX.length());
        return Optional.of(tooltipLines(nameOf(id), amounts.apply(id), anchors.apply(id)));
    }

    /**
     * The tooltip an item icon shows (decision used-by-icons-link-to-items-and-hover-their-value).
     *
     * @param name    the item's name
     * @param amounts each goo type the item is worth with its amount, empty where nothing values it
     * @param anchors the item's anchors, empty where it has none
     * @return the name, one line per goo type with its amount, then the word anchors over the anchors' icons where
     *     it has any
     */
    static List<TooltipLine> tooltipLines(String name, Map<String, String> amounts, List<String> anchors) {
        List<TooltipLine> lines = new ArrayList<>();
        lines.add(new TooltipLine.Text(Component.literal(name)));
        // decision value-tooltips-draw-each-types-icon-before-its-amount
        amounts.forEach((type, amount) -> lines.add(new TooltipLine.GooAmount(type, amount)));
        // decision item-hover-lists-its-anchors
        if (!anchors.isEmpty()) {
            lines.add(new TooltipLine.Text(Component.literal(ANCHORS)));
            lines.add(new TooltipLine.Members(anchors, NONE_LEFT_OUT));
        }
        return lines;
    }

    /**
     * @param id an item id, bare in the minecraft namespace, or a #tag
     * @return the item's stack, empty for a tag or an unknown id
     */
    ItemStack of(String id) {
        return stacks.computeIfAbsent(id, ItemIcons::stackOf);
    }

    /**
     * @param id an item id, bare in the minecraft namespace, or a #tag
     * @return the item's stack read from the registry afresh, empty for a tag or an unknown id
     */
    static ItemStack stackOf(String id) {
        if (id.startsWith(TAG_PREFIX)) {
            return ItemStack.EMPTY;
        }
        Identifier parsed = Identifier.tryParse(namespaced(id));
        return parsed == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getValue(parsed).getDefaultInstance();
    }

    /**
     * @param id an item id, bare in the minecraft namespace as the file spells it, or whole
     * @return the whole id, which the Items rows and every item's value are keyed by
     */
    static String namespaced(String id) {
        return id.contains(NAMESPACE_SEPARATOR) ? id : DEFAULT_NAMESPACE + id;
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
