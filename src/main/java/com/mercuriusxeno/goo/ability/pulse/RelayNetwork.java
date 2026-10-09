package com.mercuriusxeno.goo.ability.pulse;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The links between relay prisms: a relay links to every other relay in
 * range, in any direction, with only air between their centers, the way
 * Glow's Reflector links through air; each relay gives the strongest
 * redstone signal reaching any relay it links to.
 * relay-prism-carries-the-signal-through-air
 */
public final class RelayNetwork {

    /**
     * How far a relay links, in blocks. An assumption: abilities.md names no
     * range, so the relay reaches as far as a loaded neighborhood of chunks
     * comfortably holds.
     */
    public static final int RANGE = 16;
    /** Samples per block along a link, fine enough to cross every cell it passes. */
    private static final int SAMPLES_PER_BLOCK = 8;

    private RelayNetwork() {
    }

    /**
     * The signal a relay gives this tick: the strongest signal reaching any
     * other relay it links to, marking the relay as one so the others find it.
     *
     * @param level the server level
     * @param pos   the relay's position
     * @param relay the relay's prism
     * @return the power the relay gives, 0 to 15
     */
    public static int carriedTo(ServerLevel level, BlockPos pos, PrismBlockEntity relay) {
        relay.markRelaying();
        int carried = 0;
        for (BlockPos other : relaysNear(level, pos)) {
            if (!other.equals(pos) && linksThroughAir(level, pos, other)) {
                carried = Math.max(carried, level.getBestNeighborSignal(other));
            }
        }
        return carried;
    }

    /**
     * The relays standing within range of a position in loaded chunks.
     *
     * @param level the server level
     * @param pos   the center
     * @return the relays' positions
     */
    private static List<BlockPos> relaysNear(ServerLevel level, BlockPos pos) {
        List<BlockPos> relays = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(pos.getX() - RANGE);
        int maxChunkX = SectionPos.blockToSectionCoord(pos.getX() + RANGE);
        int minChunkZ = SectionPos.blockToSectionCoord(pos.getZ() - RANGE);
        int maxChunkZ = SectionPos.blockToSectionCoord(pos.getZ() + RANGE);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk != null) {
                    addRelaysIn(chunk, pos, relays);
                }
            }
        }
        return relays;
    }

    private static void addRelaysIn(LevelChunk chunk, BlockPos pos, List<BlockPos> relays) {
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (entity instanceof PrismBlockEntity prism && prism.relays()
                    && prism.getBlockPos().closerThan(pos, RANGE + 1)) {
                relays.add(prism.getBlockPos());
            }
        }
    }

    private static boolean linksThroughAir(ServerLevel level, BlockPos from, BlockPos to) {
        return cellsBetween(from, to).stream().allMatch(cell -> level.getBlockState(cell).isAir());
    }

    /**
     * The cells a line between two blocks' centers crosses, the two blocks aside.
     *
     * @param from one end
     * @param to   the other end
     * @return the cells between, in order from the first end
     */
    public static Set<BlockPos> cellsBetween(BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 line = Vec3.atCenterOf(to).subtract(start);
        int samples = Math.max(1, (int) Math.ceil(line.length() * SAMPLES_PER_BLOCK));
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (int i = 1; i < samples; i++) {
            BlockPos cell = BlockPos.containing(start.add(line.scale((double) i / samples)));
            if (!cell.equals(from) && !cell.equals(to)) {
                cells.add(cell);
            }
        }
        return cells;
    }
}
