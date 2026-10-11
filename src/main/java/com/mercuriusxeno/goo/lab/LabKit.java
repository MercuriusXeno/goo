package com.mercuriusxeno.goo.lab;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.VatBlockItem;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The player kit {@code /goo lab kit} puts in the inventory (decision
 * lab-holds-bays-supply-pens-kit): the goo glove, the exorite tool and armor
 * set, the choral tuner and gasket for rigging runs, and one vat at full
 * Compression holding every registered goo type, read from the registry at kit
 * time, its capacity shared evenly between the types (decision
 * lab-iterates-the-registries; operator ruling 2026-10-11: one vat rather than
 * a canister per type, which overflowed the inventory at twenty-five types). The kit replaces the inventory it fills, since
 * the lab save is scratch, and teaches nothing: {@code /goo lab learn} teaches
 * every item recipe and {@code /goo lab forget} returns the player to knowing
 * nothing, each run on request, so the lab keeps unlearned items and the
 * knowledge gate still rules play outside the lab.
 * decision lab-kit-teaches-every-recipe
 */
public final class LabKit {

    /**
     * The fixed kit items, in hotbar-first order.
     */
    private static final List<Supplier<? extends ItemLike>> FIXED_ITEMS = List.of(
            GooItems.GOO_GLOVE, GooItems.CHORAL_TUNER, GooItems.CHORAL_GASKET,
            GooItems.EXORITE_SWORD, GooItems.EXORITE_PICKAXE, GooItems.EXORITE_AXE,
            GooItems.EXORITE_SHOVEL, GooItems.EXORITE_HOE,
            GooItems.EXORITE_HELMET, GooItems.EXORITE_CHESTPLATE, GooItems.EXORITE_LEGGINGS, GooItems.EXORITE_BOOTS);

    private LabKit() {
    }

    /**
     * Answers the fixed kit items: glove, rigging tools, exorite tools and armor.
     *
     * @return the items
     */
    public static List<Item> fixedItems() {
        return FIXED_ITEMS.stream().map(supplier -> supplier.get().asItem()).toList();
    }

    /**
     * Answers the whole kit for a level: the fixed items, then the stocked vat.
     *
     * @param level the level whose goo type registry to read
     * @return the kit's stacks
     */
    public static List<ItemStack> kit(ServerLevel level) {
        List<ItemStack> stacks = new ArrayList<>();
        fixedItems().forEach(item -> stacks.add(new ItemStack(item)));
        stacks.add(stockedVat(level));
        return stacks;
    }

    /**
     * Answers a vat at full Compression holding every registered goo type, each an
     * even share of the vat's capacity.
     *
     * @param level the level whose goo type and enchantment registries to read
     * @return the vat
     */
    public static ItemStack stockedVat(ServerLevel level) {
        ItemStack vat = new ItemStack(GooItems.VAT.get());
        vat.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(GooEnchantments.COMPRESSION), ContainerCapacity.MAX_COMPRESSION);
        List<ResourceKey<GooTypeDefinition>> types =
                level.registryAccess().lookupOrThrow(GooTypes.REGISTRY).listElementIds().toList();
        int share = ContainerCapacity.vatCapacity(ContainerCapacity.MAX_COMPRESSION) / Math.max(1, types.size());
        types.forEach(type -> VatBlockItem.addGoo(vat, type, share));
        return vat;
    }

    /**
     * Answers every item id the kit teaches: each item holding a goo value and
     * each item any loaded ability requires.
     *
     * @param level the level whose goo values and abilities to read
     * @return the item ids
     */
    public static Set<Identifier> recipeIds(ServerLevel level) {
        Set<Identifier> ids = new HashSet<>(GooValues.of(level).getEffectiveValues().keySet());
        for (AbilityDefinition ability : AbilityRegistry.of(level).all()) {
            ids.addAll(ability.requires());
        }
        return ids;
    }

    /**
     * Replaces a player's inventory with the kit, teaching nothing; stacks the
     * inventory cannot hold drop at the player's feet.
     *
     * @param player the player
     * @param level  the level whose registries to read
     * @return the number of kit stacks handed out
     */
    public static int give(ServerPlayer player, ServerLevel level) {
        List<ItemStack> stacks = kit(level);
        player.getInventory().clearContent();
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.containerMenu.broadcastChanges();
        return stacks.size();
    }

    /**
     * Teaches a player every item recipe, so every loaded ability stands unlocked;
     * {@code /goo lab learn} runs it.
     *
     * @param player the player
     * @param level  the level whose goo values and abilities to read
     * @return the number of item ids taught
     */
    public static int learn(ServerPlayer player, ServerLevel level) {
        Set<Identifier> ids = recipeIds(level);
        PlayerKnowledge.learnAll(player, ids);
        return ids.size();
    }

    /**
     * Returns a player to knowing no item; {@code /goo lab forget} runs it.
     *
     * @param player the player
     */
    public static void forget(ServerPlayer player) {
        PlayerKnowledge.forgetAll(player);
    }
}
