package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.SpiritBitePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The spirits in Leech goo: on their cadence they bite a living thing the
 * goo caught, dealing their damage with no entity as its source, so the bite
 * pays drained life to nobody, and each bite rolls a chance to take a layer
 * from the cell under the bitten thing, a cell losing its last layer gone.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 * leech-goo-has-a-chance-to-deplete-when-the-spirits-damage
 */
public final class LeechSpirits {

    private static final double BODY_CENTER = 0.5;
    private static final float BITE_VOLUME = 0.6f;
    private static final float BITE_PITCH = 0.7f;
    private static final float BITE_PITCH_SPREAD = 0.4f;

    private LeechSpirits() {
    }

    /**
     * The layers a cell holds after the spirits bite a thing on it: one
     * fewer where the draw falls under the deplete chance, as many otherwise.
     *
     * @param layers the layers the cell held
     * @param draw   a random draw in [0, 1)
     * @param chance the chance a bite takes a layer
     * @return the layers left, 0 where the cell is gone
     */
    static int layersAfterBite(int layers, double draw, double chance) {
        return draw < chance ? layers - 1 : layers;
    }

    /**
     * Bites a living thing a Leech cell caught, on the ticks the spirits'
     * cadence falls on; a bite its hurt immunity turns away rolls nothing,
     * so a thing touching several cells in one tick is bitten once.
     *
     * @param level  the level
     * @param cell   the cell that caught it
     * @param state  the cell's state
     * @param living the thing caught
     * @param leech  the laying ability's leech goo step, naming the bite
     */
    static void bite(ServerLevel level, BlockPos cell, BlockState state, LivingEntity living, LeechGooStep leech) {
        if (level.getGameTime() % leech.spiritTicks() != 0
                || !living.hurtServer(level, level.damageSources().magic(), leech.spiritDamage())) {
            return;
        }
        EntityVisuals.sendToWatchers(living, new SpiritBitePayload(living.getId()));
        level.playSound(null, living.getX(), living.getY(BODY_CENTER), living.getZ(), SoundEvents.SOUL_ESCAPE,
                SoundSource.BLOCKS, BITE_VOLUME, BITE_PITCH + level.getRandom().nextFloat() * BITE_PITCH_SPREAD);
        int left = layersAfterBite(state.getValue(VolumeCellBlock.LAYERS), level.getRandom().nextDouble(),
                leech.depleteChance());
        if (left != state.getValue(VolumeCellBlock.LAYERS)) {
            level.setBlock(cell, left > 0 ? state.setValue(VolumeCellBlock.LAYERS, left)
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
