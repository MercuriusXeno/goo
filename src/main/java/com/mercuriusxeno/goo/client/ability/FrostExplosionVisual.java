package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Frost goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): a frost nova. A sphere of cold air
 * expands from the marker to 3 blocks over 16 ticks on an ease-out. Its
 * fragment shader ({@code frost_explosion.fsh}) draws frost crystallizing
 * across the surface: a voronoi pattern of ice facets that sharpens from a
 * soft D5FFFF haze into crisp ADD8E6 edges with white glints, under a bright
 * fresnel rim, alpha blended, holding for a beat then fading out. The
 * vertex color carries progress in red, how far the frost has crystallized
 * in green and how much of the nova is left in blue, since a core pipeline
 * takes no per-draw uniforms.
 */
public final class FrostExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final FrostExplosionVisual INSTANCE = new FrostExplosionVisual();

    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = 16;
    /** The nova's full radius in blocks. */
    static final float NOVA_REACH = 3f;
    /** The share of the explosion over which the facets sharpen from haze to crisp edges. */
    static final float CRYSTALLIZE_SPAN = 0.6f;
    /** The share of the explosion after which the nova fades: the beat it holds ends here. */
    static final float FADE_START = 0.7f;
    private static final int OPAQUE = 0xFF;

    private FrostExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.FROST;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float radius = novaRadius(progress);
        int color = ARGB.color(OPAQUE, NetherDiscMesh.toByte(progress),
                NetherDiscMesh.toByte(crystallized(progress)), NetherDiscMesh.toByte(remaining(progress)));
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.FROST_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, radius, color));
    }

    /**
     * The nova's radius: an ease-out growth to its full reach.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the nova's radius in blocks
     */
    static float novaRadius(float progress) {
        return NOVA_REACH * BurnoutGeometry.easeOutCubic(progress);
    }

    /**
     * How far the frost has crystallized: haze at the start, crisp facets
     * once CRYSTALLIZE_SPAN has passed.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the crystallization in [0, 1]
     */
    static float crystallized(float progress) {
        return Math.min(1f, progress / CRYSTALLIZE_SPAN);
    }

    /**
     * How much of the nova is left: whole through the beat it holds, then
     * fading to nothing at the end.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the nova's remaining opacity in [0, 1]
     */
    static float remaining(float progress) {
        if (progress <= FADE_START) {
            return 1f;
        }
        return Math.max(0f, 1f - (progress - FADE_START) / (1f - FADE_START));
    }
}
