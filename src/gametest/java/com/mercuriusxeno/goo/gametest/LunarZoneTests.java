package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.CrystalCaverns;
import com.mercuriusxeno.goo.lunar.LavaTubes;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.lunar.RubbleCaves;
import com.mercuriusxeno.goo.lunar.WarrenTunnels;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests for lunar's depth zones, each reading as its own place: the shallow zone's breccia and its rubble caves,
 * the middle zone's bare rock caverns, and the warrens' sculk-lined tunnels.
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 * decision middle-caverns-are-bare-rock-with-no-ice-or-glass
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
@RegistersGameTests
public final class LunarZoneTests {
    private static final String LUNAR_SHALLOW_ZONE_HOLDS_RUBBLE_CAVES = "lunar_shallow_zone_holds_rubble_caves";
    private static final String LUNAR_MIDDLE_CAVERNS_ARE_BARE_ROCK = "lunar_middle_caverns_are_bare_rock";
    private static final String LUNAR_WARRENS_TWIST_THROUGH_SCULK = "lunar_warrens_twist_through_sculk";
    private static final String LUNAR_RUBBLE_CAVES_HOLD_NATIVE_IRON_LODES = "lunar_rubble_caves_hold_native_iron_lodes";
    private static final String LUNAR_MIDDLE_CAVERNS_GROW_ASTRAL_CRYSTAL = "lunar_middle_caverns_grow_astral_crystal";
    private static final String LUNAR_WARRENS_HOLD_DIAMOND_GOLD_AND_ANCIENT_DEBRIS =
            "lunar_warrens_hold_diamond_gold_and_ancient_debris";
    /** Vanilla's stone and deepslate ore tags, none of whose vanilla blocks generate on lunar. */
    private static final List<TagKey<Block>> VANILLA_ORE_TAGS = List.of(BlockTags.COAL_ORES, BlockTags.IRON_ORES,
            BlockTags.COPPER_ORES, BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES,
            BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);
    /** The most astral crystal one chunk holds on average, so a cluster stays a find. */
    private static final int ASTRAL_CRYSTAL_CEILING_PER_CHUNK = 8;
    /** How many chunks from the origin a deposit scan reaches each way. */
    private static final int DEPOSIT_CHUNK_REACH = 4;
    /** The most native iron one chunk holds on average, so a lode stays a find rather than a seam. */
    private static final int NATIVE_IRON_CEILING_PER_CHUNK = 6;
    /** How far from the origin a scan of the middle zone reaches each way, in blocks. */
    private static final int CAVERN_SCAN_REACH = 64;

    /** How far from the origin a search for a column reaches each way, in blocks. */
    private static final int STRETCH_REACH = 256;
    private static final int SAMPLE_STEP = 4;
    /** A solid column's surface stands this far over the shallow zone, so no crater or rille reaches the zone. */
    private static final int SOLID_SURFACE_CLEARANCE = 24;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_SOLID_COLUMN = "No column within %d blocks of the origin runs solid through the "
            + "shallow zone";
    private static final String NO_CAVE = "No column within %d blocks of the origin holds a rubble cave";
    private static final String ZONE_READS_BRECCIA = "The solid column %s should read breccia through the shallow zone, "
            + "reads %s at y %d";
    private static final String NO_ROCK_CAVERN = "No cavern air within %d blocks of the origin in the middle zone "
            + "borders rock";
    private static final String CAVERNS_HOLD_ICE_OR_GLASS = "The middle zone within %d blocks of the origin should "
            + "hold no crater ice and no impact glass, holds %d crater ice and %d impact glass, the first at %s";
    private static final String NO_SCULK_TUNNEL = "No air within %d blocks of the origin in the warren zone borders "
            + "sculk";
    private static final String NATIVE_IRON_IN_SHALLOW_BAND = "The shallow zone over %d chunks should hold between 1 "
            + "and %d native iron, holds %d";
    private static final String NO_NATIVE_IRON_BELOW = "The bands under the shallow zone over %d chunks should hold no "
            + "native iron, hold %d, the first at %s";
    private static final String ASTRAL_CRYSTAL_IN_MIDDLE_BAND = "The middle zone over %d chunks should hold between 1 "
            + "and %d astral crystal, holds %d";
    private static final String NO_ASTRAL_CRYSTAL_OUTSIDE = "The shallow zone and the warrens over %d chunks should "
            + "hold no astral crystal, hold %d, the first at %s";
    private static final String CRYSTAL_ON_ROCK = "The astral crystal at %s should grow from rock, grows from %s";
    private static final String WARRENS_HOLD_ORE = "The warrens over %d chunks should hold %s, hold none";
    private static final String NO_WARREN_ORE_ABOVE = "The bands over the warrens across %d chunks should hold no "
            + "diamond, gold or ancient debris, hold %d, the first at %s";
    private static final String NO_VANILLA_ORE = "Lunar over %d chunks should generate no vanilla stone or deepslate "
            + "ore, generates %d, the first %s at %s";
    private static final String CAVE_IS_OPEN = "The rubble cave column %s should read air at y %d, reads %s";
    private static final String CAVE_IN_BRECCIA = "The rubble cave column %s should read breccia %s the cave at y %d, "
            + "reads %s";

