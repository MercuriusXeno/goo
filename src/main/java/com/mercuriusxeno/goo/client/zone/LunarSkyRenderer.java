package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.StagePass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws goo:lunar's sky in place of vanilla's: the pocket's sea of dim stars, then vanilla's sun where it stands over
 * the camera's x while it is up, on both halves of the moon, then the blue planet hanging fixed where the camera's x
 * stands it, lit by the phase opposite the overworld moon's, and below the horizon over the dark side. No moon or cloud
 * draws. The Lunar trip home draws its sky without the planet, which it swells itself.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 * decision blue-star-takes-the-moons-place-with-the-opposite-phase
 */
public final class LunarSkyRenderer implements CustomSkyboxRenderer {

    private static final String SUN_LABEL = "goo_lunar_sun";
    /** Vanilla's sun sprite in the celestials atlas. */
    private static final Identifier SUN_SPRITE = Identifier.withDefaultNamespace("sun");
    /** The sun's half width at the sky's distance, vanilla's sun size. */
    private static final float SUN_HALF_SIZE = 30F;
    private static final int QUAD_INDICES = 6;
    private static final float FULL_BRIGHTNESS = 1F;
    /** The uniform block the sky shader reads its model view from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String SAMPLER = "Sampler0";
    /**
     * The pipeline the sky draws the planet through, after the sun, its opaque face laid over what lies behind it.
     * decision sun-follows-the-phase-and-lights-the-far-side-too
     */
    static final RenderPipeline PLANET_PIPELINE = GooRenderTypes.LUNAR_PLANET;

    private final PocketStarSkyRenderer sea = new PocketStarSkyRenderer();
    private final boolean drawsPlanet;
    private @Nullable GpuBuffer sun;
    private @Nullable TextureAtlasSprite sunBuiltFrom;

    /**
     * A lunar sky.
     *
     * @param drawsPlanet whether it draws the blue planet; the trip home's sky leaves it to the trip
     */
    public LunarSkyRenderer(boolean drawsPlanet) {
        this.drawsPlanet = drawsPlanet;
    }

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState,
            Matrix4fc modelViewMatrix, GpuBufferSlice skyFog) {
        sea.renderSky(levelRenderState, skyRenderState, modelViewMatrix, skyFog);
        double cameraX = levelRenderState.cameraRenderState.pos.x();
        ClientLevel level = Minecraft.getInstance().level;
        // decision sun-follows-the-phase-and-lights-the-far-side-too
        if (level != null) {
            LunarSky.sunOver(cameraX, level.getOverworldClockTime()).ifPresent(sun -> drawSun(modelViewMatrix, sun));
        }
        // decision blue-planet-hangs-fixed-with-its-height-set-by-x
        if (drawsPlanet && LunarSky.drawsPlanetOver(cameraX)) {
            BluePlanetRenderer.draw(PLANET_PIPELINE,
                    new Matrix4f(modelViewMatrix).mul(LunarSky.planetPose(cameraX)),
                    LunarSight.planetPhase(skyRenderState.moonPhase));
        }
        return true;
    }

    /**
     * The turn that carries the flat sun quad to where the sky draws it, as vanilla's sky turns its sun.
     * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
     *
     * @param sunAngleDegrees the sun angle attribute, in degrees
     * @return the turn, from the flat quad to the sky
     */
    static Matrix4f sunPose(float sunAngleDegrees) {
        return sunPose(LunarSky.sunDirection(sunAngleDegrees));
    }

    /**
     * The turn that carries the flat sun quad to where the sky draws it along the sun's direction.
     *
     * @param sun the sun's unit direction
     * @return the turn, from the flat quad to the sky
     */
    private static Matrix4f sunPose(Vec3 sun) {
        return LunarSky.celestialPose(sun, PocketStarSkyRenderer.SKY_DISTANCE, SUN_HALF_SIZE);
    }

    /**
     * The sun quad's corners where the sky draws them at a sun angle; its center stands along LunarSky.sunDirection.
     *
     * @param sunAngleDegrees the sun angle attribute, in degrees
     * @return the four corners, in the flat quad's order
     */
    static List<Vector3f> sunCorners(float sunAngleDegrees) {
        Matrix4f pose = sunPose(sunAngleDegrees);
        return LunarSky.FLAT_QUAD.stream().map(corner -> pose.transformPosition(corner, new Vector3f())).toList();
    }

    private void drawSun(Matrix4fc modelViewMatrix, Vec3 sun) {
        TextureAtlas celestials = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
        GpuBuffer quad = sunOn(celestials);
        Matrix4f pose = new Matrix4f(modelViewMatrix).mul(sunPose(sun));
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indices = sequential.getBuffer(QUAD_INDICES);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(pose,
                new Vector4f(FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS), new Vector3f(),
                new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
        try (StagePass stage = StagePass.onMain(() -> SUN_LABEL)) {
            RenderPass pass = stage.pass();
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.CELESTIAL));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.setUniform(SAMPLER, celestials.getTextureView(), celestials.getSampler());
            pass.setVertexBuffer(0, quad.slice());
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(QUAD_INDICES, 1, 0, 0, 0);
        }
    }

    /**
     * The sun's quad on the celestials atlas, built again whenever the atlas's sun sprite is.
     *
     * @param celestials the celestials atlas
     * @return the sun quad's vertex buffer
     */
    private GpuBuffer sunOn(TextureAtlas celestials) {
        TextureAtlasSprite sprite = celestials.getSprite(SUN_SPRITE);
        if (sun != null && sunBuiltFrom == sprite) {
            return sun;
        }
        if (sun != null) {
            sun.close();
        }
        GpuBuffer built = buildSun(sprite);
        sun = built;
        sunBuiltFrom = sprite;
        return built;
    }

    private static GpuBuffer buildSun(TextureAtlasSprite sprite) {
        float[][] uvs = {{sprite.getU0(), sprite.getV0()}, {sprite.getU1(), sprite.getV0()},
            {sprite.getU1(), sprite.getV1()}, {sprite.getU0(), sprite.getV1()}};
        VertexFormat format = DefaultVertexFormat.POSITION_TEX;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(format.getVertexSize()
                * LunarSky.FLAT_QUAD.size())) {
            BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
            for (int corner = 0; corner < LunarSky.FLAT_QUAD.size(); corner++) {
                builder.addVertex(LunarSky.FLAT_QUAD.get(corner)).setUv(uvs[corner][0], uvs[corner][1]);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> SUN_LABEL, GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
            }
        }
    }
}
