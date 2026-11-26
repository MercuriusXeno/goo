package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.DepthCopyTarget;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.OitDrawPhase;
import com.mercuriusxeno.goo.client.StagePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One render pass drawing a box around a volume as an {@link InwardBox},
 * inside which a fragment shader marches the volume, reading a uniform
 * block and a copy of the world's depth: the pass Drain's vortex and Bore's
 * vortex both draw through, each with its own pipeline, block and depth copy.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 * decision bore-shows-a-big-drill-from-the-glove
 */
final class MarchedBoxPass {

    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";
    /** What the pass's uniform buffer, vertex buffer and depth copy add to its label in a frame capture. */
    private static final String SHAPE_SUFFIX = " shape";
    private static final String BOX_SUFFIX = " box";
    private static final String DEPTH_SUFFIX = " depth";
    private static final int FACES = InwardBox.FACES;
    private static final int CORNERS = InwardBox.CORNERS;
    private static final int INDICES_PER_QUAD = InwardBox.INDICES_PER_QUAD;
    private static final int FLOATS_PER_ROW = 4;
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);

    /** How long after a draw in an OIT stage the pass keeps copying the depth before the OIT phase, in nanoseconds. */
    private static final long WANTED_NANOS = 250_000_000L;
    /** Every marched pass, each of which may copy the depth before the OIT phase. */
    private static final List<MarchedBoxPass> PASSES = new CopyOnWriteArrayList<>();

    private final String label;
    private final RenderPipeline pipeline;
    private final OitPipelineSet oit;
    private final String blockName;
    private final String depthSampler;
    private final int bytes;
    private final DepthCopyTarget depthCopy;
    /** Until when a draw in an OIT stage wants the depth copied before the OIT phase. */
    private long wantedUntil;
    /** Whether the depth was copied before this level pass's OIT phase. */
    private boolean copiedBeforeTheOitPhase;

    /**
     * A marched pass.
     *
     * @param label        the name the pass and its buffers carry in a frame capture
     * @param pipeline     the pipeline whose fragment shader marches the volume
     * @param oit          the pipeline's OIT set, which it draws through in an OIT stage
     * @param blockName    the uniform block the shader reads the volume from
     * @param depthSampler the sampler the shader reads the world's depth through
     * @param bytes        the uniform block's size in bytes
     */
    MarchedBoxPass(String label, RenderPipeline pipeline, OitPipelineSet oit, String blockName, String depthSampler,
                   int bytes) {
        this.label = label;
        this.pipeline = pipeline;
        this.oit = oit;
        this.blockName = blockName;
        this.depthSampler = depthSampler;
        this.bytes = bytes;
        this.depthCopy = new DepthCopyTarget(label + DEPTH_SUFFIX);
        PASSES.add(this);
    }

    /**
     * Copies the level's depth for each marched pass an OIT stage drew lately, before the level pass's OIT phase
     * opens its passes, where 26.3 refuses a copy, the opaque depth whole by then.
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    static void copyDepthBeforeTheOitPhase() {
        long now = System.nanoTime();
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        for (MarchedBoxPass pass : PASSES) {
            pass.copiedBeforeTheOitPhase = now < pass.wantedUntil;
            if (pass.copiedBeforeTheOitPhase) {
                pass.depthCopy.matching(main).copyDepthFrom(main);
            }
        }
    }

    /**
     * Whether the device's depth runs 0 to 1, which a uniform block carries
     * so its shader can read the world's depth back into distances.
     *
     * @return true where depth runs 0 to 1
     */
    static boolean depthZeroToOne() {
        return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
    }

    /**
     * Writes one std140 vec4 row of a uniform block.
     *
     * @param rows where the row goes, at its position
     * @param xyz  the row's first three floats
     * @param w    its fourth
     */
    static void row(ByteBuffer rows, Vec3 xyz, double w) {
        float[] values = {(float) xyz.x, (float) xyz.y, (float) xyz.z, (float) w};
        for (int index = 0; index < FLOATS_PER_ROW; index++) {
            rows.putFloat(values[index]);
        }
    }

    /**
     * Draws the box, its shader reading the block and a fresh copy of the
     * world's depth.
     *
     * @param box       the box around the volume, camera relative
     * @param block     the uniform block's bytes, flipped for reading
     * @param modelView the level's model view matrix this frame
     */
    void draw(AABB box, ByteBuffer block, Matrix4fc modelView) {
        OitDrawPhase phase = OitDrawPhase.current();
        RenderPipeline drawn = OitDrawPhase.pipelineFor(phase, pipeline, oit);
        if (drawn == null) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        TextureTarget depth = depthCopy.matching(main);
        if (phase.stage() == null) {
            depth.copyDepthFrom(main);
        } else {
            // Inside an OIT stage's open pass the copy waits for the next phase's start, taken before it opens.
            wantedUntil = System.nanoTime() + WANTED_NANOS;
            if (!copiedBeforeTheOitPhase) {
                return;
            }
            ImmediateBuffers.noteOitIndexCount(PrimitiveTopology.QUADS, FACES * INDICES_PER_QUAD);
        }
        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer uniforms = device.createBuffer(() -> label + SHAPE_SUFFIX, GpuBuffer.USAGE_UNIFORM, block);
        try (ByteBufferBuilder vertexBytes = new ByteBufferBuilder(FACES * CORNERS
                * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            MeshData mesh = boxOf(box, vertexBytes);
            GpuBuffer vertices = device.createBuffer(() -> label + BOX_SUFFIX, GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer());
            drawBox(drawn, depth, modelView, new GpuBuffer[] {uniforms, vertices});
            mesh.close();
            vertices.close();
        }
        uniforms.close();
    }

    // In an OIT stage the stage's own pass stands for the main target, so the box draws there through its phase's
    // pipeline.
    private void drawBox(RenderPipeline drawn, TextureTarget depth, Matrix4fc modelView,
                         GpuBuffer[] uniformsAndVertices) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                new Vector3f(), new Matrix4f());
        try (StagePass stage = StagePass.onMain(() -> label)) {
            RenderPass pass = stage.pass();
            pass.setPipeline(RenderSystem.getCompiledPipeline(drawn));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, transform);
            pass.setUniform(blockName, uniformsAndVertices[0].slice(0, bytes));
            pass.setUniform(depthSampler, depth.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setVertexBuffer(0, uniformsAndVertices[1].slice());
            pass.setIndexBuffer(indices.getBuffer(FACES * INDICES_PER_QUAD), indices.type());
            pass.drawIndexed(FACES * INDICES_PER_QUAD, 1, 0, 0, 0);
        }
    }

    private static MeshData boxOf(AABB box, ByteBufferBuilder vertexBytes) {
        BufferBuilder builder = new BufferBuilder(vertexBytes, PrimitiveTopology.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        InwardBox.emit(builder, box.getMinPosition(), box.getMaxPosition());
        return builder.buildOrThrow();
    }
}
