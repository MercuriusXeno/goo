package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Blaze world effect: places (or stacks) a chain marker on impact via
 * {@link EffectBlockPlacement}. Post-fuse behavior is the blaze ability's
 * program, a {@code progressive_area} step with fortune-smelt, blaze-flame
 * visuals and generic-explode audio; see {@code blaze_tunnel} and
 * {@code blaze_flat}, and the legacy profile in {@code ChainProfiles}
 * runs {@code blaze_tunnel}.
 */
public final class BlazeBehavior implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.blazeExplosion(level, pos, targetFace);
    }
}
