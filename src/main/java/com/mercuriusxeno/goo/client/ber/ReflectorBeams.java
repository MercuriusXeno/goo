package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GlowQueue;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.GlowBeamMesh;
import com.mercuriusxeno.goo.client.ability.GlowCore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.Consumer;

/**
 * The light rails a reflector draws: glow's beam of light ({@link GlowBeamMesh}) along the rail to each reflector it
 * links to, wider and brighter the more light the network carries, and a soft core of light at the reflector's
 * middle. Each beam is drawn once, by the end whose position sorts first. The beams and the core write no depth, so
 * they queue for the shared path, which draws them after the clouds (operator ruling 2026-11-18).
 * decision reflector-rails-carry-the-brightest-light
 * decision reflector-rail-blooms-out-inside-the-prism
 * decision every-glow-beam-breathes-in-brightness
 */
public final class ReflectorBeams {

    /** A beam's width at the dimmest light, as a share of its width at the brightest. */
    static final float DIMMEST_WIDTH = 0.3f;
    private static final float BRIGHTEST = 15f;
    private static final float SCROLL_PER_TICK = 0.2f;
    private static final double HALF = 0.5;
    private static final GlowQueue QUEUED = new GlowQueue();

    private ReflectorBeams() {
    }

    /**
     * Queues the beams to the reflectors one reflector links to, and its core where it links to any.
     *
     * @param poseStack     the pose at the reflector's cell corner, camera-relative
     * @param center        the reflector's center in the world, which its beams and core sort by
     * @param links         each linked reflector's offset from this one
     * @param light         the network's light, 0 to 15
     * @param animationTime the beam's scroll and breath clock, in ticks
     */
    public static void queue(PoseStack poseStack, Vec3 center, List<Vec3> links, int light, float animationTime) {
        float share = lightShare(light);
        float scroll = Mth.frac(-animationTime * SCROLL_PER_TICK);
        float breath = GlowBeamMesh.breath(animationTime);
        for (Vec3 link : links) {
            if (drawsTheBeam(link)) {
                poseStack.pushPose();
                poseStack.translate(HALF, HALF, HALF);
                poseStack.rotate(GlowBeamMesh.alongBeam(link));
                float length = (float) link.length();
                QUEUED.queue(poseStack.last().copy(), center, GooRenderTypes.glowBeam(BeaconRenderer.BEAM_LOCATION),
                        (pose, buffer) -> emitBeam(pose, buffer, length, share, scroll, breath));
                poseStack.popPose();
            }
        }
        if (!links.isEmpty()) {
            queueCore(poseStack, center, share);
        }
    }

    /**
     * Queues the soft core of light every rail leaves from, at the reflector's middle, scaled and dimmed with the
     * rails.
     * decision diagnose-and-correct-the-sunbeam-prism-depth
     *
     * @param poseStack the pose at the reflector's cell corner, camera-relative
     * @param center    the reflector's center in the world, which the core sorts by
     * @param share     the network's light as a share of the brightest
     */
    private static void queueCore(PoseStack poseStack, Vec3 center, float share) {
        float width = DIMMEST_WIDTH + (1f - DIMMEST_WIDTH) * share;
        poseStack.pushPose();
        poseStack.translate(HALF, HALF, HALF);
        QUEUED.queue(poseStack.last().copy(), center, GooRenderTypes.GLOW_SHELL_TYPE,
                (pose, buffer) -> GlowCore.emit(pose, buffer, width, share));
        poseStack.popPose();
    }

    /**
     * Hands the shared path one draw for each rail and each core queued this frame, each sorted by the camera's
     * distance to its reflector's center, then empties the queue.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where each rail's and core's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        QUEUED.drain(frame, draws);
    }

    /**
     * Emits a rail from one reflector's middle to the linked reflector's middle, each layer fading out past both
     * middles so neither end stops square inside its reflector, and every layer breathing in alpha on top of the
     * light share.
     * decision reflector-rail-blooms-out-inside-the-prism
     * decision every-glow-beam-breathes-in-brightness
     *
     * @param pose   the pose at the drawing reflector's middle, its +y along the rail
     * @param buffer the vertex consumer
     * @param length how far the linked reflector's middle lies, in blocks
     * @param share  the network's light as a share of the brightest
     * @param scroll the texture's scroll offset in [0, 1)
     * @param breath the share of alpha and width {@link GlowBeamMesh#breath} answered, on top of the light share
     */
    static void emitBeam(PoseStack.Pose pose, VertexConsumer buffer, float length, float share, float scroll,
                         float breath) {
        float width = DIMMEST_WIDTH + (1f - DIMMEST_WIDTH) * share;
        for (GlowBeamMesh.Layer layer : GlowBeamMesh.LAYERS) {
            int color = GlowBeamMesh.breathed(layer.color(), share * breath);
            GlowBeamMesh.emitLayerFadingAtBothEnds(pose, buffer, layer.radius() * width * breath, color, scroll, length,
                    layer.depth());
        }
    }

    /**
     * How bright a beam draws at the network's light, as a share of the brightest.
     *
     * @param light the network's light, 0 to 15
     * @return the share, zero to one
     */
    static float lightShare(int light) {
        return Mth.clamp(light / BRIGHTEST, 0f, 1f);
    }

    /**
     * Whether this end draws the beam to a link: the end the link lies ahead of, so each beam draws once.
     *
     * @param link the linked reflector's offset from this one
     * @return true for the end that draws it
     */
    static boolean drawsTheBeam(Vec3 link) {
        if (link.x != 0) {
            return link.x > 0;
        }
        if (link.y != 0) {
            return link.y > 0;
        }
        return link.z > 0;
    }
}
