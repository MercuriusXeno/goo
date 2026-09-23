package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Nether world effect: places (or stacks) a chain marker on impact via
 * {@link EffectBlockPlacement}. Post-fuse behavior is the nether ability's
 * program, a {@code phased} step that expands a black hole, consumes the
 * valued blocks in it and pops them as blobs; see {@code nether_black_hole},
 * which the legacy profile in {@code ChainProfiles} also runs.
 */
public final class NetherEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.netherConvert(level, pos, targetFace);
    }
}
