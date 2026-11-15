package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.entity.EldritchMonstrosity;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.registry.GooEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the eldritch on lunar: a monstrosity may spawn on lunar's open, lit surface, where above the deep in
 * the overworld it may not.
 * decision eldritch-spawn-in-higher-volumes-on-lunar
 */
@RegistersGameTests
public final class LunarSpawnTests {
    private static final String MONSTROSITY_SPAWNS_ON_LUNARS_SURFACE = "monstrosity_spawns_on_lunars_surface";

    /** A spot on the test bay's floor, above the overworld's deep. */
    private static final BlockPos BAY_FLOOR = new BlockPos(2, 1, 2);

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String ON_LUNAR = "A monstrosity should spawn on lunar's surface at %s";
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
    }

    /**
     * The monstrosity's spawn rule admits a spot on lunar's lit surface and refuses the test bay's floor.
     *
     * @param helper the gametest helper
     */
    public static void monstrositySpawnsOnLunarsSurface(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = overworld.getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        lunar.getChunk(0, 0);
        BlockPos surface = new BlockPos(0, lunar.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0), 0);
        helper.assertTrue(EldritchMonstrosity.checkSpawnRules(GooEntities.ELDRITCH_MONSTROSITY.get(), lunar,
                EntitySpawnReason.NATURAL, surface, lunar.getRandom()), String.format(ON_LUNAR, surface));
        BlockPos bay = helper.absolutePos(BAY_FLOOR);
        helper.assertFalse(EldritchMonstrosity.checkSpawnRules(GooEntities.ELDRITCH_MONSTROSITY.get(), overworld,
                EntitySpawnReason.NATURAL, bay, overworld.getRandom()), String.format(NOT_ABOVE_THE_DEEP, bay));
        helper.succeed();
    }
}
