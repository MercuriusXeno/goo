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
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import java.util.List;

/**
 * Sight's eye on the HUD: the shroom effect's icon draws an eye shut while no
 * fungus stands within Fungal Shift's reach of the player, and open when one
 * does, so an open eye says a shift can start from here. The lid lifts and
 * falls through its frames over a quarter second rather than snapping.
 * sight-lengthens-shift-and-outlines-fungus
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SightEyeIcon implements IClientMobEffectExtensions {

    /** How long the lid takes to open or shut fully, in milliseconds. */
    static final float BLINK_MILLIS = 250f;
    private static final String FRAME_PATH = "textures/gui/sight/";
    private static final String FRAME_FORMAT = ".png";
    /** How far open the eye stands, zero shut to one open. */
    private float openness;
    private long lastMillis = Util.getMillis();
    /** Where a HUD effect's icon sits inside its background, as vanilla draws it. */
    private static final int ICON_INSET = 3;
    /** A HUD effect icon's size. */
    private static final int ICON_SIZE = 18;
    /** The eye textures' size. */
    private static final int TEXTURE_SIZE = 16;
    private static final int OPAQUE = 255;
    /** The eye from shut to open, one frame per step of the lid. */
    private static final List<Identifier> FRAMES = List.of(eye("eye_shut"), eye("eye_third"),
            eye("eye_two_thirds"), eye("eye_open"));

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

    private static Identifier eye(String name) {
        return Identifier.fromNamespaceAndPath(Goo.MODID, FRAME_PATH + name + FRAME_FORMAT);
    }

    /**
     * How far open the eye stands after time passes: it moves toward open near
     * a fungus and toward shut away from one, a full sweep taking the blink's length.
     *
     * @param from          how far open it stood, zero to one
     * @param nearFungus    whether a fungus stands within the shift's reach
     * @param elapsedMillis the milliseconds since it was last read
     * @return how far open it stands now, zero to one
     */
    static float stepOpenness(float from, boolean nearFungus, long elapsedMillis) {
        float step = elapsedMillis / BLINK_MILLIS;
        return Mth.clamp(nearFungus ? from + step : from - step, 0f, 1f);
    }

    /**
     * The frame an eye that far open draws.
     *
     * @param open how far open, zero to one
     * @return the frame's texture
     */
    static Identifier frameFor(float open) {
        return FRAMES.get(Math.round(open * (FRAMES.size() - 1)));
    }

    @Override
    public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphicsExtractor graphics, int x, int y,
                                 float z, float alpha) {
        long now = Util.getMillis();
        openness = stepOpenness(openness, FungusNearby.isNear(), now - lastMillis);
        lastMillis = now;
        graphics.blit(RenderPipelines.GUI_TEXTURED, frameFor(openness), x + ICON_INSET, y + ICON_INSET,
                0.0f, 0.0f, ICON_SIZE, ICON_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE,
                ARGB.white(Math.round(alpha * OPAQUE)));
        return true;
    }
}
