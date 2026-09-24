package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SmithingTemplateItem;
import java.util.List;

/**
 * Builds the exorite upgrade smithing template the way vanilla builds the
 * netherite one: its own applies-to, ingredient and slot descriptions, and
 * vanilla's empty-slot icons (decision exorite-template-from-ancient-city).
 */
public final class ExoriteUpgradeTemplate {

    private static final String LANG_PREFIX = "item." + Goo.MODID + ".smithing_template.exorite_upgrade.";
    public static final String APPLIES_TO_KEY = LANG_PREFIX + "applies_to";
    public static final String INGREDIENTS_KEY = LANG_PREFIX + "ingredients";
    public static final String BASE_SLOT_KEY = LANG_PREFIX + "base_slot_description";
    public static final String ADDITIONS_SLOT_KEY = LANG_PREFIX + "additions_slot_description";

    private static final String SLOT_ICON_PREFIX = "container/slot/";
    private static final List<Identifier> BASE_SLOT_ICONS = List.of(
            slotIcon("helmet"), slotIcon("sword"), slotIcon("chestplate"), slotIcon("pickaxe"),
            slotIcon("leggings"), slotIcon("axe"), slotIcon("boots"), slotIcon("hoe"), slotIcon("shovel"));
    private static final List<Identifier> ADDITIONS_SLOT_ICONS = List.of(slotIcon("ingot"));

    private ExoriteUpgradeTemplate() {
    }

    /**
     * Creates the template item with exorite's descriptions and the netherite
     * upgrade's tool and armor slot icons.
     *
     * @param properties the item properties
     * @return the template item
     */
    public static SmithingTemplateItem create(Item.Properties properties) {
        return new SmithingTemplateItem(
                Component.translatable(APPLIES_TO_KEY).withStyle(ChatFormatting.BLUE),
                Component.translatable(INGREDIENTS_KEY).withStyle(ChatFormatting.BLUE),
                Component.translatable(BASE_SLOT_KEY),
                Component.translatable(ADDITIONS_SLOT_KEY),
                BASE_SLOT_ICONS,
                ADDITIONS_SLOT_ICONS,
                properties);
    }

    private static Identifier slotIcon(String slot) {
        return Identifier.withDefaultNamespace(SLOT_ICON_PREFIX + slot);
    }
}
