package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * The exorite tool material, each stat a step past netherite's, and the repair
 * tag and equipment asset the tool and armor pieces share (decision
 * exorite-tool-and-armor-set). The enchantment value is netherite's; the
 * exorite-unenchantable decision strips enchantability from the pieces.
 */
public final class ExoriteMaterials {

    private static final String EXORITE = "exorite";
    public static final TagKey<Item> EXORITE_TOOL_MATERIALS = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(Goo.MODID, "exorite_tool_materials"));
    public static final ResourceKey<EquipmentAsset> EXORITE_EQUIPMENT_ASSET = ResourceKey.create(
            EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Goo.MODID, EXORITE));

    private static final int TOOL_DURABILITY = 2_500;
    private static final float TOOL_SPEED = 11.0F;
    private static final float TOOL_ATTACK_DAMAGE_BONUS = 5.0F;
    public static final int ENCHANTMENT_VALUE = 15;

    public static final ToolMaterial TOOL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            TOOL_DURABILITY, TOOL_SPEED, TOOL_ATTACK_DAMAGE_BONUS, ENCHANTMENT_VALUE, EXORITE_TOOL_MATERIALS);

    private ExoriteMaterials() {
    }
}
