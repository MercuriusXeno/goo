package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.CraterField;
import com.mercuriusxeno.goo.lunar.GiantCraterField;
import com.mercuriusxeno.goo.lunar.LunarDepths;
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
import net.minecraft.world.level.levelgen.synth.Noise;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests for goo:lunar's terrain: it generates from its own noise settings, and a column reads regolith at the
 * surface over anorthosite or basalt, molten iron over the iron core at depth, and bedrock at the floor; and its
 * ground holds meteoric iron veined through the anorthosite, and impact glass lines its crater floors under the
 * regolith, breccia rings their rims and crater ice fills the floors of the deepest.
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
    private static final String LUNAR_DEEP_CRATER_FLOORS_READ_CRATER_ICE = "lunar_deep_crater_floors_read_crater_ice";

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
    private static final String NO_CRATER_CENTRE = "No column within %d blocks of the origin sits at a crater's centre";
    private static final String NO_OPEN_GROUND = "No column within %d blocks of the origin lies outside every crater";
    private static final String CENTRE_READS_GLASS = "The crater centre column %s should read impact glass under its "
            + "regolith at y %d, reads %s";
    private static final String OPEN_GROUND_READS_NO_GLASS = "The open ground column %s should read no impact glass, "
            + "reads it at y %d";
    private static final String NO_CRATER_RIM = "No column within %d blocks of the origin sits on a crater's rim "
            + "clear of every crater floor";
    private static final String RIM_READS_BRECCIA = "The crater rim column %s should read breccia under its regolith "
            + "at y %d, reads %s";
    private static final String NO_DEEP_CRATER = "No crater past the depth threshold within %d crater cells of a "
            + "spot deep in the shadowed basins centres in them";
    /** How many crater cells round a spot in the shadowed basins a search for a deep crater reaches each way. */
    private static final int DEEP_CELL_REACH = 32;
    /**
     * A crater worn past this share is old and filled, its bowl too shallow to hold a pool on sloping ground, so the
     * search takes a sharp one (decision no-two-lunar-features-look-alike).
     */
    private static final double SHARP_WEAR = 0.5;
    private static final String NO_SHALLOW_CRATER = "No column within %d blocks of the origin sits at the centre of a "
            + "crater short of the depth threshold, clear of every deep crater's ice";
    private static final String DEEP_FLOOR_READS_ICE = "The deep crater floor round %s should read crater ice within "
            + "%d blocks of its centre";
    /**
     * A deep crater's pool fills the low ground of its floor, wherever the ground runs lowest, so its ice is sought
     * across the floor near its centre rather than at the centre alone.
     */
    private static final int FLOOR_ICE_REACH = 6;
    private static final String SHALLOW_FLOOR_READS_NO_ICE = "The shallow crater floor %s should read no crater ice, reads "
            + "it at y %d";
    /** How far under a floor's surface a search for crater ice reads. */
    private static final int ICE_SEARCH_DEPTH = 8;
    /** A rim column stands between its crater's rim crest at one radius and partway down the rim's outer fall. */
    private static final double RIM_INNER_REACH = 1.05;
    private static final double RIM_OUTER_REACH = 1.3;
    /** A crater's floor reaches half its radius, the band impact glass takes over breccia. */
    private static final double FLOOR_REACH = 0.5;
    /** How far from the origin a search for a crater's centre or open ground reaches, in blocks. */
    private static final int SEARCH_REACH = 160;
    /** The low bits of a block coordinate inside its chunk. */
    private static final int CHUNK_MASK = 15;
    private static final String NO_BASINS = "No spot within the search lies deep in the shadowed basins";
    /** A column within a tenth of a radius of a crater's centre stands at that centre. */
    private static final double CENTRE_REACH = 0.1;
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
        GooTestFunctions.registerTest(registrar, LUNAR_DEEP_CRATER_FLOORS_READ_CRATER_ICE,
                LunarTerrainTests::lunarDeepCraterFloorsReadCraterIce);
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
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
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
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
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
     * A column at a crater's centre in goo:lunar reads impact glass under its regolith, and a column outside every
     * crater reads none; both columns are found through the seeded CraterField the terrain sinks its craters from.
     *
     * @param helper the gametest helper
     */
    public static void lunarCraterFloorsReadImpactGlass(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        CraterField craters = CraterField.seeded(lunar.getChunkSource().randomState().getOrCreateNoise(CraterField.NOISE),
                lunar.getChunkSource().randomState());
        Predicate<BlockPos> bare = RayFree.of(lunar).and(ClearOfBoulders.in(lunar));
        BlockPos centre = findColumn(column -> craters.reachesBandAt(column.getX(), column.getZ(), 0.0, CENTRE_REACH)
                && bare.test(column));
        helper.assertTrue(centre != null, String.format(NO_CRATER_CENTRE, SEARCH_REACH));
        BlockPos openGround = findColumn(column -> craters.isOpenGroundAt(column.getX(), column.getZ()));
        helper.assertTrue(openGround != null, String.format(NO_OPEN_GROUND, SEARCH_REACH));
        assertCentreReadsImpactGlass(helper, lunar, centre);
        assertOpenGroundReadsNoImpactGlass(helper, lunar, openGround);
        helper.succeed();
    }

    /**
     * A column on a crater's rim in goo:lunar, clear of every crater's floor, reads breccia under its regolith; the
     * column is found through the seeded CraterField the terrain sinks its craters from.
     *
     * @param helper the gametest helper
     */
    public static void lunarCraterRimsReadBreccia(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        CraterField craters = CraterField.seeded(lunar.getChunkSource().randomState().getOrCreateNoise(CraterField.NOISE),
                lunar.getChunkSource().randomState());
        Predicate<BlockPos> bare = RayFree.of(lunar).and(ClearOfBoulders.in(lunar));
        BlockPos rim = findColumn(column -> craters.reachesBandAt(column.getX(), column.getZ(), RIM_INNER_REACH,
                RIM_OUTER_REACH) && !craters.reachesBandAt(column.getX(), column.getZ(), 0.0, FLOOR_REACH)
                && bare.test(column));
        helper.assertTrue(rim != null, String.format(NO_CRATER_RIM, SEARCH_REACH));
        int y = yBeneathTheRegolith(lunar, rim);
        BlockState beneath = lunar.getBlockState(rim.atY(y));
        helper.assertTrue(beneath.is(GooBlocks.BRECCIA.get()), String.format(RIM_READS_BRECCIA, rim, y, beneath));
        helper.succeed();
    }

    /**
     * The floor round the centre of a crater past the depth threshold in goo:lunar's shadowed basins, the pools' home,
     * reads crater ice where its ground runs lowest, the bottom of its
     * frozen pool, and the floor at the centre of a crater short of it, inside no deep or giant crater, reads none;
     * both are found through the seeded crater fields.
     *
     * @param helper the gametest helper
     */
    public static void lunarDeepCraterFloorsReadCraterIce(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        CraterField craters = CraterField.seeded(lunar.getChunkSource().randomState().getOrCreateNoise(CraterField.NOISE),
                lunar.getChunkSource().randomState());
        BlockPos basins = LunarSurfaceBiomes.spotIn(lunar, LunarSurfaceBiomes.SHADOWED_BASINS);
        helper.assertTrue(basins != null, NO_BASINS);
        BlockPos deep = deepCraterCentreNear(lunar, craters, basins);
        helper.assertTrue(deep != null, String.format(NO_DEEP_CRATER, DEEP_CELL_REACH));
        GiantCraterField giants = GiantCraterField.seeded(lunar.getChunkSource().randomState()
                .getOrCreateNoise(CraterField.NOISE), lunar.getChunkSource().randomState());
        BlockPos shallow = findColumnNear(basins, column -> craters.reachesBandAt(column.getX(), column.getZ(), 0.0,
                CENTRE_REACH)
                && !craters.reachesBandAt(column.getX(), column.getZ(), 0.0, 1.0, CraterField.DEEP_CRATER_RADIUS)
                && !giants.reachesBandAt(column.getX(), column.getZ(), 0.0, 1.0 + GiantCraterField.RIM_REACH, 0.0));
        helper.assertTrue(shallow != null, String.format(NO_SHALLOW_CRATER, SEARCH_REACH));
        helper.assertTrue(floorHoldsIce(lunar, deep), String.format(DEEP_FLOOR_READS_ICE, deep, FLOOR_ICE_REACH));
        lunar.getChunk(shallow.getX() >> 4, shallow.getZ() >> 4);
        int shallowY = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, shallow.getX(), shallow.getZ()) - 1;
        for (int y = shallowY; y > shallowY - ICE_SEARCH_DEPTH; y--) {
            helper.assertFalse(lunar.getBlockState(shallow.atY(y)).is(GooBlocks.CRATER_ICE.get()),
                    String.format(SHALLOW_FLOOR_READS_NO_ICE, shallow, y));
        }
        helper.succeed();
    }

    /**
     * Passes a column no large crater's ejecta ray crosses, where a ray's anorthosite would stand in for the regolith a
     * test reads under.
     *
     * @param craters the seeded crater field
     * @param giants  the seeded giant crater field
     */
    private record RayFree(CraterField craters, GiantCraterField giants) implements Predicate<BlockPos> {

        static RayFree of(ServerLevel lunar) {
            Noise noise = lunar.getChunkSource().randomState().getOrCreateNoise(CraterField.NOISE);
            return new RayFree(CraterField.seeded(noise, lunar.getChunkSource().randomState()), GiantCraterField.seeded(noise, lunar.getChunkSource().randomState()));
        }

        @Override
        public boolean test(BlockPos column) {
            return !craters.isOnRayAt(column.getX(), column.getZ()) && !giants.isOnRayAt(column.getX(), column.getZ());
        }
    }

    /**
     * Whether crater pools freeze over a column: the column and its chunk's corner, where the pools' placement reads
     * its biome, both stand in the shadowed basins, the pools' one home.
     * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
     */
    private static boolean poolsFreezeAt(ServerLevel lunar, BlockPos column) {
        return LunarSurfaceBiomes.surfaceBiomeAt(lunar, column.getX(), column.getZ())
                .is(LunarSurfaceBiomes.SHADOWED_BASINS)
                && LunarSurfaceBiomes.surfaceBiomeAt(lunar, column.getX() & ~CHUNK_MASK, column.getZ() & ~CHUNK_MASK)
                .is(LunarSurfaceBiomes.SHADOWED_BASINS);
    }

    /**
     * The centre column of the nearest sharp crater past the depth threshold round a spot, walking crater cells
     * outward, whose centre pools freeze at; null where the search finds none. Craters gather in fields, so the spot may stand
     * on a bare plain.
     */
    /** Whether some column of a crater's floor within {@link #FLOOR_ICE_REACH} of its centre reads crater ice. */
    private static boolean floorHoldsIce(ServerLevel lunar, BlockPos centre) {
        for (int dx = -FLOOR_ICE_REACH; dx <= FLOOR_ICE_REACH; dx++) {
            for (int dz = -FLOOR_ICE_REACH; dz <= FLOOR_ICE_REACH; dz++) {
                BlockPos column = centre.offset(dx, 0, dz);
                lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
                int top = lunar.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
                if (lunar.getBlockState(column.atY(top)).is(GooBlocks.CRATER_ICE.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BlockPos deepCraterCentreNear(ServerLevel lunar, CraterField craters, BlockPos spot) {
        int baseX = Math.floorDiv(spot.getX(), CraterField.CELL);
        int baseZ = Math.floorDiv(spot.getZ(), CraterField.CELL);
        for (int ring = 0; ring <= DEEP_CELL_REACH; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    BlockPos centre = craters.craterInCell(baseX + dx, baseZ + dz)
                            .filter(crater -> crater.radius() >= CraterField.DEEP_CRATER_RADIUS)
                            .filter(crater -> crater.wear() < SHARP_WEAR)
                            .map(crater -> BlockPos.containing(crater.centerX(), 0.0, crater.centerZ()))
                            .filter(column -> poolsFreezeAt(lunar, column)).orElse(null);
                    if (centre != null) {
                        return centre;
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos findColumn(Predicate<BlockPos> wanted) {
        return findColumnNear(BlockPos.ZERO, wanted);
    }

    private static BlockPos findColumnNear(BlockPos origin, Predicate<BlockPos> wanted) {
        for (int x = origin.getX() - SEARCH_REACH; x <= origin.getX() + SEARCH_REACH; x++) {
            for (int z = origin.getZ() - SEARCH_REACH; z <= origin.getZ() + SEARCH_REACH; z++) {
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
     * place on a deep floor, and any meteoric iron nugget lying in it.
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

    private static void assertCentreReadsImpactGlass(GameTestHelper helper, ServerLevel lunar, BlockPos centre) {
        int y = yBeneathTheRegolith(lunar, centre);
        BlockState beneath = lunar.getBlockState(centre.atY(y));
        helper.assertTrue(beneath.is(GooBlocks.IMPACT_GLASS.get()), String.format(CENTRE_READS_GLASS, centre, y, beneath));
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
