package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * A surface rule condition testing the column the rules stand on, reading its test once per column as the rules walk
 * down it.
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 */
final class ColumnTest implements SurfaceRules.Condition {

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

    private final SurfaceRules.Context context;
    private final ColumnPredicate predicate;
    private int testedX = Integer.MIN_VALUE;
    private int testedZ = Integer.MIN_VALUE;
    private boolean passes;

    ColumnTest(SurfaceRules.Context context, ColumnPredicate predicate) {
        this.context = context;
        this.predicate = predicate;
    }

    @Override
    public boolean test() {
        if (context.blockX != testedX || context.blockZ != testedZ) {
            testedX = context.blockX;
            testedZ = context.blockZ;
            passes = predicate.test(testedX, testedZ);
        }
        return passes;
    }
}
