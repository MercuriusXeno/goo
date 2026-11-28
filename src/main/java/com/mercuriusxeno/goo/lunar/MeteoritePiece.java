package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The one piece of a fallen meteorite: a bowl sunk into the ground around its centre, its floor scorched to basalt,
 * and the meteorite's lump resting on the floor's middle, a meteoric iron shell wrapping a lode of native iron at its
 * heart and crusted with glass on its upper face, the crust kept off the lode so iron alone encloses it. The bowl
 * follows each column's own ground, so it sits in the terrain wherever it falls.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 * decision each-lunar-landmark-holds-its-own-prize
 */
public final class MeteoritePiece extends LandmarkPiece {

    /** The scorched bowl's radius, and how deep it sinks under the centre's ground. */
    static final int CRATER_RADIUS = 7;
    static final int CRATER_DEPTH = 3;
    /** How deep the scorched basalt runs under the bowl's floor. */
    static final int SCORCH_DEPTH = 2;
    /** The lump's radius, and its native iron lode's. */
    static final double LUMP_RADIUS = 2.5;
    static final double LODE_RADIUS = 1.2;
    /** The crust keeps past this distance from the centre, so no glass touches the lode. */
    private static final double CRUST_FROM = LODE_RADIUS + 1;
    /** The lump's centre stands this far over the bowl's floor at the middle. */
    static final int LUMP_LIFT = 2;
    /** The box reaches this far over the centre's ground, for the slopes the bowl cuts into. */
    private static final int BOX_HEADROOM = 16;
    /** Every third block of the lump's upper shell crusts to glass. */
    private static final int CRUST_SPACING = 3;
    /**
     * A meteorite piece centred on a column.
     *
     * @param centerX the centre's x
     * @param groundY the top of the centre's ground
     * @param centerZ the centre's z
     */
    public MeteoritePiece(int centerX, int groundY, int centerZ) {
        super(GooStructures.METEORITE_PIECE.get(), new BoundingBox(centerX - CRATER_RADIUS,
                groundY - CRATER_DEPTH - SCORCH_DEPTH, centerZ - CRATER_RADIUS, centerX + CRATER_RADIUS,
                groundY + BOX_HEADROOM, centerZ + CRATER_RADIUS), centerX, groundY, centerZ);
    }

    /**
     * A meteorite piece read back from its saved tag.
     *
     * @param tag the saved tag
     */
    public MeteoritePiece(CompoundTag tag) {
        super(GooStructures.METEORITE_PIECE.get(), tag);
    }

    /**
     * How deep the bowl sinks at a distance from its centre, in blocks.
     *
     * @param distance the horizontal distance from the centre
     * @return the depth under the centre's ground
     */
    static int bowlDepth(double distance) {
        double reach = distance / CRATER_RADIUS;
        return (int) Math.round(CRATER_DEPTH * (1.0 - reach * reach));
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos referencePos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockState basalt = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int x = centerX - CRATER_RADIUS; x <= centerX + CRATER_RADIUS; x++) {
            for (int z = centerZ - CRATER_RADIUS; z <= centerZ + CRATER_RADIUS; z++) {
                double distance = Math.hypot(x - centerX, z - centerZ);
                if (distance > CRATER_RADIUS || !chunkBox.isInside(at.set(x, groundY, z))) {
                    continue;
                }
                int floorY = groundY - bowlDepth(distance);
                int localGround = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                for (int y = floorY + 1; y <= Math.max(localGround, floorY); y++) {
                    level.setBlock(at.set(x, y, z), Blocks.AIR.defaultBlockState(), PLACE_FLAGS);
                }
                for (int y = floorY - SCORCH_DEPTH; y <= floorY; y++) {
                    level.setBlock(at.set(x, y, z), basalt, PLACE_FLAGS);
                }
            }
        }
        placeLump(level, chunkBox, at);
    }

    private static BlockState lumpBlockAt(int dx, int dy, int dz, double distance) {
        if (distance <= LODE_RADIUS) {
            return GooBlocks.NATIVE_IRON.get().defaultBlockState();
        }
        boolean crust = dy > 0 && distance > CRUST_FROM && Math.floorMod(dx + dy + dz, CRUST_SPACING) == 0;
        return (crust ? GooBlocks.IMPACT_GLASS : GooBlocks.METEORIC_IRON).get().defaultBlockState();
    }

    private void placeLump(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos at) {
        int lumpY = groundY - CRATER_DEPTH + LUMP_LIFT;
        int reach = (int) Math.ceil(LUMP_RADIUS);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (distance > LUMP_RADIUS || !chunkBox.isInside(at.set(centerX + dx, lumpY + dy, centerZ + dz))) {
                        continue;
                    }
                    level.setBlock(at, lumpBlockAt(dx, dy, dz, distance), PLACE_FLAGS);
                }
            }
        }
    }
}
