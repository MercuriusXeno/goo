package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
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
 * Draws the splats of a pass as a screen-space fluid: every splat a
 * camera-facing quad into the fluid target, over the level's depth copied
 * there, each writing its shape's own surface depth and lit color; a
 * bilateral blur across the screen then down it, making the spheres one
 * skin; then one full-screen pass shading the skin and writing it to the
 * main target with its depth.
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
final class DrinkFluidPass {

    private static final String SPLAT_PASS = "Goo unmake drink splat";
    private static final String SHADE_PASS = "Goo unmake drink shade";
    private static final String BLUR_ACROSS_PASS = "Goo unmake drink blur across";
    private static final String BLUR_DOWN_PASS = "Goo unmake drink blur down";
    private static final String SPLATS_BUFFER = "Goo unmake drink splats";
    private static final String FRAME_BUFFER = "Goo unmake drink frame";
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";
    private static final String LIGHTMAP_SAMPLER = "Sampler2";
    private static final String COLOR_SAMPLER = "FluidColor";
    private static final String DEPTH_SAMPLER = "FluidDepth";
    private static final int CORNERS = 4;
    private static final int INDICES_PER_QUAD = 6;
    private static final int FULLSCREEN_VERTICES = 3;
    private static final int FRAME_BYTES = 16;
    /** The frame block the splat and shade passes read, with no blur axis. */
    private static final int STILL = 0;
    /** The frame block the blur across the screen reads. */
    private static final int ACROSS = 1;
    /** The frame block the blur down the screen reads. */
    private static final int DOWN = 2;
    /** Each corner of a quad, as its steps along the quad's right and up. */
    private static final int[][] CORNER_SIGNS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
    /** How much nearer than its bound the camera may come to a shape before the quad about it is left out. */
    private static final double INSIDE = 1.001;
    /** How near to straight up or down a look must be before the quad's right is taken off east rather than up. */
    private static final double STRAIGHT_UP = 1 - 1e-6;
    private static final Vec3[] NO_QUAD = {};
    private static final Vector4f WHITE = new Vector4f(1, 1, 1, 1);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final ByteBuffer FRAME = ByteBuffer.allocateDirect(FRAME_BYTES).order(ByteOrder.nativeOrder());
    /**
     * The vertex bytes, kept across frames and grown only to the largest pass: a direct buffer is freed only when
     * the heap collects its handle, which the renderer's little garbage lets wait for hours, so a fresh one a frame
     * fills the direct memory limit. The device copies the bytes at creation, so reusing them is safe.
     */
    private static ByteBuffer kept = ByteBuffer.allocateDirect(0);

    private DrinkFluidPass() {
    }

    /**
     * @return whether the device's depth runs 0 to 1 rather than -1 to 1, which the shaders' depth follows
     */
    static boolean depthZeroToOne() {
        return RenderSystem.getDevice().isZZeroToOne();
    }

