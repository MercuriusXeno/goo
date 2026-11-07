package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.BrewEffectIcon;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * Each brew effect's icon on the HUD and in the inventory effect list: the icon of the
 * ability its type grants, drawn at 16px in the middle of the slot vanilla draws an effect
 * icon in. A type granting none draws nothing of its own, so vanilla draws the effect's tile.
 * Shroom's effect is Sight's eye, which draws Sight's icon in the inventory.
 * decision every-brew-effect-draws-its-abilitys-icon
 * decision shroom-hud-eye-stays-and-its-inventory-icon-is-sights
 * decision ability-icons-read-16x16
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BrewEffectIcons {

    /** Where vanilla draws a HUD effect's icon inside its background. */
    static final int HUD_INSET = 3;
    /** How far below the inventory card's top vanilla draws its effect icon; the x arrives inset. */
    static final int INVENTORY_INSET = 7;
    /** An effect icon slot's size, as vanilla draws it. */
    private static final int SLOT_SIZE = 18;
    /** An ability icon's size, source and drawn alike. */
    private static final int ICON_SIZE = 16;
    /** The margin centering an ability icon in the slot. */
    private static final int ICON_MARGIN = (SLOT_SIZE - ICON_SIZE) / 2;
    private static final int OPAQUE = 255;
    private static final float FULL_ALPHA = 1f;

    private BrewEffectIcons() {
    }

    /**
     * Registers each bundled brew effect's icon extension.
     *
     * @param event the client extensions registration event
     */
    @SubscribeEvent
    public static void registerBrewIcons(RegisterClientExtensionsEvent event) {
        extensionsFor(GooTypes.BUNDLED).forEach((type, extension) ->
                event.registerMobEffect(extension, GooMobEffects.BREW_EFFECTS.get(type).get()));
    }

    /**
     * The icon extension of each type's brew effect: Sight's eye for shroom, and the granted
     * ability's icon for every other.
     *
     * @param types the goo types whose brew effects draw
     * @return each type's extension, in the types' order
     */
    static Map<ResourceKey<GooTypeDefinition>, IClientMobEffectExtensions> extensionsFor(
            Collection<ResourceKey<GooTypeDefinition>> types) {
        Map<ResourceKey<GooTypeDefinition>, IClientMobEffectExtensions> extensions = new LinkedHashMap<>();
        for (ResourceKey<GooTypeDefinition> type : types) {
            extensions.put(type, GooTypes.SHROOM.equals(type) ? new SightEyeIcon() : new AbilityIconExtension(type));
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
     * @param y        the card's top edge
     * @return true when an icon drew, false for none so vanilla draws its own
     */
    static boolean drawInventoryIcon(GuiGraphicsExtractor graphics, @Nullable Identifier texture, int x, int y) {
        return drawAbilityIcon(graphics, texture, x, y + INVENTORY_INSET, FULL_ALPHA);
    }

    /**
     * A brew effect drawing its type's granted ability icon on the HUD and in the inventory.
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
            return BrewEffectIcon.of(type);
        }

        @Override
        public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphicsExtractor graphics, int x, int y,
                                     float z, float alpha) {
            return drawAbilityIcon(graphics, iconTexture(), x + HUD_INSET, y + HUD_INSET, alpha);
        }

        @Override
        public boolean renderInventoryIcon(MobEffectInstance instance, AbstractContainerScreen<?> screen,
                                           GuiGraphicsExtractor graphics, int x, int y, int blitOffset) {
            return drawInventoryIcon(graphics, iconTexture(), x, y);
        }
    }
}
