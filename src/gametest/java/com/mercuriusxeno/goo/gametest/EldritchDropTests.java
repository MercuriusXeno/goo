package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.entity.EldritchMonstrosity;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.WarrenTunnels;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for what the eldritch drop on lunar: monstrosities killed in the warrens, each drawing its loot from the
 * same seed as one killed on lunar's surface, drop more than the surface's.
 * decision eldritch-drop-exorite-ingredients-xeno-goo-and-rare-infinity
 */
@RegistersGameTests
public final class EldritchDropTests {
    private static final String WARREN_ELDRITCH_DROP_MORE = "warren_eldritch_drop_more";

    /** How many monstrosities die at each spot, the nth at each drawing from the same seed. */
    private static final int KILLS = 12;
    /** The drops of a kill are gathered this far around its spot, in blocks. */
    private static final double GATHER_REACH = 3.0;
    private static final String SUMMON = "summon goo:eldritch_monstrosity %d %d %d {DeathLootTableSeed:%dL,NoAI:1b}";

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String NO_SPOT = "No %s spot stands near goo:lunar's origin";
    private static final String NO_MONSTROSITY = "The summon at %s should stand a monstrosity";
    private static final String WARRENS_DROP_MORE = "%d monstrosities killed on the warren floor %s should drop more "
            + "than as many killed on the surface at %s under the same seeds, dropped %d against %d";

    private EldritchDropTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, WARREN_ELDRITCH_DROP_MORE, EldritchDropTests::warrenEldritchDropMore);
    }

    /**
     * Monstrosities killed on a warren floor in goo:lunar drop more items than as many killed on its surface, the nth
     * kill at each drawing its loot from the same seed.
     *
     * @param helper the gametest helper
     */
    public static void warrenEldritchDropMore(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        BlockPos warren = LunarSpawnTests.findWarrenFloor(lunar, WarrenTunnels.seeded(lunar.getChunkSource()
                .randomState()));
        helper.assertTrue(warren != null, String.format(NO_SPOT, "warren floor"));
        BlockPos column = LunarSurfaceBiomes.spotIn(lunar, LunarSurfaceBiomes.HIGHLANDS);
        helper.assertTrue(column != null, String.format(NO_SPOT, "highland"));
        lunar.getChunk(column.getX() >> 4, column.getZ() >> 4);
        BlockPos surface = column.atY(lunar.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()));
        int warrenDrops = dropsOfKills(helper, lunar, warren);
        int surfaceDrops = dropsOfKills(helper, lunar, surface);
        helper.assertTrue(warrenDrops > surfaceDrops,
                String.format(WARRENS_DROP_MORE, KILLS, warren, surface, warrenDrops, surfaceDrops));
        helper.succeed();
    }

    /** Kills monstrosities at a spot one at a time, each from its own seed, and counts the items they drop. */
    private static int dropsOfKills(GameTestHelper helper, ServerLevel lunar, BlockPos spot) {
        AABB around = new AABB(spot).inflate(GATHER_REACH);
        int dropped = 0;
        for (int kill = 1; kill <= KILLS; kill++) {
            lunar.getServer().getCommands().performPrefixedCommand(lunar.getServer().createCommandSourceStack()
                    .withLevel(lunar).withSuppressedOutput(),
                    String.format(SUMMON, spot.getX(), spot.getY(), spot.getZ(), kill));
            List<EldritchMonstrosity> summoned = lunar.getEntitiesOfClass(EldritchMonstrosity.class, around);
            helper.assertTrue(!summoned.isEmpty(), String.format(NO_MONSTROSITY, spot));
            summoned.forEach(monstrosity -> monstrosity.kill(lunar));
            for (ItemEntity drop : lunar.getEntitiesOfClass(ItemEntity.class, around)) {
                dropped += drop.getItem().getCount();
                drop.discard();
            }
        }
        return dropped;
    }
}
