package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hole.HoleFrame;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Opens the space behind each portable hole's mouth, once the world's opaque
 * blocks have drawn and before the items sunk in it do: the mouth's oval
 * carves the world's depth out to the far plane wherever the oval is the
 * nearest thing seen, then the pit's dark walls and floor draw into what the
 * carve opened. The items drawn after stand in front of the pit and behind the
 * face, so they show through the mouth, and the blocks around it, still
 * holding their own depth, hide them everywhere else, at any footprint.
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HolePits {

    private static final String CARVE_PASS = "Goo portable hole carve";
    private static final String CARVE_BUFFER = "Goo portable hole carve ovals";
    private static final String DEPTH_COPY = "Goo portable hole depth";
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
    /** How far the carved oval stands off its face, so the face it lies on never hides it. */
    private static final double FACE_LIFT = 0.004;
    private static final int SEGMENTS = 32;
    private static final int INDICES_PER_QUAD = 6;
    private static final int CORNERS = 4;
    /** The pit's color at its rim, a deep void violet. */
    private static final int RIM_RGB = 0x1E0A36;
    /** The pit's color at its floor, near black. */
    private static final int FLOOR_RGB = 0x030006;

    private static @Nullable TextureTarget depthCopy;

    private HolePits() {
    }

    /**
     * Carves and fills every drawn mouth's pit.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<PortableHolesSyncPayload.Mouth> drawn = ClientPortableHoles.drawn(time).stream()
                .filter(mouth -> ClientPortableHoles.openShare(mouth, time) > 0F).toList();
        if (drawn.isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        carve(drawn, time, camera, event.getModelViewMatrix());
        fill(mc, event.getPoseStack().last(), drawn, camera);
    }

    /**
     * Pushes the depth to the far plane across each mouth's oval wherever the
     * oval is the nearest thing seen, reading a copy of the world's depth.
     *
     * @param drawn     the mouths drawn this frame
     * @param time      the client's game time, partial tick included
     * @param camera    the camera's world position
     * @param modelView the level's model view matrix this frame
     */
    private static void carve(List<PortableHolesSyncPayload.Mouth> drawn, float time, Vec3 camera,
                              Matrix4fc modelView) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        TextureTarget copy = depthCopySizedTo(main);
        copy.copyDepthFrom(main);
        GpuDevice device = RenderSystem.getDevice();
        int quads = drawn.size() * SEGMENTS;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * CORNERS
                * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            MeshData mesh = ovalsOf(drawn, time, camera, bytes);
            GpuBuffer vertices = device.createBuffer(() -> CARVE_BUFFER, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView),
                    WHITE, new Vector3f(), new Matrix4f());
            try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> CARVE_PASS,
                    main.getColorTextureView(), OptionalInt.empty(), main.getDepthTextureView(),
                    OptionalDouble.empty())) {
                pass.setPipeline(GooRenderTypes.PORTABLE_HOLE_CARVE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform(TRANSFORMS_UNIFORM, transform);
                pass.bindTexture(GooRenderTypes.PORTABLE_HOLE_DEPTH_SAMPLER, copy.getDepthTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                pass.setVertexBuffer(0, vertices);
                pass.setIndexBuffer(indices.getBuffer(quads * INDICES_PER_QUAD), indices.type());
                pass.drawIndexed(0, 0, quads * INDICES_PER_QUAD, 1);
            }
            mesh.close();
            vertices.close();
        }
    }

    private static TextureTarget depthCopySizedTo(RenderTarget main) {
        if (depthCopy == null) {
            depthCopy = new TextureTarget(DEPTH_COPY, main.width, main.height, true);
        } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
            depthCopy.resize(main.width, main.height);
        }
        return depthCopy;
    }

    /**
     * Each mouth's oval, as wide as it stands open, as a fan of quads laid just
     * off its face, camera relative.
     *
     * @param drawn  the mouths drawn this frame
     * @param time   the client's game time, partial tick included
     * @param camera the camera's world position
     * @param bytes  the buffer the quads are written into
     * @return the ovals' quads
     */
    private static MeshData ovalsOf(List<PortableHolesSyncPayload.Mouth> drawn, float time, Vec3 camera,
                                    ByteBufferBuilder bytes) {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int black = ARGB.opaque(FLOOR_RGB);
        for (PortableHolesSyncPayload.Mouth mouth : drawn) {
            Direction face = mouth.face();
            Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
            Vec3 across = HoleFrame.acrossOf(face);
            CarveSpans spans = carvedSpansAt(mouth.footprint(), face, ClientPortableHoles.openShare(mouth, time));
            Vec3 acrossRadius = across.scale(spans.across());
            Vec3 alongRadius = normal.cross(across).scale(spans.along());
            Vec3 centre = HoleFrame.pitOf(mouth.footprint(), face).rim().getCenter().add(normal.scale(FACE_LIFT))
                    .subtract(camera);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                Vec3 from = rimAt(centre, acrossRadius, alongRadius, Mth.TWO_PI * segment / SEGMENTS);
                Vec3 to = rimAt(centre, acrossRadius, alongRadius, Mth.TWO_PI * (segment + 1) / SEGMENTS);
                for (Vec3 corner : new Vec3[] {centre, from, to, centre}) {
                    builder.addVertex((float) corner.x(), (float) corner.y(), (float) corner.z()).setColor(black);
                }
            }
        }
        return builder.buildOrThrow();
    }

    /**
     * How far a fully open mouth's carved oval reaches from its middle each
     * way across its face.
     *
     * @param across its half span along the face's across axis
     * @param along  its half span along the face's along axis
     */
    record CarveSpans(double across, double along) {
    }

    /**
     * The carved oval's half spans for a footprint: the pit rim's, so the
     * carve stays within the pit's walls and every line of sight through it
     * ends on the pit.
     *
     * @param footprint the blocks whose faces the mouth covers
     * @param face      the face it opens on
     * @return the oval's half spans when fully open
     */
    static CarveSpans carveSpansOf(List<BlockPos> footprint, Direction face) {
        HoleFrame frame = HoleFrame.of(footprint, face);
        return new CarveSpans(frame.halfAcross(), frame.halfAlong());
    }

    /**
     * The carved oval's half spans for a mouth standing open by a share: the
     * oval alone shrinks as the mouth closes, sealing over the items behind it.
     * decision hole-seals-over-items-of-fixed-scale
     *
     * @param footprint the blocks whose faces the mouth covers
     * @param face      the face it opens on
     * @param open      the share the mouth stands open, 0 shut to 1 fully open
     * @return the oval's half spans at that share
     */
    static CarveSpans carvedSpansAt(List<BlockPos> footprint, Direction face, float open) {
        CarveSpans full = carveSpansOf(footprint, face);
        return new CarveSpans(full.across() * open, full.along() * open);
    }

    private static Vec3 rimAt(Vec3 centre, Vec3 acrossRadius, Vec3 alongRadius, float angle) {
        return centre.add(acrossRadius.scale(Mth.cos(angle))).add(alongRadius.scale(Mth.sin(angle)));
    }

    /**
     * Draws each pit's walls and floor: the footprint's box sunk the pit's
     * depth behind the face, violet at its rim darkening to its floor.
     *
     * @param mc     the client
     * @param pose   the level's pose this frame
     * @param drawn  the mouths drawn this frame
     * @param camera the camera's world position
     */
    private static void fill(Minecraft mc, PoseStack.Pose pose, List<PortableHolesSyncPayload.Mouth> drawn,
                             Vec3 camera) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.PORTABLE_HOLE_PIT_TYPE);
        for (PortableHolesSyncPayload.Mouth mouth : drawn) {
            HoleFrame.Pit pit = HoleFrame.pitOf(mouth.footprint(), mouth.face());
            emitPit(pose, consumer, pit.rim().move(camera.reverse()), pit.floor().move(camera.reverse()));
        }
        buffers.endBatch(GooRenderTypes.PORTABLE_HOLE_PIT_TYPE);
    }

    /**
     * Emits a pit's four walls, each from the rim's edge to the floor's, and
     * its floor; the rim and the floor are flat rectangles of one shape.
     *
     * @param pose     the level's pose this frame
     * @param consumer the pit's vertex consumer
     * @param rim      the mouth's flat rectangle, camera relative
     * @param floor    the pit floor's flat rectangle, camera relative
     */
    private static void emitPit(PoseStack.Pose pose, VertexConsumer consumer, AABB rim, AABB floor) {
        Vec3[] top = cornersOf(rim);
        Vec3[] bottom = cornersOf(floor);
        int rimColor = ARGB.opaque(RIM_RGB);
        int floorColor = ARGB.opaque(FLOOR_RGB);
        for (int side = 0; side < CORNERS; side++) {
            int next = (side + 1) % CORNERS;
            emit(pose, consumer, top[side], rimColor);
            emit(pose, consumer, top[next], rimColor);
            emit(pose, consumer, bottom[next], floorColor);
            emit(pose, consumer, bottom[side], floorColor);
        }
        for (Vec3 corner : bottom) {
            emit(pose, consumer, corner, floorColor);
        }
    }

    /**
     * A flat rectangle's four corners in order around it.
     *
     * @param flat a rectangle flat along one axis
     * @return its corners
     */
    private static Vec3[] cornersOf(AABB flat) {
        Vec3 low = flat.getMinPosition();
        Vec3 high = flat.getMaxPosition();
        if (flat.getYsize() == 0) {
            return new Vec3[] {low, new Vec3(high.x(), low.y(), low.z()), high, new Vec3(low.x(), low.y(), high.z())};
        }
        if (flat.getXsize() == 0) {
            return new Vec3[] {low, new Vec3(low.x(), high.y(), low.z()), high, new Vec3(low.x(), low.y(), high.z())};
        }
        return new Vec3[] {low, new Vec3(high.x(), low.y(), low.z()), high, new Vec3(low.x(), high.y(), low.z())};
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer consumer, Vec3 at, int color) {
        consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z()).setColor(color);
    }
}