    private LunarZoneTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_SHALLOW_ZONE_HOLDS_RUBBLE_CAVES,
                LunarZoneTests::lunarShallowZoneHoldsRubbleCaves);
        GooTestFunctions.registerTest(registrar, LUNAR_MIDDLE_CAVERNS_ARE_BARE_ROCK,
                LunarZoneTests::lunarMiddleCavernsAreBareRock);
        GooTestFunctions.registerTest(registrar, LUNAR_WARRENS_TWIST_THROUGH_SCULK,
                LunarZoneTests::lunarWarrensTwistThroughSculk);
        GooTestFunctions.registerTest(registrar, LUNAR_RUBBLE_CAVES_HOLD_NATIVE_IRON_LODES,
                LunarZoneTests::lunarRubbleCavesHoldNativeIronLodes);
        GooTestFunctions.registerTest(registrar, LUNAR_MIDDLE_CAVERNS_GROW_ASTRAL_CRYSTAL,
                LunarZoneTests::lunarMiddleCavernsGrowAstralCrystal);
        GooTestFunctions.registerTest(registrar, LUNAR_WARRENS_HOLD_DIAMOND_GOLD_AND_ANCIENT_DEBRIS,
                LunarZoneTests::lunarWarrensHoldDiamondGoldAndAncientDebris);
    }

    /**
     * In goo:lunar's shallow zone, a column clear of every cave and tube, its surface well over the zone, reads
     * breccia through the zone's band; and a cave column, found through the seeded rubble caves, reads air in the
     * cave with breccia under its floor and over its roof.
     *
     * @param helper the gametest helper
     */
    public static void lunarShallowZoneHoldsRubbleCaves(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        RandomState randomState = lunar.getChunkSource().randomState();
        RubbleCaves caves = RubbleCaves.seeded(randomState.getOrCreateNoise(RubbleCaves.FOOTPRINT_NOISE),
                randomState.getOrCreateNoise(RubbleCaves.RUBBLE_NOISE));
        LavaTubes tubes = LavaTubes.seeded(randomState.getOrCreateNoise(LavaTubes.NOISE));
        Predicate<BlockPos> clearOfTubes = column -> !tubes.crossesColumnAt(column.getX(), column.getZ());
        BlockPos solid = findColumn(column -> !caves.holdsCaveAt(column.getX(), column.getZ())
                && clearOfTubes.test(column) && surfaceY(lunar, randomState, column)
                > LunarDepths.SHALLOW_ZONE_TOP_Y + SOLID_SURFACE_CLEARANCE);
        helper.assertTrue(solid != null, String.format(NO_SOLID_COLUMN, STRETCH_REACH));
        lunar.getChunk(solid.getX() >> 4, solid.getZ() >> 4);
        for (int y = LunarDepths.CAVERN_ZONE_TOP_Y; y < LunarDepths.SHALLOW_ZONE_TOP_Y; y++) {
            BlockState rock = lunar.getBlockState(solid.atY(y));
            helper.assertTrue(rock.is(GooBlocks.BRECCIA.get()), String.format(ZONE_READS_BRECCIA, solid, rock, y));
        }
        BlockPos cave = findColumn(column -> caves.holdsCaveAt(column.getX(), column.getZ()) && clearOfTubes.test(column)
                && surfaceY(lunar, randomState, column) > LunarDepths.SHALLOW_ZONE_TOP_Y + SOLID_SURFACE_CLEARANCE);
        helper.assertTrue(cave != null, String.format(NO_CAVE, STRETCH_REACH));
        assertCave(helper, lunar, caves, cave);
        helper.succeed();
    }

    /**
     * Scans the middle zone's whole band in goo:lunar across a stretch: some cavern air the seeded crystal caverns
     * open borders rock, and no block in the band reads crater ice or impact glass.
     *
     * @param helper the gametest helper
     */
    public static void lunarMiddleCavernsAreBareRock(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        CrystalCaverns caverns = CrystalCaverns.seeded(lunar.getChunkSource().randomState()
                .getOrCreateNoise(CrystalCaverns.NOISE));
        boolean foundRockCavern = false;
        int iceCount = 0;
        int glassCount = 0;
        BlockPos firstStray = null;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -CAVERN_SCAN_REACH; x <= CAVERN_SCAN_REACH; x++) {
            for (int z = -CAVERN_SCAN_REACH; z <= CAVERN_SCAN_REACH; z++) {
                lunar.getChunk(x >> 4, z >> 4);
                for (int y = LunarDepths.WARREN_ZONE_TOP_Y; y < LunarDepths.CAVERN_ZONE_TOP_Y; y++) {
                    at.set(x, y, z);
                    BlockState state = lunar.getBlockState(at);
                    boolean ice = state.is(GooBlocks.CRATER_ICE.get());
                    boolean glass = state.is(GooBlocks.IMPACT_GLASS.get());
                    if ((ice || glass) && firstStray == null) {
                        firstStray = at.immutable();
                    }
                    iceCount += ice ? 1 : 0;
                    glassCount += glass ? 1 : 0;
                    foundRockCavern = foundRockCavern
                            || caverns.isOpenAt(x, y, z) && state.isAir() && bordersRock(lunar, at.immutable());
                }
            }
        }
        helper.assertTrue(iceCount == 0 && glassCount == 0, String.format(CAVERNS_HOLD_ICE_OR_GLASS,
                CAVERN_SCAN_REACH, iceCount, glassCount, firstStray));
        helper.assertTrue(foundRockCavern, String.format(NO_ROCK_CAVERN, CAVERN_SCAN_REACH));
        helper.succeed();
    }

    /**
     * Scans the warren zone's band in goo:lunar across a stretch, at the tunnel blocks the seeded warrens open, for
     * a tunnel's air bordered by sculk.
     *
     * @param helper the gametest helper
     */
    public static void lunarWarrensTwistThroughSculk(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        WarrenTunnels warrens = WarrenTunnels.seeded(lunar.getChunkSource().randomState());
        for (int x = -CAVERN_SCAN_REACH; x <= CAVERN_SCAN_REACH; x++) {
            for (int z = -CAVERN_SCAN_REACH; z <= CAVERN_SCAN_REACH; z++) {
                for (int y = LunarDepths.SEA_ZONE_TOP_Y; y < LunarDepths.WARREN_ZONE_TOP_Y; y++) {
                    if (warrens.isOpenAt(x, y, z) && bordersSculk(lunar, new BlockPos(x, y, z))) {
                        helper.succeed();
                        return;
                    }
                }
            }
        }
        helper.fail(String.format(NO_SCULK_TUNNEL, CAVERN_SCAN_REACH));
    }

    /**
     * Counts goo:native_iron over a square of chunks in goo:lunar, band by band: the shallow zone holds some, under a
     * ceiling per chunk, and every band under it holds none.
     * decision native-iron-lodes-are-worth-whole-iron-blocks
     *
     * @param helper the gametest helper
     */
    public static void lunarRubbleCavesHoldNativeIronLodes(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        int shallow = 0;
        int below = 0;
        BlockPos firstBelow = null;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int chunkX = -DEPOSIT_CHUNK_REACH; chunkX < DEPOSIT_CHUNK_REACH; chunkX++) {
            for (int chunkZ = -DEPOSIT_CHUNK_REACH; chunkZ < DEPOSIT_CHUNK_REACH; chunkZ++) {
                lunar.getChunk(chunkX, chunkZ);
                for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                    for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                        for (int y = LunarDepths.WORLD_FLOOR_Y; y < LunarDepths.SHALLOW_ZONE_TOP_Y; y++) {
                            if (!lunar.getBlockState(at.set(x, y, z)).is(GooBlocks.NATIVE_IRON.get())) {
                                continue;
                            }
                            if (y >= LunarDepths.CAVERN_ZONE_TOP_Y) {
                                shallow++;
                            } else {
                                below++;
                                firstBelow = firstBelow == null ? at.immutable() : firstBelow;
                            }
                        }
                    }
                }
            }
        }
        int chunks = 4 * DEPOSIT_CHUNK_REACH * DEPOSIT_CHUNK_REACH;
        int ceiling = chunks * NATIVE_IRON_CEILING_PER_CHUNK;
        helper.assertTrue(shallow >= 1 && shallow <= ceiling,
                String.format(NATIVE_IRON_IN_SHALLOW_BAND, chunks, ceiling, shallow));
        helper.assertTrue(below == 0, String.format(NO_NATIVE_IRON_BELOW, chunks, below, firstBelow));
        helper.succeed();
    }

    /**
     * Counts goo:astral_crystal over a square of chunks in goo:lunar, from the warrens up through the shallow zone:
     * the middle zone holds some, under a ceiling per chunk, each growing from the rock its facing turns away from,
     * and the shallow zone and the warrens hold none.
     * decision astral-crystal-is-lunars-scarce-rich-block
     *
     * @param helper the gametest helper
     */
    public static void lunarMiddleCavernsGrowAstralCrystal(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        int middle = 0;
        int outside = 0;
        BlockPos firstOutside = null;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int chunkX = -DEPOSIT_CHUNK_REACH; chunkX < DEPOSIT_CHUNK_REACH; chunkX++) {
            for (int chunkZ = -DEPOSIT_CHUNK_REACH; chunkZ < DEPOSIT_CHUNK_REACH; chunkZ++) {
                lunar.getChunk(chunkX, chunkZ);
                for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                    for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                        for (int y = LunarDepths.SEA_ZONE_TOP_Y; y < LunarDepths.SHALLOW_ZONE_TOP_Y; y++) {
                            BlockState state = lunar.getBlockState(at.set(x, y, z));
                            if (!state.is(GooBlocks.ASTRAL_CRYSTAL.get())) {
                                continue;
                            }
                            assertGrowsFromRock(helper, lunar, at.immutable(), state);
                            if (y >= LunarDepths.WARREN_ZONE_TOP_Y && y < LunarDepths.CAVERN_ZONE_TOP_Y) {
                                middle++;
                            } else {
                                outside++;
                                firstOutside = firstOutside == null ? at.immutable() : firstOutside;
                            }
                        }
                    }
                }
            }
        }
        int chunks = 4 * DEPOSIT_CHUNK_REACH * DEPOSIT_CHUNK_REACH;
        int ceiling = chunks * ASTRAL_CRYSTAL_CEILING_PER_CHUNK;
        helper.assertTrue(middle >= 1 && middle <= ceiling,
                String.format(ASTRAL_CRYSTAL_IN_MIDDLE_BAND, chunks, ceiling, middle));
        helper.assertTrue(outside == 0, String.format(NO_ASTRAL_CRYSTAL_OUTSIDE, chunks, outside, firstOutside));
        helper.succeed();
    }

    /**
     * Scans a square of chunks in goo:lunar from its floor to its top: the warrens' band holds lunar diamond ore, lunar
     * gold ore and ancient debris, the bands over it hold none of the three, and no band holds a vanilla stone or
     * deepslate ore.
     * decision lunar-rock-holds-diamond-gold-and-ancient-debris
     *
     * @param helper the gametest helper
     */
    public static void lunarWarrensHoldDiamondGoldAndAncientDebris(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        List<Block> warrenOres = List.of(GooBlocks.LUNAR_DIAMOND_ORE.get(), GooBlocks.LUNAR_GOLD_ORE.get(),
                Blocks.ANCIENT_DEBRIS);
        int[] inWarrens = new int[warrenOres.size()];
        int above = 0;
        BlockPos firstAbove = null;
        int vanilla = 0;
        BlockPos firstVanilla = null;
        BlockState firstVanillaState = null;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int top = LunarDepths.WORLD_FLOOR_Y + LunarDepths.WORLD_HEIGHT;
        for (int chunkX = -DEPOSIT_CHUNK_REACH; chunkX < DEPOSIT_CHUNK_REACH; chunkX++) {
            for (int chunkZ = -DEPOSIT_CHUNK_REACH; chunkZ < DEPOSIT_CHUNK_REACH; chunkZ++) {
                lunar.getChunk(chunkX, chunkZ);
                for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
                    for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                        for (int y = LunarDepths.WORLD_FLOOR_Y; y < top; y++) {
                            BlockState state = lunar.getBlockState(at.set(x, y, z));
                            int ore = warrenOres.indexOf(state.getBlock());
                            if (ore >= 0 && y >= LunarDepths.SEA_ZONE_TOP_Y && y < LunarDepths.WARREN_ZONE_TOP_Y) {
                                inWarrens[ore]++;
                            } else if (ore >= 0 && y >= LunarDepths.WARREN_ZONE_TOP_Y) {
                                above++;
                                firstAbove = firstAbove == null ? at.immutable() : firstAbove;
                            }
                            if (isVanillaOre(state)) {
                                vanilla++;
                                firstVanilla = firstVanilla == null ? at.immutable() : firstVanilla;
                                firstVanillaState = firstVanillaState == null ? state : firstVanillaState;
                            }
                        }
                    }
                }
            }
        }
        int chunks = 4 * DEPOSIT_CHUNK_REACH * DEPOSIT_CHUNK_REACH;
        for (int ore = 0; ore < warrenOres.size(); ore++) {
            helper.assertTrue(inWarrens[ore] > 0, String.format(WARRENS_HOLD_ORE, chunks, warrenOres.get(ore)));
        }
        helper.assertTrue(above == 0, String.format(NO_WARREN_ORE_ABOVE, chunks, above, firstAbove));
        helper.assertTrue(vanilla == 0, String.format(NO_VANILLA_ORE, chunks, vanilla, firstVanillaState,
                firstVanilla));
        helper.succeed();
    }

    private static boolean isVanillaOre(BlockState state) {
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return false;
        }
        for (TagKey<Block> tag : VANILLA_ORE_TAGS) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static void assertGrowsFromRock(GameTestHelper helper, ServerLevel lunar, BlockPos crystal,
            BlockState state) {
        Direction facing = state.getValue(AmethystClusterBlock.FACING);
        BlockPos root = crystal.relative(facing.getOpposite());
        BlockState rock = lunar.getBlockState(root);
        helper.assertTrue(rock.isFaceSturdy(lunar, root, facing) && !rock.is(GooBlocks.ASTRAL_CRYSTAL.get()),
                String.format(CRYSTAL_ON_ROCK, crystal, rock));
    }

    private static boolean bordersSculk(ServerLevel lunar, BlockPos air) {
        lunar.getChunk(air.getX() >> 4, air.getZ() >> 4);
        if (!lunar.getBlockState(air).isAir()) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (lunar.getBlockState(air.relative(side)).is(Blocks.SCULK)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bordersRock(ServerLevel lunar, BlockPos air) {
        for (Direction side : Direction.values()) {
            BlockState neighbor = lunar.getBlockState(air.relative(side));
            if (neighbor.is(GooBlocks.ANORTHOSITE.get()) || neighbor.is(Blocks.BASALT)) {
                return true;
            }
        }
        return false;
    }

    private static void assertCave(GameTestHelper helper, ServerLevel lunar, RubbleCaves caves, BlockPos column) {
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        int floorY = caves.floorYAt(column.getX(), column.getZ());
        int roofY = caves.roofYAt(column.getX(), column.getZ());
        BlockState inside = lunar.getBlockState(column.atY(floorY + 1));
        helper.assertTrue(inside.isAir(), String.format(CAVE_IS_OPEN, column, floorY + 1, inside));
        BlockState under = lunar.getBlockState(column.atY(floorY));
        helper.assertTrue(under.is(GooBlocks.BRECCIA.get()), String.format(CAVE_IN_BRECCIA, column, "under", floorY, under));
        BlockState over = lunar.getBlockState(column.atY(roofY));
        helper.assertTrue(over.is(GooBlocks.BRECCIA.get()), String.format(CAVE_IN_BRECCIA, column, "over", roofY, over));
    }

    private static int surfaceY(ServerLevel lunar, RandomState randomState, BlockPos column) {
        return lunar.getChunkSource().getGenerator().getBaseHeight(column.getX(), column.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG, lunar, randomState);
    }

    private static BlockPos findColumn(Predicate<BlockPos> wanted) {
        for (int x = -STRETCH_REACH; x <= STRETCH_REACH; x += SAMPLE_STEP) {
            for (int z = -STRETCH_REACH; z <= STRETCH_REACH; z += SAMPLE_STEP) {
                BlockPos column = new BlockPos(x, 0, z);
                if (wanted.test(column)) {
                    return column;
                }
            }
        }
        return null;
    }
}
