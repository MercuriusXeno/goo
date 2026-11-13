package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Clears everything built in an Outsider room: across the square a room's
 * extent covers, every block off the floor returns to air over the pocket's
 * whole build height, and the floor stands voidstone again wherever anything
 * else took its place. A build stands only inside the extent,
 * {@link OutsiderRooms#mayBuildAt}, so the square holds everything built. A
 * room grown hundreds of blocks holds tens of millions of cells, so the walk
 * goes chunk by chunk and section by section, skipping a section holding air
 * alone that the floor does not cross.
 * decision reset-command-returns-my-room-to-nine-by-nine-and-clears-its-blocks
 */
final class RoomClearance {

    /** Tell clients, drop nothing, and skip the side effects that spill a block entity's contents. */
    private static final int CLEAR_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
    /** The blocks along one side of a chunk section. */
    private static final int SECTION_SIDE = 16;

    private RoomClearance() {
    }

    /**
     * Clears the square an extent covers around a room's center voidstone,
     * loading each chunk it crosses.
     *
     * @param pocket the pocket dimension
     * @param center the room's center voidstone
     * @param extent the room's extent before the reset
     */
    static void clear(ServerLevel pocket, BlockPos center, double extent) {
        int reach = RoomExtent.ringGrowing(extent);
        Square square = new Square(center.getX() - reach, center.getX() + reach, center.getZ() - reach,
                center.getZ() + reach);
        for (int chunkX = SectionPos.blockToSectionCoord(square.west());
             chunkX <= SectionPos.blockToSectionCoord(square.east()); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(square.north());
                 chunkZ <= SectionPos.blockToSectionCoord(square.south()); chunkZ++) {
                clearChunk(pocket, pocket.getChunk(chunkX, chunkZ), square);
            }
        }
    }

    /**
     * The columns a clearance covers, in world blocks, each bound held.
     *
     * @param west  the westmost x
     * @param east  the eastmost x
     * @param north the northmost z
     * @param south the southmost z
     */
    private record Square(int west, int east, int north, int south) {
    }

    /**
     * Clears the part of the square one chunk holds, section by section.
     *
     * @param pocket the pocket dimension
     * @param chunk  the chunk
     * @param square the square cleared
     */
    private static void clearChunk(ServerLevel pocket, LevelChunk chunk, Square square) {
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
            LevelChunkSection section = chunk.getSection(index);
            int bottom = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
            boolean holdsFloor = OutsiderRooms.ROOM_FLOOR_Y >= bottom
                    && OutsiderRooms.ROOM_FLOOR_Y < bottom + SECTION_SIDE;
            if (holdsFloor || !section.hasOnlyAir()) {
                clearSection(pocket, chunk, section, bottom, square);
            }
        }
    }

    /**
     * Clears the part of the square one section holds.
     *
     * @param pocket  the pocket dimension
     * @param chunk   the chunk holding the section
     * @param section the section
     * @param bottom  the section's lowest y
     * @param square  the square cleared
     */
    private static void clearSection(ServerLevel pocket, LevelChunk chunk, LevelChunkSection section, int bottom,
            Square square) {
        int minX = Math.max(square.west(), chunk.getPos().getMinBlockX());
        int maxX = Math.min(square.east(), chunk.getPos().getMaxBlockX());
        int minZ = Math.max(square.north(), chunk.getPos().getMinBlockZ());
        int maxZ = Math.min(square.south(), chunk.getPos().getMaxBlockZ());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                clearColumn(pocket, section, at.set(x, bottom, z));
            }
        }
    }

    /**
     * Clears one column of a section, from the block given upward.
     *
     * @param pocket  the pocket dimension
     * @param section the section
     * @param at      the column's lowest block in the section, moved up the column as it clears
     */
    private static void clearColumn(ServerLevel pocket, LevelChunkSection section, BlockPos.MutableBlockPos at) {
        int bottom = at.getY();
        for (int y = bottom; y < bottom + SECTION_SIDE; y++) {
            at.setY(y);
            BlockState standing = section.getBlockState(SectionPos.sectionRelative(at.getX()), y - bottom,
                    SectionPos.sectionRelative(at.getZ()));
            BlockState laid = y == OutsiderRooms.ROOM_FLOOR_Y ? GooBlocks.VOIDSTONE.get().defaultBlockState()
                    : Blocks.AIR.defaultBlockState();
            if (!standing.is(laid.getBlock())) {
                pocket.setBlock(at, laid, CLEAR_FLAGS);
            }
        }
    }
}
