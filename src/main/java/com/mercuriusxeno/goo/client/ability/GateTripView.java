package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.gate.GateFixation;
import com.mercuriusxeno.goo.ability.gate.GateSight;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.zone.PocketStarSkyRenderer;
import com.mercuriusxeno.goo.registry.GooAttachments;
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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the gate trip on the caster's client while a moon fixation holds them in the overworld, by GateTrip's
 * curves: the sky dims to the star void under a black veil the pocket's stars fade in over, the land drops into
 * darkness as the fog closes to black around them, and the moon itself swells along the fixation until it covers
 * the view. Vanilla's own moon is turned behind the caster for the trip, so the moon that swells is the one moon.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GateTripView {

    private static final String VEIL_LABEL = "goo_gate_sky_veil";
    private static final String MOON_LABEL = "goo_gate_moon";
    /** The six axis directions a cube of veil quads stands along round the camera. */
    private static final Vec3[] VEIL_FACES = {
        new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(0, 0, 1),
        new Vec3(0, 0, -1)};
    /** Each veil quad's half width, a hair past the cube's, so no seam shows at its edges. */
    private static final float VEIL_HALF_WIDTH = PocketStarSkyRenderer.SKY_DISTANCE * 1.01F;
    private static final Vector3f BLACK = new Vector3f();
    /** How near the camera the darkness closes at full: the land is gone past arm's length. */
    private static final float DARK_REACH = 0.5F;
    private static final int CORNERS = 4;
    private static final int QUAD_INDICES = 6;
    private static final float FULL = 1F;
    /** The progress a frame reads with no moon fixation holding the caster in the overworld. */
    private static final float NO_TRIP = -1F;
    /** The uniform block the celestial shader reads its model view and color from. */
    private static final String DYNAMIC_TRANSFORMS = "DynamicTransforms";
    private static final String MOON_SAMPLER = "Sampler0";
    /** Where vanilla's moon phase sprites sit on the celestials atlas. */
    private static final String MOON_SPRITE_PREFIX = "moon/";
    private static final float QUARTER_TURN = (float) (Math.PI / 2);
    private static final float HALF_TURN = (float) Math.PI;

    private static final PocketStarSkyRenderer VOID_STARS = new PocketStarSkyRenderer();
    private static PocketStarSkyRenderer.@Nullable SkyQuads veil;
    private static @Nullable GpuBuffer moonPhases;
    private static @Nullable GpuTextureView moonPhasesAtlas;

    private GateTripView() {
    }

    /**
     * The share of the local caster's moon fixation run this frame, or none where no moon fixation holds them in
     * the overworld.
     *
     * @return the progress, zero to one, or negative for no trip
     */
    private static float tripProgress() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return NO_TRIP;
        }
        GateFixation fixation = player.getData(GooAttachments.GATE_FIXATION);
        if (!fixation.stands() || fixation.sky() != GateSight.Sky.MOON) {
            return NO_TRIP;
        }
        return GateTrip.progress(fixation, mc.level.getGameTime(),
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    /**
     * Turns vanilla's moon behind the caster for the trip, so only the moon the trip draws stands before them.
     *
     * @param event the level render state extraction event
     */
    @SubscribeEvent
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        if (tripProgress() >= 0F) {
            SkyRenderState sky = event.getRenderState().skyRenderState;
            sky.moonAngle += HALF_TURN;
        }
    }

    /**
     * Dims the sky to the star void: a black veil over vanilla's sky at the dim, and the pocket's stars over it.
     *
     * @param event the stage the sky pass ends on
     */
    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        float progress = tripProgress();
        if (progress < 0F) {
            return;
        }
        float dim = GateTrip.skyDim(progress);
        if (dim <= 0F) {
            return;
        }
        if (veil == null) {
            veil = PocketStarSkyRenderer.buildQuads(VEIL_LABEL, VEIL_FACES.length, builder -> {
                for (Vec3 face : VEIL_FACES) {
                    PocketStarSkyRenderer.addFacingQuad(builder, face, VEIL_HALF_WIDTH, 0F, BLACK);
                }
            });
        }
        PocketStarSkyRenderer.drawQuads(GooRenderTypes.GATE_SKY_VEIL, veil, event.getModelViewMatrix(), VEIL_LABEL,
                new Vector4f(FULL, FULL, FULL, dim));
        VOID_STARS.drawStars(event.getModelViewMatrix(), dim);
    }

    /**
     * Closes the fog in around the caster as the land drops into darkness, clouds and all.
     *
     * @param event the fog event
     */
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        float progress = tripProgress();
        if (progress < 0F) {
            return;
        }
        float dark = GateTrip.landDark(progress);
        event.setNearPlaneDistance(Mth.lerp(dark, event.getNearPlaneDistance(), 0F));
        event.setFarPlaneDistance(Mth.lerp(dark, event.getFarPlaneDistance(), DARK_REACH));
        event.getFogData().cloudEnd = Mth.lerp(dark, event.getFogData().cloudEnd, 0F);
    }

    /**
     * Darkens the fog's color to black as the land drops into darkness.
     *
     * @param event the fog color event
     */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        float progress = tripProgress();
        if (progress < 0F) {
            return;
        }
        float lit = FULL - GateTrip.landDark(progress);
        event.setRed(event.getRed() * lit);
        event.setGreen(event.getGreen() * lit);
        event.setBlue(event.getBlue() * lit);
    }

    /**
     * Draws the swelling moon over the whole frame, in the caster's own view alone.
     *
     * @param event the stage the level ends on
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        float progress = tripProgress();
        if (progress < 0F || PortalView.drawing()) {
            return;
        }
        GateFixation fixation = Minecraft.getInstance().player.getData(GooAttachments.GATE_FIXATION);
        SkyRenderState sky = event.getLevelRenderState().skyRenderState;
        // The moon brightens from vanilla's rain-dimmed moon to full as the sky gives way to the void.
        float brightness = Mth.lerp(GateTrip.skyDim(progress), sky.rainBrightness, FULL);
        drawMoon(event, fixation.direction(), sky.moonPhase, GateTrip.moonHalfTangent(progress), brightness);
    }

    /**
     * Draws the moon along a direction as vanilla's sky lays it, its disc at a half width's tangent.
     *
     * @param event           the stage the level ends on
     * @param direction       the moon's unit direction
     * @param phase           the moon's phase
     * @param discHalfTangent the disc's half width as a tangent
     * @param brightness      how bright it draws
     */
    private static void drawMoon(RenderLevelStageEvent.AfterLevel event, Vec3 direction, MoonPhase phase,
            double discHalfTangent, float brightness) {
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.CELESTIALS);
        GpuBuffer phases = moonPhasesOn(atlas);
        float quadHalfWidth = (float) (discHalfTangent / GateTrip.MOON_DISC_SHARE);
        // Vanilla's moon: a quarter turn about y, then the moon angle about x, a unit quad lying a unit off.
        float moonAngle = (float) Math.atan2(-direction.x(), direction.y());
        Matrix4f moon = new Matrix4f(event.getModelViewMatrix()).rotateY(-QUARTER_TURN).rotateX(moonAngle)
                .translate(0F, FULL, 0F).scale(quadHalfWidth, FULL, quadHalfWidth);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(moon,
                new Vector4f(FULL, FULL, FULL, brightness), new Vector3f(), new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = sequential.getBuffer(QUAD_INDICES);
        GpuTextureView color = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
        GpuTextureView depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> MOON_LABEL, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(GooRenderTypes.GATE_MOON);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(DYNAMIC_TRANSFORMS, transforms);
            pass.bindTexture(MOON_SAMPLER, atlas.getTextureView(), atlas.getSampler());
            pass.setVertexBuffer(0, phases);
            pass.setIndexBuffer(indices, sequential.type());
            pass.drawIndexed(phase.index() * CORNERS, 0, QUAD_INDICES, 1);
        }
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
