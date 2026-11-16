package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooStructures;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import java.util.Optional;

/**
 * A fallen meteorite, a fresh impact site on lunar's surface: a dense lump of meteoric iron cored and crusted with
 * impact glass, sitting in a small crater scorched to basalt, centred on its chunk.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
public final class MeteoriteStructure extends Structure {

    /** The data codec of the {@code goo:meteorite} structure type, which takes only the shared settings. */
    public static final MapCodec<MeteoriteStructure> CODEC = simpleCodec(MeteoriteStructure::new);

    /**
     * A meteorite structure under its settings.
     *
     * @param settings the shared structure settings: biomes, step, spawn overrides
     */
    public MeteoriteStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
                builder -> builder.addPiece(new MeteoritePiece(context.chunkPos().getMiddleBlockX(),
                        context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMiddleBlockX(),
                                context.chunkPos().getMiddleBlockZ(), Heightmap.Types.WORLD_SURFACE_WG,
                                context.heightAccessor(), context.randomState()),
                        context.chunkPos().getMiddleBlockZ())));
    }

    @Override
    public StructureType<?> type() {
        return GooStructures.METEORITE.get();
    }
}
