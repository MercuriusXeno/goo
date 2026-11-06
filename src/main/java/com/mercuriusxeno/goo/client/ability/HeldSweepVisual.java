package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.HeldSweepPace;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.HeldSweepPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The caster's side of every held sweep, Scry's, Glitter's and Magnet's:
 * each tick the server sends grows the pulse's sphere around the caster in
 * the casting goo type's color, and the faces open to air draw through walls
 * only in the thin shell at the sphere's rim, brightest at the rim, so the
 * pulse reads as a band sweeping outward; the sweep fades about a second
 * after the hold lets go.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeldSweepVisual {

    /** The most blocks one pulse's shell keeps; past it the front reveals no more. */
    static final int MOST_BLOCKS = 12_288;
    /** The sphere skin's alpha at full strength, low since it adds onto the whole view. */
    private static final int SPHERE_ALPHA = 24;
    /** A face's alpha at the rim, at full strength. */
    private static final int FACE_ALPHA = 150;
    /** How far a face quad stands off its block, so it never sinks into the face it marks. */
    private static final float FACE_LIFT = 0.002f;
    /** A pulse's sphere centers at the caster's body, this far above where the caster stood. */
    private static final double BODY_CENTER = 0.9;

    private static final List<RevealedBlock> REVEALED = new ArrayList<>();
    private static HeldSweepPace pace = new HeldSweepPace(0);
    private static float previousRadius;
    private static float radius;
    private static float pulseStrength = 1f;
    private static Vec3 origin = Vec3.ZERO;
    private static long lastRadiusAt = Long.MIN_VALUE;
    /** The current pulse's color, set by every tick before the sweep draws. */
    private static int tintRgb;

    private HeldSweepVisual() {
    }

    /**
     * Handles a tick of a held sweep on the client thread: a new pulse, or
     * one after a let-go, starts over from its own origin, and the front
     * gathers the faces it crossed since the last tick.
     *
     * @param payload the sweep payload
     * @param context the network context
     */
    public static void onPayload(HeldSweepPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                advance(mc.level, payload, mc.level.getGameTime());
            }
        });
    }

    /** Drops the sweep, as a disconnect does. */
    public static void clear() {
        REVEALED.clear();
        lastRadiusAt = Long.MIN_VALUE;
    }

    private static void advance(ClientLevel level, HeldSweepPayload pulse, long now) {
        Vec3 center = pulse.origin().add(0, BODY_CENTER, 0);
        float next = pulse.radius();
        if (startsOver(now, next, center)) {
            REVEALED.clear();
            radius = 0f;
        }
        origin = center;
        pace = new HeldSweepPace(pulse.range());
        tintRgb = tint(pulse.gooType(), ClientGooTypes::color);
        pulseStrength = pulse.strength();
        for (BlockPos pos : ScryReveal.shell(center, radius, next)) {
            List<Direction> sides = ScryReveal.exposedFaces(at -> level.getBlockState(at).isAir(), pos);
            if (!sides.isEmpty() && REVEALED.size() < MOST_BLOCKS) {
                // the reveal traces the block's own shape, so slabs, stairs and fences show as they stand
                List<AABB> boxes = level.getBlockState(pos).getShape(level, pos).toAabbs();
                REVEALED.add(new RevealedBlock(pos, boxes, sides, Vec3.atCenterOf(pos).distanceTo(center)));
            }
        }
        previousRadius = radius;
        radius = next;
        lastRadiusAt = now;
    }

    /**
     * The color the sphere and the shell draw in: the casting goo type's
     * color, so each sweep reads in its own type's colors and a datapack
     * recoloring the type recolors the sweep.
     *
     * @param gooType   the goo type the pulse names
     * @param typeColor answers a goo type's color
     * @return 0xRRGGBB
     */
    static int tint(ResourceKey<GooTypeDefinition> gooType, ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor) {
        return typeColor.applyAsInt(gooType);
    }

    /**
     * Whether a tick starts the sweep over: the hold was let go, or a new
     * pulse began, its radius back down or its origin moved.
     *
     * @param now    the game time
     * @param next   the radius that arrived
     * @param center the pulse's center
     * @return true when the gathered faces clear for a fresh pulse
     */
    private static boolean startsOver(long now, float next, Vec3 center) {
        return now - lastRadiusAt > ScryReveal.RELEASE_GRACE_TICKS || next < radius || !center.equals(origin);
    }

    /**
     * The radius a frame draws at: last tick's radius carried toward this
     * tick's by the partial tick, so the shell sweeps smoothly between ticks.
     *
     * @param from    last tick's radius
     * @param to      this tick's radius
     * @param partial the partial tick, 0 to 1
     * @return the radius to draw
     */
    static float radiusBetween(float from, float to, float partial) {
        return from + (to - from) * Math.clamp(partial, 0f, 1f);
    }

    /**
     * The radius a frame draws at: swept between the last two radii through
     * the tick the last one arrived, then standing at the last one, so a
     * let-go shell holds still as it fades rather than swinging back toward
     * last tick's radius each time the partial tick wraps.
     *
     * @param from         last tick's radius
     * @param to           this tick's radius
     * @param lastRadiusAt the game time the last radius arrived
     * @param now          the game time
     * @param partial      the partial tick, 0 to 1
     * @return the radius to draw
     */
    static float drawnRadius(float from, float to, long lastRadiusAt, long now, float partial) {
        return now == lastRadiusAt ? radiusBetween(from, to, partial) : to;
    }

    /**
     * Draws the sphere and the shell once the world has drawn, while the
     * sweep still shows.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || lastRadiusAt == Long.MIN_VALUE) {
            return;
        }
        long now = mc.level.getGameTime();
        float strength = ScryReveal.fade(now - lastRadiusAt) * pulseStrength;
        if (strength <= 0f) {
            clear();
            return;
        }
        float drawn = drawnRadius(previousRadius, radius, lastRadiusAt, now,
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        // a face the shell has passed never draws again this pulse
        REVEALED.removeIf(block -> block.distance() <= drawn - pace.shellThickness(drawn));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        ColorSphere.emit(pose, buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE), origin.subtract(camera), drawn,
                ARGB.color(Math.round(SPHERE_ALPHA * strength), tintRgb));
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
        drawShell(pose, buffers, camera, drawn, strength);
    }

    private static void drawShell(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
                                  float drawn, float strength) {
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.SCRY_FACES_TYPE);
        for (RevealedBlock block : REVEALED) {
            float glow = pace.glowAt(block.distance(), drawn);
            if (glow > 0f) {
                int color = ARGB.color(Math.round(FACE_ALPHA * glow * strength), tintRgb);
                Vec3 corner = Vec3.atLowerCornerOf(block.pos()).subtract(camera);
                for (AABB box : block.boxes()) {
                    for (Direction side : block.sides()) {
                        emitBoxFace(pose, consumer, box.move(corner), side, color);
                    }
                }
            }
        }
        buffers.endBatch(GooRenderTypes.SCRY_FACES_TYPE);
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
     * A block the front crossed: its shape's boxes, the sides of it open to
     * air and its center's distance from the pulse's center.
     *
     * @param pos      the block
     * @param boxes    its voxel shape's boxes, block-local
     * @param sides    the sides open to air
     * @param distance its center's distance from the pulse's center
     */
    record RevealedBlock(BlockPos pos, List<AABB> boxes, List<Direction> sides, double distance) {
    }
}
