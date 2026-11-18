package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SpikesStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Metal's Spikes cell: the volume cell bound to Metal's goo, which impales
 * a living thing stepping on it, by the strike, cooldown and spend chance
 * Spikes' step names. The trigger is the step, block-local, so a thing
 * standing still on the goo is left alone until it moves; the cell takes
 * the step of what stands on it at any height, so a one-layer coat strikes
 * as a full one does (operator ruling 2026-11-17). The strike names the
 * player who placed the cell.
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * urchin-rests-bristled-and-spears
 * standing-goo-thing-credits-whoever-placed-it
 */
public class SpikesCellBlock extends VolumeCellBlock {

    /**
     * Creates a Spikes cell.
     *
     * @param properties the block properties
     * @param binding    the Spikes binding
     */
    public SpikesCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * A Spikes cell's strike hurts, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }

    /**
     * Impales a living thing stepping on the cell, on the server.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        if (level instanceof ServerLevel server) {
            SpikesStep.in(layingProgram(level)).ifPresent(spikes -> SpikesImpale.impale(server, pos, state, entity,
                    spikes, creditedPlayerAt(server, pos)));
        }
        super.stepOn(level, pos, state, entity);
    }
}
