package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooEntities;
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
import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's landmarks, each generated at a spot in goo:lunar the way the place command generates a
 * structure, and each placed in goo:lunar by its structure set: the fallen meteorite in its scorched crater, and the
 * eldritch nest where the monstrosities gather, the monolith standing over open ground, and the ruin half buried in
 * the regolith.
 * decision lunar-holds-meteorites-eldritch-nests-monoliths-and-ruins
 */
@RegistersGameTests
public final class LunarLandmarkTests {
    private static final String METEORITE_SITS_IN_A_SCORCHED_CRATER = "meteorite_sits_in_a_scorched_crater";
    private static final String NEST_GATHERS_THE_ELDRITCH = "nest_gathers_the_eldritch";
    private static final String MONOLITH_STANDS_OVER_THE_SURFACE = "monolith_stands_over_the_surface";
    private static final String RUIN_LIES_HALF_BURIED = "ruin_lies_half_buried";
    private static final ResourceKey<Structure> RUIN = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_ruin"));
    private static final ResourceKey<StructureSet> RUINS = ResourceKey.create(Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_ruins"));
    private static final ResourceKey<LootTable> RUIN_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "chests/lunar_ruin"));
    private static final BlockPos RUIN_SPOT = new BlockPos(1200, 0, 1200);
    /** The surrounding surface is read this far outside the ruin's box. */
    private static final int OUTSIDE = 3;
    private static final String RUIN_WALLS = "The ruin at %s should stand end stone brick walls both over and under "
            + "the surrounding surface at y %d, stands %d over and %d under";
    private static final String RUIN_CHEST = "The ruin at %s should hold a chest reading %s, holds %s";
    private static final ResourceKey<Structure> MONOLITH = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "monolith"));
    private static final ResourceKey<StructureSet> MONOLITHS = ResourceKey.create(Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath(Goo.MODID, "monoliths"));
    private static final BlockPos MONOLITH_SPOT = new BlockPos(1200, 0, 1000);
    /** The least the slab stands over the surface beside it, in blocks. */
    private static final int LEAST_RISE = 12;
    /** The surface is read this far from the slab's centre, clear of its footing. */
    private static final int BESIDE = 6;
    private static final String MONOLITH_RISES = "The monolith at %s should stand a slab %d blocks tall over the "
            + "surface beside it at y %d, stands its top at y %d on a slab %d blocks tall";
    private static final ResourceKey<Structure> NEST = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "eldritch_nest"));
    private static final ResourceKey<StructureSet> NESTS = ResourceKey.create(Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath(Goo.MODID, "eldritch_nests"));
    private static final BlockPos NEST_SPOT = new BlockPos(1000, 0, 1200);
    /** The nest's hollow stands at least this many blocks tall down its centre. */
    private static final int LEAST_HOLLOW = 3;
    private static final String NEST_HOLLOW = "The nest at %s should stand sculk over a hollow of %d blocks over a sculk "
            + "floor, reads %s over %d blocks of air over %s";
    private static final String NEST_OUTSPAWNS = "The nest's spawn overrides should weigh goo:eldritch_monstrosity over "
            + "the lunar biome's %d, weigh %d";

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
        GooTestFunctions.registerTest(registrar, NEST_GATHERS_THE_ELDRITCH, LunarLandmarkTests::nestGathersTheEldritch);
        GooTestFunctions.registerTest(registrar, MONOLITH_STANDS_OVER_THE_SURFACE,
                LunarLandmarkTests::monolithStandsOverTheSurface);
        GooTestFunctions.registerTest(registrar, RUIN_LIES_HALF_BURIED, LunarLandmarkTests::ruinLiesHalfBuried);
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

    /**
     * The eldritch nest generated at a spot in goo:lunar leaves, down its chunk's centre column, a sculk roof over a
     * hollow over a sculk floor; its spawn overrides weigh the monstrosity over the lunar biome's spawner; and its
     * structure set places it in goo:lunar's biome.
     *
     * @param helper the gametest helper
     */
    public static void nestGathersTheEldritch(GameTestHelper helper) {
        ServerLevel lunar = place(helper, NEST, NEST_SPOT);
        ChunkPos chunk = ChunkPos.containing(NEST_SPOT);
        BlockPos column = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        int top = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        BlockState roof = lunar.getBlockState(column.atY(top));
        int y = top - 1;
        while (lunar.getBlockState(column.atY(y)).isAir()) {
            y--;
        }
        int hollow = top - 1 - y;
        BlockState floor = lunar.getBlockState(column.atY(y));
        helper.assertTrue(roof.is(Blocks.SCULK) && hollow >= LEAST_HOLLOW && floor.is(Blocks.SCULK),
                String.format(NEST_HOLLOW, column.atY(top), LEAST_HOLLOW, roof, hollow, floor));
        int biomeWeight = monstrosityWeight(lunar.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(LUNAR_BIOME)
                .value().getAttributes().applyModifier(EnvironmentAttributes.NATURAL_MOB_SPAWNS, MobSpawnSettings.EMPTY)
                .getMobsToSpawn(MobCategory.MONSTER));
        StructureSpawnOverride overrides = lunar.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(NEST)
                .value().spawnOverrides().get(MobCategory.MONSTER);
        int nestWeight = overrides == null ? 0 : monstrosityWeight(overrides.spawns());
        helper.assertTrue(nestWeight > biomeWeight, String.format(NEST_OUTSPAWNS, biomeWeight, nestWeight));
        assertSetPlacesInLunar(helper, lunar, NESTS, NEST);
        helper.succeed();
    }

    /**
     * The monolith generated at a spot in goo:lunar stands one slab of at least 12 blocks down its centre column, its
     * top at least 12 blocks over the surface beside it; and its structure set places it in goo:lunar's biome.
     *
     * @param helper the gametest helper
     */
    public static void monolithStandsOverTheSurface(GameTestHelper helper) {
        ServerLevel lunar = place(helper, MONOLITH, MONOLITH_SPOT);
        ChunkPos chunk = ChunkPos.containing(MONOLITH_SPOT);
        BlockPos column = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        int top = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        int beside = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX() + BESIDE, column.getZ()) - 1;
        int slab = 0;
        while (lunar.getBlockState(column.atY(top - slab)).is(GooBlocks.IMPACT_GLASS.get())) {
            slab++;
        }
        helper.assertTrue(slab >= LEAST_RISE && top - beside >= LEAST_RISE,
                String.format(MONOLITH_RISES, column, LEAST_RISE, beside, top, slab));
        assertSetPlacesInLunar(helper, lunar, MONOLITHS, MONOLITH);
        helper.succeed();
    }

    /**
     * The ruin generated at a spot in goo:lunar stands end stone brick walls both over and under the surface outside
     * its box, and holds a chest reading the lunar ruin loot table; and its structure set places it in goo:lunar's
     * biome.
     *
     * @param helper the gametest helper
     */
    public static void ruinLiesHalfBuried(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        StructureStart start = generate(lunar, RUIN, RUIN_SPOT);
        helper.assertTrue(start.isValid(), String.format(PLACED, RUIN.identifier(), RUIN_SPOT, "no start"));
        BoundingBox box = start.getBoundingBox();
        BlockPos outside = new BlockPos(box.minX() - OUTSIDE, 0, box.getCenter().getZ());
        lunar.getChunk(outside.getX() >> 4, outside.getZ() >> 4);
        int surfaceY = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, outside.getX(), outside.getZ()) - 1;
        int over = 0;
        int under = 0;
        String chestLoot = "no chest";
        for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState block = lunar.getBlockState(at);
            if (block.is(Blocks.END_STONE_BRICKS)) {
                over += at.getY() > surfaceY ? 1 : 0;
                under += at.getY() < surfaceY ? 1 : 0;
            }
            if (lunar.getBlockEntity(at) instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
                chestLoot = chest.getLootTable().identifier().toString();
            }
        }
        helper.assertTrue(over > 0 && under > 0, String.format(RUIN_WALLS, box.getCenter(), surfaceY, over, under));
        helper.assertTrue(chestLoot.equals(RUIN_LOOT.identifier().toString()),
                String.format(RUIN_CHEST, box.getCenter(), RUIN_LOOT.identifier(), chestLoot));
        assertSetPlacesInLunar(helper, lunar, RUINS, RUIN);
        helper.succeed();
    }

    /**
     * Generates a structure at a spot in goo:lunar as the place command does, placing it in every chunk its box
     * reaches, and answers its start.
     */
    static StructureStart generate(ServerLevel lunar, ResourceKey<Structure> structure, BlockPos spot) {
        Holder.Reference<Structure> holder = lunar.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOrThrow(structure);
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        StructureStart start = holder.value().generate(holder, lunar.dimension(), lunar.registryAccess(), generator,
                generator.getBiomeSource(), lunar.getChunkSource().randomState()
                        .createClimateSampler(SamplerContext.builder().enableCaches().build()),
                lunar.getChunkSource().randomState(), lunar.getServer().getStructureTemplateManager(),
                lunar.getSeed(), ChunkPos.containing(spot), 0, lunar, biome -> true);
        if (start.isValid()) {
            BoundingBox box = start.getBoundingBox();
            ChunkPos.rangeClosed(ChunkPos.containing(new BlockPos(box.minX(), 0, box.minZ())),
                    ChunkPos.containing(new BlockPos(box.maxX(), 0, box.maxZ()))).forEach(chunk -> {
                        lunar.getChunk(chunk.x(), chunk.z());
                        start.placeInChunk(lunar, lunar.structureManager(), generator, lunar.getRandom(),
                                new BoundingBox(chunk.getMinBlockX(), lunar.getMinY(), chunk.getMinBlockZ(),
                                        chunk.getMaxBlockX(), lunar.getMaxY(), chunk.getMaxBlockZ()), chunk);
                    });
        }
        return start;
    }

    private static int monstrosityWeight(WeightedList<MobSpawnSettings.SpawnerData> spawns) {
        return spawns.unwrap().stream().filter(spawn -> spawn.value().type() == GooEntities.ELDRITCH_MONSTROSITY.get())
                .mapToInt(Weighted::weight).max().orElse(0);
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
