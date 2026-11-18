package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.client.GooRenderTypes;
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
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Draws goo:pocket's sky in place of vanilla's: PocketStarSky's stars as
 * small quads on a sphere round the camera, built once and drawn each sky
 * pass under the view's rotation alone, so they stand far off and hold still
 * as the camera walks. A room edge view runs the same sky pass, so it draws
 * the stars the frame draws in each direction. No sun, moon or cloud draws.
 * Lunar's sky draws the same sea through it (LunarSkyRenderer).
 * outsider-room-draws-a-skybox-of-stars
 * sky-stars-sit-distant-sparser-and-dimmer-than-voidstone
 */
public final class PocketStarSkyRenderer implements CustomSkyboxRenderer {

    /** How far round the camera the stars stand, as vanilla's do. */
    public static final float SKY_DISTANCE = 100F;
    private static final float FULL_BRIGHTNESS = 1F;
    /** The faintest star's half width at the sky's distance. */
    private static final float FAINT_HALF_SIZE = 0.12F;
    /** How much wider than the faintest the brightest star stands. */
    private static final float BRIGHT_HALF_SIZE_GAIN = 0.1F;
    /** The brightest star's half width at the sky's distance. */
    static final float BRIGHTEST_HALF_SIZE = FAINT_HALF_SIZE + BRIGHT_HALF_SIZE_GAIN;
    private static final int CORNERS = 4;
    private static final float FULL_TURN = (float) (Math.PI * 2);
    /** How near straight up or down a star may stand before its quad is turned off another up. */
    private static final double NEAR_THE_POLE = 0.99;
    private static final Vector3f UP = new Vector3f(0F, 1F, 0F);
    private static final Vector3f EAST = new Vector3f(1F, 0F, 0F);
    private static final String LABEL = "goo_pocket_stars";
    /** The uniform block the sky shader reads its model view from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";

    private @Nullable SkyQuads stars;

    /**
     * Quads built once into a vertex buffer, with the index count their draw takes.
     *
     * @param vertices   the buffer
     * @param indexCount the indices the quads draw with
     */
    public record SkyQuads(GpuBuffer vertices, int indexCount) {
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState,
            Matrix4fc modelViewMatrix, Runnable setupFog) {
        setupFog.run();
        drawStars(modelViewMatrix, FULL_BRIGHTNESS);
        return true;
    }

    /**
     * Draws the sea of dim stars under the view's rotation, every star scaled by one brightness; the Lunar trip fades
     * them in over the overworld's sky with it.
     * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
     *
     * @param modelViewMatrix the sky pass's model view
     * @param brightness      how bright the sea draws, zero to one
     */
    public void drawStars(Matrix4fc modelViewMatrix, float brightness) {
        if (stars == null) {
            List<PocketStarSky.Star> sky = PocketStarSky.stars();
            stars = buildQuads(LABEL, sky.size(), builder -> sky.forEach(star -> addStar(builder, star)));
        }
        drawQuads(GooRenderTypes.POCKET_SKY, stars, modelViewMatrix, LABEL,
                new Vector4f(FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS, brightness));
    }

    /**
     * Builds quads into a vertex buffer. Call it on the first sky drawn: the device stands only once the client
     * has started.
     *
     * @param label the buffer's debug label
     * @param quads how many quads the fill adds
     * @param fill  adds the quads' vertices
     * @return the built quads
     */
    public static SkyQuads buildQuads(String label, int quads, Consumer<BufferBuilder> fill) {
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(
                DefaultVertexFormat.POSITION_COLOR.getVertexSize() * quads * CORNERS)) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            fill.accept(builder);
            try (MeshData mesh = builder.buildOrThrow()) {
                return new SkyQuads(RenderSystem.getDevice().createBuffer(() -> label,
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer()),
                        mesh.drawState().indexCount());
            }
        }
    }

    /**
     * Draws built quads through a sky pipeline, under the view's rotation alone.
     *
     * @param pipeline        the pipeline
     * @param quads           the quads
     * @param modelViewMatrix the sky pass's model view
     * @param label           the render pass's debug label
     */
    static void drawQuads(RenderPipeline pipeline, SkyQuads quads, Matrix4fc modelViewMatrix, String label) {
        drawQuads(pipeline, quads, modelViewMatrix, label,
                new Vector4f(FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS));
    }

    /**
     * Draws built quads through a sky pipeline, under the view's rotation alone, with a color modulator.
     *
     * @param pipeline        the pipeline
     * @param quads           the quads
     * @param modelViewMatrix the sky pass's model view
     * @param label           the render pass's debug label
     * @param modulator       the color modulator the shader reads
     */
    public static void drawQuads(RenderPipeline pipeline, SkyQuads quads, Matrix4fc modelViewMatrix, String label,
            Vector4f modulator) {
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = sequential.getBuffer(quads.indexCount());
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelViewMatrix,
                modulator, new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> label, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.setVertexBuffer(0, quads.vertices());
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(0, 0, quads.indexCount(), 1);
        }
    }

    /**
     * Adds one quad facing the camera at the sky's distance along a direction, turned about that direction.
     *
     * @param builder the buffer builder
     * @param along   the unit direction from the camera
     * @param half    the quad's half width
     * @param turn    the turn about the direction, a share of a full turn
     * @param color   the vertex color, or the values the shader reads in its place
     */
    public static void addFacingQuad(BufferBuilder builder, Vec3 along, float half, float turn, Vector3f color) {
        Vector3f center = new Vector3f((float) along.x(), (float) along.y(), (float) along.z()).mul(SKY_DISTANCE);
        Vector3f up = Math.abs(along.y()) > NEAR_THE_POLE ? EAST : UP;
        Matrix3f facing = new Matrix3f().rotateTowards(new Vector3f(center).negate(), up).rotateZ(-turn * FULL_TURN);
        float[][] corners = {{half, -half}, {half, half}, {-half, half}, {-half, -half}};
        for (float[] corner : corners) {
            builder.addVertex(new Vector3f(corner[0], corner[1], 0F).mul(facing).add(center))
                    .setColor(color.x(), color.y(), color.z(), 1F);
        }
    }

    /**
     * Adds one star's quad, turned about its direction by its phase so the
     * squares lie every way. The color carries its brightness, its twinkle
     * rate and its twinkle phase for the sky shader.
     *
     * @param builder the stars' buffer builder
     * @param star    the star
     */
    private static void addStar(BufferBuilder builder, PocketStarSky.Star star) {
        float faintToBright = (star.brightness() - PocketStarSky.FAINTEST)
                / (PocketStarSky.BRIGHTEST - PocketStarSky.FAINTEST);
        addFacingQuad(builder, star.direction(), FAINT_HALF_SIZE + BRIGHT_HALF_SIZE_GAIN * faintToBright,
                star.phase(), new Vector3f(star.brightness(), star.rate(), star.phase()));
    }
}
