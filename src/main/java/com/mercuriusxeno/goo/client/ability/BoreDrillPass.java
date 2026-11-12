package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
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
 * Draws Bore's drill in one render pass on its own pipeline, as Drain's
 * vortex draws: the box around the bit as an {@link InwardBox}, inside
 * which {@code bore_drill.fsh} marches each pixel's ray to the bit's grooved
 * surface, stopping at a copy of the world's depth so the blocks it sinks
 * into hide its buried length.
 * decision bore-shows-a-big-drill-from-the-glove
 */
final class BoreDrillPass {

    /** The uniform block's bytes: three std140 vec4 rows; must match {@code BoreDrill} in bore_drill.fsh. */
    static final int BYTES = 48;
    private static final String PASS = "Goo bore drill";
    private static final String UNIFORM_BUFFER = "Goo bore drill shape";
    private static final String BOX_BUFFER = "Goo bore drill box";
    private static final String DEPTH_COPY = "Goo bore drill depth";
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";
    /** Blocks the box stands past the bit, so no fragment of it is clipped at a face. */
    private static final double BOX_MARGIN = 0.1;
    private static final int FACES = InwardBox.FACES;
    private static final int CORNERS = InwardBox.CORNERS;
    private static final int INDICES_PER_QUAD = InwardBox.INDICES_PER_QUAD;
    private static final int FLOATS_PER_ROW = 4;
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);

    /** The block's bytes, kept across frames so a frame allocates no direct memory of its own. */
    private static final ByteBuffer BLOCK = ByteBuffer.allocateDirect(BYTES);

    private static @Nullable TextureTarget depthCopy;

    private BoreDrillPass() {
    }

    /**
     * Draws the bit.
     *
     * @param bit       the bit, camera relative
     * @param modelView the level's model view matrix this frame
     */
    static void draw(ConeSections.Volume bit, Matrix4fc modelView) {
        if (bit.range() <= 0) {
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        TextureTarget depth = depthCopySizedTo(main);
        depth.copyDepthFrom(main);
        GpuDevice device = RenderSystem.getDevice();
        BLOCK.clear();
        pack(BLOCK, bit, device.isZZeroToOne());
        GpuBuffer uniforms = device.createBuffer(() -> UNIFORM_BUFFER, GpuBuffer.USAGE_UNIFORM, BLOCK.flip());
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(FACES * CORNERS
                * DefaultVertexFormat.POSITION_COLOR.getVertexSize())) {
            MeshData mesh = boxOf(boxAround(bit), bytes);
            GpuBuffer vertices = device.createBuffer(() -> BOX_BUFFER, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            drawBox(device, main, depth, modelView, uniforms, vertices);
            mesh.close();
            vertices.close();
        }
        uniforms.close();
    }

    /**
     * The box holding the whole bit: its base disc at the apex and its tip,
     * standing out by the margin.
     *
     * @param bit the bit
     * @return the box
     */
    static AABB boxAround(ConeSections.Volume bit) {
        double radius = bit.radiusAt().applyAsDouble(0);
        Vec3 tip = bit.apex().add(bit.axis().scale(bit.range()));
        return new AABB(bit.apex(), bit.apex()).minmax(new AABB(tip, tip)).inflate(radius + BOX_MARGIN);
    }

    private static void drawBox(GpuDevice device, RenderTarget main, TextureTarget depth, Matrix4fc modelView,
                                GpuBuffer uniforms, GpuBuffer vertices) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                new Vector3f(), new Matrix4f());
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> PASS,
                main.getColorTextureView(), OptionalInt.empty(), main.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(GooRenderTypes.BORE_DRILL);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, transform);
            pass.setUniform(GooRenderTypes.BORE_DRILL_BLOCK, uniforms.slice(0, BYTES));
            pass.bindTexture(GooRenderTypes.BORE_DRILL_DEPTH_SAMPLER, depth.getDepthTextureView(),
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
     * Packs the bit's uniform block, three std140 vec4 rows.
     *
     * @param out       where the bytes go, at its position
     * @param bit       the bit, camera relative
     * @param zeroToOne whether the device's depth runs 0 to 1
     */
    private static void pack(ByteBuffer out, ConeSections.Volume bit, boolean zeroToOne) {
        ByteBuffer rows = out.order(ByteOrder.nativeOrder());
        row(rows, bit.apex(), bit.radiusAt().applyAsDouble(0));
        row(rows, bit.axis(), bit.range());
        row(rows, FireField.sideOf(bit.axis()), zeroToOne ? 1 : 0);
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
