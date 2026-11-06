package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;

/**
 * Urchin's shot, one per strike in flight: one of the urchin's own chrome
 * spikes shooting out of it toward the struck mob. It arms over three
 * ticks, the tip poking out past the bristles; fires over the next three,
 * standing in the mob on the tick the damage lands; then retracts over
 * six. The spike is the cone every other Urchin view draws, drawn through
 * Metal's chrome shader.
 * urchin-rests-bristled-and-spears
 * urchin-spines-return-to-the-original-spikes
 */
final class UrchinSpear {

    /** Ticks the tip takes to poke out past the bristles. */
    static final int ARM_TICKS = 3;
    /** How far from the urchin's base the armed tip stands, just past the bristles' reach. */
    static final float ARM_REACH = 1.2f;
    /** How far past the mob's body center the fired tip reaches. */
    static final float OVERSHOOT = 0.4f;
    /**
     * Ticks after the stab lands that the shot's retract shinks: a tick
     * into the retract, so the shink back follows the stab's shink rather
     * than sounding over it (decision urchin-spikes-shink-out-and-shink-back).
     */
    static final int RETRACT_SHINK_FRAME = 1;
    /** The shot spike's base radius, thicker than a bristle's so the longer spike keeps its taper. */
    static final float SHOT_BASE_RADIUS = 0.104f;
    private static final int OPAQUE = 0xFF;
    /** Below this squared distance the aim sits on the base and no shot points anywhere. */
    private static final float NO_AXIS = 1e-6f;
    /** The smoothstep's cubic coefficients, 3t^2 - 2t^3. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;

    private UrchinSpear() {
    }

    /**
     * How far the shot's tip stands from the urchin's base through a
     * strike: easing out to the armed reach, then punching out to the mob,
     * faster as it goes, and easing back in over the retract.
     *
     * @param age         the strike's age in ticks
     * @param partialTick the partial tick
     * @param strikeTick  the age the shot stands in the mob and its damage lands
     * @param length      the age the strike ends, fully retracted
     * @param reach       the tip's distance from the base at full extension
     * @return the tip's distance in blocks, zero before the strike and after it
     */
    static float tipDistance(int age, float partialTick, int strikeTick, int length, float reach) {
        float t = age + partialTick;
        float armed = Math.min(ARM_REACH, reach);
        if (t <= 0f || t >= length) {
            return 0f;
        }
        if (t < ARM_TICKS) {
            return armed * BurnoutGeometry.easeOutCubic(t / ARM_TICKS);
        }
        if (t < strikeTick) {
            float fired = (t - ARM_TICKS) / (strikeTick - ARM_TICKS);
            return armed + (reach - armed) * fired * fired;
        }
        return reach * bristleShare(age, partialTick, strikeTick, length);
    }

    /**
     * How far out the urchin's spines stand through a strike, as a share of
     * the landing flare's: easing out over the arm, held through the fire,
     * easing back in over the retract, so the spines bristle with each shot
     * and the urchin rests smooth between them (operator ruling 2026-10-11).
     *
     * @param age         the strike's age in ticks
     * @param partialTick the partial tick
     * @param strikeTick  the age the shot stands in the mob
     * @param length      the age the strike ends, fully retracted
     * @return the share in [0, 1], zero before the strike and after it
     */
    static float bristleShare(int age, float partialTick, int strikeTick, int length) {
        float t = age + partialTick;
        if (t <= 0f || t >= length) {
            return 0f;
        }
        if (t < ARM_TICKS) {
            return BurnoutGeometry.easeOutCubic(t / ARM_TICKS);
        }
        if (t < strikeTick) {
            return 1f;
        }
        float retracted = (t - strikeTick) / (length - strikeTick);
        return 1f - retracted * retracted * (SMOOTH_SQUARE - SMOOTH_CUBE * retracted);
    }

    /**
     * Emits one shot from the base toward the aimed point.
     *
     * @param pose  the pose entry, block-local
     * @param c     the chrome vertex consumer
     * @param base  the urchin's base, block-local
     * @param aim   the mob's body center, block-local
     * @param tip   the tip's distance from the base, {@link #tipDistance}
     * @param sweep the chrome band's place, a byte
     */
    static void emit(PoseStack.Pose pose, VertexConsumer c, Vector3f base, Vector3f aim, float tip, int sweep) {
        Vector3f dir = new Vector3f(aim).sub(base);
        if (tip <= 0f || dir.lengthSquared() < NO_AXIS) {
            return;
        }
        MetalExplosionVisual.emitSpike(pose, c,
                new MetalExplosionVisual.Spike(base, dir.normalize(), tip, SHOT_BASE_RADIUS), sweep, OPAQUE);
    }
}
