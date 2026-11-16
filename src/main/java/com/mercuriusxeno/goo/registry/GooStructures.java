package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.MeteoritePiece;
import com.mercuriusxeno.goo.lunar.MeteoriteStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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
    public static final DeferredHolder<StructureType<?>, StructureType<MeteoriteStructure>> METEORITE =
            STRUCTURE_TYPES.register("meteorite", () -> () -> MeteoriteStructure.CODEC);
    /** The meteorite's one piece. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> METEORITE_PIECE =
            PIECE_TYPES.register("meteorite", () -> (StructurePieceType.ContextlessType) MeteoritePiece::new);

    private GooStructures() {
    }
}
