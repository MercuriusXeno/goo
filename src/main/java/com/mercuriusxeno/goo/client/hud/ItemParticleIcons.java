package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.GooSubmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an item id to the sprite its model draws particles with, the one flat
 * picture every item model carries, block items included (decision pool-keeps-stacks-in-order).
 */
final class ItemParticleIcons {

    /** Reused render state; the HUD paints on the render thread alone. */
    private static final ItemStackRenderState RENDER_STATE = new ItemStackRenderState();
    /** Seed picking the model layer, fixed so the icon holds still across frames. */
    private static final long LAYER_SEED = 0L;

    private ItemParticleIcons() {
    }

    /**
     * Returns the item's particle sprite as an atlas region.
     *
     * @param itemId the item's registry id
     * @return the icon, or null when the item or its model has none
     */
    static CruciblePanelRows.@Nullable ItemIcon of(Identifier itemId) {
        ItemStack stack = BuiltInRegistries.ITEM.getValue(itemId).getDefaultInstance();
        if (stack.isEmpty()) {
            return null;
        }
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(
                RENDER_STATE, stack, ItemDisplayContext.GUI, null, null, 0);
        Material.Baked material = RENDER_STATE.pickParticleMaterial(RandomSource.create(LAYER_SEED));
        if (material == null) {
            return null;
        }
        return new CruciblePanelRows.ItemIcon(material.sprite().atlasLocation(),
                GooSubmitter.spriteUv(material.sprite()));
    }
}
