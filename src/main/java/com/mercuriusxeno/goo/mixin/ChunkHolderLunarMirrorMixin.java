package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.LunarMirror;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends a lunar chunk that changed again to every player seeing it from across the seam, so the other side shows its
 * builds as they stand.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(ChunkHolder.class)
public abstract class ChunkHolderLunarMirrorMixin {

    @Shadow
    @Final
    private ChunkHolder.PlayerProvider playerProvider;

    @Shadow
    public abstract boolean hasChangesToBroadcast();

    /**
     * Resends the chunk across the seam before its changes go out to the players beside it.
     *
     * @param chunk the chunk whose changes are broadcast
     * @param ci    the callback
     */
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void goo$resendsAcrossTheSeam(LevelChunk chunk, CallbackInfo ci) {
        if (hasChangesToBroadcast() && chunk.getLevel() instanceof ServerLevel level
                && playerProvider instanceof ChunkMap chunkMap) {
            LunarMirror.resendAcrossTheSeam(level, chunkMap, chunk);
        }
    }
}
