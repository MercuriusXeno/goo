package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's landmarks, each generated at a spot in goo:lunar the way the place command generates a
 * structure, and each placed in goo:lunar by its structure set: the fallen meteorite in its scorched crater.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
@RegistersGameTests
public final class LunarLandmarkTests {
    private static final String METEORITE_SITS_IN_A_SCORCHED_CRATER = "meteorite_sits_in_a_scorched_crater";

    private static final ResourceKey<Biome> LUNAR_BIOME = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));
    private static final ResourceKey<Structure> METEORITE = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "meteorite"));
    private static final ResourceKey<StructureSet> METEORITES = ResourceKey.create(Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath(Goo.MODID, "meteorites"));
    /** The spot each landmark generates at, far from the other lunar tests' ground. */
    private static final BlockPos METEORITE_SPOT = new BlockPos(1000, 0, 1000);
    /** How far under the lump's top the scan for its floor reads. */
    private static final int LUMP_SCAN_DEPTH = 12;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String PLACED = "The %s structure should generate at %s: %s";
    private static final String LUMP_HOLDS = "The meteorite's lump at %s should hold meteoric iron and impact glass, "
            + "holds iron %s and glass %s";
    private static final String ON_BASALT = "The meteorite's lump at %s should sit on a basalt floor at y %d, sits on %s";
    private static final String SET_PLACES = "The %s structure set should place %s in goo:lunar's biome";

    private LunarLandmarkTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, METEORITE_SITS_IN_A_SCORCHED_CRATER,
                LunarLandmarkTests::meteoriteSitsInAScorchedCrater);
    }

    /**
     * The meteorite generated at a spot in goo:lunar leaves, down its chunk's centre column, a lump holding meteoric
     * iron and impact glass on a basalt floor; and the meteorites structure set places the meteorite in goo:lunar's
     * biome.
     *
     * @param helper the gametest helper
     */
    public static void meteoriteSitsInAScorchedCrater(GameTestHelper helper) {
        ServerLevel lunar = place(helper, METEORITE, METEORITE_SPOT);
        ChunkPos chunk = ChunkPos.containing(METEORITE_SPOT);
        BlockPos column = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        int top = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        boolean iron = false;
        boolean glass = false;
        int y = top;
        while (y > top - LUMP_SCAN_DEPTH) {
            BlockState block = lunar.getBlockState(column.atY(y));
            if (!block.is(GooBlocks.METEORIC_IRON.get()) && !block.is(GooBlocks.IMPACT_GLASS.get())) {
                break;
            }
            iron |= block.is(GooBlocks.METEORIC_IRON.get());
            glass |= block.is(GooBlocks.IMPACT_GLASS.get());
            y--;
        }
        helper.assertTrue(iron && glass, String.format(LUMP_HOLDS, column.atY(top), iron, glass));
        BlockState floor = lunar.getBlockState(column.atY(y));
        helper.assertTrue(floor.is(Blocks.BASALT), String.format(ON_BASALT, column.atY(top), y, floor));
        assertSetPlacesInLunar(helper, lunar, METEORITES, METEORITE);
        helper.succeed();
    }

    /** Generates a structure at a spot in goo:lunar as the place command does, and answers lunar. */
    static ServerLevel place(GameTestHelper helper, ResourceKey<Structure> structure, BlockPos spot) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        Holder.Reference<Structure> holder = lunar.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(structure);
        ChunkPos chunk = ChunkPos.containing(spot);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                lunar.getChunk(chunk.x() + dx, chunk.z() + dz);
            }
        }
        try {
            PlaceCommand.placeStructure(lunar.getServer().createCommandSourceStack().withLevel(lunar).withSuppressedOutput(),
                    holder, spot);
        } catch (CommandSyntaxException failed) {
            helper.fail(String.format(PLACED, structure.identifier(), spot, failed.getMessage()));
        }
        return lunar;
    }

    /** Asserts a structure set names a structure whose biomes hold goo:lunar. */
    static void assertSetPlacesInLunar(GameTestHelper helper, ServerLevel lunar, ResourceKey<StructureSet> set,
            ResourceKey<Structure> structure) {
        StructureSet placed = lunar.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(set).value();
        boolean places = placed.structures().stream().anyMatch(entry -> entry.structure().is(structure)
                && biomesHoldLunar(entry.structure().value().biomes()));
        helper.assertTrue(places, String.format(SET_PLACES, set.identifier(), structure.identifier()));
    }

    private static boolean biomesHoldLunar(HolderSet<Biome> biomes) {
        return biomes.stream().anyMatch(biome -> biome.is(LUNAR_BIOME));
    }
}
