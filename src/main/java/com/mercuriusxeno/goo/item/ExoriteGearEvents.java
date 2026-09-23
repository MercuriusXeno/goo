package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Exorite traits applied in events. No piece is enchantable (decision
 * exorite-unenchantable). A broken piece (decision
 * zero-durability-stops-working) gathers no attribute modifiers, so it
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
     * Removes the ENCHANTABLE component every exorite piece's tool or armor
     * material gave it, so the enchanting table and ItemStack.isEnchantable
     * refuse it (decision exorite-unenchantable). The vanilla axe, shovel and
     * hoe constructors add the component after any property a caller passes,
     * so it comes off here, once construction is done.
     *
     * @param event the default component modification event
     */
    @SubscribeEvent
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        event.modifyMatching(item -> item instanceof ExoriteGear,
                components -> components.set(DataComponents.ENCHANTABLE, null));
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
