package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * follows the terrain wherever it stands. Astral crystal grows on the footing against the slab's lowest course, and
 * astral goo pools on the ground in a ring around the footing.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 * decision each-lunar-landmark-holds-its-own-prize
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
    /** The row just north of the slab, and the stride of the columns a face's crystals alternate on. */
    private static final int NORTH_OF_SLAB = -1;
    private static final int FACE_STRIDE = 2;
    /** The astral goo pools on the ground this far out past the footing's edge. */
    static final int POOL_RING = 1;
    /** The ring's distance from the slab, in blocks. */
    private static final int RING = FOOTING_APRON + POOL_RING;
    /** Every third cell of the ring pools astral goo. */
    private static final int POOL_SPACING = 3;
    /** The most layers a pooled cell of astral goo stands. */
    private static final int POOL_MOST_LAYERS = 4;

    /**
     * A monolith piece centred on a column.
     *
     * @param centerX the centre's x
     * @param groundY the top of the centre's ground
     * @param centerZ the centre's z
     */
    public MonolithPiece(int centerX, int groundY, int centerZ) {
        super(GooStructures.MONOLITH_PIECE.get(), new BoundingBox(centerX + WEST - RING, groundY - FOOTING_DEPTH,
                centerZ - RING, centerX + WEST + WIDTH - 1 + RING, groundY + HEIGHT, centerZ + THICKNESS - 1 + RING),
                centerX, groundY, centerZ);
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
        growCrystal(level, chunkBox, at);
        poolGoo(level, random, chunkBox, at);
    }

    /**
     * Whether astral crystal grows on the footing at an offset from the centre: against both ends of the slab, and
     * against its faces on alternate columns, the north face's even and the south face's odd.
     *
     * @param dx the offset east
     * @param dz the offset south
     * @return true where a crystal grows
     */
    static boolean growsCrystal(int dx, int dz) {
        return againstEnd(dx, dz) || againstFace(dx, dz);
    }

    private static boolean againstEnd(int dx, int dz) {
        boolean acrossSlab = dz >= 0 && dz < THICKNESS;
        return acrossSlab && (dx == WEST - 1 || dx == WEST + WIDTH);
    }

    private static boolean againstFace(int dx, int dz) {
        boolean alongSlab = dx >= WEST && dx < WEST + WIDTH;
        boolean evenColumn = Math.floorMod(dx - WEST, FACE_STRIDE) == 0;
        return alongSlab && dz == (evenColumn ? NORTH_OF_SLAB : THICKNESS);
    }

    /**
     * Whether astral goo pools at an offset from the centre: every third cell of the ring around the footing.
     *
     * @param dx the offset east
     * @param dz the offset south
     * @return true where the goo pools
     */
    static boolean poolsGoo(int dx, int dz) {
        boolean onRing = dx == WEST - RING || dx == WEST + WIDTH - 1 + RING || dz == -RING
                || dz == THICKNESS - 1 + RING;
        return onRing && Math.floorMod(dx + dz, POOL_SPACING) == 0;
    }

    private void growCrystal(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos at) {
        BlockState crystal = GooBlocks.ASTRAL_CRYSTAL.get().defaultBlockState();
        for (int dx = WEST - FOOTING_APRON; dx < WEST + WIDTH + FOOTING_APRON; dx++) {
            for (int dz = -FOOTING_APRON; dz < THICKNESS + FOOTING_APRON; dz++) {
                if (growsCrystal(dx, dz)) {
                    place(level, chunkBox, at.set(centerX + dx, groundY + 1, centerZ + dz), crystal);
                }
            }
        }
    }

    private void poolGoo(WorldGenLevel level, RandomSource random, BoundingBox chunkBox, BlockPos.MutableBlockPos at) {
        for (int dx = WEST - RING; dx < WEST + WIDTH + RING; dx++) {
            for (int dz = -RING; dz < THICKNESS + RING; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                at.set(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
                if (poolsGoo(dx, dz) && level.isEmptyBlock(at)
                        && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                    place(level, chunkBox, at, GooBlocks.ASTRAL_DOWSING.get().defaultBlockState()
                            .setValue(VolumeCellBlock.LAYERS, 1 + random.nextInt(POOL_MOST_LAYERS)));
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
