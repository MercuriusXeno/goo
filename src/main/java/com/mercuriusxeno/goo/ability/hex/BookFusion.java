package com.mercuriusxeno.goo.ability.hex;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

/**
 * Fuse's anvil: of the enchanted books a player carries, the first two
 * identical ones in inventory order, hotbar first, fuse into one book whose
 * every enchantment stands a level higher, capped at its maximum. A pair
 * with nothing left to raise does not count.
 * fuse-two-books-for-hex-goo
 */
public final class BookFusion {

    private BookFusion() {
    }

    /**
     * Two slots holding a fusable pair, the first of them where the fused book lands.
     *
     * @param first  the earlier slot
     * @param second the later slot
     */
    public record SlotPair(int first, int second) {
    }

    /**
     * Finds the first pair of slots, in slot order, holding equal contents
     * that can still rise.
     *
     * @param slots   each slot's contents, empty where it holds no enchanted book
     * @param canRise whether contents have a level left to raise
     * @param <T>     the contents' type
     * @return the pair, or empty when no slot pairs with a later one
     */
    public static <T> Optional<SlotPair> firstIdenticalPair(List<Optional<T>> slots, Predicate<T> canRise) {
        for (int first = 0; first < slots.size(); first++) {
            Optional<T> held = slots.get(first).filter(canRise);
            if (held.isPresent()) {
                int second = slots.subList(first + 1, slots.size()).indexOf(held);
                if (second >= 0) {
                    return Optional.of(new SlotPair(first, first + 1 + second));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The level an enchantment fuses to: one higher, held at its maximum.
     *
     * @param level    the level both books hold
     * @param maxLevel the enchantment's maximum level
     * @return the fused level
     */
    public static int fusedLevel(int level, int maxLevel) {
        return Math.min(level + 1, maxLevel);
    }

    /**
     * Whether the inventory holds a fusable pair.
     *
     * @param inventory the player's inventory
     * @return true when Fuse can act
     */
    public static boolean holdsPair(Inventory inventory) {
        return pairIn(inventory).isPresent();
    }

    /**
     * Fuses the inventory's first fusable pair: the fused book takes the
     * first book's slot and the second book's slot empties.
     *
     * @param inventory the player's inventory
     * @return true when a pair fused
     */
    public static boolean fuseIn(Inventory inventory) {
        Optional<SlotPair> pair = pairIn(inventory);
        pair.ifPresent(slots -> {
            ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(inventory.getItem(slots.first()));
            ItemStack fused = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantmentHelper.setEnchantments(fused, raised(stored));
            inventory.setItem(slots.first(), fused);
            inventory.setItem(slots.second(), ItemStack.EMPTY);
        });
        return pair.isPresent();
    }

    private static Optional<SlotPair> pairIn(Inventory inventory) {
        List<Optional<ItemEnchantments>> slots = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            slots.add(storedOn(inventory.getItem(slot)));
        }
        return firstIdenticalPair(slots, BookFusion::canRise);
    }

    private static Optional<ItemEnchantments> storedOn(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) {
            return Optional.empty();
        }
        ItemEnchantments stored = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        return stored.isEmpty() ? Optional.empty() : Optional.of(stored);
    }

    private static boolean canRise(ItemEnchantments stored) {
        return stored.entrySet().stream()
                .anyMatch(entry -> entry.getIntValue() < entry.getKey().value().getMaxLevel());
    }

    private static ItemEnchantments raised(ItemEnchantments stored) {
        ItemEnchantments.Mutable fused = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stored.entrySet()) {
            fused.set(entry.getKey(), fusedLevel(entry.getIntValue(), entry.getKey().value().getMaxLevel()));
        }
        return fused.toImmutable();
    }
}
