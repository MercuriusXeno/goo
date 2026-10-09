package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ScryPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/**
 * The caster's side of Scry: each radius the server sends grows a sphere of
 * glow light around the player, and every face open to air that the front
 * crosses flashes and then shows through walls while the hold lasts, fading
 * about a second after it lets go, the whole reading as a sonar sweep.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ScrySweep {

    /** The most blocks one sweep keeps; past it the front reveals no more. */
    static final int MOST_BLOCKS = 12_288;
    private static final int GLOW_RGB = 0xFFE628;
    /** The sphere shell's alpha at full strength, low since it adds onto the whole view. */
    private static final int SPHERE_ALPHA = 28;
    /** A settled face's alpha at full strength. */
    private static final int FACE_ALPHA = 46;
    /** How much brighter a face shows the tick the front crosses it, as a share of its settled alpha. */
    private static final float FLASH_GAIN = 3f;
    private static final int OPAQUE = 255;
    private static final int STACKS = 24;
    private static final int SLICES = 48;
    /** How far a face quad stands off its block, so it never sinks into the face it marks. */
    private static final float FACE_LIFT = 0.002f;
    private static final double HALF_HEIGHT = 0.5;

    private static final List<RevealedBlock> revealed = new ArrayList<>();
    private static float radius;
    private static long lastRadiusAt = Long.MIN_VALUE;

    private ScrySweep() {
    }

    /**
     * Handles a radius on the client thread: a sweep after a let-go starts
     * over, and the front reveals the faces it crossed since the last radius.
     *
     * @param payload the radius payload
     * @param context the network context
     */
    public static void onPayload(ScryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                advance(mc.level, center(mc.player, 1f), payload.radius(), mc.level.getGameTime());
            }
        });
    }

    private static void advance(ClientLevel level, Vec3 center, float next, long now) {
        if (now - lastRadiusAt > ScryReveal.RELEASE_GRACE_TICKS || next < radius) {
            revealed.clear();
            radius = 0f;
        }
        for (BlockPos pos : ScryReveal.shell(center, radius, next)) {
            List<Direction> sides = ScryReveal.exposedFaces(at -> level.getBlockState(at).isAir(), pos);
            if (!sides.isEmpty() && revealed.size() < MOST_BLOCKS) {
                // the reveal traces the block's own shape, so slabs, stairs and fences show as they stand
                List<AABB> boxes = level.getBlockState(pos).getShape(level, pos).toAabbs();
                revealed.add(new RevealedBlock(pos, boxes, sides, now));
            }
        }
        radius = next;
        lastRadiusAt = now;
    }

    /**
     * Draws the sphere and the revealed faces once the world has drawn,
     * while the sweep still shows.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || lastRadiusAt == Long.MIN_VALUE) {
            return;
        }
        long now = mc.level.getGameTime();
        float strength = ScryReveal.fade(now - lastRadiusAt);
        if (strength <= 0f) {
            revealed.clear();
            lastRadiusAt = Long.MIN_VALUE;
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        emitSphere(pose, buffers.getBuffer(GooRenderTypes.SCRY_SPHERE_TYPE),
                center(player, partialTick).subtract(camera), radius,
                ARGB.color(Math.round(SPHERE_ALPHA * strength), GLOW_RGB));
        buffers.endBatch(GooRenderTypes.SCRY_SPHERE_TYPE);
        drawRevealed(pose, buffers, camera, now, strength);
    }

    private static void drawRevealed(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                                     long now, float strength) {
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.SCRY_FACES_TYPE);
        for (RevealedBlock block : revealed) {
            float flash = 1f + FLASH_GAIN * ScryReveal.flash(now - block.revealedAt());
            int color = ARGB.color(Math.min(OPAQUE, Math.round(FACE_ALPHA * flash * strength)), GLOW_RGB);
            Vec3 corner = Vec3.atLowerCornerOf(block.pos()).subtract(camera);
            for (AABB box : block.boxes()) {
                for (Direction side : block.sides()) {
                    emitBoxFace(pose, consumer, box.move(corner), side, color);
                }
            }
        }
        buffers.endBatch(GooRenderTypes.SCRY_FACES_TYPE);
    }

    private static Vec3 center(LocalPlayer player, float partialTick) {
        return player.getPosition(partialTick).add(0, player.getBbHeight() * HALF_HEIGHT, 0);
    }

    /**
     * Emits a latitude-longitude sphere of quads about a camera-relative center.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param center   the sphere's center, camera-relative
     * @param r        the radius in blocks
     * @param color    the packed ARGB color
     */
    static void emitSphere(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, float r, int color) {
        for (int stack = 0; stack < STACKS; stack++) {
            float lat0 = Mth.PI * stack / STACKS;
            float lat1 = Mth.PI * (stack + 1) / STACKS;
            for (int slice = 0; slice < SLICES; slice++) {
                float lon0 = Mth.TWO_PI * slice / SLICES;
                float lon1 = Mth.TWO_PI * (slice + 1) / SLICES;
                spherePoint(pose, consumer, center, r, lat0, lon0, color);
                spherePoint(pose, consumer, center, r, lat1, lon0, color);
                spherePoint(pose, consumer, center, r, lat1, lon1, color);
                spherePoint(pose, consumer, center, r, lat0, lon1, color);
            }
        }
    }

    private static void spherePoint(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, float r,
                                    float lat, float lon, int color) {
        float ring = Mth.sin(lat) * r;
        consumer.addVertex(pose, (float) center.x + ring * Mth.cos(lon), (float) center.y + Mth.cos(lat) * r,
                (float) center.z + ring * Mth.sin(lon)).setColor(color);
    }

    /**
     * Emits one side of a box as a quad standing just off it.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the box, camera-relative
     * @param side     the side to draw
     * @param color    the packed ARGB color
     */
    static void emitBoxFace(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side, int color) {
        Direction.Axis axis = side.getAxis();
        double plane = side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.max(axis) + FACE_LIFT : box.min(axis) - FACE_LIFT;
        Direction.Axis first = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis second = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double[][] corners = {{box.min(first), box.min(second)}, {box.max(first), box.min(second)},
                {box.max(first), box.max(second)}, {box.min(first), box.max(second)}};
        for (double[] corner : corners) {
            double[] xyz = new double[Direction.Axis.values().length];
            xyz[axis.ordinal()] = plane;
            xyz[first.ordinal()] = corner[0];
            xyz[second.ordinal()] = corner[1];
            consumer.addVertex(pose, (float) xyz[Direction.Axis.X.ordinal()], (float) xyz[Direction.Axis.Y.ordinal()],
                    (float) xyz[Direction.Axis.Z.ordinal()]).setColor(color);
        }
    }

    /**
     * A block the front revealed: its shape's boxes and the sides of it open to air.
     *
     * @param pos        the block
     * @param boxes      its voxel shape's boxes, block-local
     * @param sides      the sides open to air
     * @param revealedAt the game time the front crossed it
     */
    record RevealedBlock(BlockPos pos, List<AABB> boxes, List<Direction> sides, long revealedAt) {
    }
}
