package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.ability.oculus.OculusRegistry;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The oculus's block entity: it lists the oculus in its level's oculi as it loads and takes it off as it goes or
 * unloads, so Blink finds every oculus it can snap to and the client draws every one in reach, and its renderer
 * draws the hovering eye.
 * decision oculus-prism-becomes-a-hovering-eye
 */
public class OculusBlockEntity extends BlockEntity {

    /**
     * Creates the oculus's block entity.
     *
     * @param pos   its position
     * @param state its state
     */
    public OculusBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.OCULUS.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            OculusRegistry.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        unlist();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unlist();
        super.onChunkUnloaded();
    }

    private void unlist() {
        if (level != null) {
            OculusRegistry.remove(level, worldPosition);
        }
    }
}
