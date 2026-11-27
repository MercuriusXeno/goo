package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The relay's block entity, which holds no state of its own: it marks the relay for the network's walk and ticks
 * the signal it carries.
 * decision relay-prism-carries-the-signal-through-air
 */
public class RelayBlockEntity extends BlockEntity {

    /**
     * Creates the relay's block entity.
     *
     * @param pos   its position
     * @param state its state
     */
    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.RELAY.get(), pos, state);
    }
}
