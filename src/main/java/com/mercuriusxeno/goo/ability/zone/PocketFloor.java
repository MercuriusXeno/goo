package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The pocket's voidstone floor, standing whole at every room's floor height
 * before any growth, so growing a room moves its boundary alone. The pocket
 * dimension's generator lays the floor in every chunk it makes
 * (data/goo/dimension/pocket.json). A chunk a save generated before the
 * floor holds none, so a chunk first loaded with no floor laid is filled
 * once, the next pocket tick, at every open spot of the floor height, and
 * marked laid; a fresh chunk from the generator is marked laid as it loads.
 * decision voidstone-floor-stands-whole-and-growth-moves-the-boundary
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PocketFloor {

    /** Chunks loaded into the pocket that still wait on their floor. */
    private static final Set<ChunkPos> UNLAID = new LinkedHashSet<>();

    private PocketFloor() {
    }

    /**
     * Notes a pocket chunk loading: a generated one is laid already, and an
     * older one with no floor waits for the next pocket tick.
     *
     * @param event the chunk load event
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        ChunkAccess chunk = event.getChunk();
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != ZoneRooms.POCKET
                || chunk.getData(GooAttachments.POCKET_FLOOR_LAID)) {
            return;
        }
        if (event.isNewChunk()) {
            chunk.setData(GooAttachments.POCKET_FLOOR_LAID, true);
        } else {
            UNLAID.add(chunk.getPos());
        }
    }

    /**
     * Lays the floor in every loaded pocket chunk waiting on it.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (UNLAID.isEmpty() || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != ZoneRooms.POCKET) {
            return;
        }
        for (ChunkPos pos : Set.copyOf(UNLAID)) {
            UNLAID.remove(pos);
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) {
                layFloor(level, chunk);
            }
        }
    }

    /**
     * Fills every open spot of a chunk's floor height with voidstone and marks the chunk laid.
     *
     * @param level the pocket dimension
     * @param chunk the chunk
     */
    private static void layFloor(ServerLevel level, LevelChunk chunk) {
        BlockState voidstone = GooBlocks.VOIDSTONE.get().defaultBlockState();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                at.set(x, ZoneRooms.ROOM_FLOOR_Y, z);
                if (chunk.getBlockState(at).isAir()) {
                    level.setBlock(at, voidstone, Block.UPDATE_CLIENTS);
                }
            }
        }
        chunk.setData(GooAttachments.POCKET_FLOOR_LAID, true);
    }
}
