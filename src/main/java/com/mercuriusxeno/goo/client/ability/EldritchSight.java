package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.xeno.EldritchEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Draws the out-of-phase for the eldritch alone: an out-of-phase mob goes
 * undrawn to a plain player, and to an eldritch one it is drawn warped,
 * its body swelling and twisting out of true.
 * eldritch-sight-reveals-the-out-of-phase
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class EldritchSight {

    /** The render data saying how this frame draws the mob. */
    public static final ContextKey<Phase> PHASE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "eldritch_phase"));

    /** How far the warp swells and squeezes the body, as a share of its size. */
    private static final float SWELL = 0.12f;
    /** How far the warp twists the body either way, in degrees. */
    private static final float TWIST_DEGREES = 9f;
    /** The swell's pace, radians per tick. */
    private static final float SWELL_PER_TICK = 0.21f;
    /** The twist's pace, radians per tick, out of step with the swell so the warp never settles. */
    private static final float TWIST_PER_TICK = 0.13f;

    /** How a frame draws a mob. */
    public enum Phase {
        /** In phase: drawn as it is. */
        IN_PHASE,
        /** Out of phase and the viewer plain: undrawn. */
        UNSEEN,
        /** Out of phase and the viewer eldritch: drawn warped. */
        WARPED
    }

    private EldritchSight() {
    }

    /**
     * Stamps how this frame draws the entity onto its render state, read
     * off its out-of-phase flag and the local player's eldritch state.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampPhase(Entity entity, EntityRenderState state) {
        LocalPlayer viewer = Minecraft.getInstance().player;
        state.setRenderData(PHASE, phaseOf(EldritchEvents.isOutOfPhase(entity),
                viewer != null && EldritchEvents.isEldritch(viewer)));
    }

    /**
     * How a mob is drawn to a viewer.
     *
     * @param outOfPhase whether the mob is out of phase
     * @param eldritch   whether the viewer is eldritch
     * @return the phase the frame draws it in
     */
    static Phase phaseOf(boolean outOfPhase, boolean eldritch) {
        if (!outOfPhase) {
            return Phase.IN_PHASE;
        }
        return eldritch ? Phase.WARPED : Phase.UNSEEN;
    }

    /**
     * Skips an unseen mob, and opens the warp around a warped one.
     *
     * @param event the living render event, before the mob draws
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        LivingEntityRenderState state = event.getRenderState();
        Phase phase = state.getRenderDataOrDefault(PHASE, Phase.IN_PHASE);
        if (phase == Phase.UNSEEN) {
            event.setCanceled(true);
        } else if (phase == Phase.WARPED) {
            warp(event.getPoseStack(), state.ageInTicks);
        }
    }

    /**
     * Closes the warp opened around a warped mob.
     *
     * @param event the living render event, after the mob drew
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (event.getRenderState().getRenderDataOrDefault(PHASE, Phase.IN_PHASE) == Phase.WARPED) {
            event.getPoseStack().popPose();
        }
    }

    private static void warp(PoseStack poseStack, float ageInTicks) {
        float swell = SWELL * Mth.sin(ageInTicks * SWELL_PER_TICK);
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(TWIST_DEGREES * Mth.sin(ageInTicks * TWIST_PER_TICK)));
        poseStack.scale(1f + swell, 1f - swell, 1f + swell);
    }
}
