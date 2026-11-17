package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.zone.LunarSky;
import com.mercuriusxeno.goo.client.zone.LunarSkyRenderer;
import com.mercuriusxeno.goo.client.zone.PocketStarSkyRenderer;
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
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * The bodies the Lunar trip swells over the frame: vanilla's moon, its sprite on its quad as vanilla's sky lays it,
 * and lunar's blue star, its core and halos as lunar's sky lays them, each grown about its own center, passing every
 * depth test so it swells over the darkened land as well as the sky.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 */
final class LunarTripBodies {

    private static final String MOON_LABEL = "goo_lunar_moon";
    private static final String STAR_LABEL = "goo_lunar_star";
    private static final int CORNERS = 4;
    private static final int QUAD_INDICES = 6;
    private static final float FULL = 1F;
    /** The uniform block the celestial shader reads its model view and color from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String MOON_SAMPLER = "Sampler0";
    /** Where vanilla's moon phase sprites sit on the celestials atlas. */
    private static final String MOON_SPRITE_PREFIX = "moon/";
    private static final float QUARTER_TURN = (float) (Math.PI / 2);

    private static @Nullable GpuBuffer moonPhases;
    private static @Nullable GpuTextureView moonPhasesAtlas;
    private static PocketStarSkyRenderer.@Nullable SkyQuads star;

    private LunarTripBodies() {
    }

    /**
     * Draws the moon along a direction as vanilla's sky lays it, its disc at a half width's tangent.
     *
     * @param view            the view's rotation
     * @param direction       the moon's unit direction
     * @param phase           the moon's phase
     * @param discHalfTangent the disc's half width as a tangent
     * @param brightness      how bright it draws
     */
    static void drawMoon(Matrix4fc view, Vec3 direction, MoonPhase phase, double discHalfTangent, float brightness) {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
        GpuBuffer phases = moonPhasesOn(atlas);
        float quadHalfWidth = (float) (discHalfTangent / LunarTrip.MOON_DISC_SHARE);
        // Vanilla's moon: a quarter turn about y, then the moon angle about x, a unit quad lying a unit off.
        float moonAngle = (float) Math.atan2(-direction.x(), direction.y());
        Matrix4f moon = new Matrix4f(view).rotateY(-QUARTER_TURN).rotateX(moonAngle)
                .translate(0F, FULL, 0F).scale(quadHalfWidth, FULL, quadHalfWidth);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(moon,
                new Vector4f(FULL, FULL, FULL, brightness), new Vector3f(), new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = sequential.getBuffer(QUAD_INDICES);
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> MOON_LABEL, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(GooRenderTypes.LUNAR_MOON);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.bindTexture(MOON_SAMPLER, atlas.getTextureView(), atlas.getSampler());
            pass.setVertexBuffer(0, phases);
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(phase.index() * CORNERS, 0, QUAD_INDICES, 1);
        }
    }

    /**
     * Draws lunar's blue star in its own blue, its core at a half width's tangent: the sky's star grown about its
     * center, brought in to a unit off so even a star covering the view stands well inside the far plane.
     *
     * @param view            the view's rotation
     * @param coreHalfTangent the core's half width as a tangent
     */
    static void drawStar(Matrix4fc view, double coreHalfTangent) {
        if (star == null) {
            star = LunarSkyRenderer.buildOverworldStar(STAR_LABEL);
        }
        Vec3 along = LunarSky.OVERWORLD_STAR.direction();
        Vector3f center = new Vector3f((float) along.x(), (float) along.y(), (float) along.z())
                .mul(PocketStarSkyRenderer.SKY_DISTANCE);
        float growth = (float) (coreHalfTangent / LunarTrip.RESTING_STAR_HALF_TANGENT);
        Matrix4f grown = new Matrix4f(view).scale(FULL / PocketStarSkyRenderer.SKY_DISTANCE)
                .translate(center).scale(growth).translate(center.negate(new Vector3f()));
        PocketStarSkyRenderer.drawQuads(GooRenderTypes.LUNAR_STAR, star, grown, STAR_LABEL,
                new Vector4f(FULL, FULL, FULL, FULL));
    }

    /**
     * Vanilla's moon phases as unit quads on the celestials atlas, built again whenever the atlas is.
     *
     * @param atlas the celestials atlas
     * @return the phases' vertex buffer
     */
    private static GpuBuffer moonPhasesOn(TextureAtlas atlas) {
        if (moonPhases != null && moonPhasesAtlas == atlas.getTextureView()) {
            return moonPhases;
        }
        if (moonPhases != null) {
            moonPhases.close();
        }
        MoonPhase[] all = MoonPhase.values();
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(all.length * CORNERS * format.getVertexSize())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (MoonPhase phase : all) {
                TextureAtlasSprite sprite = atlas.getSprite(
                        Identifier.withDefaultNamespace(MOON_SPRITE_PREFIX + phase.getSerializedName()));
                builder.addVertex(-FULL, 0F, -FULL).setUv(sprite.getU1(), sprite.getV1());
                builder.addVertex(FULL, 0F, -FULL).setUv(sprite.getU0(), sprite.getV1());
                builder.addVertex(FULL, 0F, FULL).setUv(sprite.getU0(), sprite.getV0());
                builder.addVertex(-FULL, 0F, FULL).setUv(sprite.getU1(), sprite.getV0());
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                moonPhases = RenderSystem.getDevice().createBuffer(() -> MOON_LABEL,
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
            }
        }
        moonPhasesAtlas = atlas.getTextureView();
        return moonPhases;
    }
}
