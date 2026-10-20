package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * The goo label's tooltip names the type it holds a vessel to
 * (decision hud-panel-shows-the-label-type); vanilla deprecates the item's own
 * tooltip hook, so the line arrives through the tooltip event.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooLabelEvents {

    private GooLabelEvents() {
    }

    /**
     * Adds the label's type line to its tooltip.
     *
     * @param event the tooltip event
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof GooLabelItem) {
            event.getToolTip().add(GooLabelItem.tooltipLine(GooLabelItem.typeOf(stack)));
        }
    }
}
