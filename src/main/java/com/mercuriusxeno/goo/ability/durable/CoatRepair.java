package com.mercuriusxeno.goo.ability.durable;

import com.mercuriusxeno.goo.ability.LoadedAbilityCost;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * What a layer of Coat restores to an item lying in it: the share of the
 * item's full durability that the layer's worth is of the item's whole
 * worth, so restoring an item from empty to full costs goo equal to what the
 * item is worth.
 * items-dropped-in-durable-goo-are-repaired-by-their-worth
 */
public final class CoatRepair {

    private CoatRepair() {
    }

    /**
     * The durability one layer of a world goo restores to a stack, priced by
     * the server's values: a layer is a fifteenth of its laying ability's
     * cost, and the stack's worth its whole goo value.
     *
     * @param level   the server level whose values price both
     * @param laying  the ability laying the goo
     * @param stack   the stack lying in the goo
     * @return the points the layer restores, 0 where either goes unpriced or the stack takes no damage
     */
    public static int layerRestores(ServerLevel level, Identifier laying, ItemStack stack) {
        GooValue worth = GooValues.of(level).lookup(stack);
        if (worth == null || !stack.isDamageableItem()) {
            return 0;
        }
        return LoadedAbilityCost.of(level, laying)
                .map(VolumeCellBlock::layerPrice)
                .map(layer -> pointsRestored(layer, worth.totalExact(), stack.getMaxDamage(), stack.getDamageValue()))
                .orElse(0);
    }

    /**
     * The durability one layer restores: the layer's worth over the item's,
     * of its full durability, rounded down, at least one point and never past
     * full; nothing to an undamaged item, an item with no durability, or an
     * item with no worth.
     *
     * @param layer          the worth of a layer of Coat, in mB
     * @param worth          the item's whole goo value, in mB
     * @param fullDurability the item's full durability
     * @param damage         the durability the item lacks
     * @return the points the layer restores
     */
    public static int pointsRestored(GooFraction layer, GooFraction worth, int fullDurability, int damage) {
        if (damage <= 0 || fullDurability <= 0 || !worth.isPositive() || !layer.isPositive()) {
            return 0;
        }
        int share = layer.times(fullDurability).dividedBy(worth).floor();
        return Math.min(damage, Math.max(1, share));
    }
}
