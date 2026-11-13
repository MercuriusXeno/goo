package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exorite tool material stands above netherite's on durability, mining
 * speed and attack bonus (decision exorite-tool-and-armor-set), its durability
 * netherite's times 1.35 (decision exorite-tool-durability-is-2742), and
 * enchants at 20 against netherite's 15 (decision
 * exorite-enchants-above-netherite). The armor material holds durability 45,
 * toughness 4 and its defense by slot.
 */
class ExoriteMaterialsTest {

    @Test
    void toolDurabilityExceedsNetherite() {
        assertTrue(ExoriteMaterials.TOOL.durability() > ToolMaterial.NETHERITE.durability());
    }

    @Test
    void toolDurabilityIsNetheriteTimesOnePointThreeFive() {
        assertEquals(Math.round(ToolMaterial.NETHERITE.durability() * 1.35f), ExoriteMaterials.TOOL.durability());
        assertEquals(2742, ExoriteMaterials.TOOL.durability());
    }

    @Test
    void toolEnchantmentValueIsTwentyAboveNetherite() {
        assertEquals(20, ExoriteMaterials.TOOL.enchantmentValue());
        assertTrue(ExoriteMaterials.TOOL.enchantmentValue() > ToolMaterial.NETHERITE.enchantmentValue());
    }

    @Test
    void toolSpeedExceedsNetherite() {
        assertTrue(ExoriteMaterials.TOOL.speed() > ToolMaterial.NETHERITE.speed());
    }

    @Test
    void toolAttackBonusExceedsNetherite() {
        assertTrue(ExoriteMaterials.TOOL.attackDamageBonus() > ToolMaterial.NETHERITE.attackDamageBonus());
    }

    @Test
    void armorHasDurabilityFortyFiveAndToughnessFour() {
        assertEquals(45, ExoriteArmorMaterial.MATERIAL.durability());
        assertEquals(4.0F, ExoriteArmorMaterial.MATERIAL.toughness());
    }

    @Test
    void armorDefendsFourSevenNineFourAndTwentyThreeBySlot() {
        assertEquals(Map.of(ArmorType.BOOTS, 4, ArmorType.LEGGINGS, 7, ArmorType.CHESTPLATE, 9, ArmorType.HELMET, 4,
                ArmorType.BODY, 23), ExoriteArmorMaterial.MATERIAL.defense());
    }
}
