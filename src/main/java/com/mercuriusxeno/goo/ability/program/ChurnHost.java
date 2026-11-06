package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can lift the vein under the block it landed on
 * (decision churn-pulls-a-vein-up-as-a-fluid-chain).
 */
public interface ChurnHost extends StepHost {

    /**
     * Lifts the vein under the landed block one layer, finding it the first
     * time it is called.
     *
     * @param depth how far down the landed block's column the churn reaches for a mineral
     * @return true while a vein cell rose this tick
     */
    boolean pullVein(int depth);
}
