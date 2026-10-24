package com.mercuriusxeno.goo.command;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

/**
 * Everything the loadout registers, as namespaced ids, which the values workbench data-drives from
 * (decision inventory-dump-from-a-gametest-and-a-command). Minecraft-free, so the writer is tested
 * on a fake listing. It lists no block: the mod values a block only through its item
 * (decision workbench-drops-the-blocks-list).
 *
 * @param items    every item, with its display name and image
 * @param itemTags every item tag and its members
 * @param gooTypes every goo type's id
 * @param recipes  every recipe the server loaded
 */
public record GooInventory(List<NamedEntry> items, List<TagEntry> itemTags, List<String> gooTypes,
                           List<RecipeEntry> recipes) {

    /**
     * An item.
     *
     * @param id    the namespaced id
     * @param name  the display name
     * @param image the path under the inventory folder the item's slot capture lands at, or null
     */
    public record NamedEntry(String id, String name, @Nullable String image) {
    }

    /**
     * A tag and the ids it holds.
     *
     * @param id      the tag's namespaced id
     * @param members the member ids
     */
    public record TagEntry(String id, List<String> members) {
    }

    /**
     * One ingredient slot, as the recipe adapter reads it.
     *
     * @param alternatives the item ids the slot accepts
     * @param tag          the tag the slot names, or null for a listed ingredient
     */
    public record SlotEntry(List<String> alternatives, @Nullable String tag) {
    }

    /**
     * A shaped recipe's grid.
     *
     * @param pattern the rows, a space for an empty cell
     * @param key     each pattern symbol's slot
     */
    public record ShapedGrid(List<String> pattern, Map<String, SlotEntry> key) {
    }

    /**
     * A recipe.
     *
     * @param id         the recipe's namespaced id
     * @param type       the recipe type's id
     * @param output     the output item id, or null where the adapter reads no result
     * @param count      the output count
     * @param slots      the ingredient slots
     * @param containers each ingredient that leaves an item behind, to the item it leaves
     * @param grid       the shaped grid, or null for a recipe without one
     */
    public record RecipeEntry(String id, String type, @Nullable String output, int count, List<SlotEntry> slots,
                              Map<String, String> containers, @Nullable ShapedGrid grid) {
    }
}
