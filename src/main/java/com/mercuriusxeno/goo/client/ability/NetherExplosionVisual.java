package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Nether goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): an inward rush, an implosion rather than a
 * blast. A sphere of dark red streaks starts at 4 blocks and rushes inward
 * onto the marker over the 15 ticks of the black hole's expand, slow and
 * then fast. Its fragment shader ({@code nether_explosion.fsh}) breaks the
 * shell into streaking specks that brighten from 8B0000 through B32828 to
 * white-hot as they near the center, so matter reads as falling into the
 * hole as it opens. Additive, fading as the hole's own body covers the
 * center. The vertex color carries progress in red, how near the rush has
 * come in green and how much of it is left in blue, since a core pipeline
 * takes no per-draw uniforms.
 */
public final class NetherExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final NetherExplosionVisual INSTANCE = new NetherExplosionVisual();

    /** Ticks the explosion plays: the black hole's expand phase. */
    static final int DURATION_TICKS = 15;
    /** The radius the rush starts from, in blocks. */
    static final float RUSH_START = 4f;
    /** The share of the explosion after which the rush fades under the hole's body. */
    static final float FADE_START = 0.7f;
    private static final int OPAQUE = 0xFF;

    private NetherExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.NETHER;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float radius = rushRadius(progress);
        int color = ARGB.color(OPAQUE, NetherDiscMesh.toByte(progress),
                NetherDiscMesh.toByte(1f - radius / RUSH_START), NetherDiscMesh.toByte(remaining(progress)));
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.NETHER_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, radius, color));
    }

    /**
     * The rush's radius: from RUSH_START down onto the marker, slow and
     * then fast, as matter falling in.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the shell's radius in blocks
     */
    static float rushRadius(float progress) {
        return RUSH_START * (1f - progress * progress);
    }

    /**
     * How much of the rush is left: whole until FADE_START, then fading
     * to nothing as the hole's body covers the center.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the rush's remaining strength in [0, 1]
     */
    static float remaining(float progress) {
        return BurnoutGeometry.fadeAfter(progress, FADE_START);
    }
}
