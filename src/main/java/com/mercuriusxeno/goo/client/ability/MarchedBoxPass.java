package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

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

    private final String label;
    private final RenderPipeline pipeline;
    private final String blockName;
    private final String depthSampler;
    private final int bytes;
    private @Nullable TextureTarget depthCopy;

    /**
     * A marched pass.
     *
     * @param label        the name the pass and its buffers carry in a frame capture
     * @param pipeline     the pipeline whose fragment shader marches the volume
     * @param blockName    the uniform block the shader reads the volume from
     * @param depthSampler the sampler the shader reads the world's depth through
     * @param bytes        the uniform block's size in bytes
     */
    MarchedBoxPass(String label, RenderPipeline pipeline, String blockName, String depthSampler, int bytes) {
        this.label = label;
        this.pipeline = pipeline;
        this.blockName = blockName;
        this.depthSampler = depthSampler;
        this.bytes = bytes;
    }

    /**
     * Whether the device's depth runs 0 to 1, which a uniform block carries
     * so its shader can read the world's depth back into distances.
     *
     * @return true where depth runs 0 to 1
     */
    static boolean depthZeroToOne() {
        return RenderSystem.getDevice().isZZeroToOne();
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
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        TextureTarget depth = depthCopySizedTo(main);
        depth.copyDepthFrom(main);
        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer uniforms = device.createBuffer(() -> label + SHAPE_SUFFIX, GpuBuffer.USAGE_UNIFORM, block);
        try (ByteBufferBuilder vertexBytes = new ByteBufferBuilder(FACES * CORNERS
                * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            MeshData mesh = boxOf(box, vertexBytes);
            GpuBuffer vertices = device.createBuffer(() -> label + BOX_SUFFIX, GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer());
            drawBox(device, main, depth, modelView, new GpuBuffer[] {uniforms, vertices});
            mesh.close();
            vertices.close();
        }
        uniforms.close();
    }

    private void drawBox(GpuDevice device, RenderTarget main, TextureTarget depth, Matrix4fc modelView,
                         GpuBuffer[] uniformsAndVertices) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                new Vector3f(), new Matrix4f());
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> label,
                main.getColorTextureView(), OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, transform);
            pass.setUniform(blockName, uniformsAndVertices[0].slice(0, bytes));
            pass.bindTexture(depthSampler, depth.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setVertexBuffer(0, uniformsAndVertices[1]);
            pass.setIndexBuffer(indices.getBuffer(FACES * INDICES_PER_QUAD), indices.type());
            pass.drawIndexed(0, 0, FACES * INDICES_PER_QUAD, 1);
        }
    }

    private TextureTarget depthCopySizedTo(RenderTarget main) {
        if (depthCopy == null) {
            depthCopy = new TextureTarget(label + DEPTH_SUFFIX, main.width, main.height, true);
        } else if (depthCopy.width != main.width || depthCopy.height != main.height) {
            depthCopy.resize(main.width, main.height);
        }
        return depthCopy;
    }

    private static MeshData boxOf(AABB box, ByteBufferBuilder vertexBytes) {
        BufferBuilder builder = new BufferBuilder(vertexBytes, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        InwardBox.emit(builder, box.getMinPosition(), box.getMaxPosition());
        return builder.buildOrThrow();
    }
}
