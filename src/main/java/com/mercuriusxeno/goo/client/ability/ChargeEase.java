package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Charge;

/**
 * The radius a charge-sized cast's spheroid draws at while the hold grows it:
 * up from nothing across the charge's first stage, then easing from each
 * stage's whole size to the next across that stage's ticks, so it never pops
 * in nor jumps a block, and stands at the stage's whole size as each stage
 * completes; a release still casts the whole stage reached.
 * decision blast-eases-between-stages-at-a-second-each
 */
public final class ChargeEase {

    private static final double SMOOTHSTEP_CUBE = 2;
    private static final double SMOOTHSTEP_SQUARE = 3;

    private ChargeEase() {
    }

    /**
     * The radius the spheroid draws at a moment of the hold.
     *
     * @param charge    the cast's sizing charge
     * @param heldTicks the ticks the hold has run, partial ticks included
     * @return the radius in blocks
     */
    public static double drawnRadius(Charge charge, float heldTicks) {
        double stages = charge.maxSize() - charge.openingSize();
        if (stages <= 0 || charge.maxTicks() <= 0) {
            return charge.stageAt(Math.round(heldTicks));
        }
        double progress = Math.clamp(heldTicks / (charge.maxTicks() / stages), 0, stages);
        int stage = (int) Math.floor(progress);
        if (stage >= stages) {
            return charge.maxSize();
        }
        double from = stage == 0 ? 0 : charge.openingSize() + stage;
        double to = charge.openingSize() + stage + 1;
        return from + (to - from) * smoothstep(progress - stage);
    }

    private static double smoothstep(double t) {
        return t * t * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * t);
    }
}
