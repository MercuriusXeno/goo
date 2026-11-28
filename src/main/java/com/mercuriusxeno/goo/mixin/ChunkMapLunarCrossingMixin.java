package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarCrossingViews;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Consumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Compares a lunar player's view jumping across the seam against what their client moved over, so the chunks the
 * client already holds across the seam are neither forgotten nor sent again. Outside lunar, and for every view the
 * crossing did not move, vanilla compares the views as ever; a missing target leaves the crossing a plain teleport.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapLunarCrossingMixin {

    /**
     * Keeps what the client moved across the seam.
     *
     * @param from     the view the player held
     * @param to       the view they now hold
     * @param onEnter  vanilla's queueing of a chunk to send
     * @param onLeave  vanilla's forgetting of a chunk
     * @param original vanilla's comparison of the views
     * @param player   the player whose view changes
     */
    @WrapOperation(method = "applyChunkTrackingView", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkTrackingView;difference("
                    + "Lnet/minecraft/server/level/ChunkTrackingView;Lnet/minecraft/server/level/ChunkTrackingView;"
                    + "Ljava/util/function/Consumer;Ljava/util/function/Consumer;)V"))
    private void goo$keepsWhatTheClientMovedAcrossTheSeam(ChunkTrackingView from, ChunkTrackingView to,
            Consumer<ChunkPos> onEnter, Consumer<ChunkPos> onLeave, Operation<Void> original,
            @Local(argsOnly = true) ServerPlayer player) {
        if (player.level().dimension() != Lunar.LEVEL
                || !LunarCrossingViews.differenceAcross(player, from, to, onEnter, onLeave)) {
            original.call(from, to, onEnter, onLeave);
        }
    }
}
