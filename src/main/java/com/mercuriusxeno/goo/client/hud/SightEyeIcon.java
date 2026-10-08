package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.overlay.FungusNearby;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Sight's eye on the HUD: the shroom effect's icon draws an eye shut while no
 * fungus stands within Fungal Shift's reach of the player, and open when one
 * does, so an open eye says a shift can start from here.
 * sight-lengthens-shift-and-outlines-fungus
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SightEyeIcon implements IClientMobEffectExtensions {

    private static final Identifier EYE_OPEN = Identifier.fromNamespaceAndPath(Goo.MODID,
            "textures/gui/sight/eye_open.png");
    private static final Identifier EYE_SHUT = Identifier.fromNamespaceAndPath(Goo.MODID,
            "textures/gui/sight/eye_shut.png");
    /** Where a HUD effect's icon sits inside its background, as vanilla draws it. */
    private static final int ICON_INSET = 3;
    /** A HUD effect icon's size. */
    private static final int ICON_SIZE = 18;
    /** The eye textures' size. */
    private static final int TEXTURE_SIZE = 16;
    private static final int OPAQUE = 255;

    private SightEyeIcon() {
    }

    /**
     * Registers the eye as the shroom effect's HUD icon.
     *
     * @param event the client extensions registration event
     */
    @SubscribeEvent
    public static void registerEye(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new SightEyeIcon(), GooMobEffects.BREW_EFFECTS.get(GooTypes.SHROOM));
    }

    /**
     * The eye a frame draws: open near a fungus, shut away from one.
     *
     * @param nearFungus whether a fungus stands within the shift's reach
     * @return the eye's texture
     */
    static Identifier eyeFor(boolean nearFungus) {
        return nearFungus ? EYE_OPEN : EYE_SHUT;
    }

    @Override
    public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphicsExtractor graphics, int x, int y,
                                 float z, float alpha) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, eyeFor(FungusNearby.isNear()), x + ICON_INSET, y + ICON_INSET,
                0.0f, 0.0f, ICON_SIZE, ICON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE,
                ARGB.white(Math.round(alpha * OPAQUE)));
        return true;
    }
}
