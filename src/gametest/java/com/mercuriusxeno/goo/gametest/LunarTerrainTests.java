package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.lunar.LunarRolls;
import com.mercuriusxeno.goo.lunar.SeaCavern;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests for goo:lunar's terrain: it generates from its own noise settings, and a column reads regolith at the
 * surface over anorthosite or basalt, molten iron over the iron core at depth, and bedrock at the floor; and its
 * ground holds meteoric iron veined through the anorthosite, and impact glass lines its giant craters' floors under
 * the regolith and breccia rings their rims.
 * decision lunar-generates-from-its-own-noise-and-surface-rules
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
 */
@RegistersGameTests
public final class LunarTerrainTests {
    private static final String LUNAR_COLUMN_READS_ITS_STRATA = "lunar_column_reads_its_strata";
    private static final String LUNAR_GROUND_HOLDS_METEORIC_IRON = "lunar_ground_holds_meteoric_iron";
    private static final String LUNAR_CRATER_FLOORS_READ_IMPACT_GLASS = "lunar_crater_floors_read_impact_glass";
    private static final String LUNAR_CRATER_RIMS_READ_BRECCIA = "lunar_crater_rims_read_breccia";

    private static final ResourceKey<Level> LUNAR = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar"));
    private static final Identifier LUNAR_NOISE = Identifier.fromNamespaceAndPath(Goo.MODID, "lunar");
    /** Columns spread over a few chunks, so one crater or basin reads no different from open ground. */
    private static final List<BlockPos> TEST_SPOTS = List.of(
            new BlockPos(8, 0, 8), new BlockPos(-40, 0, 72), new BlockPos(120, 0, -24));
    /** A walk down from the surface stops at the top of the shallow zone, past every surface skin. */
    private static final int MOLTEN_IRON_TOP_Y = LunarDepths.SHALLOW_ZONE_TOP_Y;
    /** The open air standing over the sea, at the least, in blocks. */
    private static final int AIR_OVER_THE_SEA = 12;
    /** How far from a test spot a search for a sea column reaches, in blocks. */
    private static final int SEA_SEARCH_REACH = 96;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String OWN_NOISE = "goo:lunar should generate from goo:lunar's noise settings, generates from %s";
    private static final String SURFACE = "%s should read the ground its biome lays, or a crater ray's anorthosite, at the surface y %d, "
            + "reads %s";
    private static final String BELOW_REGOLITH = "%s should read anorthosite, basalt or a crater's breccia or impact glass "
            + "under the ground its biome lays at y %d, reads %s";
    private static final String STRATUM = "%s should read %s at y %d, reads %s";
    private static final String NO_SEA_COLUMN = "No column within %d blocks of %s reads open sea";
    private static final String METEORIC_IRON_IN_ANORTHOSITE =
            "The chunks around %s should hold meteoric iron touching anorthosite";
    private static final String NO_CRATER_COLUMN = "No giant crater within %d cells of the origin has a %s column "
            + "standing in the highlands clear of every ray and boulder";
    private static final String NO_OPEN_GROUND = "No column within %d blocks of the origin lies outside every giant "
            + "crater and outside the shadowed basins";
    private static final String FLOOR_READS_GLASS = "The giant crater floor column %s should read impact glass under "
            + "its regolith at y %d, reads %s";
    private static final String OPEN_GROUND_READS_NO_GLASS = "The open ground column %s should read no impact glass, "
            + "reads it at y %d";
    private static final String RIM_READS_BRECCIA = "The giant crater rim column %s should read breccia under its "
            + "regolith at y %d, reads %s";
    /** How many giant crater cells round the origin a search for a crater column reaches each way. */
    private static final int CELL_REACH = 8;
    /** A floor column stands between the central peak's foot and the terraced wall, in radii from the centre. */
    private static final double FLOOR_COLUMN_REACH = 0.3;
    /** A rim column stands between the rim crest at one radius and partway down the rim's outer fall. */
    private static final double RIM_COLUMN_REACH = 1.15;
    /** A crater column is sought at this many angles round its crater. */
    private static final int COLUMN_ANGLES = 24;
    /** A crater column stands this far inside the highlands every way. */
    private static final int BIOME_MARGIN = 8;
    /** How far from the origin a search for open ground reaches, in blocks. */
    private static final int SEARCH_REACH = 160;
    /** The chunks one step around a test spot's own, which a vein search scans. */
    private static final int CHUNK_REACH = 1;
    private static final int CHUNK_SIDE = 16;
    /** The tallest the ground stands, which a vein search reads up to. */
    private static final int GROUND_TOP_Y = 100;

