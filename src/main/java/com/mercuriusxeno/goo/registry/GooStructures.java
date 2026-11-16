package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.LandmarkStructure;
import com.mercuriusxeno.goo.lunar.MeteoritePiece;
import com.mercuriusxeno.goo.lunar.MonolithPiece;
import com.mercuriusxeno.goo.lunar.NestPiece;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/**
 * Registers the structure types and structure piece types goo's worldgen JSON names.
 */
public final class GooStructures {

    /**
     * Deferred register for structure types.
     */
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, Goo.MODID);
    /**
     * Deferred register for structure piece types.
     */
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, Goo.MODID);

    /**
     * {@code goo:meteorite}, a fallen meteorite in its scorched crater on lunar's surface.
     * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
     */
    public static final DeferredHolder<StructureType<?>, StructureType<LandmarkStructure>> METEORITE =
            STRUCTURE_TYPES.register("meteorite", () -> landmark(MeteoritePiece::new, GooStructures::meteorite));
    /** The meteorite's one piece. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> METEORITE_PIECE =
            PIECE_TYPES.register("meteorite", () -> (StructurePieceType.ContextlessType) MeteoritePiece::new);

    /**
     * {@code goo:eldritch_nest}, a hollow sculk mound where the eldritch gather on lunar's surface.
     * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
     */
    public static final DeferredHolder<StructureType<?>, StructureType<LandmarkStructure>> ELDRITCH_NEST =
            STRUCTURE_TYPES.register("eldritch_nest", () -> landmark(NestPiece::new, GooStructures::eldritchNest));
    /** The nest's one piece. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> NEST_PIECE =
            PIECE_TYPES.register("eldritch_nest", () -> (StructurePieceType.ContextlessType) NestPiece::new);

    /**
     * {@code goo:monolith}, a tall, alien standing stone on lunar's open ground.
     * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
     */
    public static final DeferredHolder<StructureType<?>, StructureType<LandmarkStructure>> MONOLITH =
            STRUCTURE_TYPES.register("monolith", () -> landmark(MonolithPiece::new, GooStructures::monolith));
    /** The monolith's one piece. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> MONOLITH_PIECE =
            PIECE_TYPES.register("monolith", () -> (StructurePieceType.ContextlessType) MonolithPiece::new);

    private GooStructures() {
    }

    private static StructureType<LandmarkStructure> landmark(LandmarkStructure.PieceFactory pieces,
            Supplier<StructureType<?>> type) {
        MapCodec<LandmarkStructure> codec = LandmarkStructure.codec(pieces, type);
        return () -> codec;
    }

    private static StructureType<?> meteorite() {
        return METEORITE.get();
    }

    private static StructureType<?> eldritchNest() {
        return ELDRITCH_NEST.get();
    }

    private static StructureType<?> monolith() {
        return MONOLITH.get();
    }
}
