package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Metal world effect: places (or stacks) a chain marker on impact via
 * {@link EffectBlockPlacement}. Post-fuse behavior is the metal ability's
 * program, a {@code field_effect} step impaling what enters its radius;
 * see {@code metal_spikes}, which the legacy profile in
 * {@code ChainProfiles} also runs.
 */
public final class MetalEffect implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.metalSpikeTrap(level, pos, targetFace);
    }
}
