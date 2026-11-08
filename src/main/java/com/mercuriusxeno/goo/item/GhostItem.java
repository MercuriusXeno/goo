package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

/**
 * The ghost item: one item type standing for any item the player knows, carrying that
 * item's id, so a plexer can target an item the player no longer holds.
 * decision ghost-is-one-item-type-whose-entity-self-destroys
 */
public class GhostItem extends Item {

    /**
     * Creates the ghost item.
     *
     * @param properties the item properties
     */
    public GhostItem(Properties properties) {
        super(properties);
    }

    /**
     * Builds a ghost standing for the item the id names.
     *
     * @param represented the id of the item the ghost stands for
     * @return a one-count ghost stack
     */
    public static ItemStack of(Identifier represented) {
        ItemStack ghost = new ItemStack(GooItems.GHOST_ITEM.get());
        ghost.set(GooDataComponents.GHOST_OF.get(), represented);
        return ghost;
    }

    /**
     * Answers the id of the item a ghost stands for.
     *
     * @param stack any stack
     * @return the represented item's id, or null for a stack that is no ghost
     */
    public static @Nullable Identifier represented(ItemStack stack) {
        return stack.getItem() instanceof GhostItem ? stack.get(GooDataComponents.GHOST_OF.get()) : null;
    }

    /**
     * Answers a one-count stack of the item a ghost stands for.
     *
     * @param stack any stack
     * @return the represented item's stack, empty for a stack that is no ghost
     */
    public static ItemStack representedStack(ItemStack stack) {
        Identifier represented = represented(stack);
        return represented == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(represented));
    }

    /**
     * A ghost reads the name of the item it stands for.
     */
    @Override
    public Component getName(ItemStack stack) {
        ItemStack represented = representedStack(stack);
        return represented.isEmpty() ? super.getName(stack) : represented.getItemName();
    }

    /**
     * A sneak use reaches the block, so a sneaking player targets a plexer and keeps the ghost.
     * decision ghost-item-targets-the-plexer-sneak-keeps-throw-deletes
     */
    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }
}
