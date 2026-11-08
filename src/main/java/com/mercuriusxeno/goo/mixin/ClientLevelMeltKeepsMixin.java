package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.BlockTransforms;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a melting cell's swap on the client past the block the cell was,
 * which vanilla's use handling resends every few ticks while the use key
 * is held on or beside it, so the block melting away stays eaten rather
 * than standing back up under the noise.
 * decision flames-melts-vanilla-meltable-ice-and-snow-over-the-hold
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMeltKeepsMixin {

    /**
     * Drops a server-verified state that only resends the block a melting cell was.
     *
     * @param pos        the block
     * @param blockState the state the server sent
     * @param updateFlag the update flags
     * @param ci         the callback
     */
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), cancellable = true)
    private void goo$keepMelt(BlockPos pos, BlockState blockState, int updateFlag, CallbackInfo ci) {
        if (BlockTransforms.CLIENT.keepsMelt(pos, blockState)) {
            ci.cancel();
        }
    }
}
