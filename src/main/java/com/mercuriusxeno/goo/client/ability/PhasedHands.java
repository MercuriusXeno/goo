package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.xeno.EldritchEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * A phased player's own first-person hands draw as the ghost the world sees:
 * the bare arm through the phase pipeline, faint and grey, and the glove the
 * hand holds translucent under the same fade. The fade holds only while the
 * first-person hands draw, so the glove in a slot or on another body draws as
 * it is.
 * phased-things-draw-as-a-ghostly-image
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PhasedHands {

    /** The arm's sideways tilt the first-person hand draws with, as vanilla's hand does. */
    private static final float ARM_TILT = 0.1F;
    private static final int WHITE = 0xFFFFFFFF;

    private static boolean ghosting;

    private PhasedHands() {
    }

    /**
     * Whether the first-person hands drawing now are a phased player's, so
     * what they hold draws faded.
     *
     * @return true while a phased player's hands draw
     */
    public static boolean ghosting() {
        return ghosting;
    }

    /**
     * Notes whether the hands about to draw are a phased player's.
     *
     * @param event the hand render event
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        ghosting = player != null && EldritchEvents.isOutOfPhase(player);
    }

    /**
     * Ends the fade once the hands have drawn, before the slots draw.
     *
     * @param event the gui render event
     */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        ghosting = false;
    }

    /**
     * Draws a phased player's bare arm through the phase pipeline in place of
     * vanilla's, after every other arm listener has drawn over it.
     *
     * @param event the arm render event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        if (!EldritchEvents.isOutOfPhase(player)) {
            return;
        }
        event.setCanceled(true);
        PlayerModel model = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player).getModel();
        ModelPart arm = event.getArm() == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        arm.resetPose();
        arm.visible = true;
        arm.zRot = event.getArm() == HumanoidArm.RIGHT ? ARM_TILT : -ARM_TILT;
        event.getSubmitNodeCollector().submitModelPart(arm, event.getPoseStack(),
                PhaseLook.bodyType(player.getSkin().body().texturePath()), event.getPackedLight(),
                OverlayTexture.NO_OVERLAY, null, PhaseLook.bodyTint(WHITE), null);
    }
}
