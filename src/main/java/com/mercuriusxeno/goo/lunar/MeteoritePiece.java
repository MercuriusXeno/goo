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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The one piece of a fallen meteorite: a bowl sunk into the ground around its centre, its floor scorched to basalt,
 * and the meteorite's lump resting on the floor's middle, an iron shell holding a glass core and crusted with glass
 * on its upper face. The bowl follows each column's own ground, so it sits in the terrain wherever it falls.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
public final class MeteoritePiece extends StructurePiece {

    /** The scorched bowl's radius, and how deep it sinks under the centre's ground. */
    static final int CRATER_RADIUS = 7;
    static final int CRATER_DEPTH = 3;
    /** How deep the scorched basalt runs under the bowl's floor. */
    static final int SCORCH_DEPTH = 2;
    /** The lump's radius, and its glass core's. */
    static final double LUMP_RADIUS = 2.5;
    static final double CORE_RADIUS = 1.2;
    /** The lump's centre stands this far over the bowl's floor at the middle. */
    static final int LUMP_LIFT = 2;
    /** The box reaches this far over the centre's ground, for the slopes the bowl cuts into. */
    private static final int BOX_HEADROOM = 16;
    /** Every third block of the lump's upper shell crusts to glass. */
    private static final int CRUST_SPACING = 3;
    /** Block flags for a structure's placement: tell clients, and skip neighbour updates. */
    private static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final String CENTER_X = "CX";
    private static final String CENTER_Y = "CY";
    private static final String CENTER_Z = "CZ";

    private final int centerX;
    private final int groundY;
    private final int centerZ;

    /**
     * A meteorite piece centred on a column.
     *
     * @param centerX the centre's x
     * @param groundY the top of the centre's ground
     * @param centerZ the centre's z
     */
    public MeteoritePiece(int centerX, int groundY, int centerZ) {
        super(GooStructures.METEORITE_PIECE.get(), 0, new BoundingBox(centerX - CRATER_RADIUS,
                groundY - CRATER_DEPTH - SCORCH_DEPTH, centerZ - CRATER_RADIUS, centerX + CRATER_RADIUS,
                groundY + BOX_HEADROOM, centerZ + CRATER_RADIUS));
        this.centerX = centerX;
        this.groundY = groundY;
        this.centerZ = centerZ;
    }

    /**
     * A meteorite piece read back from its saved tag.
     *
     * @param tag the saved tag
     */
    public MeteoritePiece(CompoundTag tag) {
        super(GooStructures.METEORITE_PIECE.get(), tag);
        this.centerX = tag.getIntOr(CENTER_X, 0);
        this.groundY = tag.getIntOr(CENTER_Y, 0);
        this.centerZ = tag.getIntOr(CENTER_Z, 0);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt(CENTER_X, centerX);
        tag.putInt(CENTER_Y, groundY);
        tag.putInt(CENTER_Z, centerZ);
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
                    boolean core = distance <= CORE_RADIUS;
                    boolean crust = dy > 0 && Math.floorMod(dx + dy + dz, CRUST_SPACING) == 0;
                    level.setBlock(at, (core || crust ? GooBlocks.IMPACT_GLASS : GooBlocks.METEORIC_IRON).get()
                            .defaultBlockState(), PLACE_FLAGS);
                }
            }
        }
    }
}
