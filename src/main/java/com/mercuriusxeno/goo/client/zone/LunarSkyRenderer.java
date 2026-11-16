package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Draws goo:lunar's sky in place of vanilla's: the pocket's sea of dim stars, then LunarSky's blue overworld star
 * over it, a bright core inside two wider, fainter halos turned across it so it reads as a glowing point. No sun,
 * moon or cloud draws.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 */
public final class LunarSkyRenderer implements CustomSkyboxRenderer {

    private static final String LABEL = "goo_lunar_overworld_star";
    /** The core and its two halos, each wider and fainter than the last. */
    private static final float[] HALO_SCALES = {1F, 2.2F, 4F};
    private static final float[] HALO_STRENGTHS = {1F, 0.35F, 0.12F};
    /** Each halo turns an eighth of a turn from the last, so their corners spread as rays. */
    private static final float HALO_TURN = 0.125F;

    private final PocketStarSkyRenderer sea = new PocketStarSkyRenderer();
    private PocketStarSkyRenderer.@Nullable SkyQuads overworldStar;

    @Override
    public boolean renderSky(LevelRenderState levelRenderState, SkyRenderState skyRenderState,
            Matrix4fc modelViewMatrix, Runnable setupFog) {
        sea.renderSky(levelRenderState, skyRenderState, modelViewMatrix, setupFog);
        if (overworldStar == null) {
            overworldStar = buildOverworldStar(LABEL);
        }
        PocketStarSkyRenderer.drawQuads(GooRenderTypes.OVERWORLD_STAR, overworldStar, modelViewMatrix, LABEL);
        return true;
    }

    /**
     * Builds the blue overworld star's core and halos at the sky's distance along its direction, as lunar's sky
     * draws it; the gate trip home draws the same star swelling.
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
