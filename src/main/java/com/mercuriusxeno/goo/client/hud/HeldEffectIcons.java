package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.HeldEffectIcon;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Each held effect's icon on the HUD and in the inventory effect list: the icon of the
 * ability its type grants, drawn at 16px in the middle of the slot vanilla draws an effect
 * icon in. A type granting none draws nothing of its own, so vanilla draws the effect's tile.
 * decision every-brew-effect-draws-its-abilitys-icon
 * decision ability-icons-read-16x16
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeldEffectIcons {

    /** An effect icon slot's size, as vanilla draws it. */
    private static final int SLOT_SIZE = 18;
    /** An ability icon's size, source and drawn alike. */
    private static final int ICON_SIZE = 16;
    /** The margin centering an ability icon in the slot. */
    private static final int ICON_MARGIN = (SLOT_SIZE - ICON_SIZE) / 2;
    private static final int OPAQUE = 255;
    private static final float FULL_ALPHA = 1f;

    private HeldEffectIcons() {
    }

    /**
     * Registers each bundled held effect's icon extension.
     *
     * @param event the client extensions registration event
     */
    @SubscribeEvent
    public static void registerHeldEffectIcons(RegisterClientExtensionsEvent event) {
        extensionsFor(GooTypes.BUNDLED).forEach((type, extension) ->
                event.registerMobEffect(extension, GooMobEffects.HELD_EFFECTS.get(type).get()));
    }

    /**
     * The icon extension of each type's held effect: the granted ability's icon.
     *
     * @param types the goo types whose held effects draw
     * @return each type's extension, in the types' order
     */
    static Map<ResourceKey<GooTypeDefinition>, IClientMobEffectExtensions> extensionsFor(
            Collection<ResourceKey<GooTypeDefinition>> types) {
        Map<ResourceKey<GooTypeDefinition>, IClientMobEffectExtensions> extensions = new LinkedHashMap<>();
        for (ResourceKey<GooTypeDefinition> type : types) {
            extensions.put(type, new AbilityIconExtension(type));
        }
        return extensions;
    }

    /**
     * Draws an ability icon at 16px in the middle of an effect icon slot.
     *
     * @param graphics the graphics to draw into
     * @param texture  the icon texture, null for none
     * @param slotX    the slot's left edge
     * @param slotY    the slot's top edge
     * @param alpha    the opacity, zero to one
     * @return true when an icon drew, false for none so vanilla draws its own
     */
    static boolean drawAbilityIcon(GuiGraphicsExtractor graphics, @Nullable Identifier texture, int slotX, int slotY,
                                   float alpha) {
        if (texture == null) {
            return false;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, slotX + ICON_MARGIN, slotY + ICON_MARGIN,
                0.0f, 0.0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE,
                ARGB.white(Math.round(alpha * OPAQUE)));
        return true;
    }

    /**
     * Draws an ability icon in the inventory effect card's icon slot, at full opacity.
     *
     * @param graphics the graphics to draw into
     * @param texture  the icon texture, null for none
     * @param x        the slot's left edge, as the inventory hands it
     * @param y        the slot's top edge, as the inventory hands it
     * @return true when an icon drew, false for none so vanilla draws its own
     */
    static boolean drawInventoryIcon(GuiGraphicsExtractor graphics, @Nullable Identifier texture, int x, int y) {
        return drawAbilityIcon(graphics, texture, x, y, FULL_ALPHA);
    }

    /**
     * A held effect drawing its type's granted ability icon on the HUD and in the inventory.
     */
    static final class AbilityIconExtension implements IClientMobEffectExtensions {

        private final ResourceKey<GooTypeDefinition> type;

        AbilityIconExtension(ResourceKey<GooTypeDefinition> type) {
            this.type = type;
        }

        /**
         * The texture the effect draws, the granted ability's icon.
         *
         * @return the texture, or null for a type granting none
         */
        @Nullable Identifier iconTexture() {
            return HeldEffectIcon.of(type);
        }

        // Both hooks hand the icon slot itself, vanilla's inset already taken, and the HUD's fade as a color.
        @Override
        public boolean extractHudIcon(MobEffectInstance instance, Hud hud, GuiGraphicsExtractor graphics, int x, int y,
                                      int width, int height, int color) {
            return drawAbilityIcon(graphics, iconTexture(), x, y, ARGB.alphaFloat(color));
        }

        @Override
        public boolean extractInventoryIcon(MobEffectInstance instance, AbstractContainerScreen<?> screen,
                                            GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                            int color) {
            return drawInventoryIcon(graphics, iconTexture(), x, y);
        }
    }
}
