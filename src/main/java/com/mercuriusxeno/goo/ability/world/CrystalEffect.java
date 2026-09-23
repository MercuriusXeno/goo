package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Crystal world effect: places (or stacks) a chain marker on impact via
 * {@link EffectBlockPlacement}. Post-fuse behavior is the crystal ability's
 * program, a {@code field_effect} step shredding what moves through its
 * cloud; see {@code crystal_cloud}, which the legacy profile in
 * {@code ChainProfiles} also runs.
 */
public final class CrystalEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.crystalCloud(level, pos, targetFace);
    }
}
