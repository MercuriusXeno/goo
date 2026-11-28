package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
            + "eldritch ground's %d, weigh %d";

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
    private static final String LUMP_HOLDS = "The meteorite's lump at %s should hold meteoric iron";
    private static final String ON_BASALT = "The meteorite's lump at %s should sit on a basalt floor at y %d, sits on %s";
    private static final String SET_PLACES = "The %s structure set should place %s in one of goo:lunar's surface biomes";
    private static final String METEORITE_HOLDS_A_NATIVE_IRON_LODE = "meteorite_holds_a_native_iron_lode";
    /** The lode's meteorite generates here, apart from the scorched crater test's. */
    private static final BlockPos LODE_SPOT = new BlockPos(1400, 0, 1000);
    /** The scan for native iron reaches this far from the lump's centre. */
    private static final int LODE_SCAN_REACH = 4;
    private static final String LODE_AT_HEART = "The meteorite's lump spanning y %d to %d at %s should hold native iron "
            + "at its centre, holds %s";
    private static final String NEST_HOLDS_A_GUARDED_CACHE = "nest_holds_a_guarded_cache";
    /** The cache's nest generates here, apart from the gathering test's. */
    private static final BlockPos CACHE_SPOT = new BlockPos(1400, 0, 1200);
    private static final ResourceKey<LootTable> NEST_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Goo.MODID, "chests/lunar_nest"));
    private static final String NEST_CACHE = "The nest at %s should hold a container on its floor at its centre reading "
            + "%s, holds %s";
    private static final String MONOLITH_GROWS_ASTRAL_CRYSTAL_AND_POOLS_ASTRAL_GOO =
            "monolith_grows_astral_crystal_and_pools_astral_goo";
    /** The prize's monolith generates here, apart from the standing test's. */
    private static final BlockPos PRIZE_SPOT = new BlockPos(1400, 0, 1400);
    /** The slab's lowest course is read this far either side of its centre. */
    private static final int SLAB_SCAN_REACH = 3;
    /** Astral goo is sought this far from the slab's centre, in blocks, across and up and down. */
    private static final int POOL_REACH = 5;
    private static final String CRYSTAL_AT_BASE = "The monolith at %s should grow astral crystal beside its lowest "
            + "course at y %d, grows none";
    private static final String GOO_BESIDE = "The monolith at %s should pool astral goo within %d blocks, pools none";
    private static final String LODE_ENCLOSED = "The meteorite's native iron at %s should be enclosed, touches air at %s";

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
        GooTestFunctions.registerTest(registrar, METEORITE_HOLDS_A_NATIVE_IRON_LODE,
                LunarLandmarkTests::meteoriteHoldsANativeIronLode);
        GooTestFunctions.registerTest(registrar, NEST_HOLDS_A_GUARDED_CACHE, LunarLandmarkTests::nestHoldsAGuardedCache);
        GooTestFunctions.registerTest(registrar, MONOLITH_GROWS_ASTRAL_CRYSTAL_AND_POOLS_ASTRAL_GOO,
                LunarLandmarkTests::monolithGrowsAstralCrystalAndPoolsAstralGoo);
    }

    /**
     * The monolith generated at a spot in goo:lunar grows astral crystal beside its slab's lowest course, and pools
     * astral world goo on the ground within a few blocks of it.
     * decision each-lunar-landmark-holds-its-own-prize
     *
     * @param helper the gametest helper
     */
    public static void monolithGrowsAstralCrystalAndPoolsAstralGoo(GameTestHelper helper) {
        ServerLevel lunar = place(helper, MONOLITH, PRIZE_SPOT);
        ChunkPos chunk = ChunkPos.containing(PRIZE_SPOT);
        BlockPos column = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        int base = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        while (lunar.getBlockState(column.atY(base - 1)).is(GooBlocks.IMPACT_GLASS.get())) {
            base--;
        }
        BlockPos center = column.atY(base);
        boolean crystal = false;
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-SLAB_SCAN_REACH, 0, -SLAB_SCAN_REACH),
                center.offset(SLAB_SCAN_REACH, 0, SLAB_SCAN_REACH))) {
            if (lunar.getBlockState(at).is(GooBlocks.IMPACT_GLASS.get())) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    crystal |= lunar.getBlockState(at.relative(side)).is(GooBlocks.ASTRAL_CRYSTAL.get());
                }
            }
        }
        helper.assertTrue(crystal, String.format(CRYSTAL_AT_BASE, column, base));
        boolean goo = false;
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-POOL_REACH, -POOL_REACH, -POOL_REACH),
                center.offset(POOL_REACH, POOL_REACH, POOL_REACH))) {
            goo |= lunar.getBlockState(at).is(GooBlocks.ASTRAL_DOWSING.get());
        }
        helper.assertTrue(goo, String.format(GOO_BESIDE, column, POOL_REACH));
        helper.succeed();
    }

    /**
     * The eldritch nest generated at a spot in goo:lunar holds, on the floor of its hollow down its chunk's centre
     * column, one container reading the lunar nest loot table.
     * decision each-lunar-landmark-holds-its-own-prize
     *
     * @param helper the gametest helper
     */
    public static void nestHoldsAGuardedCache(GameTestHelper helper) {
        ServerLevel lunar = place(helper, NEST, CACHE_SPOT);
        ChunkPos chunk = ChunkPos.containing(CACHE_SPOT);
        BlockPos column = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        int top = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        int y = top - 1;
        while (lunar.getBlockState(column.atY(y)).isAir()) {
            y--;
        }
        String cacheLoot = lunar.getBlockEntity(column.atY(y)) instanceof RandomizableContainerBlockEntity cache
                && cache.getLootTable() != null ? cache.getLootTable().identifier().toString()
                : lunar.getBlockState(column.atY(y)).toString();
        helper.assertTrue(cacheLoot.equals(NEST_LOOT.identifier().toString()),
                String.format(NEST_CACHE, column.atY(y), NEST_LOOT.identifier(), cacheLoot));
        helper.succeed();
    }

    /**
     * The meteorite generated at a spot in goo:lunar holds native iron at the centre of its lump, down its chunk's
     * centre column, and no native iron block of it touches air.
     * decision each-lunar-landmark-holds-its-own-prize
     *
     * @param helper the gametest helper
     */
    public static void meteoriteHoldsANativeIronLode(GameTestHelper helper) {
        ServerLevel lunar = place(helper, METEORITE, LODE_SPOT);
        ChunkPos chunk = ChunkPos.containing(LODE_SPOT);
        BlockPos column = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        int top = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        int bottom = top;
        while (bottom > top - LUMP_SCAN_DEPTH && !lunar.getBlockState(column.atY(bottom - 1)).is(Blocks.BASALT)) {
            bottom--;
        }
        BlockPos heart = column.atY((top + bottom) / 2);
        BlockState atHeart = lunar.getBlockState(heart);
        helper.assertTrue(atHeart.is(GooBlocks.NATIVE_IRON.get()), String.format(LODE_AT_HEART, bottom, top, column,
                atHeart));
        for (BlockPos at : BlockPos.betweenClosed(heart.offset(-LODE_SCAN_REACH, -LODE_SCAN_REACH, -LODE_SCAN_REACH),
                heart.offset(LODE_SCAN_REACH, LODE_SCAN_REACH, LODE_SCAN_REACH))) {
            if (!lunar.getBlockState(at).is(GooBlocks.NATIVE_IRON.get())) {
                continue;
            }
            for (Direction side : Direction.values()) {
                BlockPos beside = at.relative(side);
                helper.assertTrue(!lunar.getBlockState(beside).isAir(), String.format(LODE_ENCLOSED, at.immutable(),
                        beside));
            }
        }
        helper.succeed();
    }

    /**
     * The meteorite generated at a spot in goo:lunar leaves, down its chunk's centre column, a lump holding meteoric
     * iron on a basalt floor; and the meteorites structure set places the meteorite in goo:lunar's
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
        int y = top;
        while (y > top - LUMP_SCAN_DEPTH) {
            BlockState block = lunar.getBlockState(column.atY(y));
            if (!block.is(GooBlocks.METEORIC_IRON.get()) && !block.is(GooBlocks.NATIVE_IRON.get())) {
                break;
            }
            iron |= block.is(GooBlocks.METEORIC_IRON.get());
            y--;
        }
        helper.assertTrue(iron, String.format(LUMP_HOLDS, column.atY(top)));
        BlockState floor = lunar.getBlockState(column.atY(y));
        helper.assertTrue(floor.is(Blocks.BASALT), String.format(ON_BASALT, column.atY(top), y, floor));
        assertSetPlacesInLunar(helper, lunar, METEORITES, METEORITE);
        helper.succeed();
    }

    /**
     * The eldritch nest generated at a spot in goo:lunar leaves, down its chunk's centre column, a sculk roof over a
     * hollow, its cache standing in it, over a sculk floor; its spawn overrides weigh the monstrosity over the lunar biome's spawner; and its
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
        while (lunar.getBlockState(column.atY(y)).isAir() || lunar.getBlockState(column.atY(y)).is(Blocks.CHEST)) {
            y--;
        }
        int hollow = top - 1 - y;
        BlockState floor = lunar.getBlockState(column.atY(y));
        helper.assertTrue(roof.is(Blocks.SCULK) && hollow >= LEAST_HOLLOW && floor.is(Blocks.SCULK),
                String.format(NEST_HOLLOW, column.atY(top), LEAST_HOLLOW, roof, hollow, floor));
        int biomeWeight = monstrosityWeight(lunar.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(
                LunarSurfaceBiomes.ELDRITCH_GROUND)
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

    /** Asserts a structure set names a structure whose biomes hold one of goo:lunar's surface biomes. */
    static void assertSetPlacesInLunar(GameTestHelper helper, ServerLevel lunar, ResourceKey<StructureSet> set,
            ResourceKey<Structure> structure) {
        StructureSet placed = lunar.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(set).value();
        boolean places = placed.structures().stream().anyMatch(entry -> entry.structure().is(structure)
                && biomesHoldLunar(entry.structure().value().biomes()));
        helper.assertTrue(places, String.format(SET_PLACES, set.identifier(), structure.identifier()));
    }

    private static boolean biomesHoldLunar(HolderSet<Biome> biomes) {
        return biomes.stream().anyMatch(biome -> LunarSurfaceBiomes.ALL.stream().anyMatch(biome::is));
    }
}
