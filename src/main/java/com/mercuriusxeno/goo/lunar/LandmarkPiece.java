package com.mercuriusxeno.goo.lunar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The one piece of a lunar landmark, centred on a column at the top of its ground: it saves that centre and builds
 * itself around it in world coordinates, each landmark its own shape.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
public abstract class LandmarkPiece extends StructurePiece {

    /** Block flags for a landmark's placement: tell clients, and skip neighbour updates. */
    protected static final int PLACE_FLAGS = Block.UPDATE_CLIENTS;
    private static final String CENTER_X = "CX";
    private static final String CENTER_Y = "CY";
    private static final String CENTER_Z = "CZ";

    /** The centre column's x, the top of its ground, and its z. */
    protected final int centerX;
    protected final int groundY;
    protected final int centerZ;

    /**
     * A landmark piece centred on a column.
     *
     * @param type    the piece's type
     * @param box     the box the piece builds inside
     * @param centerX the centre's x
     * @param groundY the top of the centre's ground
     * @param centerZ the centre's z
     */
    protected LandmarkPiece(StructurePieceType type, BoundingBox box, int centerX, int groundY, int centerZ) {
        super(type, 0, box);
        this.centerX = centerX;
        this.groundY = groundY;
        this.centerZ = centerZ;
    }

    /**
     * A landmark piece read back from its saved tag.
     *
     * @param type the piece's type
     * @param tag  the saved tag
     */
    protected LandmarkPiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
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
}
