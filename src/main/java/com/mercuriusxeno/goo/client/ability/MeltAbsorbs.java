package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.network.MeltAbsorbPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Melt's blob sinking into the stone it melts: it flattens onto the struck
 * face and sinks through it, shrinking to nothing over the same run the
 * stone takes to mingle into the lava, so the two read as one transition.
 * melt-blob-absorbs-into-lava
 * <p>
 * The operator's ruling (idea melt-rulings): the blob sinks in while the
 * stone mingles into lava, no burnout.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeltAbsorbs {

    /** Ticks the sinking takes, the stone's mingle into lava. */
    static final float SINK_TICKS = BlockTransforms.DURATION_TICKS;
    /** Blocks past the face the blob's middle has sunk by the end. */
    static final float SINK_DEPTH = 0.3f;
    /** How flat the blob squashes along the face's normal once it has met the face. */
    static final float FLATTENED = 0.4f;
    /** How wide the blob spreads across the face as it flattens. */
    static final float SPREAD = 1.4f;
    /** The share of the run the blob takes to flatten onto the face. */
    static final float FLATTEN_SHARE = 0.25f;
    private static final double HALF = 0.5;

    private static final List<Sinking> SINKING = new ArrayList<>();

    private MeltAbsorbs() {
    }

    /**
     * One blob sinking into a face.
     *
     * @param gooType the goo type
     * @param face    the struck face's middle
     * @param normal  the face
     * @param start   the game tick it landed
     */
    record Sinking(ResourceKey<GooTypeDefinition> gooType, Vec3 face, Direction normal, long start) {
    }

    /**
     * Starts a blob sinking where a Melt landed.
     *
     * @param payload the landing
     * @param context the network context
     */
    public static void onPayload(MeltAbsorbPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Vec3 face = Vec3.atCenterOf(payload.struck())
                    .add(Vec3.atLowerCornerOf(payload.face().getUnitVec3i()).scale(HALF));
            SINKING.add(new Sinking(payload.gooType(), face, payload.face(), mc.level.getGameTime()));
        });
    }

    /**
     * Draws each sinking blob after the translucent blocks, where the flying blobs draw.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SINKING.isEmpty()) {
            return;
        }
        float gameTime = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        SINKING.removeIf(sinking -> share(sinking, gameTime) >= 1f);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack poseStack = event.getPoseStack();
        for (Sinking sinking : SINKING) {
            draw(poseStack, sinking, share(sinking, gameTime), camera, gameTime);
        }
    }

    private static void draw(PoseStack poseStack, Sinking sinking, float share, Vec3 camera, float gameTime) {
        Vec3 out = Vec3.atLowerCornerOf(sinking.normal().getUnitVec3i());
        Vec3 at = sinking.face().subtract(out.scale(depth(share))).subtract(camera);
        float across = across(share);
        float along = along(share);
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        poseStack.scale(scaleOn(out.x, across, along), scaleOn(out.y, across, along), scaleOn(out.z, across, along));
        GooFlightRenderer.renderBlob(poseStack, Minecraft.getInstance().renderBuffers().bufferSource(),
                sinking.gooType(), gameTime, 1f);
        poseStack.popPose();
    }

    /**
     * How far through its sinking a blob stands.
     *
     * @param sinking  the blob
     * @param gameTime the game time with the partial tick
     * @return 0 as it lands, 1 once it has sunk away
     */
    static float share(Sinking sinking, float gameTime) {
        return Math.clamp((gameTime - sinking.start()) / SINK_TICKS, 0f, 1f);
    }

    /**
     * How far past the face the blob's middle has sunk.
     *
     * @param share how far through its sinking
     * @return the depth in blocks
     */
    static float depth(float share) {
        return SINK_DEPTH * share;
    }

    /**
     * The blob's size across the face: spreading as it flattens, then
     * shrinking to nothing as it sinks.
     *
     * @param share how far through its sinking
     * @return the scale across the face
     */
    static float across(float share) {
        return (1f + (SPREAD - 1f) * flattened(share)) * (1f - share);
    }

    /**
     * The blob's size along the face's normal: squashing flat as it meets
     * the face, then shrinking to nothing as it sinks.
     *
     * @param share how far through its sinking
     * @return the scale along the normal
     */
    static float along(float share) {
        return (1f - (1f - FLATTENED) * flattened(share)) * (1f - share);
    }

    private static float flattened(float share) {
        return Math.min(1f, share / FLATTEN_SHARE);
    }

    private static float scaleOn(double normalComponent, float across, float along) {
        return normalComponent == 0 ? across : along;
    }
}
