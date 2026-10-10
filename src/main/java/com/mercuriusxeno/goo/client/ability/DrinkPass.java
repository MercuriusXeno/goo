package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
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
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the uploads of every Unmake drink this frame in one render pass on
 * the drink field pipeline, against the level's depth: every stream's proxy
 * boxes as quads wound to show their inner faces, so a camera inside a box
 * still sees it, each stream drawn with its own uniform block, the block
 * atlas and the level's lightmap bound.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
final class DrinkPass {

    private static final String PASS = "Goo unmake drink";
    private static final String STREAMS_BUFFER = "Goo unmake drink streams";
    private static final String PROXIES_BUFFER = "Goo unmake drink proxies";
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";
    private static final String ATLAS_SAMPLER = "Sampler0";
    private static final String LIGHTMAP_SAMPLER = "Sampler2";
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

    private DrinkPass() {
    }

    /**
     * @return whether the device's depth runs 0 to 1 rather than -1 to 1, which the shader's depth write follows
     */
    static boolean depthZeroToOne() {
        return RenderSystem.getDevice().isZZeroToOne();
    }

    /**
     * Draws every stream's upload.
     *
     * @param blocks    the uploads
     * @param modelView the level's model view matrix this frame
     */
    static void draw(List<DrinkUpload.Block> blocks, Matrix4fc modelView) {
        int quads = 0;
        for (DrinkUpload.Block block : blocks) {
            quads += block.proxies().size() * FACES;
        }
        if (quads == 0) {
            return;
        }
        GpuDevice device = RenderSystem.getDevice();
        int stride = strideOf(device);
        GpuBuffer uniforms = device.createBuffer(() -> STREAMS_BUFFER, GpuBuffer.USAGE_UNIFORM,
                packed(blocks, stride));
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(quads * CORNERS
                * DefaultVertexFormat.ENTITY.getVertexSize())) {
            MeshData mesh = proxiesOf(blocks, bytes);
            GpuBuffer vertices = device.createBuffer(() -> PROXIES_BUFFER, GpuBuffer.USAGE_VERTEX,
                    mesh.vertexBuffer());
            drawAll(device, modelView, blocks, uniforms, stride, vertices, quads);
            mesh.close();
            vertices.close();
        }
        uniforms.close();
    }

    private static void drawAll(GpuDevice device, Matrix4fc modelView, List<DrinkUpload.Block> blocks,
                                GpuBuffer uniforms, int stride, GpuBuffer vertices, int quads) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        try (RenderPass pass = openPass(device, modelView)) {
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices.getBuffer(quads * INDICES_PER_QUAD), indices.type());
            drawStreams(pass, blocks, uniforms, stride);
        }
    }

    private static int strideOf(GpuDevice device) {
        int alignment = Math.max(1, device.getUniformOffsetAlignment());
        return Math.ceilDiv(DrinkUpload.BYTES, alignment) * alignment;
    }

    /**
     * @param blocks the uploads
     * @param stride bytes from one block to the next, the device's alignment
     * @return every block's bytes laid end to end at the stride, in a buffer the device can read
     */
    private static ByteBuffer packed(List<DrinkUpload.Block> blocks, int stride) {
        ByteBuffer packed = ByteBuffer.allocateDirect(stride * blocks.size()).order(ByteOrder.nativeOrder());
        for (int index = 0; index < blocks.size(); index++) {
            packed.put(index * stride, blocks.get(index).bytes(), 0, DrinkUpload.BYTES);
        }
        return packed;
    }

    private static RenderPass openPass(GpuDevice device, Matrix4fc modelView) {
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                new Vector3f(), new Matrix4f());
        AbstractTexture atlas = mc.getTextureManager().getTexture(
                mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        RenderPass pass = device.createCommandEncoder().createRenderPass(() -> PASS, main.getColorTextureView(),
                OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty());
        pass.setPipeline(GooRenderTypes.DRINK_FIELD);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform(TRANSFORMS_UNIFORM, transform);
        pass.bindTexture(ATLAS_SAMPLER, atlas.getTextureView(), atlas.getSampler());
        pass.bindTexture(LIGHTMAP_SAMPLER, mc.gameRenderer.levelLightmap(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        return pass;
    }

    private static void drawStreams(RenderPass pass, List<DrinkUpload.Block> blocks, GpuBuffer uniforms, int stride) {
        int firstQuad = 0;
        for (int index = 0; index < blocks.size(); index++) {
            int quads = blocks.get(index).proxies().size() * FACES;
            pass.setUniform(GooRenderTypes.DRINK_REGION_BLOCK, uniforms.slice((long) index * stride,
                    DrinkUpload.BYTES));
            pass.drawIndexed(0, firstQuad * INDICES_PER_QUAD, quads * INDICES_PER_QUAD, 1);
            firstQuad += quads;
        }
    }

    /**
     * @param blocks the uploads
     * @param bytes  where the vertices go
     * @return every proxy box of every block as six inward quads, each vertex naming its proxy in UV1
     */
    private static MeshData proxiesOf(List<DrinkUpload.Block> blocks, ByteBufferBuilder bytes) {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.ENTITY);
        for (DrinkUpload.Block block : blocks) {
            for (int index = 0; index < block.proxies().size(); index++) {
                box(builder, block.proxies().get(index), index);
            }
        }
        return builder.buildOrThrow();
    }

    private static void box(BufferBuilder builder, DrinkUpload.Proxy proxy, int index) {
        for (int[] face : INWARD_FACES) {
            for (int corner : face) {
                Vec3 at = cornerOf(proxy, corner);
                builder.addVertex((float) at.x, (float) at.y, (float) at.z).setColor(GooRenderUtil.OPAQUE_WHITE)
                        .setUv(0, 0)
                        .setUv1(index, 0).setUv2(0, 0).setNormal(0, 1, 0);
            }
        }
    }

    private static Vec3 cornerOf(DrinkUpload.Proxy proxy, int corner) {
        return new Vec3((corner & X_BIT) == 0 ? proxy.low().x : proxy.high().x,
                (corner & Y_BIT) == 0 ? proxy.low().y : proxy.high().y,
                (corner & Z_BIT) == 0 ? proxy.low().z : proxy.high().z);
    }
}
