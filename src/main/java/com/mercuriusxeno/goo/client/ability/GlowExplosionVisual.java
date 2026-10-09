package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Glow goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): an aurora bloom over the crystal the
 * marker just placed, drawn from the burnout alone since the marker is gone
 * the tick it fires. A soft dome of light rises out of the placed face over
 * 20 ticks on an ease-out, to about 1.25 blocks over Bulb's one crystal
 * (decision bulb-one-model-max-light-beacon-combo). The bloom is a whole
 * sphere about the landing cell's center, the same whichever face the blob
 * lands on (decision burnouts-are-whole-spheres). Its fragment shader
 * ({@code glow_explosion.fsh}) draws vertical aurora bands running up world
 * up, FFFF28 at the base shading to FFD700 and a pale white crown, sliding
 * slowly around the sphere like the fade walls' curtains. Additive,
 * breathing brighter once then fading as the crystal takes over the light.
 * The vertex color carries progress in red and the bloom's brightness in
 * blue, since a core pipeline takes no per-draw uniforms.
 */
public final class GlowExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final GlowExplosionVisual INSTANCE = new GlowExplosionVisual();

    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = 20;
    /** The dome's full radius in blocks over Bulb's one crystal. */
    static final float DOME_REACH = 1.25f;
    /** The share of the explosion at which the bloom breathes brightest. */
    static final float BREATH_PEAK = 0.3f;
    private static final int OPAQUE = 0xFF;

    private GlowExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.GLOW;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float radius = domeRadius(progress);
        int color = domeColor(progress, OPAQUE);
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.GLOW_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, radius, color));
    }

    /**
     * Packs the sphere's vertex color: opacity in alpha, which the shader
     * multiplies into its output alpha, progress in red and the bloom's
     * brightness in blue.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param alpha    the sphere's opacity as a byte
     * @return the packed ARGB color
     */
    static int domeColor(float progress, int alpha) {
        return ARGB.color(alpha, NetherDiscMesh.toByte(progress), 0,
                NetherDiscMesh.toByte(brightness(progress)));
    }

    /**
     * The aurora dome's radius: an ease-out growth to its reach.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the dome's radius in blocks
     */
    static float domeRadius(float progress) {
        return DOME_REACH * BurnoutGeometry.easeOutCubic(progress);
    }

    /**
     * The bloom's brightness: breathing up to full at BREATH_PEAK, then
     * fading to nothing as the crystal takes over the light.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the brightness in [0, 1]
     */
    static float brightness(float progress) {
        if (progress < BREATH_PEAK) {
            return BurnoutGeometry.easeOutCubic(progress / BREATH_PEAK);
        }
        return BurnoutGeometry.fadeAfter(progress, BREATH_PEAK);
    }
}
