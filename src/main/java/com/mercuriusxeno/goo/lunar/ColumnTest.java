package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.level.levelgen.material.MaterialRuleContext;

/**
 * A surface rule condition testing the column the rules stand on, reading its test once per column as the rules walk
 * down it.
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 */
final class ColumnTest extends MaterialRuleContext.LazyXZCondition {

    /**
     * A test of one column.
     */
    @FunctionalInterface
    interface ColumnPredicate {
        /**
         * @param x the column's x
         * @param z the column's z
         * @return whether the column passes
         */
        boolean test(int x, int z);
    }

    private final ColumnPredicate predicate;

    ColumnTest(MaterialRuleContext context, ColumnPredicate predicate) {
        super(context);
        this.predicate = predicate;
    }

    @Override
    protected boolean compute() {
        return predicate.test(context.blockX(), context.blockZ());
    }
}
