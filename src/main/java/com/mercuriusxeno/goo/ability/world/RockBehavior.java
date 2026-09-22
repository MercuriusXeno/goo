package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Rock world effect: places (or stacks) a chain marker on impact via
 * {@link EffectBlockPlacement}. Post-fuse behavior is the rock ability's
 * program, a {@code progressive_area} step with silk-break, rock-dust
 * visuals and stone-break audio; see {@code rock_tunnel} and
 * {@code rock_flat}, and the legacy profile in {@code ChainProfiles}
 * runs {@code rock_tunnel}.
 */
public final class RockBehavior implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.rockImplosion(level, pos, targetFace);
    }
}
