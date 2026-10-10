package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import java.util.List;

/**
 * The ailment overlay on the first-person arm: the player sees the golden
 * haste overlay on its own arm, as watchers see it on its whole model,
 * since the first-person hand draws without the model's layers.
 * haste-stacks-speed-under-the-golden-overlay
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AilmentArmOverlay {

    /** The arm's sideways tilt the first-person hand draws with, as vanilla's hand does. */
    private static final float ARM_TILT = 0.1F;
    private static final boolean NOT_SHEETED = false;
    private static final boolean NO_FOIL = false;
    private static final int NO_OUTLINE = 0;

    private AilmentArmOverlay() {
    }

    /**
     * Draws each ailment the player wears over the arm the hand is drawing.
     *
     * @param event the arm render event
     */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<MobAilments.Worn> worn = MobAilments.CLIENT.ailmentsOf(player.getId(), player.level().getGameTime());
        if (worn.isEmpty()) {
            return;
        }
        PlayerModel model = Minecraft.getInstance().getEntityRenderDispatcher().getPlayerRenderer(player).getModel();
        ModelPart arm = posedArm(model, event.getArm());
        PoseStack poseStack = event.getPoseStack();
        for (MobAilments.Worn ailment : worn) {
            float strength = MobAilments.strength(ailment.ticksLeft() - partialTick);
            event.getSubmitNodeCollector().submitModelPart(arm, poseStack, GooRenderTypes.GOO_AILMENT_OVERLAY_TYPE,
                    event.getPackedLight(), AilmentOverlayLayer.patternCoords(ailment.kind()), null, NOT_SHEETED,
                    NO_FOIL, AilmentOverlayLayer.overlayColor(ailment.kind(), strength), null, NO_OUTLINE);
        }
    }

    /**
     * The arm the hand draws, posed as vanilla's first-person hand poses it.
     *
     * @param model the player's model
     * @param side  which arm
     * @return the posed arm
     */
    private static ModelPart posedArm(PlayerModel model, HumanoidArm side) {
        ModelPart arm = side == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        arm.resetPose();
        arm.visible = true;
        arm.zRot = side == HumanoidArm.RIGHT ? ARM_TILT : -ARM_TILT;
        return arm;
    }
}
