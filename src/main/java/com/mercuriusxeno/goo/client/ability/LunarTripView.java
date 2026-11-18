package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.zone.LunarSkyRenderer;
import com.mercuriusxeno.goo.client.zone.PocketStarSkyRenderer;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.ResourceKey;
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
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the Lunar trip on the caster's client while a fixation holds them, by LunarTrip's curves. Out from the
 * overworld: the sky dims to the star void under a black veil the pocket's stars fade in over, the land drops into
 * darkness as the fog closes to black around them, and the moon itself swells along the fixation until it covers
 * the view; vanilla's own moon is turned behind the caster, so the moon that swells is the one moon. Home from
 * lunar the trip mirrors it: lunar's sky is already the void, the land goes dark the same way, and the blue planet
 * swells in its phase from the direction the fixation holds, lunar's sky drawing its sea and sun while the trip draws
 * the planet.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LunarTripView {

    private static final String VEIL_LABEL = "goo_lunar_sky_veil";
    /** The six axis directions a cube of veil quads stands along round the camera. */
    private static final Vec3[] VEIL_FACES = {
        new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, -1, 0), new Vec3(0, 0, 1),
        new Vec3(0, 0, -1)};
    /** Each veil quad's half width, a hair past the cube's, so no seam shows at its edges. */
    private static final float VEIL_HALF_WIDTH = PocketStarSkyRenderer.SKY_DISTANCE * 1.01F;
    private static final Vector3f BLACK = new Vector3f();
    /** How near the camera the darkness closes at full: the land is gone past arm's length. */
    private static final float DARK_REACH = 0.5F;
    private static final float FULL = 1F;
    private static final float HALF_TURN = (float) Math.PI;

    /** The pocket's sea of stars, which the overworld's sky dims to. */
    private static final PocketStarSkyRenderer VOID_STARS = new PocketStarSkyRenderer();
    /** Lunar's sky with its planet taken by the trip, which swells it. */
    private static final LunarSkyRenderer SKY_WITHOUT_THE_PLANET = new LunarSkyRenderer(false);
    private static PocketStarSkyRenderer.@Nullable SkyQuads veil;

    private LunarTripView() {
    }

    /**
     * A trip the local caster is on this frame.
     *
     * @param sky       what swells, the moon or the blue planet
     * @param fixation  the fixation holding them
     * @param progress  the share of it run, zero to one
     */
    private record Trip(LunarSight.Sky sky, LunarFixation fixation, float progress) {
    }

    /**
     * The trip the local caster is on this frame: a moon fixation in the overworld or a blue planet fixation in
     * lunar, held, or let go of and still running back.
     * decision holding-right-click-pulls-the-moon-and-early-release-cancels
     *
     * @return the trip, or null for none
     */
    private static @Nullable Trip trip() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return null;
        }
        LunarFixation fixation = player.getData(GooAttachments.LUNAR_FIXATION);
        if (!(fixation.stands() || fixation.released()) || mc.level.dimension() != homeOf(fixation.sky())) {
            return null;
        }
        float progress = LunarTrip.progress(fixation, mc.level.getGameTime(),
                mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        return fixation.released() && progress <= 0F ? null : new Trip(fixation.sky(), fixation, progress);
    }

    private static ResourceKey<Level> homeOf(LunarSight.Sky sky) {
        return sky == LunarSight.Sky.MOON ? Level.OVERWORLD : Lunar.LEVEL;
    }

    /**
     * Takes the swelling body out of the sky for the trip: vanilla's moon turns behind the caster, and lunar's sky
     * draws its sea and sun without the planet.
     *
     * @param event the level render state extraction event
     */
    @SubscribeEvent
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        Trip trip = trip();
        if (trip == null) {
            return;
        }
        LevelRenderState state = event.getRenderState();
        if (trip.sky() == LunarSight.Sky.MOON) {
            SkyRenderState sky = state.skyRenderState;
            sky.moonAngle += HALF_TURN;
        } else {
            state.customSkyboxRenderer = SKY_WITHOUT_THE_PLANET;
        }
    }

    /**
     * Dims the overworld's sky to the star void: a black veil over vanilla's sky at the dim, and the pocket's stars
     * over it.
     *
     * @param event the stage the sky pass ends on
     */
    @SubscribeEvent
    public static void onAfterSky(RenderLevelStageEvent.AfterSky event) {
        Trip trip = trip();
        if (trip == null || trip.sky() != LunarSight.Sky.MOON) {
            return;
        }
        float dim = LunarTrip.skyDim(trip.progress());
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
        PocketStarSkyRenderer.drawQuads(GooRenderTypes.LUNAR_SKY_VEIL, veil, event.getModelViewMatrix(), VEIL_LABEL,
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
        Trip trip = trip();
        if (trip == null) {
            return;
        }
        float dark = LunarTrip.landDark(trip.progress());
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
        Trip trip = trip();
        if (trip == null) {
            return;
        }
        float lit = FULL - LunarTrip.landDark(trip.progress());
        event.setRed(event.getRed() * lit);
        event.setGreen(event.getGreen() * lit);
        event.setBlue(event.getBlue() * lit);
    }

    /**
     * Draws the swelling moon or blue planet over the whole frame, in the caster's own view alone.
     *
     * @param event the stage the level ends on
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Trip trip = trip();
        if (trip == null || PortalView.drawing()) {
            return;
        }
        if (trip.sky() == LunarSight.Sky.MOON) {
            SkyRenderState sky = event.getLevelRenderState().skyRenderState;
            // The moon brightens from vanilla's rain-dimmed moon to full as the sky gives way to the void.
            float brightness = Mth.lerp(LunarTrip.skyDim(trip.progress()), sky.rainBrightness, FULL);
            // The frame's moon angle, before the half turn that took vanilla's own moon behind the caster.
            LunarTripBodies.drawMoon(event.getModelViewMatrix(), sky.moonAngle - HALF_TURN, sky.moonPhase,
                    LunarTrip.moonHalfTangent(trip.progress()), brightness);
        } else {
            MoonPhase planetPhase = LunarSight.planetPhase(event.getLevelRenderState().skyRenderState.moonPhase);
            LunarTripBodies.drawPlanet(event.getModelViewMatrix(), trip.fixation().direction(), planetPhase,
                    LunarTrip.planetHalfTangent(trip.progress()));
        }
    }
}
