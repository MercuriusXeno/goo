package com.mercuriusxeno.goo.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import org.jspecify.annotations.NonNull;

/**
 * Non-flowing goo fluid variants. Goo sits in place at the level it was
 * placed and never spreads, decays, or changes state on its own. Overriding
 * tick() is sufficient because spread() is called from tick(), so no goo
 * block ever reaches a block it did not start in. An entity standing in goo
 * is put out when the type stamped at that block extinguishes (decision
 * generic-goo-fluids).
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass") // container for Source/Flowing inner classes
public final class GooFluid {

    private GooFluid() {}

    /**
     * Puts out an entity inside the block when the type stamped there
     * extinguishes, read through the positional fluid type overload.
     *
     * @param level         the level
     * @param pos           the goo block position
     * @param fluidState    the fluid state at the position
     * @param effectApplier the collector of inside-block effects
     */
    static void extinguishByStampedType(Level level, BlockPos pos, FluidState fluidState,
                                        InsideBlockEffectApplier effectApplier) {
        if (fluidState.getFluidType().canExtinguish(fluidState, level, pos)) {
            effectApplier.apply(InsideBlockEffectType.EXTINGUISH);
        }
    }

    /**
     * Source (full-block) goo fluid. Overrides tick to prevent
     * any autonomous state changes or spreading.
     */
    public static class Source extends BaseFlowingFluid.Source {

        /**
         * Creates a non-flowing source fluid with the given properties.
         *
         * @param properties the fluid properties
         */
        public Source(Properties properties) {
            super(properties);
        }

        /**
         * No-op: goo does not tick, spread, or decay.
         *
         * @param level      the server level
         * @param pos        the block position
         * @param blockState the block state at the position
         * @param fluidState the fluid state at the position
         */
        @Override
        public void tick(@NonNull ServerLevel level, @NonNull BlockPos pos,
                         @NonNull BlockState blockState, @NonNull FluidState fluidState) {
            // intentionally empty - goo stays where placed
        }

        @Override
        protected void entityInside(@NonNull Level level, @NonNull BlockPos pos, @NonNull Entity entity,
                                    @NonNull InsideBlockEffectApplier effectApplier) {
            extinguishByStampedType(level, pos, level.getFluidState(pos), effectApplier);
        }
    }

    /**
     * Flowing (partial-level) goo fluid. Overrides tick to prevent
     * any autonomous state changes. Used for levels 1-7.
     */
    public static class Flowing extends BaseFlowingFluid.Flowing {

        /**
         * Creates a non-flowing flowing-variant fluid with the given properties.
         *
         * @param properties the fluid properties
         */
        public Flowing(Properties properties) {
            super(properties);
        }

        /**
         * No-op: goo does not tick, spread, or decay.
         *
         * @param level      the server level
         * @param pos        the block position
         * @param blockState the block state at the position
         * @param fluidState the fluid state at the position
         */
        @Override
        public void tick(@NonNull ServerLevel level, @NonNull BlockPos pos,
                         @NonNull BlockState blockState, @NonNull FluidState fluidState) {
            // intentionally empty - goo stays where placed
        }

        @Override
        protected void entityInside(@NonNull Level level, @NonNull BlockPos pos, @NonNull Entity entity,
                                    @NonNull InsideBlockEffectApplier effectApplier) {
            extinguishByStampedType(level, pos, level.getFluidState(pos), effectApplier);
        }
    }
}
