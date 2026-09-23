package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Frost goo: the blob impact places a chain marker. The marker's
 * post-fuse behavior is the frost ability's program; the legacy profile
 * in {@code ChainProfiles} runs the {@code frost_sphere} program.
 */
public final class FrostBehavior implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        EffectBlockPlacement.frostColdSnap(level, pos, targetFace);
    }
}
