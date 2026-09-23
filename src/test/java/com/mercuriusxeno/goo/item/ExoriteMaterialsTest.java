package com.mercuriusxeno.goo.item;

import net.minecraft.world.item.ToolMaterial;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exorite tool material stands above netherite's on durability, mining
 * speed and attack bonus (decision exorite-tool-and-armor-set). The armor
 * material needs a booted game, so gametest goo:exorite_armor_stats covers it.
 */
class ExoriteMaterialsTest {

    @Test
    void toolDurabilityExceedsNetherite() {
        assertTrue(ExoriteMaterials.TOOL.durability() > ToolMaterial.NETHERITE.durability());
    }

    @Test
    void toolSpeedExceedsNetherite() {
        assertTrue(ExoriteMaterials.TOOL.speed() > ToolMaterial.NETHERITE.speed());
    }

    @Test
    void toolAttackBonusExceedsNetherite() {
        assertTrue(ExoriteMaterials.TOOL.attackDamageBonus() > ToolMaterial.NETHERITE.attackDamageBonus());
    }
}
