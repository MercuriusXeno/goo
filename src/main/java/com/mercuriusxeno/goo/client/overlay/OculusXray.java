package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.oculus.OculusNodes;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ber.style.OculusStyle;
import com.mercuriusxeno.goo.client.model.OculusModels;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.BlinkAim;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Shows every oculus Blink can snap to through walls while Blink is the
 * glove's selection, the way Sight shows fungus: each eye ghosted in ender
 * green with a purple halo breathing about it, drawn after the translucent
 * world through pipelines that ignore depth. The oculus the blink would
 * snap to this frame glows bright and swells, and the ripple cursor stands
 * beside it. The oculi are found again twice a second.
 * Decision oculus-prism-becomes-a-hovering-eye.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class OculusXray {

    /** Ticks between one look for oculi and the next. */
    static final int RESCAN_TICKS = 10;
    /** The ghost's tint: translucent ender green. */
    private static final int GHOST_TINT = 0x9070F0C0;
    /** The halo's ender purple. */
    private static final int GLOW_RGB = 0xB05CFF;
    /** The halo's alpha at full pulse for an oculus in reach. */
    private static final float HALO_ALPHA = 0.35f;
    /** The halo's alpha at full pulse for the oculus the blink snaps to. */
    private static final float TARGET_HALO_ALPHA = 0.9f;
    /** How far a halo swells past the eye. */
    private static final float HALO_SWELL = 1.3f;
    /** How far the halo of the oculus the blink snaps to swells past the eye. */
    private static final float TARGET_HALO_SWELL = 1.8f;
    private static final float HALO_PULSE_PER_SECOND = 2.2f;
    private static final float MILLIS_PER_SECOND = 1000f;
    private static final float HALF = 0.5f;
    private static final float WHOLE = 1f;
    private static final int OPAQUE = 255;
    private static final long UNSCANNED = Long.MIN_VALUE;

    private static final List<BlockPos> SEEN = new ArrayList<>();
    private static long scannedAt = UNSCANNED;

    private OculusXray() {
    }

    /**
     * Draws the oculi in Blink's reach through walls once the world has drawn,
     * while Blink is selected.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility blink = player == null || mc.level == null ? null : selected(player);
        OptionalDouble reach = blink == null ? OptionalDouble.empty() : TeleportStep.snapRange(blink.behaviors());
        if (reach.isEmpty()) {
            SEEN.clear();
            return;
        }
        rescan(mc.level, player.getEyePosition(), reach.getAsDouble());
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Optional<BlockPos> target = BlinkAim.trip(player, blink, partialTick).flatMap(BlinkLanding::node);
        if (!SEEN.isEmpty()) {
            drawAll(mc, event.getPoseStack(), target);
        }
    }

    /**
     * The glove's selected ability, where one is synced.
     *
     * @param player the local player
     * @return the ability, or null
     */
    private static @Nullable ClientAbility selected(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    /**
     * Finds the oculi in reach again when the last look has gone stale.
     *
     * @param level the client level
     * @param eye   the player's eye
     * @param reach the snap range
     */
    private static void rescan(Level level, Vec3 eye, double reach) {
        long now = level.getGameTime();
        if (scannedAt == UNSCANNED || now < scannedAt || now - scannedAt >= RESCAN_TICKS) {
            SEEN.clear();
            SEEN.addAll(OculusNodes.oculiNear(level, eye, reach));
            scannedAt = now;
        }
    }

    /**
     * Draws each oculus's ghost and halo, the target's brighter and larger.
     *
     * @param mc        the client
     * @param poseStack the pose stack
     * @param target    the oculus the blink snaps to this frame
     */
    private static void drawAll(Minecraft mc, PoseStack poseStack, Optional<BlockPos> target) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Identifier atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        RenderType xray = GooRenderTypes.fungusXray(atlas);
        VertexConsumer ghost = buffers.getBuffer(xray);
        QuadInstance ghostLook = FungusXray.instance(GHOST_TINT);
        for (BlockPos pos : SEEN) {
            drawEye(mc, poseStack, ghost, camera, pos, ghostLook, WHOLE);
        }
        buffers.endBatch(xray);
        RenderType glow = GooRenderTypes.fungusGlow(atlas);
        VertexConsumer halo = buffers.getBuffer(glow);
        float strength = FungusXray.pulse(Util.getMillis() / MILLIS_PER_SECOND, HALO_PULSE_PER_SECOND);
        for (BlockPos pos : SEEN) {
            boolean aimed = target.filter(pos::equals).isPresent();
            QuadInstance look = FungusXray.instance(glowColor(strength, aimed ? TARGET_HALO_ALPHA : HALO_ALPHA));
            drawEye(mc, poseStack, halo, camera, pos, look, aimed ? TARGET_HALO_SWELL : HALO_SWELL);
        }
        buffers.endBatch(glow);
    }

    /**
     * The halo's ender purple at a strength and a peak alpha.
     *
     * @param strength the pulse's strength, zero to one
     * @param alpha    the alpha at full strength, zero to one
     * @return the ARGB color
     */
    static int glowColor(float strength, float alpha) {
        return ARGB.color(Math.round(Math.clamp(strength * alpha, 0f, 1f) * OPAQUE), GLOW_RGB);
    }

    /**
     * Draws an oculus's eye model where its eye hovers, turned toward the
     * camera and swollen about its middle.
     *
     * @param mc        the client
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param pos       the oculus's cell
     * @param instance  the color, light and overlay the quads take
     * @param swell     the scale about the eye's middle
     */
    private static void drawEye(Minecraft mc, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                                BlockPos pos, QuadInstance instance, float swell) {
        BlockState state = mc.level.getBlockState(pos);
        Direction facing = state.hasProperty(PrismBlock.FACING) ? state.getValue(PrismBlock.FACING) : Direction.UP;
        Vec3 eye = Vec3.atLowerCornerOf(pos).add(OculusStyle.eyeInCell(facing));
        float yaw = (float) Math.toDegrees(Math.atan2(camera.x - eye.x, camera.z - eye.z));
        poseStack.pushPose();
        poseStack.translate(eye.x - camera.x, eye.y - camera.y, eye.z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.scale(swell, swell, swell);
        poseStack.translate(-HALF, -HALF, -HALF);
        for (BakedQuad quad : OculusModels.eye().getAll()) {
            consumer.putBakedQuad(poseStack.last(), quad, instance);
        }
        poseStack.popPose();
    }
}
