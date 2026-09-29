package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Blaze goo's burnout explosion, the design the operator settled (decision
 * elemental-explosion-per-type): a flame bloom. A dome of fire rises out of
 * the placed face and grows to 2.5 blocks over 14 ticks on an ease-out. Its
 * fragment shader ({@code blaze_explosion.fsh}) scrolls noise outward along
 * the face's normal so tongues of flame lick outward, graded from a
 * white-yellow base through FF8E28 to a deep red tip that burns off to
 * nothing, and discards the half of the sphere behind the face. Additive,
 * so it lights what it covers, fading out over the last half. The vertex
 * color carries progress in red, the placed face's ordinal in green and
 * the flame's remaining strength in blue, since a core pipeline takes no
 * per-draw uniforms. A blaze_tunnel marker
 * plays no burnout explosion: its per-layer flame carries the moment.
 */
public final class BlazeExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final BlazeExplosionVisual INSTANCE = new BlazeExplosionVisual();

    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = 14;
    /** The dome's full radius in blocks. */
    static final float DOME_REACH = 2.5f;
    /** The share of the explosion after which the flames burn off. */
    static final float BURN_OFF_START = 0.5f;
    /** How far the dome's center sits from the block center along the face's step: on the face plane. */
    private static final float DOME_LIFT = -0.5f;
    private static final int OPAQUE = 0xFF;

    private BlazeExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.BLAZE;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float radius = domeRadius(progress);
        int color = ARGB.color(OPAQUE, NetherDiscMesh.toByte(progress), burnout.placedFace().ordinal(),
                NetherDiscMesh.toByte(flameStrength(progress)));
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.BLAZE_EXPLOSION_TYPE, (pose, c) ->
                BurnoutGeometry.emitSphere(pose, c, burnout.placedFace(), DOME_LIFT, radius, color));
    }

    /**
     * The flame dome's radius: an ease-out growth to its full reach.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the dome's radius in blocks
     */
    static float domeRadius(float progress) {
        return DOME_REACH * BurnoutGeometry.easeOutCubic(progress);
    }

    /**
     * How much of the flame is left: whole until BURN_OFF_START, then
     * burning off to nothing at the end.
     *
     * @param progress the explosion's progress in [0, 1]
     * @return the flame's remaining strength in [0, 1]
     */
    static float flameStrength(float progress) {
        return BurnoutGeometry.fadeAfter(progress, BURN_OFF_START);
    }
}
