package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Ends every ghost before it exists in the world: thrown, dropped on death or spilled
 * from a container, the item entity carrying it never joins the level.
 * decision ghost-is-one-item-type-whose-entity-self-destroys
 * decision ghost-item-targets-the-plexer-sneak-keeps-throw-deletes
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GhostItemEvents {

    private GhostItemEvents() {
    }

    /**
     * Cancels a server item entity carrying a ghost as it joins the level.
     *
     * @param event the join event
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item
                && cancelsJoin(item.getItem())) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether an item entity carrying the stack is kept out of the level.
     *
     * @param carried the stack the item entity carries
     * @return true for a ghost
     */
    static boolean cancelsJoin(ItemStack carried) {
        return carried.getItem() instanceof GhostItem;
    }
}
