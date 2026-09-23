package com.mercuriusxeno.goo.ability.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Glow world effect: places (or stacks) a chain marker via
 * {@link EffectBlockPlacement}. Post-fuse behavior is the
 * {@code glow_crystal} ability's program, one {@code place_block} step;
 * the legacy profile in {@code ChainProfiles} runs that same program.
 */
public final class GlowBehavior implements WorldEffect {

    @Override
    public void apply(Level level, BlockPos pos, @Nullable Direction targetFace) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        EffectBlockPlacement.glowCrystal(level, pos, targetFace);
    }
}
