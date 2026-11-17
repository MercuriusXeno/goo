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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws goo:lunar's sky in place of vanilla's: the pocket's sea of dim stars, then vanilla's sun turned by lunar's sun
 * angle, then LunarSky's blue overworld star over it, a bright core inside two wider, fainter halos turned across it so
 * it reads as a glowing point. No moon or cloud draws.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
public final class LunarSkyRenderer implements CustomSkyboxRenderer {

    private static final String LABEL = "goo_lunar_overworld_star";
    private static final String SUN_LABEL = "goo_lunar_sun";
    /** The core and its two halos, each wider and fainter than the last. */
    private static final float[] HALO_SCALES = {1F, 2.2F, 4F};
    private static final float[] HALO_STRENGTHS = {1F, 0.35F, 0.12F};
    /** Each halo turns an eighth of a turn from the last, so their corners spread as rays. */
    private static final float HALO_TURN = 0.125F;

    /** Vanilla's sun sprite in the celestials atlas. */
    private static final Identifier SUN_SPRITE = Identifier.withDefaultNamespace("sun");
    /** The sun's half width at the sky's distance, vanilla's sun size. */
    private static final float SUN_HALF_SIZE = 30F;
    /** Vanilla turns its whole sky a quarter turn about the vertical before it turns the sun by its angle. */
    private static final float SKY_QUARTER_TURN = (float) Math.toRadians(-90.0);
    /** The sun's quad before its turn: a unit square lying flat, as vanilla builds its celestial quad. */
    private static final List<Vector3f> FLAT_SUN = List.of(new Vector3f(-1F, 0F, -1F), new Vector3f(1F, 0F, -1F),
            new Vector3f(1F, 0F, 1F), new Vector3f(-1F, 0F, 1F));
    private static final int QUAD_INDICES = 6;
    private static final float FULL_BRIGHTNESS = 1F;
    /** The uniform block the sky shader reads its model view from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String SAMPLER = "Sampler0";

    private final PocketStarSkyRenderer sea = new PocketStarSkyRenderer();
    private PocketStarSkyRenderer.@Nullable SkyQuads overworldStar;
    private @Nullable GpuBuffer sun;
    private @Nullable TextureAtlasSprite sunBuiltFrom;

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState,
            Matrix4fc modelViewMatrix, Runnable setupFog) {
        sea.renderSky(levelRenderState, skyRenderState, modelViewMatrix, setupFog);
        drawSun(modelViewMatrix, (float) Math.toDegrees(skyRenderState.sunAngle));
        if (overworldStar == null) {
            overworldStar = buildOverworldStar(LABEL);
        }
        PocketStarSkyRenderer.drawQuads(GooRenderTypes.OVERWORLD_STAR, overworldStar, modelViewMatrix, LABEL);
        return true;
    }

    /**
     * The turn that carries the flat sun quad to where the sky draws it: a quarter turn about the vertical, the sun
     * angle about the east-west axis, then out to the sky's distance at the sun's size, as vanilla's sky turns its sun.
     * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
     *
     * @param sunAngleDegrees the sun angle attribute, in degrees
     * @return the turn, from the flat quad to the sky
     */
    static Matrix4f sunPose(float sunAngleDegrees) {
        return new Matrix4f().rotateY(SKY_QUARTER_TURN).rotateX((float) Math.toRadians(sunAngleDegrees))
                .translate(0F, PocketStarSkyRenderer.SKY_DISTANCE, 0F).scale(SUN_HALF_SIZE, 1F, SUN_HALF_SIZE);
    }

    /**
     * The sun quad's corners where the sky draws them at a sun angle; its center stands along LunarSky.sunDirection.
     *
     * @param sunAngleDegrees the sun angle attribute, in degrees
     * @return the four corners, in the flat quad's order
     */
    static List<Vector3f> sunCorners(float sunAngleDegrees) {
        Matrix4f pose = sunPose(sunAngleDegrees);
        return FLAT_SUN.stream().map(corner -> pose.transformPosition(corner, new Vector3f())).toList();
    }

    private void drawSun(Matrix4fc modelViewMatrix, float sunAngleDegrees) {
        TextureAtlas celestials = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
        GpuBuffer quad = sunOn(celestials);
        Matrix4f pose = new Matrix4f(modelViewMatrix).mul(sunPose(sunAngleDegrees));
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = sequential.getBuffer(QUAD_INDICES);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(pose,
                new Vector4f(FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS, FULL_BRIGHTNESS), new Vector3f(),
                new Matrix4f());
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> SUN_LABEL, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.CELESTIAL);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.bindTexture(SAMPLER, celestials.getTextureView(), celestials.getSampler());
            pass.setVertexBuffer(0, quad);
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(0, 0, QUAD_INDICES, 1);
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
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(format.getVertexSize() * FLAT_SUN.size())) {
            BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            for (int corner = 0; corner < FLAT_SUN.size(); corner++) {
                builder.addVertex(FLAT_SUN.get(corner)).setUv(uvs[corner][0], uvs[corner][1]);
            }
            try (MeshData mesh = builder.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> SUN_LABEL, GpuBuffer.USAGE_VERTEX,
                        mesh.vertexBuffer());
            }
        }
    }

    /**
     * Builds the blue overworld star's core and halos at the sky's distance along its direction, as lunar's sky
     * draws it; the Lunar trip home draws the same star swelling.
     * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
     *
     * @param label the buffer's debug label
     * @return the built quads
     */
    public static PocketStarSkyRenderer.SkyQuads buildOverworldStar(String label) {
        return PocketStarSkyRenderer.buildQuads(label, HALO_SCALES.length, builder -> {
            LunarSky.OverworldStar star = LunarSky.OVERWORLD_STAR;
            for (int halo = 0; halo < HALO_SCALES.length; halo++) {
                Vector3f color = new Vector3f(star.red(), star.green(), star.blue()).mul(HALO_STRENGTHS[halo]);
                PocketStarSkyRenderer.addFacingQuad(builder, star.direction(), star.halfSize() * HALO_SCALES[halo],
                        halo * HALO_TURN, color);
            }
        });
    }
}
