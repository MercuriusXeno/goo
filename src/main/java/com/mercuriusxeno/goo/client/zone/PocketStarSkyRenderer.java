package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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

/**
 * Draws goo:pocket's sky in place of vanilla's: PocketStarSky's stars as
 * small quads on a sphere round the camera, built once and drawn each sky
 * pass under the view's rotation alone, so they stand far off and hold still
 * as the camera walks. A room edge view runs the same sky pass, so it draws
 * the stars the frame draws in each direction. No sun, moon or cloud draws.
 * outsider-room-draws-a-skybox-of-stars
 * sky-stars-sit-distant-sparser-and-dimmer-than-voidstone
 */
public final class PocketStarSkyRenderer implements CustomSkyboxRenderer {

    /** How far round the camera the stars stand, as vanilla's do. */
    private static final float SKY_DISTANCE = 100F;
    /** The faintest star's half width at the sky's distance. */
    private static final float FAINT_HALF_SIZE = 0.12F;
    /** How much wider than the faintest the brightest star stands. */
    private static final float BRIGHT_HALF_SIZE_GAIN = 0.1F;
    private static final int CORNERS = 4;
    private static final float FULL_TURN = (float) (Math.PI * 2);
    /** How near straight up or down a star may stand before its quad is turned off another up. */
    private static final double NEAR_THE_POLE = 0.99;
    private static final Vector3f UP = new Vector3f(0F, 1F, 0F);
    private static final Vector3f EAST = new Vector3f(1F, 0F, 0F);
    private static final String LABEL = "goo_pocket_stars";
    /** The uniform block the sky shader reads its model view from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";

    private @Nullable GpuBuffer stars;
    private int indexCount;

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState,
            Matrix4fc modelViewMatrix, Runnable setupFog) {
        setupFog.run();
        GpuBuffer vertices = starBuffer();
        RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = quads.getBuffer(indexCount);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelViewMatrix,
                new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> LABEL, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(GooRenderTypes.POCKET_SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, quads.type());
            pass.drawIndexed(0, 0, indexCount, 1);
        }
        return true;
    }

    /**
     * The stars' vertex buffer, built on the first sky drawn: the device
     * stands only once the client has started.
     *
     * @return the buffer
     */
    private GpuBuffer starBuffer() {
        if (stars == null) {
            List<PocketStarSky.Star> sky = PocketStarSky.stars();
            try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(
                    DefaultVertexFormat.POSITION_COLOR.getVertexSize() * sky.size() * CORNERS)) {
                BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS,
                        DefaultVertexFormat.POSITION_COLOR);
                sky.forEach(star -> addStar(builder, star));
                try (MeshData mesh = builder.buildOrThrow()) {
                    indexCount = mesh.drawState().indexCount();
                    stars = RenderSystem.getDevice().createBuffer(() -> LABEL,
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
                }
            }
        }
        return stars;
    }

    /**
     * Adds one star's quad, facing the camera at the sky's distance along the
     * star's direction, turned about that direction by its phase so the
     * squares lie every way. The color carries its brightness, its twinkle
     * rate and its twinkle phase for the sky shader.
     *
     * @param builder the stars' buffer builder
     * @param star    the star
     */
    private static void addStar(BufferBuilder builder, PocketStarSky.Star star) {
        Vec3 along = star.direction();
        Vector3f center = new Vector3f((float) along.x(), (float) along.y(), (float) along.z()).mul(SKY_DISTANCE);
        Vector3f up = Math.abs(along.y()) > NEAR_THE_POLE ? EAST : UP;
        Matrix3f facing = new Matrix3f().rotateTowards(new Vector3f(center).negate(), up)
                .rotateZ(-star.phase() * FULL_TURN);
        float faintToBright = (star.brightness() - PocketStarSky.FAINTEST)
                / (PocketStarSky.BRIGHTEST - PocketStarSky.FAINTEST);
        float half = FAINT_HALF_SIZE + BRIGHT_HALF_SIZE_GAIN * faintToBright;
        float[][] corners = {{half, -half}, {half, half}, {-half, half}, {-half, -half}};
        for (float[] corner : corners) {
            builder.addVertex(new Vector3f(corner[0], corner[1], 0F).mul(facing).add(center))
                    .setColor(star.brightness(), star.rate(), star.phase(), 1F);
        }
    }
}
