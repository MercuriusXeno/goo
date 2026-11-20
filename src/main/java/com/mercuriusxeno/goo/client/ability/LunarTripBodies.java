package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.zone.BluePlanetRenderer;
import com.mercuriusxeno.goo.client.zone.LunarSky;
import com.mojang.blaze3d.PrimitiveTopology;
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
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The bodies the Lunar trip swells over the frame: vanilla's moon, its sprite on its quad as vanilla's sky lays it,
 * and lunar's blue planet, its lit face on its disc as lunar's sky lays it, each grown about its own center, passing
 * every depth test so it swells over the darkened land as well as the sky.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 */
final class LunarTripBodies {

    private static final String MOON_LABEL = "goo_lunar_moon";
    private static final int CORNERS = 4;
    private static final int QUAD_INDICES = 6;
    private static final float FULL = 1F;
    /** The uniform block the celestial shader reads its model view and color from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String MOON_SAMPLER = "Sampler0";
    /** Where vanilla's moon phase sprites sit on the celestials atlas. */
    private static final String MOON_SPRITE_PREFIX = "moon/";
    private static final float QUARTER_TURN = (float) (Math.PI / 2);
    /**
     * The pipeline the trip swells the planet through, solid as lunar's sky draws it.
     * decision sun-follows-the-phase-and-lights-the-far-side-too
     */
    static final RenderPipeline PLANET_PIPELINE = GooRenderTypes.LUNAR_TRIP_PLANET;

    private static @Nullable GpuBuffer moonPhases;
    private static @Nullable GpuTextureView moonPhasesAtlas;

    private LunarTripBodies() {
    }

    /**
     * Draws the moon at the frame's moon angle as vanilla's sky lays it, its disc at a half width's tangent.
     * decision diagnose-and-correct-the-moon-twitch-on-the-first-grab
     *
     * @param view            the view's rotation
     * @param moonAngle       the frame's moon angle, in radians, the one vanilla's sky draws its moon at
     * @param phase           the moon's phase
     * @param discHalfTangent the disc's half width as a tangent
     * @param brightness      how bright it draws
     */
    static void drawMoon(Matrix4fc view, float moonAngle, MoonPhase phase, double discHalfTangent, float brightness) {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
        GpuBuffer phases = moonPhasesOn(atlas);
        Matrix4f moon = new Matrix4f(view).mul(moonPose(moonAngle, discHalfTangent));
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(moon,
                new Vector4f(FULL, FULL, FULL, brightness), new Vector3f(), new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = sequential.getBuffer(QUAD_INDICES);
        GpuTextureView color = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> MOON_LABEL, color, Optional.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(GooRenderTypes.LUNAR_MOON);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.bindTexture(MOON_SAMPLER, atlas.getTextureView(), atlas.getSampler());
            pass.setVertexBuffer(0, phases.slice());
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(QUAD_INDICES, 1, 0, phase.index() * CORNERS, 0);
        }
    }

    /**
     * The moon quad's corners as the trip draws them at a moon angle, its disc at a half width's tangent.
     *
     * @param moonAngle       the moon angle, in radians
     * @param discHalfTangent the disc's half width as a tangent
     * @return the four corners, in the flat quad's order
     */
    static List<Vector3f> moonCorners(float moonAngle, double discHalfTangent) {
        Matrix4f pose = moonPose(moonAngle, discHalfTangent);
        return LunarSky.FLAT_QUAD.stream().map(corner -> pose.transformPosition(corner, new Vector3f())).toList();
    }

    private static Matrix4f moonPose(float moonAngle, double discHalfTangent) {
        float distance = swellingDistance(discHalfTangent, LunarTrip.RESTING_MOON_HALF_TANGENT);
        float quadHalfWidth = (float) (discHalfTangent / LunarTrip.MOON_DISC_SHARE * distance);
        // Vanilla's moon: a quarter turn about y, then the moon angle about x, out to its distance.
        return new Matrix4f().rotateY(-QUARTER_TURN).rotateX(moonAngle).translate(0F, distance, 0F)
                .scale(quadHalfWidth, FULL, quadHalfWidth);
    }

    /**
     * How far off a swelling body draws: the sky's distance at rest, where the sky draws it, coming in toward the
     * caster as it swells, so its quad keeps the half width the sky gives it and even a body covering the view
     * stands well inside the far plane. The view bob's sway moves the frame by a distance, not an angle, so a body
     * drawn nearer than the sky's sways further than the sky's does: at rest the trip's body stands where the sky's
     * stands, and moves with it.
     * decision diagnose-and-correct-the-moon-twitch-on-the-first-grab
     *
     * @param halfTangent        the body's half width as a tangent
     * @param restingHalfTangent its half width as the sky draws it, as a tangent
     * @return the distance it draws at
     */
    private static float swellingDistance(double halfTangent, double restingHalfTangent) {
        return (float) (LunarTrip.SKY_DISTANCE * restingHalfTangent / Math.max(halfTangent, restingHalfTangent));
    }

    /**
     * Draws the blue planet's disc in its phase along a direction, at a half width's tangent: lunar's sky's planet
     * grown about its center, at the sky's distance at rest and coming in as it swells.
     * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     *
     * @param view        the view's rotation
     * @param direction   the planet's unit direction, the one the fixation holds
     * @param planetPhase the planet's phase
     * @param halfTangent the disc's half width as a tangent
     */
    static void drawPlanet(Matrix4fc view, Vec3 direction, MoonPhase planetPhase, double halfTangent) {
        BluePlanetRenderer.draw(PLANET_PIPELINE, new Matrix4f(view).mul(planetPose(direction,
                halfTangent)), planetPhase);
    }

    /**
     * The planet disc's corners as the trip home draws them: centered along the direction at the distance it swells
     * in to, at the half width's tangent.
     *
     * @param direction   the planet's unit direction
     * @param halfTangent the disc's half width as a tangent
     * @return the four corners, in the flat quad's order
     */
    static List<Vector3f> planetCorners(Vec3 direction, double halfTangent) {
        Matrix4f pose = planetPose(direction, halfTangent);
        return LunarSky.FLAT_QUAD.stream().map(corner -> pose.transformPosition(corner, new Vector3f())).toList();
    }

    private static Matrix4f planetPose(Vec3 direction, double halfTangent) {
        float distance = swellingDistance(halfTangent, LunarTrip.RESTING_PLANET_HALF_TANGENT);
        return LunarSky.celestialPose(direction, distance, (float) (halfTangent * distance));
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
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
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
