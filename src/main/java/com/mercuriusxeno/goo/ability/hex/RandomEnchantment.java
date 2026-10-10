package com.mercuriusxeno.goo.ability.hex;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import java.util.List;
import java.util.Optional;

/**
 * Enchant's book: one enchantment drawn at random from every enchantment
 * the world registers, curses and treasure among them, at level one, given
 * to the player as an enchanted book.
 * enchant-book-with-a-purple-afterimage
 */
public final class RandomEnchantment {

    /** Every enchanted book Enchant gives holds its one enchantment at this level. */
    public static final int LEVEL = 1;

    private RandomEnchantment() {
    }

    /**
     * Picks one entry of a pool, each as likely as any other; nothing in the
     * pool is held back, curses included.
     *
     * @param pool   the entries to draw from
     * @param random the draw
     * @param <T>    the entry type
     * @return the drawn entry, or empty for an empty pool
     */
    public static <T> Optional<T> pick(List<T> pool, RandomSource random) {
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool.get(random.nextInt(pool.size())));
    }

    /**
     * Gives the player an enchanted book holding one random enchantment at
     * level one, dropping it at the player's feet when the inventory is full.
     *
     * @param player the player
     */
    public static void giveBook(Player player) {
        List<Holder<Enchantment>> pool = List.copyOf(player.level().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).listElements().toList());
        pick(pool, player.getRandom()).ifPresent(enchantment -> {
            ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, LEVEL));
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        });
    }
}
