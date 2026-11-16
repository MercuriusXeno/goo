package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The one piece of an eldritch nest, a lair where the eldritch gather: a hollow mound of sculk growth rising from the
 * ground, its shell sculk, its floor sculk, its hollow cleared of whatever ground it swallowed, and a low doorway cut
 * through its east wall.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
public final class NestPiece extends LandmarkPiece {

    /** The mound's outer radius, and its shell's thickness. */
    static final double MOUND_RADIUS = 6.5;
    static final double SHELL_THICKNESS = 1.2;
    /** The doorway's half width and its height over the floor. */
    static final int DOOR_HALF_WIDTH = 1;
    static final int DOOR_HEIGHT = 3;
    /** How deep the sculk floor and footing run under the ground. */
    static final int FOOTING_DEPTH = 2;

    /**
     * A nest piece centred on a column.
     *
     * @param centerX the centre's x
     * @param groundY the top of the centre's ground
     * @param centerZ the centre's z
     */
    public NestPiece(int centerX, int groundY, int centerZ) {
        super(GooStructures.NEST_PIECE.get(), box(centerX, groundY, centerZ), centerX, groundY, centerZ);
    }

    /**
     * A nest piece read back from its saved tag.
     *
     * @param tag the saved tag
     */
    public NestPiece(CompoundTag tag) {
        super(GooStructures.NEST_PIECE.get(), tag);
    }

    private static BoundingBox box(int centerX, int groundY, int centerZ) {
        int reach = (int) Math.ceil(MOUND_RADIUS);
        return new BoundingBox(centerX - reach, groundY - FOOTING_DEPTH, centerZ - reach, centerX + reach,
                groundY + reach, centerZ + reach);
    }

    /**
     * What the nest lays at an offset from its centre on the ground: sculk in its shell and floor, air in its
     * hollow and doorway, nothing past its shell.
     *
     * @param dx the offset east
     * @param dy the offset up from the ground's top
     * @param dz the offset south
     * @return the block, or null where the nest leaves the world be
     */
    static BlockState blockAt(int dx, int dy, int dz) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean underGround = dy <= 0;
        if (underGround) {
            return Math.hypot(dx, dz) <= MOUND_RADIUS && dy > -FOOTING_DEPTH ? Blocks.SCULK.defaultBlockState() : null;
        }
        if (distance > MOUND_RADIUS) {
            return null;
        }
        boolean doorway = dx > 0 && Math.abs(dz) <= DOOR_HALF_WIDTH && dy <= DOOR_HEIGHT;
        boolean shell = distance > MOUND_RADIUS - SHELL_THICKNESS;
        return shell && !doorway ? Blocks.SCULK.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos referencePos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int reach = (int) Math.ceil(MOUND_RADIUS);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -FOOTING_DEPTH + 1; dy <= reach; dy++) {
                    BlockState block = blockAt(dx, dy, dz);
                    if (block != null && chunkBox.isInside(at.set(centerX + dx, groundY + dy, centerZ + dz))) {
                        level.setBlock(at, block, PLACE_FLAGS);
                    }
                }
            }
        }
    }
}
