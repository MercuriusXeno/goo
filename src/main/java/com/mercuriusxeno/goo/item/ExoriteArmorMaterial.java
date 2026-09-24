package com.mercuriusxeno.goo.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import java.util.EnumMap;
import java.util.Map;

/**
 * The exorite armor material, each stat a step past netherite's (decision
 * exorite-tool-and-armor-set). It stands apart from ExoriteMaterials because
 * its equip sound needs the sound registry, which only a booted game holds.
 */
public final class ExoriteArmorMaterial {

    private static final int ARMOR_DURABILITY = 45;
    private static final int BOOTS_DEFENSE = 4;
    private static final int LEGGINGS_DEFENSE = 7;
    private static final int CHESTPLATE_DEFENSE = 9;
    private static final int HELMET_DEFENSE = 4;
    private static final int BODY_DEFENSE = 23;
    private static final float ARMOR_TOUGHNESS = 4.0F;
    private static final float KNOCKBACK_RESISTANCE = 0.15F;

    public static final ArmorMaterial MATERIAL = new ArmorMaterial(ARMOR_DURABILITY, armorDefense(),
            ExoriteMaterials.ENCHANTMENT_VALUE, SoundEvents.ARMOR_EQUIP_NETHERITE, ARMOR_TOUGHNESS,
            KNOCKBACK_RESISTANCE, ExoriteMaterials.EXORITE_TOOL_MATERIALS, ExoriteMaterials.EXORITE_EQUIPMENT_ASSET);

    private ExoriteArmorMaterial() {
    }

    private static Map<ArmorType, Integer> armorDefense() {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.BOOTS, BOOTS_DEFENSE);
        defense.put(ArmorType.LEGGINGS, LEGGINGS_DEFENSE);
        defense.put(ArmorType.CHESTPLATE, CHESTPLATE_DEFENSE);
        defense.put(ArmorType.HELMET, HELMET_DEFENSE);
        defense.put(ArmorType.BODY, BODY_DEFENSE);
        return defense;
    }
}
