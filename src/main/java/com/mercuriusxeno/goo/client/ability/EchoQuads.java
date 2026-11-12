package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The quads each lit block draws, kept across frames: its model's quads on
 * every side open to sound and those facing no side, in the order an echo
 * draws them. A block's quads change only when it or a block beside it
 * changes or its chunk loads or unloads, so each is collected once and
 * dropped on those changes alone.
 * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
 */
final class EchoQuads {

    /** How many kept blocks the memory may hold past what the echoes remember before the stale go. */
    private static final int SLACK = 1024;
    private static final Direction[] SIDES = Direction.values();

    private final Map<BlockPos, List<BakedQuad>> kept = new HashMap<>();

    /**
     * The quads a lit block draws, collected the first time it is asked.
     *
     * @param world the world the block stands in
     * @param pos   the block
     * @return its quads, none for air
     */
    List<BakedQuad> at(SculkerEchoRenderer.EchoWorld world, BlockPos pos) {
        List<BakedQuad> quads = kept.get(pos);
        if (quads == null) {
            quads = collected(world, pos);
            kept.put(pos.immutable(), quads);
        }
        return quads;
    }

    private static List<BakedQuad> collected(SculkerEchoRenderer.EchoWorld world, BlockPos pos) {
        BlockState state = world.stateAt(pos);
        if (state.isAir()) {
            return List.of();
        }
        List<BlockStateModelPart> parts = new ArrayList<>();
        world.collectParts(pos, state, parts);
        boolean[] open = new boolean[SIDES.length];
        for (Direction side : SIDES) {
            open[side.ordinal()] = world.passesSound(pos.relative(side));
        }
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : SIDES) {
                if (open[side.ordinal()]) {
                    quads.addAll(part.getQuads(side));
                }
            }
            quads.addAll(part.getQuads(null));
        }
        return List.copyOf(quads);
    }

    /**
     * Drops a changed block's quads and its neighbours', whose open sides it sets.
     *
     * @param changed the block that changed
     */
    void forgetAround(BlockPos changed) {
        kept.remove(changed);
        for (Direction side : SIDES) {
            kept.remove(changed.relative(side));
        }
    }

    /**
     * Drops the quads of every block in a chunk that loaded or unloaded, and
     * in the chunks beside it, whose border blocks it sets open or shut.
     *
     * @param chunkX the chunk's x
     * @param chunkZ the chunk's z
     */
    void forgetChunk(int chunkX, int chunkZ) {
        kept.keySet().removeIf(pos -> Math.abs(SectionPos.blockToSectionCoord(pos.getX()) - chunkX) <= 1
                && Math.abs(SectionPos.blockToSectionCoord(pos.getZ()) - chunkZ) <= 1);
    }

    /**
     * Lets go of the blocks no echo remembers, once enough have gone stale
     * to be worth the sweep.
     *
     * @param remembered the blocks the echoes remember
     */
    void keepWithin(Set<BlockPos> remembered) {
        if (kept.size() > remembered.size() + SLACK) {
            kept.keySet().retainAll(remembered);
        }
    }

    /** Forgets every block's quads, as the brew's end does. */
    void clear() {
        kept.clear();
    }
}
