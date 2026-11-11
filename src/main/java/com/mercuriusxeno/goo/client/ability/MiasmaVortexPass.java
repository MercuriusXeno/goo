package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.throwing.VortexCone;
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
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws Drain's miasma vortex in one render pass on its own pipeline: the
 * box around the cone as an {@link InwardBox}, inside which
 * {@code miasma_vortex.fsh} marches the cone inside it, stopping at a copy
 * of the world's depth so the miasma lies in front of whatever stands in it.
 * drain-casts-a-filled-tornadic-miasma-vortex-along-the-look
 */
final class MiasmaVortexPass {

    /** The uniform block's bytes: five std140 vec4 rows; must match {@code MiasmaVortex} in miasma_vortex.fsh. */
    static final int BYTES = 80;
    private static final String PASS = "Goo miasma vortex";
    private static final String UNIFORM_BUFFER = "Goo miasma vortex shape";
    private static final String BOX_BUFFER = "Goo miasma vortex box";
    private static final String DEPTH_COPY = "Goo miasma vortex depth";
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";
    /** Blocks the box stands past the cone, so no fragment of it is clipped at a face. */
    private static final double BOX_MARGIN = 0.25;
    private static final int FACES = InwardBox.FACES;
    private static final int CORNERS = InwardBox.CORNERS;
    private static final int INDICES_PER_QUAD = InwardBox.INDICES_PER_QUAD;
    private static final int FLOATS_PER_ROW = 4;
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
    private static final float CHANNEL = 255f;

    /** The block's bytes, kept across frames so a frame allocates no direct memory of its own. */
    private static final ByteBuffer BLOCK = ByteBuffer.allocateDirect(BYTES);

    private static @Nullable TextureTarget depthCopy;

    private MiasmaVortexPass() {
    }

    /**
     * The colors the vortex wears.
     *
     * @param body   the cloud's packed RGB, the goo's wheel color
     * @param streak the streaks' packed RGB, the goo's bright color
     */
    record Colors(int body, int streak) {
    }

    /**
     * Draws the vortex.
     *
     * @param vortex    the vortex, camera relative
     * @param colors    the colors it wears
     * @param modelView the level's model view matrix this frame
     */
    static void draw(VortexCone vortex, Colors colors, Matrix4fc modelView) {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        TextureTarget depth = depthCopySizedTo(main);
        depth.copyDepthFrom(main);
        GpuDevice device = RenderSystem.getDevice();
        BLOCK.clear();
        pack(BLOCK, vortex, colors, device.isZZeroToOne());
        GpuBuffer uniforms = device.createBuffer(() -> UNIFORM_BUFFER, GpuBuffer.USAGE_UNIFORM, BLOCK.flip());
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(FACES * CORNERS
                * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            MeshData mesh = boxOf(vortex.box(BOX_MARGIN), bytes);
            GpuBuffer vertices = device.createBuffer(() -> BOX_BUFFER, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            drawBox(device, main, depth, modelView, uniforms, vertices);
            mesh.close();
            vertices.close();
        }
        uniforms.close();
    }

    private static void drawBox(GpuDevice device, RenderTarget main, TextureTarget depth, Matrix4fc modelView,
                                GpuBuffer uniforms, GpuBuffer vertices) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                new Vector3f(), new Matrix4f());
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> PASS,
                main.getColorTextureView(), OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(GooRenderTypes.MIASMA_VORTEX);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, transform);
            pass.setUniform(GooRenderTypes.MIASMA_VORTEX_BLOCK, uniforms.slice(0, BYTES));
            pass.bindTexture(GooRenderTypes.MIASMA_DEPTH_SAMPLER, depth.getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices.getBuffer(FACES * INDICES_PER_QUAD), indices.type());
            pass.drawIndexed(0, 0, FACES * INDICES_PER_QUAD, 1);
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
     * Packs the vortex's uniform block, five std140 vec4 rows.
     *
     * @param out       where the bytes go, at its position
     * @param vortex    the vortex, camera relative
     * @param colors    the colors it wears
     * @param zeroToOne whether the device's depth runs 0 to 1
     */
    private static void pack(ByteBuffer out, VortexCone vortex, Colors colors, boolean zeroToOne) {
        ByteBuffer rows = out.order(ByteOrder.nativeOrder());
        row(rows, vortex.apex(), vortex.tan());
        row(rows, vortex.axis(), vortex.length());
        row(rows, FireField.sideOf(vortex.axis()), zeroToOne ? 1 : 0);
        row(rows, rgbOf(colors.body()), 0);
        row(rows, rgbOf(colors.streak()), 0);
    }

    private static Vec3 rgbOf(int rgb) {
        return new Vec3(ARGB.red(rgb) / CHANNEL, ARGB.green(rgb) / CHANNEL, ARGB.blue(rgb) / CHANNEL);
    }

    private static void row(ByteBuffer rows, Vec3 xyz, double w) {
        float[] values = {(float) xyz.x, (float) xyz.y, (float) xyz.z, (float) w};
        for (int index = 0; index < FLOATS_PER_ROW; index++) {
            rows.putFloat(values[index]);
        }
    }

    private static MeshData boxOf(AABB box, ByteBufferBuilder bytes) {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        InwardBox.emit(builder, box.getMinPosition(), box.getMaxPosition());
        return builder.buildOrThrow();
    }
}
