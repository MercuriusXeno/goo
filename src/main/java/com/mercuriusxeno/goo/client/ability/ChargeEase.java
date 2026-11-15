package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.Charge;

/**
 * The radius a charge-sized cast's spheroid draws at while the hold grows it:
 * up from nothing to the size the charge opens at over the first quarter
 * second, then sitting at the size a release would cast and easing into the
 * next size over the quarter second after each step, so it never pops in,
 * never jumps a block and never runs ahead of the cast.
 * decision blast-eases-between-stages-at-a-second-each
 */
public final class ChargeEase {

    /** The ticks each ease runs: a quarter second. */
    static final float EASE_TICKS = 5f;
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
        if (heldTicks < EASE_TICKS) {
            return charge.openingSize() * smoothstep(Math.max(0, heldTicks) / EASE_TICKS);
        }
        double stageTicks = charge.maxTicks() / stages;
        int step = (int) Math.min(Math.floor(heldTicks / stageTicks), stages);
        double size = charge.openingSize() + step;
        double sinceStep = heldTicks - step * stageTicks;
        if (step == 0 || sinceStep >= EASE_TICKS) {
            return size;
        }
        return size - 1 + smoothstep(sinceStep / EASE_TICKS);
    }

    private static double smoothstep(double t) {
        return t * t * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * t);
    }
}
