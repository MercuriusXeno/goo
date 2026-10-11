package com.mercuriusxeno.goo.block.ability;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

/**
 * Weird's bouncy pad: whatever falls on it takes no fall damage and is sent
 * back up as fast as it came down, and the pad removes itself when the tick
 * its landing scheduled comes due.
 * weird-bounces-and-softens-harm
 */
public class BouncePadBlock extends HalfTransparentBlock {

    public static final MapCodec<BouncePadBlock> CODEC = simpleCodec(BouncePadBlock::new);

    /** The share of its fall speed a living thing keeps on the way back up. */
    static final double LIVING_REBOUND = 1.0;
    /** The share of its fall speed anything else keeps on the way back up. */
    static final double OTHER_REBOUND = 0.8;

    /**
     * Creates the block.
     *
     * @param properties the block properties
     */
    public BouncePadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<? extends HalfTransparentBlock> codec() {
        return CODEC;
    }

    @Override
    public void fallOn(@NonNull Level level, @NonNull BlockState state, @NonNull BlockPos pos,
                       @NonNull Entity entity, double fallDistance) {
        entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
    }

    @Override
    public void updateEntityMovementAfterFallOn(@NonNull BlockGetter level, @NonNull Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        if (motion.y >= 0) {
            super.updateEntityMovementAfterFallOn(level, entity);
            return;
        }
        double rebound = entity instanceof LivingEntity ? LIVING_REBOUND : OTHER_REBOUND;
        entity.setDeltaMovement(motion.x, -motion.y * rebound, motion.z);
    }

    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        level.removeBlock(pos, false);
    }
}
