package com.mercuriusxeno.goo.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LightChunk;

/**
 * Marker for block entities whose goo contents contribute to block-light
 * emission. The host block's {@code getLightEmission} reads this via
 * {@link #blockEmissionFor} and returns the contribution clamped
 * to {@link GooLightContribution#MAX_LIGHT}.
 */
public interface IGooLightSource {

    /**
     * @return current block-light contribution from goo contents, in [0, 15]
     */
    int gooLightEmission();

    /**
     * Convenience for {@code Block.getLightEmission(state, getter, pos)}
     * overrides on blocks that host an {@link IGooLightSource} BE.
     * Returns 0 when the BE is absent or not a goo light source -- safe
     * during chunk load and on unloaded edges.
     *
     * <p>The server light engine calls this from its own worker thread with
     * the level as the getter, where {@code Level.getBlockEntity} answers
     * null off the server thread. The read then goes through the chunk the
     * engine itself lights, so the recompute {@code checkBlock} enqueued sees
     * the goo contents (decision light-kick-never-drains). That chunk read
     * races the server thread's own edits; a stale value is corrected by the
     * next sync's kick.
     *
     * @param getter the block-getter (level or chunk)
     * @param pos    the block position
     * @return goo emission for the BE at {@code pos}, or 0
     */
    static int blockEmissionFor(BlockGetter getter, BlockPos pos) {
        BlockEntity be = lightSourceEntity(getter, pos);
        return be instanceof IGooLightSource src ? src.gooLightEmission() : 0;
    }

    /**
     * Resolves the BE at {@code pos}, falling back to the light engine's own
     * chunk view when the getter is a level read off its thread.
     *
     * @param getter the block-getter (level or chunk)
     * @param pos    the block position
     * @return the block entity at {@code pos}, or null
     */
    private static BlockEntity lightSourceEntity(BlockGetter getter, BlockPos pos) {
        BlockEntity be = getter.getBlockEntity(pos);
        if (be != null || !(getter instanceof Level level)) {
            return be;
        }
        LightChunk chunk = level.getChunkSource().getChunkForLighting(
            SectionPos.blockToSectionCoord(pos.getX()),
            SectionPos.blockToSectionCoord(pos.getZ()));
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }
}
