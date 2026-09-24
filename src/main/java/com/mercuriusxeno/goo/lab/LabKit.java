package com.mercuriusxeno.goo.lab;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The player kit {@code /goo lab kit} puts in the inventory (decision
 * lab-holds-bays-supply-pens-kit): the goo glove, the exorite tool and armor
 * set, the choral tuner and gasket for rigging runs, and one filled canister
 * per registered goo type, read from the registry at kit time (decision
 * lab-iterates-the-registries). The kit replaces the inventory it fills, since
 * the lab save is scratch.
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
     * Answers the whole kit for a level: the fixed items, then one filled canister per registered goo type.
     *
     * @param level the level whose goo type registry to read
     * @return the kit's stacks
     */
    public static List<ItemStack> kit(ServerLevel level) {
        List<ItemStack> stacks = new ArrayList<>();
        fixedItems().forEach(item -> stacks.add(new ItemStack(item)));
        level.registryAccess().lookupOrThrow(GooTypes.REGISTRY).listElementIds()
                .sorted((a, b) -> a.identifier().compareTo(b.identifier()))
                .forEach(type -> stacks.add(LabRigs.filledCanister(type)));
        return stacks;
    }

    /**
     * Replaces a player's inventory with the kit; stacks the inventory cannot hold drop at the player's feet.
     *
     * @param player the player
     * @param level  the level whose registries to read
     * @return the number of kit stacks handed out
     */
    public static int give(Player player, ServerLevel level) {
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
}
