package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The light beacon's block entity, which holds no state of its own: it is what the client's renderer draws the
 * beacon's far-seen column for.
 * decision bulb-one-model-max-light-beacon-combo
 */
public class LightBeaconBlockEntity extends BlockEntity {

    /**
     * Creates the light beacon's block entity.
     *
     * @param pos   its position
     * @param state its state
     */
    public LightBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.LIGHT_BEACON.get(), pos, state);
    }
}
