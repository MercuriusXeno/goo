package com.mercuriusxeno.goo.item;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Reads and takes the reagents an ability consumes beside its goo cost, the
 * items its JSON names under {@code consumes}, one of each per throw.
 * decision ability-json-names-its-reagent
 */
public final class ReagentScanner {

    private ReagentScanner() {
    }

    /**
     * Whether the player's inventory holds at least one of every reagent named.
     *
     * @param player   the player whose inventory to read
     * @param reagents the item ids consumed, one of each
     * @return true when no reagent is missing
     */
    public static boolean holdsEvery(Player player, List<Identifier> reagents) {
        return holdsEvery(reagents, reagent -> holds(player, reagent));
    }

    /**
     * Whether every reagent named passes the hold test.
     *
     * @param reagents the item ids consumed, one of each
     * @param holds    whether the inventory holds one of an item id
     * @return true when no reagent is missing
     */
    public static boolean holdsEvery(List<Identifier> reagents, Predicate<Identifier> holds) {
        return reagents.stream().allMatch(holds);
    }

    /**
     * Whether the player's inventory holds at least one of an item.
     *
     * @param player  the player whose inventory to read
     * @param reagent the item id
     * @return true when a stack of that item stands in the inventory
     */
    public static boolean holds(Player player, Identifier reagent) {
        return player.getInventory().contains(stack -> isItem(stack, reagent));
    }

    /**
     * How many whole sets of the reagents named the player's inventory holds.
     * reagent-count-is-the-smaller-bound
     *
     * @param player   the player whose inventory to read
     * @param reagents the item ids consumed, one of each
     * @return the smallest count among the reagents, Integer.MAX_VALUE for none named
     */
    public static int setsHeld(Player player, List<Identifier> reagents) {
        return setsHeld(reagents, reagent -> countHeld(player.getInventory(), reagent));
    }

    /**
     * How many whole sets of the reagents named the counts afford.
     *
     * @param reagents the item ids consumed, one of each
     * @param countOf  how many of an item id the inventory holds
     * @return the smallest count among the reagents, Integer.MAX_VALUE for none named
     */
    public static int setsHeld(List<Identifier> reagents, ToIntFunction<Identifier> countOf) {
        return reagents.stream().mapToInt(countOf).min().orElse(Integer.MAX_VALUE);
    }

    private static int countHeld(Inventory inventory, Identifier reagent) {
        int held = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isItem(stack, reagent)) {
                held += stack.getCount();
            }
        }
        return held;
    }

    /**
     * Takes one of each reagent named from the player's inventory, the first
     * stack of each found.
     *
     * @param player   the player whose inventory pays
     * @param reagents the item ids consumed, one of each
     */
    public static void consumeOneOfEach(Player player, List<Identifier> reagents) {
        Inventory inventory = player.getInventory();
        for (Identifier reagent : reagents) {
            consumeOne(inventory, reagent);
        }
        inventory.setChanged();
    }

    private static void consumeOne(Inventory inventory, Identifier reagent) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isItem(stack, reagent)) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static boolean isItem(ItemStack stack, Identifier item) {
        return !stack.isEmpty() && stack.typeHolder().is(item);
    }
}
