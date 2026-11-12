package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.SculkerSenses;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells Sculker's echoes of every block change the client draws, so the
 * quads each lit block keeps across frames drop where a change reaches them.
 * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSculkerEchoMixin {

    /**
     * Passes a block change on to the echoes' kept quads.
     *
     * @param pos         the block
     * @param old         the block that stood there
     * @param current     the block standing there now
     * @param updateFlags the update flags
     * @param ci          the callback
     */
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void goo$forgetEchoQuads(BlockPos pos, BlockState old, BlockState current, int updateFlags,
                                     CallbackInfo ci) {
        SculkerSenses.blockChanged(pos);
    }
}
