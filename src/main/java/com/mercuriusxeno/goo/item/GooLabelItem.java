package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypeNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The goo label: paper coded to one goo type in the GOO_TYPE component, so labels
 * of one type stack and labels of two types do not, and nothing rewrites the type
 * once crafted (decision goo-label-is-paper-plus-one-mb-of-goo).
 */
public class GooLabelItem extends Item implements IGooItemInteraction {

    /** Translation key of the tooltip line naming the label's type (decision hud-panel-shows-the-label-type). */
    private static final String TOOLTIP = "item.goo.goo_label.tooltip";

    /**
     * @param properties item properties
     */
    public GooLabelItem(Properties properties) {
        super(properties);
    }

    /**
     * @param type the goo type the label carries
     * @return one label of that type
     */
    public ItemStack labelOf(ResourceKey<GooTypeDefinition> type) {
        ItemStack label = new ItemStack(this);
        label.set(GooDataComponents.GOO_TYPE.get(), type);
        return label;
    }

    /**
     * @param stack a label stack
     * @return the goo type it carries, or null
     */
    public static @Nullable ResourceKey<GooTypeDefinition> typeOf(ItemStack stack) {
        return stack.getItem() instanceof GooLabelItem ? stack.get(GooDataComponents.GOO_TYPE.get()) : null;
    }

    @Override
    public @NonNull Component getName(@NonNull ItemStack stack) {
        return GooTypeNames.labelName(typeOf(stack));
    }

    /**
     * The tooltip line naming the goo type a label holds its vessel to.
     *
     * @param type the label's goo type
     * @return the tooltip line
     */
    public static Component tooltipLine(@Nullable ResourceKey<GooTypeDefinition> type) {
        return Component.translatable(TOOLTIP, GooTypeNames.name(type)).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public GooInteractionType canisterInteraction() {
        return GooInteractionType.LABEL_APPLY;
    }
}
