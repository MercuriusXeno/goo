package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws every burning Flames field this frame in one render pass on the fire
 * field pipeline, against the level's depth: each field's box as quads wound
 * to show their inner faces, so a camera inside the box, as the glove's own
 * camera always is, still sees it, each field drawn with its own uniform
 * block. flames-pixelated-fire-stream
 */
final class FirePass {

    private static final String PASS = "Goo flames";
    private static final String FIELDS_BUFFER = "Goo flames fields";
    private static final String BOXES_BUFFER = "Goo flames boxes";
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";
    private static final int FACES = 6;
    private static final int CORNERS = 4;
    private static final int INDICES_PER_QUAD = 6;
    private static final int X_BIT = 1;
    private static final int Y_BIT = 2;
    private static final int Z_BIT = 4;
    /** Each face's corners, by the bits of their coordinates, wound to face inward. */
    private static final int[][] INWARD_FACES = {
        {2, 6, 4, 0}, {5, 7, 3, 1}, {4, 5, 1, 0}, {3, 7, 6, 2}, {1, 3, 2, 0}, {6, 7, 5, 4},
    };
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);

    private static ByteBuffer kept = ByteBuffer.allocateDirect(0);

    private FirePass() {
    }

    /**
     * Draws every field.
     *
     * @param fields    the burning fields, camera relative
     * @param camera    the camera's world position
     * @param modelView the level's model view matrix this frame
     */
    static void draw(List<FireField> fields, Vec3 camera, Matrix4fc modelView) {
        if (fields.isEmpty()) {
            return;
        }
        GpuDevice device = RenderSystem.getDevice();
        int stride = strideOf(device);
        GpuBuffer uniforms = device.createBuffer(() -> FIELDS_BUFFER, GpuBuffer.USAGE_UNIFORM,
                pack(fields, stride, camera, device.isZZeroToOne()));
        int quads = fields.size() * FACES;
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * CORNERS
                * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            MeshData mesh = boxesOf(fields, bytes);
            GpuBuffer vertices = device.createBuffer(() -> BOXES_BUFFER, GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer());
            drawAll(device, modelView, fields, uniforms, stride, vertices, quads);
            mesh.close();
            vertices.close();
        }
        uniforms.close();
    }

    /**
     * Packs every field's block end to end at the stride, in one direct
     * buffer kept across frames, as DrinkPacking does, so a frame allocates no
     * direct memory of its own.
     *
     * @param fields    the burning fields
     * @param stride    bytes from one block to the next, the device's alignment
     * @param camera    the camera's world position
     * @param zeroToOne whether the device's depth runs 0 to 1
     * @return every field's block, positioned at the buffer's start
     */
    private static ByteBuffer pack(List<FireField> fields, int stride, Vec3 camera, boolean zeroToOne) {
        int size = stride * fields.size();
        if (kept.capacity() < size) {
            kept = ByteBuffer.allocateDirect(size);
        }
        kept.clear().limit(size);
        for (int index = 0; index < fields.size(); index++) {
            kept.position(index * stride);
            fields.get(index).pack(kept, camera, zeroToOne);
        }
        return kept.position(0);
    }

    private static void drawAll(GpuDevice device, Matrix4fc modelView, List<FireField> fields, GpuBuffer uniforms,
                                int stride, GpuBuffer vertices, int quads) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        try (RenderPass pass = openPass(device, modelView)) {
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices.getBuffer(quads * INDICES_PER_QUAD), indices.type());
            for (int index = 0; index < fields.size(); index++) {
                pass.setUniform(GooRenderTypes.FIRE_FIELD_BLOCK, uniforms.slice((long) index * stride,
                        FireField.BYTES));
                pass.drawIndexed(0, index * FACES * INDICES_PER_QUAD, FACES * INDICES_PER_QUAD, 1);
            }
        }
    }

    private static int strideOf(GpuDevice device) {
        int alignment = Math.max(1, device.getUniformOffsetAlignment());
        return Math.ceilDiv(FireField.BYTES, alignment) * alignment;
    }

    private static RenderPass openPass(GpuDevice device, Matrix4fc modelView) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                new Vector3f(), new Matrix4f());
        RenderPass pass = device.createCommandEncoder().createRenderPass(() -> PASS, main.getColorTextureView(),
                OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty());
        pass.setPipeline(GooRenderTypes.FIRE_FIELD);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform(TRANSFORMS_UNIFORM, transform);
        return pass;
    }

    private static MeshData boxesOf(List<FireField> fields, ByteBufferBuilder bytes) {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (FireField field : fields) {
            Vec3[] box = field.box();
            for (int[] face : INWARD_FACES) {
                for (int corner : face) {
                    builder.addVertex((float) ((corner & X_BIT) == 0 ? box[0].x : box[1].x),
                            (float) ((corner & Y_BIT) == 0 ? box[0].y : box[1].y),
                            (float) ((corner & Z_BIT) == 0 ? box[0].z : box[1].z))
                            .setColor(GooRenderUtil.OPAQUE_WHITE);
                }
            }
        }
        return builder.buildOrThrow();
    }
}