    private LunarTerrainTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_COLUMN_READS_ITS_STRATA, LunarTerrainTests::lunarColumnReadsItsStrata);
        GooTestFunctions.registerTest(registrar, LUNAR_GROUND_HOLDS_METEORIC_IRON,
                LunarTerrainTests::lunarGroundHoldsMeteoricIron);
        GooTestFunctions.registerTest(registrar, LUNAR_CRATER_FLOORS_READ_IMPACT_GLASS,
                LunarTerrainTests::lunarCraterFloorsReadImpactGlass);
        GooTestFunctions.registerTest(registrar, LUNAR_CRATER_RIMS_READ_BRECCIA, LunarTerrainTests::lunarCraterRimsReadBreccia);
    }

    /**
     * Each test spot's column in goo:lunar reads the ground its biome lays at its surface (or a crater ray's
     * anorthosite) (decision lunar-biomes-add-block-types-to-the-ground), anorthosite
     * or basalt beneath (or the breccia and impact glass a crater lays there); and the nearest column through the
     * open sea, found through the seeded sea cavern, reads bedrock at the world's floor, iron core over it to the sea
     * floor, molten iron from there up to the sea's level, and open air for 24 blocks over the sea.
     *
     * @param helper the gametest helper
     */
    public static void lunarColumnReadsItsStrata(GameTestHelper helper) {
        ServerLevel lunar = lunar(helper);
        ChunkGenerator generator = lunar.getChunkSource().getGenerator();
        String noise = generator instanceof NoiseBasedChunkGenerator noiseBased
                ? noiseBased.generatorSettings().unwrapKey().map(key -> key.identifier().toString()).orElse("inline")
                : generator.getClass().getSimpleName();
        helper.assertTrue(noise.equals(LUNAR_NOISE.toString()), String.format(OWN_NOISE, noise));
        for (BlockPos spot : TEST_SPOTS) {
            assertColumn(helper, lunar, spot);
        }
        helper.succeed();
    }

    private static void assertColumn(GameTestHelper helper, ServerLevel lunar, BlockPos spot) {
        lunar.getChunk(spot.getX() >> 4, spot.getZ() >> 4);
        int surfaceY = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, spot.getX(), spot.getZ()) - 1;
        BlockState surface = lunar.getBlockState(spot.atY(surfaceY));
        helper.assertTrue(LunarSurfaceBiomes.isGroundOf(lunar.getBiome(spot.atY(surfaceY)), surface)
                || surface.is(GooBlocks.ANORTHOSITE.get()), String.format(SURFACE, spot, surfaceY, surface));
        int y = surfaceY;
        while (y > MOLTEN_IRON_TOP_Y && LunarSurfaceBiomes.isLaidGround(lunar.getBlockState(spot.atY(y)))) {
            y--;
        }
        BlockState beneath = lunar.getBlockState(spot.atY(y));
        helper.assertTrue(beneath.is(GooBlocks.ANORTHOSITE.get()) || beneath.is(Blocks.BASALT)
                || beneath.is(GooBlocks.BRECCIA.get()) || beneath.is(GooBlocks.IMPACT_GLASS.get()),
                String.format(BELOW_REGOLITH, spot, y, beneath));
        assertSeaColumn(helper, lunar, spot);
    }

    private static void assertSeaColumn(GameTestHelper helper, ServerLevel lunar, BlockPos spot) {
        RandomState randomState = lunar.getChunkSource().randomState();
        SeaCavern sea = SeaCavern.seeded(randomState.getOrCreateNoise(SeaCavern.FLOOR_NOISE),
                randomState.getOrCreateNoise(SeaCavern.ROOF_NOISE), randomState.getOrCreateNoise(SeaCavern.PILLAR_NOISE));
        BlockPos column = null;
        for (int reach = 0; reach <= SEA_SEARCH_REACH && column == null; reach++) {
            BlockPos candidate = spot.offset(reach, 0, 0);
            if (sea.isOpenSeaAt(candidate.getX(), candidate.getZ())) {
                column = candidate;
            }
        }
        helper.assertTrue(column != null, String.format(NO_SEA_COLUMN, SEA_SEARCH_REACH, spot));
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        int floorY = sea.floorYAt(column.getX(), column.getZ());
        assertStratum(helper, lunar, column.atY(LunarDepths.WORLD_FLOOR_Y), Blocks.BEDROCK.defaultBlockState());
        for (int y = LunarDepths.WORLD_FLOOR_Y + 1; y <= floorY; y++) {
            assertStratum(helper, lunar, column.atY(y), GooBlocks.IRON_CORE.get().defaultBlockState());
        }
        for (int y = floorY + 1; y < LunarDepths.SEA_LEVEL_Y; y++) {
            assertStratum(helper, lunar, column.atY(y), GooBlocks.MOLTEN_IRON.get().defaultBlockState());
        }
        for (int y = LunarDepths.SEA_LEVEL_Y; y < LunarDepths.SEA_LEVEL_Y + AIR_OVER_THE_SEA; y++) {
            assertStratum(helper, lunar, column.atY(y), Blocks.AIR.defaultBlockState());
        }
    }

    private static void assertStratum(GameTestHelper helper, ServerLevel lunar, BlockPos pos, BlockState expected) {
        BlockState found = lunar.getBlockState(pos);
        helper.assertTrue(found.is(expected.getBlock()),
                String.format(STRATUM, pos.atY(0), expected.getBlock(), pos.getY(), found));
    }

    /**
     * The chunks around each test spot in goo:lunar hold meteoric iron touching anorthosite: the veins run through
     * lunar's highland stone.
     *
     * @param helper the gametest helper
     */
    public static void lunarGroundHoldsMeteoricIron(GameTestHelper helper) {
        ServerLevel lunar = lunar(helper);
        for (BlockPos spot : TEST_SPOTS) {
            helper.assertTrue(holdsMeteoricIronInAnorthosite(lunar, spot), String.format(METEORIC_IRON_IN_ANORTHOSITE, spot));
        }
        helper.succeed();
    }

    private static boolean holdsMeteoricIronInAnorthosite(ServerLevel lunar, BlockPos spot) {
        int spotChunkX = spot.getX() >> 4;
        int spotChunkZ = spot.getZ() >> 4;
        for (int chunkX = spotChunkX - CHUNK_REACH; chunkX <= spotChunkX + CHUNK_REACH; chunkX++) {
            for (int chunkZ = spotChunkZ - CHUNK_REACH; chunkZ <= spotChunkZ + CHUNK_REACH; chunkZ++) {
                lunar.getChunk(chunkX, chunkZ);
                if (chunkHoldsMeteoricIronInAnorthosite(lunar, chunkX, chunkZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean chunkHoldsMeteoricIronInAnorthosite(ServerLevel lunar, int chunkX, int chunkZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < CHUNK_SIDE; x++) {
            for (int z = 0; z < CHUNK_SIDE; z++) {
                for (int y = MOLTEN_IRON_TOP_Y + 1; y <= GROUND_TOP_Y; y++) {
                    pos.set(chunkX * CHUNK_SIDE + x, y, chunkZ * CHUNK_SIDE + z);
                    if (lunar.getBlockState(pos).is(GooBlocks.METEORIC_IRON.get()) && touchesAnorthosite(lunar, pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean touchesAnorthosite(ServerLevel lunar, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (lunar.getBlockState(pos.relative(side)).is(GooBlocks.ANORTHOSITE.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A column on a giant crater's floor in goo:lunar's highlands reads impact glass under its regolith, and a column
     * outside every giant crater and outside the shadowed basins, whose own ground is impact glass and breccia, reads
     * none; both columns are found through the seeded GiantCraterField the terrain sinks its craters from.
     * decision craters-are-rare-giant-landmarks-only
     *
     * @param helper the gametest helper
     */
    public static void lunarCraterFloorsReadImpactGlass(GameTestHelper helper) {
        ServerLevel lunar = lunar(helper);
        GiantCraterField giants = giants(lunar);
        BlockPos floor = craterColumn(lunar, giants, FLOOR_COLUMN_REACH, bareGround(lunar, giants));
        helper.assertTrue(floor != null, String.format(NO_CRATER_COLUMN, CELL_REACH, "floor"));
        BlockPos openGround = findColumn(column -> giants.isOpenGroundAt(column.getX(), column.getZ())
                && !LunarSurfaceBiomes.surfaceBiomeAt(lunar, column.getX(), column.getZ())
                .is(LunarSurfaceBiomes.SHADOWED_BASINS));
        helper.assertTrue(openGround != null, String.format(NO_OPEN_GROUND, SEARCH_REACH));
        int y = yBeneathTheRegolith(lunar, floor);
        BlockState beneath = lunar.getBlockState(floor.atY(y));
        helper.assertTrue(beneath.is(GooBlocks.IMPACT_GLASS.get()), String.format(FLOOR_READS_GLASS, floor, y, beneath));
        assertOpenGroundReadsNoImpactGlass(helper, lunar, openGround);
        helper.succeed();
    }

    /**
     * A column on a giant crater's rim in goo:lunar's highlands, past its floor, reads breccia under its regolith; the
     * column is found through the seeded GiantCraterField the terrain sinks its craters from.
     * decision craters-are-rare-giant-landmarks-only
     *
     * @param helper the gametest helper
     */
    public static void lunarCraterRimsReadBreccia(GameTestHelper helper) {
        ServerLevel lunar = lunar(helper);
        GiantCraterField giants = giants(lunar);
        BlockPos rim = craterColumn(lunar, giants, RIM_COLUMN_REACH, bareGround(lunar, giants));
        helper.assertTrue(rim != null, String.format(NO_CRATER_COLUMN, CELL_REACH, "rim"));
        int y = yBeneathTheRegolith(lunar, rim);
        BlockState beneath = lunar.getBlockState(rim.atY(y));
        helper.assertTrue(beneath.is(GooBlocks.BRECCIA.get()), String.format(RIM_READS_BRECCIA, rim, y, beneath));
        helper.succeed();
    }

    private static ServerLevel lunar(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        return lunar;
    }

    private static GiantCraterField giants(ServerLevel lunar) {
        return GiantCraterField.seeded(lunar.getChunkSource().randomState().getOrCreateNoise(LunarRolls.CRATER_NOISE),
                lunar.getChunkSource().randomState());
    }

    /**
     * Passes a column no giant crater's ejecta ray crosses and no thrown boulder covers, where a ray's anorthosite or
     * a boulder's stone would stand in for the regolith a test reads under.
     */
    private static Predicate<BlockPos> bareGround(ServerLevel lunar, GiantCraterField giants) {
        Predicate<BlockPos> rayFree = column -> !giants.isOnRayAt(column.getX(), column.getZ());
        return rayFree.and(ClearOfBoulders.in(lunar));
    }

    /**
     * A column a reach out from the centre of the nearest giant crater whose column there stands in the highlands,
     * where regolith lies over the stone, and passes a test; walking giant crater cells out from the origin, or null
     * where the search finds none.
     */
    private static BlockPos craterColumn(ServerLevel lunar, GiantCraterField giants, double reach,
            Predicate<BlockPos> wanted) {
        for (int ring = 0; ring <= CELL_REACH; ring++) {
            for (int cellX = -ring; cellX <= ring; cellX++) {
                for (int cellZ = -ring; cellZ <= ring; cellZ++) {
                    if (Math.max(Math.abs(cellX), Math.abs(cellZ)) != ring) {
                        continue;
                    }
                    BlockPos column = craterColumnIn(lunar, giants.craterInCell(cellX, cellZ), reach, wanted);
                    if (column != null) {
                        return column;
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos craterColumnIn(ServerLevel lunar, GiantCraterField.Crater crater, double reach,
            Predicate<BlockPos> wanted) {
        for (int i = 0; i < COLUMN_ANGLES; i++) {
            double angle = 2.0 * Math.PI * i / COLUMN_ANGLES;
            BlockPos column = BlockPos.containing(crater.centerX() + Math.cos(angle) * crater.radius() * reach, 0.0,
                    crater.centerZ() + Math.sin(angle) * crater.radius() * reach);
            if (inHighlandsWithMargin(lunar, column) && wanted.test(column)) {
                return column;
            }
        }
        return null;
    }

    /**
     * Whether a column and its neighbours a margin out every way stand in the highlands, so the surface rule's biome
     * lookup, which vanilla's biome zoom jitters by a few blocks, reads the highlands at the column.
     */
    private static boolean inHighlandsWithMargin(ServerLevel lunar, BlockPos column) {
        for (int dx = -BIOME_MARGIN; dx <= BIOME_MARGIN; dx += BIOME_MARGIN) {
            for (int dz = -BIOME_MARGIN; dz <= BIOME_MARGIN; dz += BIOME_MARGIN) {
                if (!LunarSurfaceBiomes.surfaceBiomeAt(lunar, column.getX() + dx, column.getZ() + dz)
                        .is(LunarSurfaceBiomes.HIGHLANDS)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockPos findColumn(Predicate<BlockPos> wanted) {
        for (int x = -SEARCH_REACH; x <= SEARCH_REACH; x++) {
            for (int z = -SEARCH_REACH; z <= SEARCH_REACH; z++) {
                BlockPos column = new BlockPos(x, 0, z);
                if (wanted.test(column)) {
                    return column;
                }
            }
        }
        return null;
    }

    /**
     * The height of the first block under a column's surface skin: its regolith, or the crater ice standing in its
     * place on a frozen floor, and any meteoric iron nugget lying in it.
     */
    private static int yBeneathTheRegolith(ServerLevel lunar, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        int y = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        while (y > MOLTEN_IRON_TOP_Y && (lunar.getBlockState(column.atY(y)).is(GooBlocks.REGOLITH.get())
                || lunar.getBlockState(column.atY(y)).is(GooBlocks.METEORIC_IRON.get())
                || lunar.getBlockState(column.atY(y)).is(GooBlocks.CRATER_ICE.get()))) {
            y--;
        }
        return y;
    }

    private static void assertOpenGroundReadsNoImpactGlass(GameTestHelper helper, ServerLevel lunar, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        int surfaceY = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        for (int y = surfaceY; y > MOLTEN_IRON_TOP_Y; y--) {
            helper.assertFalse(lunar.getBlockState(column.atY(y)).is(GooBlocks.IMPACT_GLASS.get()),
                    String.format(OPEN_GROUND_READS_NO_GLASS, column, y));
        }
    }
}
