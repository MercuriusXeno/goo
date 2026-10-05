package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Crystal goo's burnout explosion, the design the operator settled
 * (decision elemental-explosion-per-type): a prism burst. A glassy sphere
 * grows from the marker to the shard cloud's radius over the cloud's 10
 * expand ticks on an ease-out. Its fragment shader
 * ({@code crystal_explosion.fsh}) cuts it into sharp voronoi facets, each
 * edge splitting light into a thin rainbow band about 77E9FF with bright
 * cyan glints; then over 6 more ticks the shell shatters, its facets
 * dropping out one by one by cell noise until none remain, leaving the
 * floating splinters in their place. Alpha blended. The vertex color
 * carries progress in red and how far the shell has shattered in blue,
 * since a core pipeline takes no per-draw uniforms.
 */
public final class CrystalExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final CrystalExplosionVisual INSTANCE = new CrystalExplosionVisual();

    /** Ticks the sphere grows: the shard cloud's expand. */
    static final int GROW_TICKS = 10;
    /** Ticks the shell takes to shatter. */
    static final int SHATTER_TICKS = 6;
    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = GROW_TICKS + SHATTER_TICKS;
    /** The cloud radius drawn when the ability's field-effect step cannot be read. */
    static final float FALLBACK_REACH = 4.5f;
    private static final int OPAQUE = 0xFF;

    private CrystalExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.CRYSTAL;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float radius = shellRadius(progress, cloudReach(burnout));
        int color = shellColor(progress, OPAQUE);
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.CRYSTAL_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, radius, color));
    }

    /**
     * Packs the shell's vertex color: opacity in alpha, which the shader
     * multiplies into its output alpha, progress in red and how far the
     * shell has shattered in blue.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param alpha    the shell's opacity as a byte
     * @return the packed ARGB color
     */
    static int shellColor(float progress, int alpha) {
        return ARGB.color(alpha, NetherDiscMesh.toByte(progress), 0, NetherDiscMesh.toByte(shattered(progress)));
    }

    /**
     * The glass shell's radius: an ease-out growth to the cloud's radius
     * over GROW_TICKS, held there while it shatters.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param reach    the shard cloud's radius in blocks
     * @return the shell's radius in blocks
     */
    static float shellRadius(float progress, float reach) {
        float grown = Math.min(1f, progress * DURATION_TICKS / GROW_TICKS);
        return reach * BurnoutGeometry.easeOutCubic(grown);
    }

    /**
     * How far the shell has shattered: none while it grows, then every
     * facet gone by the end.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the share of facets dropped, in [0, 1]
     */
    static float shattered(float progress) {
        float tick = progress * DURATION_TICKS;
        return Math.min(1f, Math.max(0f, (tick - GROW_TICKS) / SHATTER_TICKS));
    }

    /**
     * The shard cloud's radius at the burnout's stack count, read off the synced ability.
     *
     * @param burnout the burnout
     * @return the cloud's radius in blocks
     */
    private static float cloudReach(ChainBurnouts.Burnout burnout) {
        return SyncedSteps.first(burnout.abilityId(), FieldEffectStep.class)
                .map(step -> step.radius().evaluateFloat(burnout.variables()))
                .orElse(FALLBACK_REACH);
    }
}
