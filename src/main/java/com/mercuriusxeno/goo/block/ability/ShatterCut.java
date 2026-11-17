package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.EntityScan;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ShatterStep;
import com.mercuriusxeno.goo.ability.program.StepContext;
import com.mercuriusxeno.goo.ability.program.Variables;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Set;

/**
 * The cut the glass shards over a Shatter cell make on a living thing moving
 * through them: the shatter step's strike runs on the thing on each tick its
 * interval, read on the thing, divides, so a sprinter is cut faster, and
 * each cut rolls the step's deplete chance of taking one layer from the cell
 * under the shards, the cell gone with its last layer. A cut whose roll
 * misses leaves the goo whole; the damage is rapid, so a layer spent on
 * every cut would leave the goo gone in an instant.
 * each-shatter-cut-has-a-chance-to-deplete-a-layer
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 */
final class ShatterCut {

    private ShatterCut() {
    }

    /**
     * Cuts a thing moving through the shards over a cell, where every filter
     * keeps it and its interval falls on this tick, and rolls the cell's depletion.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param state  the cell's state
     * @param entity the thing on the cell
     * @param step   the shatter step the cell's laying ability runs
     */
    static void cut(ServerLevel level, BlockPos pos, BlockState state, Entity entity, ShatterStep step) {
        if (!(entity instanceof LivingEntity living) || !EntityScan.passes(living, Set.copyOf(step.where()), null)) {
            return;
        }
        EntityHost host = new EntityHost(level, living, null);
        StepContext onTheThing = new StepContext(host, 0, 0);
        if (level.getGameTime() % Math.max(1, step.interval().evaluateInt(onTheThing)) != 0) {
            return;
        }
        new ProgramBehavior(step.strike()).tick(host);
        int layers = state.getValue(VolumeCellBlock.LAYERS);
        int left = layersAfterCut(layers, level.getRandom().nextDouble(), depleteChanceOf(step, onTheThing));
        if (left < layers) {
            level.setBlock(pos, left > 0 ? state.setValue(VolumeCellBlock.LAYERS, left)
                    : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * The layers a cell holds after one cut: one fewer where the cut's draw
     * falls under the deplete chance, as many where it does not.
     *
     * @param layers the layers the cell holds before the cut
     * @param draw   the cut's random draw in [0, 1)
     * @param chance the deplete chance in [0, 1]
     * @return the layers left, 0 where the cut takes the last
     */
    static int layersAfterCut(int layers, double draw, double chance) {
        return draw < chance ? layers - 1 : layers;
    }

    /**
     * The chance a cut takes a layer, the shatter step's deplete chance read on the cut thing.
     *
     * @param step       the shatter step
     * @param onTheThing the variables of the thing the cut lands on
     * @return the chance in [0, 1]
     */
    static double depleteChanceOf(ShatterStep step, Variables onTheThing) {
        return step.depleteChance().evaluate(onTheThing);
    }
}
