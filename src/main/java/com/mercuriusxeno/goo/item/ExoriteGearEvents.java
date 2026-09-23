package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * What a broken exorite piece shows and grants, applied in events (decision
 * zero-durability-stops-working): it gathers no attribute modifiers, so it
 * deals a bare hand's damage and gives no protection, and its tooltip says it
 * is broken. The pieces carry their modifiers as a component, which the
 * item's own default-modifiers hook never overrides, and vanilla deprecates
 * the item's own tooltip hook.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ExoriteGearEvents {

    private ExoriteGearEvents() {
    }

    /**
     * Clears the gathered modifiers of a broken exorite stack.
     *
     * @param event the attribute modifier gathering event
     */
    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof ExoriteGear && ExoriteGear.isBroken(stack)) {
            event.clearModifiers();
        }
    }

    /**
     * Adds the broken line to a broken exorite piece's tooltip.
     *
     * @param event the tooltip event
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof ExoriteGear) {
            ExoriteGear.appendBrokenLine(stack, event.getToolTip()::add);
        }
    }
}
