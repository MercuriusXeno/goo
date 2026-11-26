package com.mercuriusxeno.goo.ability.nether;

import com.mercuriusxeno.goo.ability.program.GloomStep;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Runs Gloom on a slime in it or on it: where the cell holds the turn's
 * layers the slime becomes a random hostile mob of its biome and the cell
 * spends them; a cell holding fewer leaves it a slime.
 * gloom-turns-slimes-into-random-hostiles-in-place-of-spawn
 */
public final class GloomEvents {

    private GloomEvents() {
    }

    /**
     * Turns a slime on a Gloom cell into a random hostile mob, through the
     * slime transmutation Spawn ran, and leaves the cell its layers less the
     * turn's, or air where none are left.
     *
     * @param level  the level
     * @param entity the thing in or on the cell
     * @param pos    the cell
     * @param cell   the cell's state
     * @param gloom  the cell's laying step
     */
    public static void touch(ServerLevel level, Entity entity, BlockPos pos, BlockState cell, GloomStep gloom) {
        if (!(entity instanceof Slime slime) || slime.isRemoved()) {
            return;
        }
        GloomTurn turn = GloomTurn.decide(cell.getValue(VolumeCellBlock.LAYERS), gloom.slimeTurnLayers());
        if (!turn.turns() || gloom.slimeTurn().transmuteDrawn(level, slime, null).isEmpty()) {
            return;
        }
        level.setBlock(pos, turn.layersLeft() > 0 ? cell.setValue(VolumeCellBlock.LAYERS, turn.layersLeft())
                : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
