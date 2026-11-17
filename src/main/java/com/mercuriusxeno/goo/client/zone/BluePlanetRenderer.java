package com.mercuriusxeno.goo.client.zone;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the blue planet's disc: its face, lit by its phase as LunarSky lights it, on a texture the disc's quad lays
 * as vanilla's sky lays its moon sprite. Lunar's sky and the Lunar trip home draw the one planet through it, so the
 * disc that swells is the disc the sky showed.
 * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 */
public final class BluePlanetRenderer {

    private static final String LABEL = "goo_lunar_planet";
    private static final int QUAD_INDICES = 6;
    private static final float FULL = 1F;
    /** The uniform block the celestial shader reads its model view and color from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String SAMPLER = "Sampler0";
    /** The texture corners the flat quad's corners take, turned as vanilla lays its moon sprite. */
    private static final float[][] CORNER_UVS = {{1F, 1F}, {0F, 1F}, {0F, 0F}, {1F, 0F}};

    private static final int[] FACE = LunarSky.plainFace();
    private static @Nullable DynamicTexture texture;
    private static @Nullable MoonPhase shownPhase;
    private static @Nullable GpuBuffer quad;

    private BluePlanetRenderer() {
    }

    /**
     * Draws the planet's disc in a phase under a pose carrying the flat quad to it.
     *
     * @param pipeline    the pipeline it draws through
     * @param pose        the view's rotation times the turn to the disc
     * @param planetPhase the planet's phase
     */
    public static void draw(RenderPipeline pipeline, Matrix4fc pose, MoonPhase planetPhase) {
        if (!LunarSky.planetDraws(planetPhase)) {
            return;
        }
        DynamicTexture lit = textureIn(planetPhase);
        GpuBuffer vertices = quad();
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = sequential.getBuffer(QUAD_INDICES);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(pose,
                new Vector4f(FULL, FULL, FULL, FULL), new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> LABEL, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.bindTexture(SAMPLER, lit.getTextureView(), lit.getSampler());
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(0, 0, QUAD_INDICES, 1);
        }
    }

    /**
     * The face texture as a phase lights it, written again whenever the phase it shows changes.
     *
     * @param planetPhase the planet's phase
     * @return the texture
     */
    private static DynamicTexture textureIn(MoonPhase planetPhase) {
        DynamicTexture built = texture;
        if (built == null) {
            built = new DynamicTexture(() -> LABEL, LunarSky.FACE_SIZE, LunarSky.FACE_SIZE, true);
            texture = built;
        }
        if (shownPhase != planetPhase) {
            NativeImage pixels = built.getPixels();
            int[] shown = LunarSky.litFace(FACE, planetPhase);
            for (int pixel = 0; pixel < shown.length; pixel++) {
                pixels.setPixel(pixel % LunarSky.FACE_SIZE, pixel / LunarSky.FACE_SIZE, shown[pixel]);
            }
            built.upload();
            shownPhase = planetPhase;
        }
        return built;
    }

    private static GpuBuffer quad() {
        GpuBuffer built = quad;
        if (built != null) {
            return built;
        }
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(format.getVertexSize()
                * LunarSky.FLAT_QUAD.size())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (int corner = 0; corner < LunarSky.FLAT_QUAD.size(); corner++) {
                builder.addVertex(LunarSky.FLAT_QUAD.get(corner)).setUv(CORNER_UVS[corner][0], CORNER_UVS[corner][1]);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                built = RenderSystem.getDevice().createBuffer(() -> LABEL, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
        quad = built;
        return built;
    }
}
