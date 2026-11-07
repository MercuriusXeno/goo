package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Sunbeam's scorch as this client sees it: each frame a ray strikes a block,
 * the spot burns into its caster's {@link ScorchTrail}, and every mark still
 * showing draws as a quad flush on the face it burnt, white-hot where the
 * ray strikes now, cooling through orange to a dark burn mark behind it and
 * fading away. Client-only: it changes no block.
 * decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SunbeamScorch {

    /** Half a mark's quad across, in blocks; wider than the spacing, so the marks of a rake overlap into one streak. */
    private static final float MARK_HALF_WIDTH = 0.28f;
    /** How far a mark stands off its face toward the camera, beneath the depth bias. */
    private static final double OFF_THE_FACE = 0.002;
    /** Each mark's ragged edge turns by this share of its game time of birth, so neighbors differ. */
    private static final float SEED_PER_TICK = 0.618034f;
    /** A corner's place across the quad along one tangent: its near edge and its far edge. */
    private static final int NEAR_EDGE = -1;
    private static final int FAR_EDGE = 1;

    private static final Map<Integer, ScorchTrail> TRAILS = new HashMap<>();

    private SunbeamScorch() {
    }

    /**
     * Burns the spot a caster's ray strikes this frame, where it strikes a block.
     *
     * @param casterId the caster's entity id
     * @param hit      what the ray struck
     * @param now      the game time, in ticks, with the frame's partial tick
     */
    static void burn(int casterId, HitResult hit, double now) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            TRAILS.computeIfAbsent(casterId, id -> new ScorchTrail())
                    .burn(block.getLocation(), block.getDirection(), now);
        }
    }

    /**
     * Draws every mark still showing once the world has drawn, dropping the
     * faded ones first.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAILS.clear();
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        TRAILS.values().forEach(trail -> trail.expire(now));
        TRAILS.values().removeIf(ScorchTrail::isEmpty);
        if (TRAILS.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.SUNBEAM_SCORCH_TYPE);
        PoseStack.Pose pose = event.getPoseStack().last();
        for (ScorchTrail trail : TRAILS.values()) {
            for (ScorchTrail.Mark mark : trail.marks()) {
                emitMark(pose, consumer, mark, camera, now);
            }
        }
        buffers.endBatch(GooRenderTypes.SUNBEAM_SCORCH_TYPE);
    }

    /**
     * Emits one mark's quad flush on its face. Red carries its heat and
     * green its seed; the normal carries each corner's place across the
     * quad, which the fragment shader reads as the disc's radius.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param mark     the mark
     * @param camera   the camera's position
     * @param now      the game time, in ticks
     */
    private static void emitMark(PoseStack.Pose pose, VertexConsumer consumer, ScorchTrail.Mark mark, Vec3 camera,
                                 double now) {
        Direction face = mark.face();
        Vec3 center = mark.point().add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(OFF_THE_FACE))
                .subtract(camera);
        Vec3 across = Vec3.atLowerCornerOf(face.getAxis() == Direction.Axis.Y
                ? Direction.EAST.getUnitVec3i() : Direction.UP.getUnitVec3i());
        Vec3 along = across.cross(Vec3.atLowerCornerOf(face.getUnitVec3i()));
        float heat = (float) mark.heat(now);
        float seed = (float) (mark.bornAt() * SEED_PER_TICK % 1.0);
        corner(pose, consumer, center, across, along, NEAR_EDGE, NEAR_EDGE, heat, seed);
        corner(pose, consumer, center, across, along, FAR_EDGE, NEAR_EDGE, heat, seed);
        corner(pose, consumer, center, across, along, FAR_EDGE, FAR_EDGE, heat, seed);
        corner(pose, consumer, center, across, along, NEAR_EDGE, FAR_EDGE, heat, seed);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, Vec3 across, Vec3 along,
                               int u, int v, float heat, float seed) {
        Vec3 at = center.add(across.scale(u * MARK_HALF_WIDTH)).add(along.scale(v * MARK_HALF_WIDTH));
        consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(heat, seed, 0f, 1f)
                .setNormal(u, v, 0f);
    }
}
