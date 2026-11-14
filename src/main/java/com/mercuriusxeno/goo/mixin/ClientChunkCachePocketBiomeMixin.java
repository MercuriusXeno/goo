package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.zone.ZoneRooms;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the Zone pocket's stand-in for a chunk the client has yet to
 * receive the pocket's own biome in place of plains, so the sky and fog the
 * camera reads from its biome stay the pocket's black while it stands where
 * no chunk has come, as just after crossing a room wider than the client's
 * view, rather than flashing plains white.
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCachePocketBiomeMixin {

    private static final ResourceKey<Biome> GOO$POCKET_BIOME = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("goo", "pocket_void"));

    @Shadow
    @Final
    @Mutable
    private LevelChunk emptyChunk;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void goo$pocketStandInWearsThePocketBiome(ClientLevel level, int serverChunkRadius, CallbackInfo ci) {
        if (level.dimension() == ZoneRooms.POCKET) {
            level.registryAccess().lookupOrThrow(Registries.BIOME).get(GOO$POCKET_BIOME).ifPresent(
                    biome -> emptyChunk = new EmptyLevelChunk(level, new ChunkPos(0, 0), biome));
        }
    }
}
