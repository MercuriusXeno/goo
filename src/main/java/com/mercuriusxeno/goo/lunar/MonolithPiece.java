package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The one piece of a monolith, a tall, alien standing stone on open ground, seen from far off against the void sky
 * and unexplained: a single slab of black impact glass, one block thick, four wide and eighteen tall, the proportion
 * one to four to nine, standing on a footing of anorthosite that fills down to each column's own ground, so it
 * follows the terrain wherever it stands.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
public final class MonolithPiece extends LandmarkPiece {

    /** The slab's width east to west, its thickness north to south, and its height. */
    static final int WIDTH = 4;
    static final int THICKNESS = 1;
    static final int HEIGHT = 18;
    /** The footing reaches this far past the slab on every side. */
    static final int FOOTING_APRON = 1;
    /** The footing fills at most this far down to meet lower ground. */
    static final int FOOTING_DEPTH = 8;
    /** The slab's western column, from its centre. */
    private static final int WEST = -WIDTH / 2;

    /**
     * A monolith piece centred on a column.
     *
     * @param centerX the centre's x
     * @param groundY the top of the centre's ground
     * @param centerZ the centre's z
     */
    public MonolithPiece(int centerX, int groundY, int centerZ) {
        super(GooStructures.MONOLITH_PIECE.get(), new BoundingBox(centerX + WEST - FOOTING_APRON,
                groundY - FOOTING_DEPTH, centerZ - FOOTING_APRON, centerX + WEST + WIDTH - 1 + FOOTING_APRON,
                groundY + HEIGHT, centerZ + THICKNESS - 1 + FOOTING_APRON), centerX, groundY, centerZ);
    }

    /**
     * A monolith piece read back from its saved tag.
     *
     * @param tag the saved tag
     */
    public MonolithPiece(CompoundTag tag) {
        super(GooStructures.MONOLITH_PIECE.get(), tag);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos referencePos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockState footing = GooBlocks.ANORTHOSITE.get().defaultBlockState();
        BlockState slab = GooBlocks.IMPACT_GLASS.get().defaultBlockState();
        for (int dx = WEST - FOOTING_APRON; dx < WEST + WIDTH + FOOTING_APRON; dx++) {
            for (int dz = -FOOTING_APRON; dz < THICKNESS + FOOTING_APRON; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int localGround = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = Math.max(localGround + 1, groundY - FOOTING_DEPTH); y <= groundY; y++) {
                    place(level, chunkBox, at.set(x, y, z), footing);
                }
                boolean inSlab = dx >= WEST && dx < WEST + WIDTH && dz >= 0 && dz < THICKNESS;
                for (int y = groundY + 1; inSlab && y <= groundY + HEIGHT; y++) {
                    place(level, chunkBox, at.set(x, y, z), slab);
                }
            }
        }
    }

    private static void place(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos at,
            BlockState block) {
        if (chunkBox.isInside(at)) {
            level.setBlock(at, block, PLACE_FLAGS);
        }
    }
}
