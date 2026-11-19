package com.mercuriusxeno.goo.client.zone;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.level.MoonPhase;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the blue planet's disc: the hand-drawn sprite of its phase on a quad laid as vanilla's sky lays its moon
 * sprite, through the pipeline its caller names. Lunar's sky and the Lunar trip home draw the one planet through it,
 * each through a pipeline laying the sprite's opaque texels over what lies behind, so the disc that swells is the
 * disc the sky showed and its unlit face hides the sun behind it.
 * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 * decision planet-face-is-eight-hand-drawn-phase-sprites
 */
public final class BluePlanetRenderer {

    private static final String LABEL = "goo_lunar_planet";
    private static final int QUAD_INDICES = 6;
    private static final float FULL = 1F;
    /** The uniform block the celestial shader reads its model view and color from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String SAMPLER = "Sampler0";
    /**
     * The texture corners the flat quad's corners take: vanilla's moon sprite's laying turned a quarter about the
     * planet's face, so the sprite's left and right lie along the sun's east-west path and the lit edge faces the sun.
     * decision sun-follows-the-phase-and-lights-the-far-side-too
     */
    static final float[][] CORNER_UVS = {{0F, 1F}, {0F, 0F}, {1F, 0F}, {1F, 1F}};

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
        AbstractTexture sprite = Minecraft.getInstance().getTextureManager()
                .getTexture(LunarSky.planetSprite(planetPhase));
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
            pass.bindTexture(SAMPLER, sprite.getTextureView(), sprite.getSampler());
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(0, 0, QUAD_INDICES, 1);
        }
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
