package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.lunar.LunarMirror;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.BitSet;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Sends a player seeing a lunar chunk past the seam the chunk it wraps to, labelled where they see it.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderLunarMirrorMixin {

    /**
     * Builds the chunk's packet as the moon reads it.
     *
     * @param chunk       the chunk the player sees
     * @param lightEngine the level's light engine
     * @param skyFilter   vanilla's sky light filter
     * @param blockFilter vanilla's block light filter
     * @param original    vanilla's building of the packet, which the moon's reading stands in place of
     * @param level       the level sending the chunk
     * @return the packet to send
     */
    @WrapOperation(method = "sendChunk", at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/level/chunk/LevelChunk;"
                    + "Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)"
                    + "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;"))
    private static ClientboundLevelChunkWithLightPacket goo$sendsTheOtherSidePastTheSeam(
            LevelChunk chunk, LevelLightEngine lightEngine, BitSet skyFilter, BitSet blockFilter,
            Operation<ClientboundLevelChunkWithLightPacket> original, @Local(argsOnly = true) ServerLevel level) {
        return LunarMirror.packetFor(level, chunk);
    }
}
