package com.mercuriusxeno.goo.block.zone;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A room doorway cell's block entity, which carries the cell to its
 * renderer so it draws its share of the doorway as the End portal's
 * starfield.
 * decision room-door-wears-the-end-portal-starfield
 */
public class DimensionDoorBlockEntity extends BlockEntity {

    /**
     * Creates the doorway cell's block entity.
     *
     * @param pos   the cell
     * @param state the door block's state
     */
    public DimensionDoorBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.DIMENSION_DOOR.get(), pos, state);
    }
}
