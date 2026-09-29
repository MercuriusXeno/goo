package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;

/**
 * Unstable goo's burnout explosion, the design the operator settled
 * (decision elemental-explosion-per-type): a neon fireball and ring. A
 * sphere at the marker's center grows on an ease-out to the blast radius,
 * the explode step's power, over 16 ticks. Its fragment shader
 * ({@code unstable_explosion.fsh}) draws a white-green core fading to a
 * 39FF14 rim, with animated noise that makes the surface crackle and
 * flicker, the goo's instability. A thin additive shockwave ring runs out
 * ahead of the sphere in the placed face's plane. Both fade to nothing by
 * the end. Progress reaches the shader through the vertex color, since a
 * core pipeline takes no per-draw uniforms: red carries progress, green
 * marks the ring and blue carries the ring's radial position.
 */
public final class UnstableExplosionVisual implements BurnoutVisual {

    /** The one instance the burnout registry holds. */
    public static final UnstableExplosionVisual INSTANCE = new UnstableExplosionVisual();

    /** Ticks the explosion plays. */
    static final int DURATION_TICKS = 16;
    /** How far past the sphere the shockwave ring reaches, as a multiple of the blast radius. */
    static final float RING_REACH = 1.35f;
    /** The ring band's inner edge, as a fraction of the ring's radius. */
    static final float RING_INNER = 0.85f;
    /** The blast radius drawn when the ability's explode step cannot be read. */
    static final float FALLBACK_REACH = 2f;

    private static final int OPAQUE = 0xFF;
    private static final int RING_SEGMENTS = 48;

    private UnstableExplosionVisual() {
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return GooTypes.UNSTABLE;
    }

    @Override
    public int durationTicks() {
        return DURATION_TICKS;
    }

    @Override
    public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
        float progress = burnout.progress(frame.gameTime());
        float reach = blastReach(burnout);
        float sphere = sphereRadius(progress, reach);
        float ring = ringRadius(progress, reach);
        int progressByte = NetherDiscMesh.toByte(progress);
        BurnoutGeometry.drawAtMarker(frame, burnout.pos(), GooRenderTypes.UNSTABLE_EXPLOSION_TYPE, (pose, c) -> {
            BurnoutGeometry.emitSphere(pose, c, sphere, ARGB.color(OPAQUE, progressByte, 0, 0));
            BurnoutGeometry.emitAnnulus(pose, c, burnout.placedFace(), 0f, ring * RING_INNER, ring,
                    RING_SEGMENTS, (angle, outer) -> ARGB.color(OPAQUE, progressByte, OPAQUE, outer ? OPAQUE : 0));
        });
    }

    /**
     * The fireball's radius: an ease-out growth to the blast radius.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param reach    the blast radius in blocks
     * @return the sphere's radius in blocks
     */
    static float sphereRadius(float progress, float reach) {
        return reach * BurnoutGeometry.easeOutCubic(progress);
    }

    /**
     * The shockwave ring's radius, running out ahead of the sphere.
     *
     * @param progress the explosion's progress in [0, 1]
     * @param reach    the blast radius in blocks
     * @return the ring's outer radius in blocks
     */
    static float ringRadius(float progress, float reach) {
        return sphereRadius(progress, reach) * RING_REACH;
    }

    /**
     * The blast radius: the power of the ability's explode step at the
     * marker's stack count, read off the synced ability.
     *
     * @param burnout the burnout
     * @return the blast radius in blocks
     */
    private static float blastReach(ChainBurnouts.Burnout burnout) {
        return SyncedSteps.first(burnout.abilityId(), ExplodeStep.class)
                .map(step -> step.power().evaluateFloat(burnout.variables()))
                .orElse(FALLBACK_REACH);
    }
}