    /**
     * Draws a pass's splats: splatted into the fluid target, blurred across
     * then down into one skin, then shaded onto the main target.
     *
     * @param splats    the splats, camera-relative
     * @param modelView the level's model view matrix this frame
     */
    static void draw(List<DrinkSplats.Splat> splats, Matrix4fc modelView) {
        int quads = pack(splats);
        if (quads == 0) {
            return;
        }
        GpuDevice device = RenderSystem.getDevice();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        RenderTarget fluid = DrinkFluidTarget.fluidSizedTo(main);
        RenderTarget blur = DrinkFluidTarget.blurSizedTo(main);
        fluid.copyDepthFrom(main);
        GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                new Vector3f(), new Matrix4f());
        List<GpuBuffer> frames = List.of(frameOf(device, 0, 0), frameOf(device, 1, 0), frameOf(device, 0, 1));
        GpuBuffer vertices = device.createBuffer(() -> SPLATS_BUFFER, GpuBuffer.USAGE_VERTEX, kept);
        splat(device, fluid, vertices, quads, transform, frames.get(STILL));
        fullScreen(device, new Screen(BLUR_ACROSS_PASS, GooRenderTypes.DRINK_BLUR, blur, fluid), transform,
                frames.get(ACROSS));
        fullScreen(device, new Screen(BLUR_DOWN_PASS, GooRenderTypes.DRINK_BLUR, fluid, blur), transform,
                frames.get(DOWN));
        fullScreen(device, new Screen(SHADE_PASS, GooRenderTypes.DRINK_SHADE, main, fluid), transform,
                frames.get(STILL));
        vertices.close();
        frames.forEach(GpuBuffer::close);
    }

    /**
     * @param device     the device
     * @param acrossStep how far a blur tap steps across the screen, in taps
     * @param downStep   how far a blur tap steps down the screen, in taps
     * @return the frame's uniform block: the depth convention and the blur's axis
     */
    private static GpuBuffer frameOf(GpuDevice device, float acrossStep, float downStep) {
        FRAME.clear();
        putFloats(FRAME, 0, depthZeroToOne() ? 1 : 0, acrossStep, downStep);
        return device.createBuffer(() -> FRAME_BUFFER, GpuBuffer.USAGE_UNIFORM, FRAME);
    }

    private static void splat(GpuDevice device, RenderTarget fluid, GpuBuffer vertices, int quads,
                              GpuBufferSlice transform, GpuBuffer frame) {
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(() -> SPLAT_PASS,
                fluid.getColorTextureView(), OptionalInt.of(0), fluid.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(GooRenderTypes.DRINK_SPLAT);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, transform);
            pass.setUniform(GooRenderTypes.DRINK_FRAME_BLOCK, frame);
            pass.bindTexture(LIGHTMAP_SAMPLER, Minecraft.getInstance().gameRenderer.levelLightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices.getBuffer(quads * INDICES_PER_QUAD), indices.type());
            pass.drawIndexed(0, 0, quads * INDICES_PER_QUAD, 1);
        }
    }

    /**
     * One full-screen pass reading one target's color and depth into another's.
     *
     * @param label    the pass's label in GPU debuggers
     * @param pipeline the pass's pipeline
     * @param into     the target written
     * @param from     the target read
     */
    private record Screen(String label, RenderPipeline pipeline, RenderTarget into, RenderTarget from) {
    }

    private static void fullScreen(GpuDevice device, Screen screen, GpuBufferSlice transform, GpuBuffer frame) {
        try (RenderPass pass = device.createCommandEncoder().createRenderPass(screen::label,
                screen.into().getColorTextureView(), OptionalInt.empty(), screen.into().getDepthTextureView(),
                OptionalDouble.empty())) {
            pass.setPipeline(screen.pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, transform);
            pass.setUniform(GooRenderTypes.DRINK_FRAME_BLOCK, frame);
            pass.bindTexture(COLOR_SAMPLER, screen.from().getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.bindTexture(DEPTH_SAMPLER, screen.from().getDepthTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, FULLSCREEN_VERTICES);
        }
    }

    /**
     * Packs every splat's quad into the kept vertex bytes, leaving out a
     * splat the camera is inside.
     *
     * @param splats the splats, camera-relative
     * @return how many quads were packed
     */
    private static int pack(List<DrinkSplats.Splat> splats) {
        int vertexSize = GooRenderTypes.DRINK_SPLAT_FORMAT.getVertexSize();
        int size = splats.size() * CORNERS * vertexSize;
        if (kept.capacity() < size) {
            kept = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        }
        kept.clear();
        int quads = 0;
        for (DrinkSplats.Splat splat : splats) {
            Vec3[] corners = quadOf(splat);
            if (corners.length == CORNERS) {
                for (int corner = 0; corner < CORNERS; corner++) {
                    putVertex(kept, (quads * CORNERS + corner) * vertexSize, corners[corner], splat);
                }
                quads++;
            }
        }
        kept.limit(quads * CORNERS * vertexSize);
        return quads;
    }

    /**
     * The quad covering a shape on screen: square to the camera's line to
     * the shape's middle, through that middle, as wide as the cone from the
     * camera grazing the shape's bounding sphere is there.
     *
     * @param splat a splat, camera-relative
     * @return the quad's corners, wound about it, or none where the camera is inside its bounding sphere
     */
    static Vec3[] quadOf(DrinkSplats.Splat splat) {
        Vec3 center = splat.center();
        double distance = center.length();
        double bound = splat.bound();
        if (distance <= bound * INSIDE) {
            return NO_QUAD;
        }
        Vec3 toward = center.scale(1 / distance);
        Vec3 right = toward.cross(Math.abs(toward.y) < STRAIGHT_UP ? UP : EAST).normalize();
        Vec3 up = right.cross(toward);
        double reach = bound * distance / Math.sqrt(distance * distance - bound * bound);
        Vec3[] corners = new Vec3[CORNERS];
        for (int corner = 0; corner < CORNERS; corner++) {
            corners[corner] = center.add(right.scale(CORNER_SIGNS[corner][0] * reach))
                    .add(up.scale(CORNER_SIGNS[corner][1] * reach));
        }
        return corners;
    }

    private static void putVertex(ByteBuffer bytes, int at, Vec3 corner, DrinkSplats.Splat splat) {
        VertexFormat format = GooRenderTypes.DRINK_SPLAT_FORMAT;
        int color = splat.color();
        putFloats(bytes, at + format.getOffset(VertexFormatElement.POSITION), corner.x, corner.y, corner.z);
        int[] channels = {ARGB.red(color), ARGB.green(color), ARGB.blue(color), ARGB.alpha(color)};
        int colorAt = at + format.getOffset(VertexFormatElement.COLOR);
        for (int channel = 0; channel < channels.length; channel++) {
            bytes.put(colorAt + channel, (byte) channels[channel]);
        }
        putFloats(bytes, at + format.getOffset(VertexFormatElement.UV0), splat.rounding(), 0);
        int light = at + format.getOffset(VertexFormatElement.UV2);
        bytes.putShort(light, (short) splat.coat().blockLight()).putShort(light + Short.BYTES,
                (short) splat.coat().skyLight());
        putFloats(bytes, at + format.getOffset(GooRenderTypes.DRINK_SHAPE), splat.center().x, splat.center().y,
                splat.center().z, splat.half());
    }

    private static void putFloats(ByteBuffer bytes, int at, double... values) {
        for (int index = 0; index < values.length; index++) {
            bytes.putFloat(at + index * Float.BYTES, (float) values[index]);
        }
    }
}
