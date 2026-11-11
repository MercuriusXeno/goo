package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.List;

/**
 * Draws each Void portable hole's mouth across the faces it opens on: an oval
 * opening filling its footprint, black at its heart, ringed in void violet
 * that swirls slowly inward, growing open from nothing and shrinking shut as
 * its time runs out.
 * It is a look of its own, a flat vortex on the face, not the tunnel's oval
 * view through the wall.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HoleMouths {

    /** How far the mouth stands off its face, so it never sinks into it. */
    private static final double FACE_LIFT = 0.004;
    /** Ticks the mouth takes to grow open. */
    private static final float GROW_TICKS = 8F;
    /** Ticks before closing the mouth starts to shrink shut. */
    private static final float SHRINK_TICKS = 20F;
    private static final int RINGS = 6;
    private static final int SEGMENTS = 32;
    /** The radians a swirl band turns each tick at the rim; the heart turns faster. */
    private static final float SPIN = 0.06F;
    /** How many violet bands wind around the mouth. */
    private static final int BANDS = 3;
    /** How far the bands wind inward across the mouth, in radians from rim to heart. */
    private static final float WIND = 2.5F;
    private static final int HEART_RGB = 0x050008;
    private static final int RIM_RGB = 0x8A3CFF;
    private static final int HEART_ALPHA = 245;
    private static final int RIM_ALPHA = 150;
    /** The share of its alpha the outermost edge keeps, so the rim feathers out. */
    private static final float RIM_FEATHER = 0.35F;
    /** How much faster than the rim the heart swirls. */
    private static final float HEART_SPIN = 2F;
    /** The violet a band's darkest stretch keeps, as a share of the rim's. */
    private static final float VIOLET_FLOOR = 0.55F;
    private static final float HALF = 0.5F;
    /** A box's face's distance from its center, as a share of the box's size. */
    private static final double FACE_OFFSET = 0.5;

    private HoleMouths() {
    }

    /**
     * Draws every open mouth once the world's translucent blocks have drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientPortableHoles.mouths().isEmpty()) {
            return;
        }
        float time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.PORTABLE_HOLE_MOUTH_TYPE);
        for (PortableHolesSyncPayload.Mouth mouth : ClientPortableHoles.mouths()) {
            float open = openShare(time - ClientPortableHoles.firstSeen(mouth), mouth.closesAt() - time);
            if (open > 0F) {
                drawMouth(pose, consumer, mouth, camera, open, time);
            }
        }
        buffers.endBatch(GooRenderTypes.PORTABLE_HOLE_MOUTH_TYPE);
    }

    /**
     * How far open a mouth stands: growing from nothing as it opens, and
     * shrinking shut over its last second.
     *
     * @param age  ticks since the client first saw it
     * @param left ticks until it closes
     * @return the share of its full size, in [0, 1]
     */
    static float openShare(float age, float left) {
        float grown = Mth.clamp(age / GROW_TICKS, 0F, 1F);
        float shrunk = Mth.clamp(left / SHRINK_TICKS, 0F, 1F);
        return (float) Mth.smoothstep(grown) * shrunk;
    }

    /**
     * The flat rectangle a mouth opens across: the faces of its footprint's
     * blocks on the side the mouth stands on, the oval of its opening
     * inscribed in it.
     * decision hole-mouth-opens-three-by-three-and-fits-its-room
     *
     * @param footprint the blocks whose faces the mouth covers
     * @param face      the face it opens on
     * @return the rectangle, flat along the face's normal
     */
    static AABB openingOn(List<BlockPos> footprint, Direction face) {
        AABB cells = footprint.stream().map(AABB::new).reduce(AABB::minmax).orElseThrow();
        Direction.Axis normal = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? cells.max(normal)
                : cells.min(normal);
        return new AABB(cells.getMinPosition().with(normal, plane), cells.getMaxPosition().with(normal, plane));
    }

    private static void drawMouth(PoseStack.Pose pose, VertexConsumer consumer, PortableHolesSyncPayload.Mouth mouth,
                                  Vec3 camera, float open, float time) {
        Direction face = mouth.face();
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        AABB opening = openingOn(mouth.footprint(), face);
        Vec3 centre = opening.getCenter().add(normal.scale(FACE_LIFT)).subtract(camera);
        Vec3 across = face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        Vec3 along = normal.cross(across);
        Vec3 acrossRadius = across.scale(halfSpan(opening, across) * open);
        Vec3 alongRadius = along.scale(halfSpan(opening, along) * open);
        for (int ring = 0; ring < RINGS; ring++) {
            float inner = (float) ring / RINGS;
            float outer = (float) (ring + 1) / RINGS;
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float from = Mth.TWO_PI * segment / SEGMENTS;
                float to = Mth.TWO_PI * (segment + 1) / SEGMENTS;
                emit(pose, consumer, centre, acrossRadius, alongRadius, inner, from, colorAt(inner, from, time));
                emit(pose, consumer, centre, acrossRadius, alongRadius, outer, from, colorAt(outer, from, time));
                emit(pose, consumer, centre, acrossRadius, alongRadius, outer, to, colorAt(outer, to, time));
                emit(pose, consumer, centre, acrossRadius, alongRadius, inner, to, colorAt(inner, to, time));
            }
        }
    }

    private static double halfSpan(AABB opening, Vec3 axis) {
        return (Math.abs(axis.x()) * opening.getXsize() + Math.abs(axis.y()) * opening.getYsize()
                + Math.abs(axis.z()) * opening.getZsize()) * FACE_OFFSET;
    }

    /**
     * The mouth's color at a point: black at the heart, darkening violet bands
     * swirling toward it from the rim, the rim itself feathered out.
     *
     * @param out   the point's share of the radius, 0 at the heart
     * @param angle the point's angle around the mouth
     * @param time  the game time, partial tick included
     * @return the packed ARGB color
     */
    static int colorAt(float out, float angle, float time) {
        float swirl = Mth.sin(BANDS * angle + WIND * (1F - out) - time * SPIN * (HEART_SPIN - out));
        float band = HALF + HALF * swirl;
        float violet = out * out * Mth.lerp(band, VIOLET_FLOOR, 1F);
        int rgb = ARGB.srgbLerp(violet, ARGB.opaque(HEART_RGB), ARGB.opaque(RIM_RGB));
        int alpha = Math.round(Mth.lerp(out * out, HEART_ALPHA, RIM_ALPHA) * (out >= 1F ? RIM_FEATHER : 1F));
        return ARGB.color(alpha, rgb);
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, Vec3 centre, Vec3 acrossRadius,
                             Vec3 alongRadius, float out, float angle, int color) {
        Vec3 at = centre.add(acrossRadius.scale(out * Mth.cos(angle))).add(alongRadius.scale(out * Mth.sin(angle)));
        consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z()).setColor(color);
    }
}
