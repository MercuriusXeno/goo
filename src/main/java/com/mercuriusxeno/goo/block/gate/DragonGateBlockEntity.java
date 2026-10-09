package com.mercuriusxeno.goo.block.gate;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Dragon Gate cell's block entity, drawn by the End portal's starfield on
 * every face that looks out of the gate: a face against another gate cell
 * or against a solid block stays hidden.
 * Decision dragon-gate-banishes-blocks-and-opens-a-portal.
 */
public class DragonGateBlockEntity extends TheEndPortalBlockEntity {

    /**
     * Creates the gate cell's block entity.
     *
     * @param pos   the cell
     * @param state the gate block's state
     */
    public DragonGateBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.DRAGON_GATE.get(), pos, state);
    }

    @Override
    public boolean shouldRenderFace(Direction direction) {
        if (level == null) {
            return true;
        }
        BlockPos beside = worldPosition.relative(direction);
        BlockState neighbor = level.getBlockState(beside);
        return !(neighbor.getBlock() instanceof DragonGateBlock) && !neighbor.isSolidRender();
    }
}
