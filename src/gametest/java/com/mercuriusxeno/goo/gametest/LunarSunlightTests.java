package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's sunlight on the server: an open-sky block reads the light the sun's height over its x sets,
 * on the dark side as on the light side.
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
@RegistersGameTests
public final class LunarSunlightTests {

    private static final String LUNAR_SUNLIGHT_FOLLOWS_THE_SUN_ON_BOTH_HALVES =
            "lunar_sunlight_follows_the_sun_on_both_halves";
    /** An open-sky block on the dark side, high over the ground, where the planet's angle stands at 144 degrees. */
    private static final BlockPos DARK_SIDE_SKY = new BlockPos(6_000_000, 150, 0);
    /** An open-sky block at the light side's middle, under the planet. */
    private static final BlockPos LIGHT_SIDE_SKY = new BlockPos(0, 150, 0);
    /** The overworld's midnight under its full moon, when the sun stands where the planet stands at every x. */
    private static final long FULL_MOON_MIDNIGHT = 18_000;
    private static final long WHOLE_CYCLE = 192_000;
    private static final double FULL_TURN = 360;
    private static final double OVERHEAD = 0;
    /** The sun angle of a spot's midnight, the sun straight underfoot. */
    static final double UNDERFOOT = 180;
    private static final int FULL_DAYLIGHT = 15;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String DAYLIGHT = "An open-sky dark-side block under the sun overhead should read full"
            + " daylight, %d, read %d";
    private static final String NIGHT = "An open-sky dark-side block with the sun underfoot should read night's"
            + " level, the %d a light-side block reads at its own midnight, read %d";

    private LunarSunlightTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_SUNLIGHT_FOLLOWS_THE_SUN_ON_BOTH_HALVES,
                LunarSunlightTests::lunarSunlightFollowsTheSunOnBothHalves);
    }

    /**
     * With the overworld's clock pinned where the sun stands overhead at a dark-side x, an open-sky block there reads
     * full daylight; pinned where it stands underfoot there, the block reads night's level, the level a light-side
     * block reads at its own midnight.
     *
     * @param helper the gametest helper
     */
    public static void lunarSunlightFollowsTheSunOnBothHalves(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        lunar.getChunkAt(DARK_SIDE_SKY);
        lunar.getChunkAt(LIGHT_SIDE_SKY);
        long pinnedAt = lunar.getOverworldClockTime();
        pinOverworldClock(lunar, clockWhenTheSunStandsAt(DARK_SIDE_SKY, OVERHEAD));
        int darkSideNoon = lunar.getMaxLocalRawBrightness(DARK_SIDE_SKY);
        pinOverworldClock(lunar, clockWhenTheSunStandsAt(DARK_SIDE_SKY, UNDERFOOT));
        int darkSideMidnight = lunar.getMaxLocalRawBrightness(DARK_SIDE_SKY);
        pinOverworldClock(lunar, clockWhenTheSunStandsAt(LIGHT_SIDE_SKY, UNDERFOOT));
        int lightSideMidnight = lunar.getMaxLocalRawBrightness(LIGHT_SIDE_SKY);
        pinOverworldClock(lunar, pinnedAt);
        helper.assertTrue(darkSideNoon == FULL_DAYLIGHT, String.format(DAYLIGHT, FULL_DAYLIGHT, darkSideNoon));
        helper.assertTrue(darkSideMidnight == lightSideMidnight && lightSideMidnight < FULL_DAYLIGHT,
                String.format(NIGHT, lightSideMidnight, darkSideMidnight));
        helper.succeed();
    }

    /**
     * The overworld clock time in the first phase cycle the sun stands at an angle over a block: the sun's angle is
     * the planet's angle at the block's x, turned a full circle a cycle from the full moon's midnight.
     *
     * @param pos          the block
     * @param angleDegrees the sun angle, in degrees
     * @return the overworld clock time
     */
    static long clockWhenTheSunStandsAt(BlockPos pos, double angleDegrees) {
        double turn = angleDegrees - Lunar.planetAngleDegrees(pos.getX());
        double wrapped = turn - FULL_TURN * Math.floor(turn / FULL_TURN);
        return FULL_MOON_MIDNIGHT + Math.round(wrapped / FULL_TURN * WHOLE_CYCLE);
    }

    /**
     * Sets the overworld's clock, which every level's day reads; a test pinning it runs under an environment that
     * stops the clock and holds no other test that reads it.
     *
     * @param level          any level of the server
     * @param totalTicks     the clock's total ticks
     */
    static void pinOverworldClock(ServerLevel level, long totalTicks) {
        Holder<WorldClock> overworldClock = level.registryAccess().getOrThrow(WorldClocks.OVERWORLD);
        level.clockManager().setTotalTicks(overworldClock, totalTicks);
    }
}
