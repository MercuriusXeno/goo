package com.mercuriusxeno.goo.ability.program;

/**
 * A host that can churn the column under the block it landed on
 * (decision churn-rotates-a-plus-shaped-column).
 */
public interface ChurnHost extends StepHost {

    /**
     * Turns the column under the landed block one step: the core rises a
     * layer and the strips sink one.
     *
     * @param depth the column's layers, the landed block's layer the top
     */
    void churnColumn(int depth);
}
