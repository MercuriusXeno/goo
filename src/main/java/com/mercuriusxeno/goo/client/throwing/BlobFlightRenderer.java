package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ability.ConeGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Collection;

/**
 * Renders blob flights as multi-layer animated projectiles.
 * Core: opaque fluid cuboid with pulsing width.
 * Shell: translucent cuboid for slime effect.
 * Tail: billboarded sprite quads behind the velocity vector.
 * Particles: drip sparks and color-tinted bubbles shed along the trail.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BlobFlightRenderer {

    /**
     * Half-width of the core cuboid (~2.5 pixels).
     */
    private static final float CORE_HW = 0.08f;
    /**
     * Half-width of the shell cuboid (~5 pixels).
     */
    private static final float SHELL_HW = 0.15f;
    /**
     * Shell alpha (translucent).
     */
    private static final int SHELL_ALPHA = 0x60;

    /**
     * Tail quad length behind the blob.
     */
    private static final float TAIL_LENGTH = 0.4f;
    /**
     * Tail quad half-width.
     */
    private static final float TAIL_HW = 0.06f;

    /**
     * Pulse amplitude for core breathing animation.
     */
    private static final float CORE_PULSE_AMP = 0.1f;

    /**
     * Pulse speed multiplier for core breathing animation.
     */
    private static final float CORE_PULSE_SPEED = 0.3f;

    /**
     * Normal direction for negative-facing surfaces.
     */
    private static final float NORMAL_NEG = -1f;


    /**
     * Tail alpha value (semi-transparent).
     */
    private static final int TAIL_ALPHA = 0x80;

    /**
     * Threshold for up-vector selection to avoid parallel cross products.
     */
    private static final double UP_THRESHOLD = 0.9;


    /**
     * Billboard half-width matches the blob core size.
     */
    private static final float BEAM_HW = CORE_HW;
    /**
     * ARGB color for the beam center (white-hot).
     */
    private static final int BEAM_CENTER_COLOR = 0xFFFFFFFF;
    /**
     * ARGB color for the beam edges at the head (saturated glowstone yellow).
     */
    private static final int BEAM_EDGE_COLOR = 0xD0FFD700;
    /**
     * ARGB color for the beam tail (faded glowstone yellow).
     */
    private static final int BEAM_TAIL_COLOR = 0x30FFD700;


    /**
     * Short-range threshold: metal spine starts fully formed below this.
     */
    private static final float SHORT_RANGE_THRESHOLD = 1.5f;
    /**
     * Front spear cone length in blocks.
     */
    private static final float DART_FRONT_LENGTH = 2.5f;
    /**
     * Front spear cone base radius (narrow, needlelike).
     */
    private static final float DART_FRONT_RADIUS = 0.05f;
    /**
     * Rear spear butt length in blocks.
     */
    private static final float DART_REAR_LENGTH = 0.5f;
    /**
     * Rear spear butt base radius.
     */
    private static final float DART_REAR_RADIUS = 0.09f;
    /**
     * Number of triangular faces on dart cones.
     */
    private static final int DART_SIDES = 3;
    /**
     * Morph rate: spine is fully formed at 40% of flight time.
     */
    private static final float MORPH_RATE = 2.5f;
    /**
     * Epsilon for near-zero length detection in beam/direction math.
     */
    private static final double LENGTH_EPSILON = 1e-6;
    /**
     * Fraction along the beam where its midpoint sits.
     */
    private static final float BEAM_MIDPOINT = 0.5f;

    private BlobFlightRenderer() {
    }

    /**
     * Renders all active blob flights after translucent blocks so the shell blends correctly.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Collection<BlobFlightManager.BlobFlight> flights = BlobFlightManager.getActiveFlights();
        if (flights.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        FlightFrame ctx = buildFlightFrame(mc, event.getPoseStack());
        for (BlobFlightManager.BlobFlight flight : flights) {
            renderFlight(ctx, flight);
        }
    }

    /**
     * Captures the per-frame rendering state needed by all flight renders.
     *
     * @param mc        the Minecraft client instance
     * @param poseStack the pose stack for rendering
     * @return the render context for this frame
     */
    private static FlightFrame buildFlightFrame(Minecraft mc, PoseStack poseStack) {
        Camera camera = mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = mc.level.getGameTime() + partialTick;
        return new FlightFrame(poseStack, buffers, camera, gameTime, partialTick);
    }

    /**
     * Renders a single flight: core, shell, tail, and particles. The
     * flight position and velocity are sampled at the current partial
     * tick so the blob interpolates smoothly at render FPS rather than
     * snapping once per 20 Hz client tick.
     *
     * @param ctx    the per-frame render context
     * @param flight the flight to render
     */
    private static void renderFlight(FlightFrame ctx, BlobFlightManager.BlobFlight flight) {
        Vec3 pos = flight.getPosition(ctx.partialTick);
        Vec3 vel = flight.getVelocity(ctx.partialTick);

        if (flight.gooType == GooTypes.GLOW) {
            renderGlowBeam(ctx, flight);
            return;
        }

        translateToFlight(ctx, pos);
        if (flight.gooType == GooTypes.METAL && flight.targetEntityId >= 0) {
            renderMetalSpineLayers(ctx, flight, vel);
        } else {
            renderFlightLayers(ctx, flight.gooType, vel);
        }
        ctx.poseStack.popPose();

        BlobTrailParticles.spawnTrailParticles(pos, vel, flight.gooType, flight);
    }

    /**
     * Pushes pose and translates to the flight's camera-relative position.
     *
     * @param ctx the per-frame render context
     * @param pos the flight's world position
     */
    private static void translateToFlight(FlightFrame ctx, Vec3 pos) {
        Vec3 camPos = ctx.camera.position();
        ctx.poseStack.pushPose();
        ctx.poseStack.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);
    }

    /**
     * Renders core, shell, and tail layers for a single flight.
     *
     * @param ctx  the per-frame render context
     * @param type the goo type
     * @param vel  the velocity vector
     */
    private static void renderFlightLayers(FlightFrame ctx, ResourceKey<GooTypeDefinition> type, Vec3 vel) {
        renderCore(ctx.poseStack, ctx.buffers, type, ctx.gameTime);
        renderShell(ctx.poseStack, ctx.buffers, type);
        renderTail(ctx.poseStack, ctx.buffers, type, vel, ctx.gameTime);
    }

    /**
     * Core: opaque fluid cuboid with sin-pulsing width.
     * Draws solid on the block atlas for fully opaque rendering.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers   the buffer source for rendering
     * @param type      the goo type
     * @param gameTime  the level game time in ticks
     */
    private static void renderCore(PoseStack poseStack, MultiBufferSource buffers,
                                   ResourceKey<GooTypeDefinition> type, float gameTime) {
        float pulse = 1.0f + CORE_PULSE_AMP * Mth.sin(gameTime * CORE_PULSE_SPEED);
        float hw = CORE_HW * pulse;

        GooRenderUtil.UvRect uv = spriteToUv(type);
        VertexConsumer c = buffers.getBuffer(GooSubmitter.solidOnBlockAtlas());
        RenderContext ctx = new RenderContext(poseStack.last(), c, GooSubmitter.fullbrightLight());
        CuboidBounds box = new CuboidBounds(-hw, hw, -hw, hw, -hw, hw);
        ctx.emitBox(box, uv);
    }

    /**
     * Shell: larger translucent cuboid with the goo type's color tint.
     * Gives the blob a slime-like outer glow.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers   the buffer source for rendering
     * @param type      the goo type
     */
    private static void renderShell(PoseStack poseStack, MultiBufferSource buffers,
                                    ResourceKey<GooTypeDefinition> type) {
        int color = ARGB.color(SHELL_ALPHA, ClientGooTypes.color(type));
        GooRenderUtil.UvRect uv = spriteToUv(type);
        VertexConsumer c = buffers.getBuffer(GooSubmitter.renderType());
        RenderContext ctx = new RenderContext(poseStack.last(), c, GooSubmitter.fullbrightLight());
        CuboidBounds box = new CuboidBounds(-SHELL_HW, SHELL_HW, -SHELL_HW, SHELL_HW, -SHELL_HW, SHELL_HW);
        ctx.emitBox(color, box, uv);
    }

    /**
     * Looks up the fluid sprite for a goo type and converts it to a UV rect.
     *
     * @param type the goo type
     * @return the UV rectangle for the fluid sprite
     */
    private static GooRenderUtil.UvRect spriteToUv(ResourceKey<GooTypeDefinition> type) {
        return GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(type));
    }

    /**
     * Tail: two crossing quads extending behind the blob along the velocity vector.
     * Gives the projectile a streaking motion feel.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers   the buffer source for rendering
     * @param type      the goo type
     * @param velocity  the velocity direction vector
     * @param gameTime  the level game time in ticks
     */
    private static void renderTail(PoseStack poseStack, MultiBufferSource buffers,
                                   ResourceKey<GooTypeDefinition> type, Vec3 velocity, float gameTime) {
        GooRenderUtil.UvRect uv = spriteToUv(type);
        int tailColor = ARGB.color(TAIL_ALPHA, ClientGooTypes.color(type));
        VertexConsumer c = buffers.getBuffer(GooSubmitter.renderType());
        TailAxes axes = buildTailAxes(velocity);

        emitCrossingTailQuads(poseStack, c, tailColor, axes, uv);
    }

    /**
     * Emits two perpendicular tail quads inside a pose push/pop scope.
     *
     * @param poseStack the pose stack for rendering
     * @param c         the vertex consumer
     * @param tailColor the ARGB tail color
     * @param axes      the tail coordinate axes
     * @param uv        the UV texture rectangle
     */
    private static void emitCrossingTailQuads(PoseStack poseStack, VertexConsumer c,
                                              int tailColor, TailAxes axes, GooRenderUtil.UvRect uv) {
        poseStack.pushPose();
        PoseStack.Pose pose = poseStack.last();
        emitTailQuad(pose, c, GooSubmitter.fullbrightLight(), tailColor, axes.tailEnd, axes.right, TAIL_HW, uv);
        emitTailQuad(pose, c, GooSubmitter.fullbrightLight(), tailColor, axes.tailEnd, axes.up, TAIL_HW, uv);
        poseStack.popPose();
    }

    /**
     * Builds a local coordinate system from the velocity vector for tail rendering.
     *
     * @param velocity the velocity direction vector
     * @return the computed tail axes (right, up, and tail endpoint)
     */
    private static TailAxes buildTailAxes(Vec3 velocity) {
        Vec3 up = (Math.abs(velocity.y) < UP_THRESHOLD)
                ? new Vec3(0, 1, 0)
                : new Vec3(1, 0, 0);
        Vec3 right = velocity.cross(up).normalize();
        Vec3 realUp = right.cross(velocity).normalize();
        Vec3 tailEnd = velocity.scale(-TAIL_LENGTH);
        return new TailAxes(right, realUp, tailEnd);
    }

    /**
     * Emits a single tail quad stretched from origin to tailEnd, with half-width along the axis.
     *
     * @param pose    the pose matrix entry
     * @param c       the vertex consumer
     * @param light   the packed light value
     * @param color   the ARGB color value
     * @param tailEnd the tail endpoint behind the blob
     * @param axis    the perpendicular axis vector
     * @param hw      the half-width in block coords
     * @param uv      the UV texture rectangle
     */
    private static void emitTailQuad(PoseStack.Pose pose, VertexConsumer c, int light, int color,
                                     Vec3 tailEnd, Vec3 axis, float hw, GooRenderUtil.UvRect uv) {
        TailCorners corners = buildTailCorners(tailEnd, axis, hw);
        Vec3 normal = tailEnd.normalize().cross(axis);
        emitTailFrontFace(pose, c, light, color, corners, uv, normal);
        emitTailBackFace(pose, c, light, color, corners, uv, normal);
    }

    /**
     * Computes the axis and endpoint offsets for a tail quad's four corners.
     *
     * @param tailEnd the tail endpoint behind the blob
     * @param axis    the perpendicular axis vector
     * @param hw      the half-width in block coords
     * @return the precomputed corner offsets
     */
    private static TailCorners buildTailCorners(Vec3 tailEnd, Vec3 axis, float hw) {
        return new TailCorners(
                (float) (axis.x * hw), (float) (axis.y * hw), (float) (axis.z * hw),
                (float) tailEnd.x, (float) tailEnd.y, (float) tailEnd.z);
    }

    /**
     * Emits the front face of a tail quad using precomputed corner offsets.
     *
     * @param pose   the pose matrix entry
     * @param c      the vertex consumer
     * @param light  the packed light value
     * @param color  the ARGB color value
     * @param tc     the precomputed tail corner offsets
     * @param uv     the UV texture rectangle
     * @param normal the face normal vector
     */
    private static void emitTailFrontFace(PoseStack.Pose pose, VertexConsumer c, int light, int color,
                                          TailCorners tc, GooRenderUtil.UvRect uv, Vec3 normal) {
        RenderContext tail = new RenderContext(pose, c, light);
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;
        tail.vertexColored(color, tc.ax, tc.ay, tc.az, uv.u0(), uv.v0(), nx, ny, nz);
        tail.vertexColored(color, -tc.ax, -tc.ay, -tc.az, uv.u1(), uv.v0(), nx, ny, nz);
        tail.vertexColored(color, tc.ex - tc.ax, tc.ey - tc.ay, tc.ez - tc.az, uv.u1(), uv.v1(), nx, ny, nz);
        tail.vertexColored(color, tc.ex + tc.ax, tc.ey + tc.ay, tc.ez + tc.az, uv.u0(), uv.v1(), nx, ny, nz);
    }

    /**
     * Emits the back face (reverse winding) of a tail quad using precomputed corner offsets.
     *
     * @param pose   the pose matrix entry
     * @param c      the vertex consumer
     * @param light  the packed light value
     * @param color  the ARGB color value
     * @param tc     the precomputed tail corner offsets
     * @param uv     the UV texture rectangle
     * @param normal the face normal vector (will be negated for back face)
     */
    private static void emitTailBackFace(PoseStack.Pose pose, VertexConsumer c, int light, int color,
                                         TailCorners tc, GooRenderUtil.UvRect uv, Vec3 normal) {
        RenderContext tail = new RenderContext(pose, c, light);
        float nx = (float) -normal.x;
        float ny = (float) -normal.y;
        float nz = (float) -normal.z;
        tail.vertexColored(color, tc.ex + tc.ax, tc.ey + tc.ay, tc.ez + tc.az, uv.u0(), uv.v1(), nx, ny, nz);
        tail.vertexColored(color, tc.ex - tc.ax, tc.ey - tc.ay, tc.ez - tc.az, uv.u1(), uv.v1(), nx, ny, nz);
        tail.vertexColored(color, -tc.ax, -tc.ay, -tc.az, uv.u1(), uv.v0(), nx, ny, nz);
        tail.vertexColored(color, tc.ax, tc.ay, tc.az, uv.u0(), uv.v0(), nx, ny, nz);
    }

    /**
     * Renders a glow flight as a camera-facing billboard beam with a
     * white-hot center stripe tapering to glowstone yellow edges.
     * Two-phase lifecycle: extend (head advances, tail pinned at origin)
     * then collapse (tail chases head into the target).
     *
     * @param ctx    the per-frame render context
     * @param flight the glow flight
     */
    private static void renderGlowBeam(FlightFrame ctx,
                                       BlobFlightManager.BlobFlight flight) {
        Vec3 start = flight.start;
        Vec3 end = flight.getEnd();
        Vec3 dir = end.subtract(start);
        double totalLen = dir.length();
        if (totalLen < LENGTH_EPSILON) {
            return;
        }

        float smoothTick = flight.ticksElapsed + ctx.partialTick;
        Vec3 headPos = glowHeadPos(start, end, smoothTick, flight.travelTicks);
        Vec3 tailPos = glowTailPos(start, end, smoothTick, flight.travelTicks);

        Vec3 beamVec = headPos.subtract(tailPos);
        double beamLen = beamVec.length();
        if (beamLen < LENGTH_EPSILON) {
            return;
        }

        emitGlowBillboard(ctx, tailPos, beamVec);
        renderGlowHead(ctx, headPos);
    }

    /**
     * Renders the glow blob at the beam's leading edge.
     *
     * @param ctx     the render context
     * @param headPos world-space head position
     */
    private static void renderGlowHead(FlightFrame ctx, Vec3 headPos) {
        translateToFlight(ctx, headPos);
        renderCore(ctx.poseStack, ctx.buffers, GooTypes.GLOW, ctx.gameTime);
        ctx.poseStack.popPose();
    }

    /**
     * Computes the head position along the beam path. Clamps at the
     * target during the collapse phase.
     *
     * @param start      the captured origin
     * @param end        the live target position
     * @param smoothTick interpolated elapsed ticks
     * @param travel     ticks for the extend phase
     * @return world-space head position
     */
    private static Vec3 glowHeadPos(Vec3 start, Vec3 end,
                                    float smoothTick, int travel) {
        float headT = Math.min(1f, smoothTick / travel);
        return start.add(end.subtract(start).scale(headT));
    }

    /**
     * Computes the tail position. Pinned at origin during extend,
     * then advances toward the target during collapse.
     *
     * @param start      the captured origin
     * @param end        the live target position
     * @param smoothTick interpolated elapsed ticks
     * @param travel     ticks for the extend phase
     * @return world-space tail position
     */
    private static Vec3 glowTailPos(Vec3 start, Vec3 end,
                                    float smoothTick, int travel) {
        if (smoothTick <= travel) {
            return start;
        }
        float tailT = Math.min(1f, (smoothTick - travel) / travel);
        return start.add(end.subtract(start).scale(tailT));
    }

    /**
     * Emits a camera-facing billboard quad from tailPos along beamVec.
     * Per-vertex colors: white-hot center, glowstone yellow edges,
     * transparent fade at the tail end.
     *
     * @param ctx     render context
     * @param tailPos world-space tail
     * @param beamVec head minus tail
     */
    private static void emitGlowBillboard(FlightFrame ctx, Vec3 tailPos,
                                          Vec3 beamVec) {
        Vec3 camPos = ctx.camera.position();
        Vec3 lateral = computeGlowLateral(tailPos, beamVec, camPos);
        if (lateral == null) {
            return;
        }

        ctx.poseStack.pushPose();
        ctx.poseStack.translate(
                tailPos.x - camPos.x,
                tailPos.y - camPos.y,
                tailPos.z - camPos.z);

        Vec3 normal = beamVec.cross(lateral).normalize();
        GooRenderUtil.UvRect uv = spriteToUv(GooTypes.GLOW);
        VertexConsumer c = ctx.buffers.getBuffer(
                GooSubmitter.renderType());
        PoseStack.Pose pose = ctx.poseStack.last();
        emitGlowHalves(pose, c, lateral, beamVec, normal, uv);
        ctx.poseStack.popPose();
    }

    /**
     * Computes the camera-perpendicular lateral offset for the billboard,
     * scaled to {@link #BEAM_HW}. Returns null if the beam is edge-on.
     *
     * @param tailPos world-space tail position
     * @param beamVec head minus tail vector
     * @param camPos  camera world position
     * @return the lateral offset, or null if degenerate
     */
    private static @Nullable Vec3 computeGlowLateral(Vec3 tailPos,
                                                     Vec3 beamVec, Vec3 camPos) {
        Vec3 beamMid = tailPos.add(beamVec.scale(BEAM_MIDPOINT));
        Vec3 toCamera = camPos.subtract(beamMid);
        Vec3 lateral = beamVec.cross(toCamera);
        double latLen = lateral.length();
        if (latLen < LENGTH_EPSILON) {
            return null;
        }
        return lateral.scale(BEAM_HW / latLen);
    }

    /**
     * Emits both halves of the glow billboard (front and back faces).
     *
     * @param pose    the pose matrix
     * @param c       the vertex consumer
     * @param lateral the lateral offset vector
     * @param fwd     the forward (tail-to-head) vector
     * @param normal  the face normal
     * @param uv      the fluid sprite UV rectangle
     */
    private static void emitGlowHalves(PoseStack.Pose pose, VertexConsumer c,
                                       Vec3 lateral, Vec3 fwd, Vec3 normal, GooRenderUtil.UvRect uv) {
        float lx = (float) lateral.x;
        float ly = (float) lateral.y;
        float lz = (float) lateral.z;
        float fx = (float) fwd.x;
        float fy = (float) fwd.y;
        float fz = (float) fwd.z;
        float nx = (float) normal.x;
        float ny = (float) normal.y;
        float nz = (float) normal.z;
        emitGlowFace(pose, c, lx, ly, lz, fx, fy, fz, nx, ny, nz, uv);
        emitGlowFace(pose, c, -lx, -ly, -lz, fx, fy, fz, -nx, -ny, -nz, uv);
    }

    /**
     * Emits one face of the glow billboard. Four vertices: two at the
     * tail (v=0) and two at the head (v=1). Lateral edges get the warm
     * yellow edge color; center vertices get white-hot.
     *
     * @param pose the pose matrix
     * @param c    the vertex consumer
     * @param lx   lateral offset X (positive side)
     * @param ly   lateral offset Y
     * @param lz   lateral offset Z
     * @param fx   forward vector X (tail to head)
     * @param fy   forward vector Y
     * @param fz   forward vector Z
     * @param nx   face normal X
     * @param ny   face normal Y
     * @param nz   face normal Z
     * @param uv   the fluid sprite UV rectangle
     */
    private static void emitGlowFace(PoseStack.Pose pose, VertexConsumer c,
                                     float lx, float ly, float lz,
                                     float fx, float fy, float fz,
                                     float nx, float ny, float nz,
                                     GooRenderUtil.UvRect uv) {
        RenderContext beam = new RenderContext(pose, c, GooSubmitter.fullbrightLight());
        // tail-edge (transparent), tail-center (transparent),
        // head-center (white-hot), head-edge (yellow)
        beam.vertexColored(BEAM_TAIL_COLOR,
                lx, ly, lz, uv.u0(), uv.v0(), nx, ny, nz);
        beam.vertexColored(BEAM_TAIL_COLOR,
                0, 0, 0, uv.u1(), uv.v0(), nx, ny, nz);
        beam.vertexColored(BEAM_CENTER_COLOR,
                fx, fy, fz, uv.u1(), uv.v1(), nx, ny, nz);
        beam.vertexColored(BEAM_EDGE_COLOR,
                fx + lx, fy + ly, fz + lz, uv.u0(), uv.v1(), nx, ny, nz);
    }

    /**
     * Renders a metal blob flight that morphs into a dart spine mid-flight.
     * The blob shrinks as the spine grows, fully morphed by the midpoint.
     * For distances under 1.5 blocks, the spine starts fully formed.
     *
     * @param ctx    the per-frame render context
     * @param flight the metal flight
     * @param vel    the velocity vector
     */
    private static void renderMetalSpineLayers(FlightFrame ctx,
                                               BlobFlightManager.BlobFlight flight, Vec3 vel) {
        float progress = Math.min(1f,
                (flight.ticksElapsed + ctx.partialTick) / flight.travelTicks);
        float dist = (float) flight.start.distanceTo(flight.getEnd());
        float morphFrac = dist < SHORT_RANGE_THRESHOLD
                ? 1f : Math.min(1f, progress * MORPH_RATE);

        ResourceKey<GooTypeDefinition> type = flight.gooType;

        if (morphFrac < 1f) {
            float blobScale = 1f - morphFrac;
            ctx.poseStack.pushPose();
            ctx.poseStack.scale(blobScale, blobScale, blobScale);
            renderCore(ctx.poseStack, ctx.buffers, type, ctx.gameTime);
            renderShell(ctx.poseStack, ctx.buffers, type);
            ctx.poseStack.popPose();
        }

        if (morphFrac > 0f) {
            emitMetalSpine(ctx.poseStack, ctx.buffers, type, vel, morphFrac);
        }

        renderTail(ctx.poseStack, ctx.buffers, type, vel, ctx.gameTime);
    }

    /**
     * Emits the metal spine geometry: a long pointy front dart and a
     * stubby rear pyramid, both oriented along the velocity vector.
     * Uses the goo fluid texture for a metallic appearance.
     *
     * @param poseStack the pose stack
     * @param buffers   the buffer source
     * @param type      the goo type (for texture lookup)
     * @param vel       the velocity direction
     * @param morphFrac morph progress [0, 1]
     */
    private static void emitMetalSpine(PoseStack poseStack, MultiBufferSource buffers,
                                       ResourceKey<GooTypeDefinition> type, Vec3 vel, float morphFrac) {
        GooRenderUtil.UvRect uv = spriteToUv(type);
        VertexConsumer c = buffers.getBuffer(
                GooSubmitter.renderType());
        PoseStack.Pose pose = poseStack.last();

        Vec3 dir = vel.normalize();
        float dx = (float) dir.x;
        float dy = (float) dir.y;
        float dz = (float) dir.z;
        float[] basis = ConeGeometry.computeBasis(dx, dy, dz);

        emitDartCone(pose, c, dx, dy, dz,
                DART_FRONT_LENGTH * morphFrac,
                DART_FRONT_RADIUS * morphFrac,
                basis, uv);
        emitDartCone(pose, c, -dx, -dy, -dz,
                DART_REAR_LENGTH * morphFrac,
                DART_REAR_RADIUS * morphFrac,
                basis, uv);
    }

    /**
     * Emits a dart cone from the origin along a direction, fullbright and
     * textured with the goo fluid sprite.
     *
     * @param pose       the pose matrix
     * @param c          the vertex consumer
     * @param dirX       cone direction X
     * @param dirY       cone direction Y
     * @param dirZ       cone direction Z
     * @param length     cone length
     * @param baseRadius cone base radius
     * @param basis      orthonormal basis {perpX,Y,Z, crossX,Y,Z}
     * @param uv         the fluid sprite UV rectangle
     */
    private static void emitDartCone(PoseStack.Pose pose, VertexConsumer c,
                                     float dirX, float dirY, float dirZ,
                                     float length, float baseRadius,
                                     float[] basis, GooRenderUtil.UvRect uv) {
        RenderContext dart = new RenderContext(pose, c, GooSubmitter.fullbrightLight());
        ConeGeometry.Cone cone = new ConeGeometry.Cone(0f, 0f, 0f, dirX, dirY, dirZ, length, baseRadius);
        ConeGeometry.emitCone(dart, cone, basis, DART_SIDES, GooRenderUtil.OPAQUE_WHITE, uv);
    }

    /**
     * Per-frame render state shared across all flight renders.
     *
     * @param poseStack   the pose stack for rendering
     * @param buffers     the buffer source for rendering
     * @param camera      the render camera
     * @param gameTime    the level game time including partial tick
     * @param partialTick the sub-tick interpolation factor for this frame
     */
    private record FlightFrame(
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            Camera camera,
            float gameTime,
            float partialTick
    ) {
    }

    /**
     * Holds the local coordinate axes and endpoint for tail quad emission.
     *
     * @param right   the perpendicular right axis
     * @param up      the perpendicular up axis
     * @param tailEnd the tail endpoint behind the blob
     */
    private record TailAxes(Vec3 right, Vec3 up, Vec3 tailEnd) {
    }

    /**
     * Precomputed axis and endpoint offsets for a tail quad.
     *
     * @param ax the axis X offset scaled by half-width
     * @param ay the axis Y offset scaled by half-width
     * @param az the axis Z offset scaled by half-width
     * @param ex the tail end X coordinate
     * @param ey the tail end Y coordinate
     * @param ez the tail end Z coordinate
     */
    private record TailCorners(float ax, float ay, float az, float ex, float ey, float ez) {
    }

}
