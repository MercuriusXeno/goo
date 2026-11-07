package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.entity.Meteor;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Meteo's blob marking its target: where the blob landed it lies as a
 * splat of astral goo flat on the ground, a ragged blot with a few
 * droplets flung about it, through the wind-up and the fall, so it paints
 * where the meteor will strike until the strike swallows it (operator
 * rulings 2026-11-06: the blob stays, and a splat, not a floating shape).
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeteorMarkers {

    /** How far below the target's cell center the ground it lies on stands. */
    private static final double TO_THE_GROUND = 0.5;
    /** How far the splat stands off the ground, so it never sinks into it. */
    private static final double LIFT = 0.005;
    /** The splat's radius before its edge breaks, in blocks. */
    private static final double SPLAT_RADIUS = 0.4;
    /** How far its edge breaks in or out, as a share of the radius. */
    private static final double RAGGED = 0.35;
    /** Spreads a random share from [0, 1) to [-1, 1), so the edge breaks in as often as out. */
    private static final double BOTH_WAYS = 2;
    private static final int EDGE_POINTS = 18;
    /** Droplets flung about the splat, their radius, and how far out they land, as shares of the splat's. */
    private static final int DROPLETS = 5;
    private static final double DROPLET_RADIUS = 0.07;
    private static final double DROPLET_REACH_LEAST = 1.2;
    private static final double DROPLET_REACH_SPAN = 0.6;
    private static final int DROPLET_POINTS = 8;
    private static final int SPLAT_ALPHA = 230;
    /** Mixes the target's coordinates into the splat's shape, so each splat is its own. */
    private static final long SEED_MIX = 31L;

    private MeteorMarkers() {
    }

    /**
     * Draws a splat at every meteor's target once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.METEOR_SCORCH_TYPE);
        int color = ARGB.color(SPLAT_ALPHA, ClientGooTypes.color(GooTypes.ASTRAL));
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Meteor meteor) {
                Vec3 target = meteor.syncedTarget();
                emitSplat(event.getPoseStack().last(), consumer, markerAt(target).subtract(camera), target, color);
            }
        }
        buffers.endBatch(GooRenderTypes.METEOR_SCORCH_TYPE);
    }

    /**
     * Emits the splat flat about a point: a ragged blot and its droplets,
     * shaped by the target so a splat keeps its shape frame to frame.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer, position and color quads
     * @param center   where the splat lies, camera-relative
     * @param target   the meteor's target, which seeds the splat's shape
     * @param color    the packed ARGB color
     */
    static void emitSplat(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, Vec3 target, int color) {
        RandomSource random = RandomSource.create(
                Double.doubleToLongBits(target.x) * SEED_MIX + Double.doubleToLongBits(target.z));
        emitBlot(pose, consumer, center, SPLAT_RADIUS, EDGE_POINTS, random, color);
        for (int index = 0; index < DROPLETS; index++) {
            double angle = random.nextDouble() * Math.TAU;
            double reach = SPLAT_RADIUS * (DROPLET_REACH_LEAST + random.nextDouble() * DROPLET_REACH_SPAN);
            Vec3 at = center.add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
            emitBlot(pose, consumer, at, DROPLET_RADIUS, DROPLET_POINTS, random, color);
        }
    }

    /**
     * Emits one flat blot whose edge breaks in and out at random, as quads
     * fanned from its center.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer, position and color quads
     * @param center   the blot's center, camera-relative
     * @param radius   its radius before its edge breaks
     * @param points   the points around its edge
     * @param random   breaks its edge
     * @param color    the packed ARGB color
     */
    private static void emitBlot(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, double radius,
                                 int points, RandomSource random, int color) {
        Vec3[] edge = new Vec3[points];
        for (int index = 0; index < points; index++) {
            double angle = index * Math.TAU / points;
            double reach = radius * (1 + (random.nextDouble() * BOTH_WAYS - 1) * RAGGED);
            edge[index] = center.add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
        }
        for (int index = 0; index < points; index++) {
            Vec3 next = edge[(index + 1) % points];
            vertex(pose, consumer, center, color);
            vertex(pose, consumer, edge[index], color);
            vertex(pose, consumer, next, color);
            vertex(pose, consumer, next, color);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at, int color) {
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(color);
    }

    /**
     * Where the splat lies: on the ground at the bottom of the air cell the
     * blob landed in, just off it.
     *
     * @param target the point the meteor strikes, the landing cell's center
     * @return the splat's center
     */
    static Vec3 markerAt(Vec3 target) {
        return target.subtract(0, TO_THE_GROUND - LIFT, 0);
    }
}
