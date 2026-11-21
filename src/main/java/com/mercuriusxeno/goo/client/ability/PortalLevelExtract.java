package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.SectionUpdateTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SectionUpdateRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.CustomEnvironmentEffectsRendererManager;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Extracts the level for a portal's camera, as 26.1's LevelRenderer
 * extractLevel did. Minecraft 26.2 moved extraction into LevelExtractor,
 * whose extract also re-culls the view area, consumes the chunk cache's
 * load updates and resets the frame's pending invalidations; a view drawn
 * ahead of the frame's own extraction takes none of those, so it runs the
 * extractor's steps alone, over the sections the view sees, queueing a
 * build for each one waiting for one as the extractor does for the frame's.
 * portals-link-by-looking-through-portal-vision
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
final class PortalLevelExtract {

    /** How far the particles' frustum reaches past the camera's, as the frame's extraction offsets it. */
    private static final float PARTICLE_FRUSTUM_OFFSET = -3.0F;
    private static final int BLOCKS_PER_CHUNK = 16;

    private PortalLevelExtract() {
    }

    /**
     * Fills the frame's level render state for a portal camera.
     *
     * @param mc      the client
     * @param delta   the frame's delta tracker
     * @param camera  the portal camera
     * @param partial the frame's partial tick
     */
    static void extract(Minecraft mc, DeltaTracker delta, Camera camera, float partial) {
        LevelExtractor extractor = mc.levelExtractor;
        LevelRenderer levels = mc.levelRenderer;
        ClientLevel level = mc.level;
        LevelRenderState state = mc.gameRenderer.gameRenderState.levelRenderState;
        Vec3 position = camera.position();
        Frustum cull = camera.getCullFrustum();
        state.reset();
        state.worldPartialTicks = partial;
        state.gameTime = level.getGameTime();
        extractor.extractPlayerState(camera, delta, partial, state.playerRenderState);
        levels.blockEntityRenderDispatcher().prepare(position);
        levels.entityRenderDispatcher().prepare(camera, mc.crosshairPickEntity);
        queueSectionBuilds(extractor, levels, level, state);
        extractor.extractVisibleEntities(camera, cull, delta, state);
        extractor.extractVisibleBlockEntities(camera, partial, state);
        extractor.extractBlockOutline(camera, state);
        extractor.extractBlockDestroyAnimation(camera, state);
        extractSurroundings(mc, camera, partial, state);
        state.shouldShowEntityOutlines = LevelExtractor.shouldShowEntityOutlines(camera, state.playerRenderState);
        NeoForge.EVENT_BUS.post(new ExtractLevelRenderStateEvent(extractor, state,
                mc.gameRenderer.gameRenderState().optionsRenderState, level, camera, cull, delta));
        state.playerCompiledSectionCallback = null;
    }

    // The weather, sky, border, particles, clouds and NeoForge's custom environment renderers round the camera.
    private static void extractSurroundings(Minecraft mc, Camera camera, float partial, LevelRenderState state) {
        LevelRenderer levels = mc.levelRenderer;
        ClientLevel level = mc.level;
        Vec3 position = camera.position();
        levels.weatherEffectRenderer().extractRenderState(level, partial, position, state.weatherRenderState);
        SkyRenderer sky = levels.skyRenderer();
        if (sky != null) {
            sky.extractRenderState(level, partial, camera, state.skyRenderState);
        }
        levels.worldBorderRenderer().extract(level.getWorldBorder(), partial, position,
                mc.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK, state.worldBorderRenderState);
        mc.particleEngine.extract(state.particlesRenderState,
                new Frustum(camera.getCullFrustum()).offset(PARTICLE_FRUSTUM_OFFSET), camera, partial);
        state.cloudColor = ARGB.colorFromVector4f(camera.attributeProbe().getValue(EnvironmentAttributes.CLOUD_COLOR,
                partial));
        if (ARGB.alpha(state.cloudColor) > 0) {
            state.cloudHeight = camera.attributeProbe().getValue(EnvironmentAttributes.CLOUD_HEIGHT, partial);
        }
        state.customWeatherEffectRenderer = CustomEnvironmentEffectsRendererManager
                .getCustomWeatherEffectRenderer(level, position);
        state.customSkyboxRenderer = CustomEnvironmentEffectsRendererManager.getCustomSkyboxRenderer(level, position);
        state.customCloudsRenderer = CustomEnvironmentEffectsRendererManager.getCustomCloudsRenderer(level, position);
    }

    // The frame builds only the sections the main camera sees, so a section seen through the portal alone would
    // stand unbuilt; each dirty one the view sees is queued the way the frame's extraction queues its own.
    private static void queueSectionBuilds(LevelExtractor extractor, LevelRenderer levels, ClientLevel level,
                                           LevelRenderState state) {
        SectionUpdateTracker tracker = extractor.sectionUpdateTracker;
        if (tracker == null) {
            return;
        }
        RenderRegionCache cache = new RenderRegionCache();
        for (SectionRenderDispatcher.RenderSection section : levels.visibleSections()) {
            SectionUpdateTracker.SectionDirtyState dirty = tracker.getDirtyState(section.getSectionNode());
            if (waitsForBuild(tracker, level, section, dirty)) {
                state.sectionUpdateRenderStates.add(new SectionUpdateRenderState(section.getSectionNode(),
                        dirty.isDirtyFromPlayer(), cache.createRegion(level, section.getSectionNode()),
                        ClientHooks.gatherAdditionalRenderers(section.getRenderOrigin(), level)));
                dirty.setNotDirty();
            }
        }
    }

    // A dirty section waits for a build once it holds a mesh to replace, or once every neighbour it reads is loaded.
    private static boolean waitsForBuild(SectionUpdateTracker tracker, ClientLevel level,
            SectionRenderDispatcher.RenderSection section, SectionUpdateTracker.@Nullable SectionDirtyState dirty) {
        return dirty != null && dirty.isDirty() && (section.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED
                || tracker.hasAllNeighbors(level, section.getSectionNode()));
    }
}
