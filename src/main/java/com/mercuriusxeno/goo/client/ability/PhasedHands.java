package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.xeno.EldritchEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A phased player's own first-person hands draw as the ghost the world sees:
 * the bare arm through the phase pipeline, faint and grey, and whatever the
 * hand holds translucent under the same fade, the glove through its own
 * renderer and any other item through ItemStackLayerPhaseMixin. The fade
 * holds only while the first-person hands draw, so an item in a slot or on
 * another body draws as it is.
 * phased-things-draw-as-a-ghostly-image
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PhasedHands {

    /** The arm's sideways tilt the first-person hand draws with, as vanilla's hand does. */
    private static final float ARM_TILT = 0.1F;
    private static final int WHITE = 0xFFFFFFFF;
    /** The tint index a quad no layer tints reads. */
    static final int UNTINTED = -1;

    private static boolean ghosting;

    private PhasedHands() {
    }

    /**
     * Submits a held item's quads translucent under the phase fade, each
     * through the translucent sheet of the atlas its sprite sits on, in the
     * color its tint layer gives it. The glint draws not at all.
     * phased-things-draw-as-a-ghostly-image
     *
     * @param poseStack the pose stack, at the item's model
     * @param collector the render node collector
     * @param quads     the item layer's quads
     * @param tints     the item layer's tint colors
     * @param light     the packed light
     * @param overlay   the packed overlay
     */
    public static void submitFaded(PoseStack poseStack, SubmitNodeCollector collector, List<BakedQuad> quads,
                                   int[] tints, int light, int overlay) {
        Map<RenderType, List<BakedQuad>> bySheet = quads.stream()
                .collect(Collectors.groupingBy(PhasedHands::fadedSheet, LinkedHashMap::new, Collectors.toList()));
        bySheet.forEach((sheet, onSheet) -> collector.submitCustomGeometry(poseStack, sheet, (pose, consumer) -> {
            QuadInstance instance = new QuadInstance();
            instance.setLightCoords(light);
            instance.setOverlayCoords(overlay);
            for (BakedQuad quad : onSheet) {
                BakedQuad.MaterialInfo material = quad.materialInfo();
                instance.setColor(fadedColor(tints, material.isTinted() ? material.tintIndex() : UNTINTED));
                consumer.putBakedQuad(pose, quad, instance);
            }
        }));
    }

    /**
     * The color a held item's quad draws under the phase fade: its tint
     * layer's color, or white for a quad no layer tints, at the player's own
     * phase alpha.
     *
     * @param tints     the item layer's tint colors
     * @param tintIndex the quad's tint index, UNTINTED for none
     * @return the faded color
     */
    static int fadedColor(int[] tints, int tintIndex) {
        int color = tintIndex >= 0 && tintIndex < tints.length ? tints[tintIndex] : WHITE;
        return PhaseLook.ownTint(color);
    }

    // The translucent sheet beside the one the quad's own item render type draws on, blocks' or items'.
    private static RenderType fadedSheet(BakedQuad quad) {
        RenderType own = quad.materialInfo().itemRenderType();
        boolean onBlocks = own == Sheets.cutoutBlockItemSheet() || own == Sheets.translucentBlockItemSheet();
        return onBlocks ? Sheets.translucentBlockItemSheet() : Sheets.translucentItemSheet();
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
     * Ends the fade the moment the first-person hands have drawn, from
     * GameRendererPhasedHandsMixin: the frame renders the gui's slots after
     * the hands, and caches their item icons, so a fade left standing faded
     * every slot while the player was phased and kept it after.
     * phased-things-draw-as-a-ghostly-image
     */
    public static void endHands() {
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
                OverlayTexture.NO_OVERLAY, null, PhaseLook.ownTint(WHITE), null);
    }
}
