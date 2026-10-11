package com.mercuriusxeno.goo.block.gate;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Dragon Gate cell's block entity, which carries the cell to its renderer
 * so it draws its share of the gate's starfield square.
 * Decision end-clears-blocks-and-opens-a-portal.
 */
public class DragonGateBlockEntity extends BlockEntity {

    /**
     * Creates the gate cell's block entity.
     *
     * @param pos   the cell
     * @param state the gate block's state
     */
    public DragonGateBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.DRAGON_GATE.get(), pos, state);
    }
}
