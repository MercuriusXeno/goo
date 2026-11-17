package com.mercuriusxeno.goo.lunar;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A lunar landmark on the surface: one piece centred on its chunk, at the top of the ground there. Each landmark's
 * type names the piece it builds.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
public final class LandmarkStructure extends Structure {

    /**
     * Builds a landmark's piece centred on a column.
     */
    @FunctionalInterface
    public interface PieceFactory {
        /**
         * @param centerX the centre's x
         * @param groundY the top of the centre's ground
         * @param centerZ the centre's z
         * @return the piece
         */
        LandmarkPiece create(int centerX, int groundY, int centerZ);
    }

    private final PieceFactory pieces;
    private final Supplier<StructureType<?>> type;

    private LandmarkStructure(Structure.StructureSettings settings, PieceFactory pieces,
            Supplier<StructureType<?>> type) {
        super(settings);
        this.pieces = pieces;
        this.type = type;
    }

    /**
     * The data codec of a landmark's structure type, which takes only the shared settings.
     *
     * @param pieces builds the landmark's piece
     * @param type   the landmark's structure type
     * @return the codec
     */
    public static MapCodec<LandmarkStructure> codec(PieceFactory pieces, Supplier<StructureType<?>> type) {
        return simpleCodec(settings -> new LandmarkStructure(settings, pieces, type));
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        int centerX = context.chunkPos().getMiddleBlockX();
        int centerZ = context.chunkPos().getMiddleBlockZ();
        int groundY = context.chunkGenerator().getFirstOccupiedHeight(centerX, centerZ,
                Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
                builder -> builder.addPiece(pieces.create(centerX, groundY, centerZ)));
    }

    @Override
    public StructureType<?> type() {
        return type.get();
    }
}
