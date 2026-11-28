package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.entity.EldritchMonstrosity;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarDepths;
import com.mercuriusxeno.goo.lunar.WarrenTunnels;
import com.mercuriusxeno.goo.registry.GooEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the eldritch on lunar: a monstrosity may spawn on lunar's open, lit surface, where above the deep in
 * the overworld it may not; and one spawns on a warren tunnel's floor, in the warrens' own biome.
 * decision eldritch-spawn-in-higher-volumes-on-lunar
 * decision lunar-underground-changes-by-depth-down-to-a-molten-iron-sea
 */
@RegistersGameTests
public final class LunarSpawnTests {
    private static final String MONSTROSITY_SPAWNS_ON_LUNARS_SURFACE = "monstrosity_spawns_on_lunars_surface";
    private static final String MONSTROSITY_SPAWNS_IN_THE_WARRENS = "monstrosity_spawns_in_the_warrens";
    private static final ResourceKey<Biome> WARREN_BIOME = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Goo.MODID, "lunar_warrens"));
    /** How far from the origin a search for a warren floor reaches each way, in blocks. */
    private static final int WARREN_SEARCH_REACH = 64;
    private static final String NO_WARREN_FLOOR = "No warren tunnel within %d blocks of the origin has a floor";
    private static final String IN_THE_WARRENS_BIOME = "The warren floor %s should stand in goo:lunar_warrens, stands "
            + "in %s";
    private static final String SPAWNS_IN_THE_WARRENS = "A monstrosity should spawn on the warren floor %s";

    /** A column on lunar's dark side, just past the line. */
    private static final int DARK_SIDE_X = 3_750_024;
    /** A spot on the test bay's floor, above the overworld's deep. */
    private static final BlockPos BAY_FLOOR = new BlockPos(2, 1, 2);

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String ON_LUNAR = "A monstrosity should spawn on lunar's surface at %s";
    private static final String NO_SPOT = "No spot within the search lies deep in %s";
    private static final String NOT_ABOVE_THE_DEEP = "A monstrosity should not spawn in the overworld above the deep at %s";

    private LunarSpawnTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MONSTROSITY_SPAWNS_ON_LUNARS_SURFACE,
                LunarSpawnTests::monstrositySpawnsOnLunarsSurface);
        GooTestFunctions.registerTest(registrar, MONSTROSITY_SPAWNS_IN_THE_WARRENS,
                LunarSpawnTests::monstrositySpawnsInTheWarrens);
    }

    /**
     * The monstrosity's spawn rule admits a spot on lunar's dark-side surface and a spot deep in each of lunar's four
     * surface biomes, each under the open sky at that spot's midnight, and refuses the test bay's floor.
     * decision lunar-surface-has-maria-highlands-shadowed-basins-and-eldritch-ground
     * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
     * decision sun-follows-the-phase-and-lights-the-far-side-too
     *
     * @param helper the gametest helper
     */
    public static void monstrositySpawnsOnLunarsSurface(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = overworld.getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        assertAdmittedAtMidnight(helper, lunar, new BlockPos(DARK_SIDE_X, 0, 0));
        for (ResourceKey<Biome> biome : LunarSurfaceBiomes.ALL) {
            BlockPos spot = LunarSurfaceBiomes.spotIn(lunar, biome);
            helper.assertTrue(spot != null, String.format(NO_SPOT, biome.identifier()));
            assertAdmittedAtMidnight(helper, lunar, spot);
        }
        BlockPos bay = helper.absolutePos(BAY_FLOOR);
        helper.assertFalse(EldritchMonstrosity.checkSpawnRules(GooEntities.ELDRITCH_MONSTROSITY.get(), overworld,
                EntitySpawnReason.NATURAL, bay, overworld.getRandom()), String.format(NOT_ABOVE_THE_DEEP, bay));
        helper.succeed();
    }

    /** The monstrosity's spawn rule admits a column's surface under the open sky at that column's midnight. */
    private static void assertAdmittedAtMidnight(GameTestHelper helper, ServerLevel lunar, BlockPos column) {
        lunar.getChunk(SectionPos.blockToSectionCoord(column.getX()), SectionPos.blockToSectionCoord(column.getZ()));
        BlockPos surface = column.atY(lunar.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()));
        long pinnedAt = lunar.getOverworldClockTime();
        LunarSunlightTests.pinOverworldClock(lunar,
                LunarSunlightTests.clockWhenTheSunStandsAt(surface, LunarSunlightTests.UNDERFOOT));
        boolean admitted = EldritchMonstrosity.checkSpawnRules(GooEntities.ELDRITCH_MONSTROSITY.get(), lunar,
                EntitySpawnReason.NATURAL, surface, lunar.getRandom());
        LunarSunlightTests.pinOverworldClock(lunar, pinnedAt);
        helper.assertTrue(admitted, String.format(ON_LUNAR, surface));
    }

    /**
     * A warren tunnel's floor in goo:lunar, found through the seeded warrens, stands in the warrens' own biome, the
     * monstrosity's spawn rule admits it, and a monstrosity spawns there.
     *
     * @param helper the gametest helper
     */
    public static void monstrositySpawnsInTheWarrens(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        BlockPos floor = findWarrenFloor(lunar, WarrenTunnels.seeded(lunar.getChunkSource().randomState()));
        helper.assertTrue(floor != null, String.format(NO_WARREN_FLOOR, WARREN_SEARCH_REACH));
        String biome = lunar.getBiome(floor).unwrapKey().map(key -> key.identifier().toString()).orElse("inline");
        helper.assertTrue(lunar.getBiome(floor).is(WARREN_BIOME), String.format(IN_THE_WARRENS_BIOME, floor, biome));
        helper.assertTrue(EldritchMonstrosity.checkSpawnRules(GooEntities.ELDRITCH_MONSTROSITY.get(), lunar,
                EntitySpawnReason.NATURAL, floor, lunar.getRandom()), String.format(SPAWNS_IN_THE_WARRENS, floor));
        EldritchMonstrosity spawned = GooEntities.ELDRITCH_MONSTROSITY.get().spawn(lunar, floor, EntitySpawnReason.NATURAL);
        helper.assertTrue(spawned != null, String.format(SPAWNS_IN_THE_WARRENS, floor));
        spawned.discard();
        helper.succeed();
    }

    /** The first open warren block standing on solid ground, mid-band, near the origin. */
    static BlockPos findWarrenFloor(ServerLevel lunar, WarrenTunnels warrens) {
        int middleY = (LunarDepths.SEA_ZONE_TOP_Y + LunarDepths.WARREN_ZONE_TOP_Y) / 2;
        for (int x = -WARREN_SEARCH_REACH; x <= WARREN_SEARCH_REACH; x++) {
            for (int z = -WARREN_SEARCH_REACH; z <= WARREN_SEARCH_REACH; z++) {
                for (int y = middleY - 8; y < middleY + 8; y++) {
                    if (warrens.isOpenAt(x, y, z) && !warrens.isOpenAt(x, y - 1, z)) {
                        BlockPos floor = new BlockPos(x, y, z);
                        lunar.getChunk(x >> 4, z >> 4);
                        if (lunar.getBlockState(floor).isAir() && !lunar.getBlockState(floor.below()).isAir()) {
                            return floor;
                        }
                    }
                }
            }
        }
        return null;
    }
}
