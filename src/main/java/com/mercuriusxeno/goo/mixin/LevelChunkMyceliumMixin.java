package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.program.MyceliumNetworks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands each block write a chunk takes to the stored mycelium network before
 * it lands, so a write that makes a place mycelial or stops it being so drops
 * the chunk's stored network; every write a level makes, a player's, sculk's
 * spread or a goo cell's pour, passes here.
 * mycelium-networks-are-cached-dags-joined-by-chunk-connectors
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMyceliumMixin {

    /**
     * Tells the stored network a block is about to be written.
     *
     * @param pos   the place
     * @param state the block written
     * @param flags the write's update flags
     * @param info  the callback
     */
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void goo$myceliumChanging(BlockPos pos, BlockState state, int flags,
                                      CallbackInfoReturnable<BlockState> info) {
        MyceliumNetworks.blockChanging((LevelChunk) (Object) this, pos, state);
    }
}
