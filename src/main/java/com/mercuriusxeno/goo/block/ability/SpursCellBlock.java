package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SpursStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;

/**
 * Osseous's Spurs cell: the volume cell bound to Osseous's goo, which shoots
 * a bone spike at a living thing stepping on it, by the strike, cooldown,
 * spend chance and shot speed Spurs' step names. The shot credits the player
 * who placed the cell.
 * spurs-shoots-bone-spikes-at-walkers
 * standing-goo-thing-credits-whoever-placed-it
 */
public class SpursCellBlock extends VolumeCellBlock {

    /**
     * Creates a Spurs cell.
     *
     * @param properties the block properties
     * @param binding    the Spurs binding
     */
    public SpursCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * A Spurs cell's shot hurts, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }

    /**
     * Shoots a bone spike at a living thing stepping on the cell, on the server.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        if (level instanceof ServerLevel server) {
            SpursStep.in(layingProgram(level)).ifPresent(spurs -> SpursShot.shoot(server, pos, state, entity,
                    spurs, creditedPlayerAt(server, pos)));
        }
        super.stepOn(level, pos, state, entity);
    }
}
