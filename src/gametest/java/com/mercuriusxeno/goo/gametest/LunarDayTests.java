package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's day: goo:lunar's sun angle follows the overworld's clock, standing where the overworld's
 * stands and moving as the overworld's time of day moves.
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
@RegistersGameTests
public final class LunarDayTests {
    private static final String LUNAR_SUN_TURNS_WITH_THE_OVERWORLDS_CLOCK = "lunar_sun_turns_with_the_overworlds_clock";

    /** The ticks the overworld's clock runs between the two reads. */
    private static final int CLOCK_RUN = 100;
    /**
     * How far apart the two levels' sun angles may read, in degrees: each level caches its value on its own tick, so
     * one may read a tick behind the other, and the sun turns well under this in a tick.
     */
    private static final float SAME_ANGLE = 0.1F;
    /** How far lunar's sun must turn across the clock run, in degrees, well under the run's turn at any hour. */
    private static final float TURNED = 0.5F;
    /** Where each level's sun angle is read: the sun angle is positional, and no biome of either level moves it. */
    private static final Vec3 SAMPLED_AT = Vec3.ZERO;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String MATCHES = "Lunar's sun angle should read the overworld's, %s, reads %s";
    private static final String MOVED = "Lunar's sun angle should move as the overworld's clock runs, read %s then %s";

    private LunarDayTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_SUN_TURNS_WITH_THE_OVERWORLDS_CLOCK,
                LunarDayTests::lunarSunTurnsWithTheOverworldsClock);
    }

    /**
     * Lunar's sun angle reads the overworld's at one tick, and once the overworld's clock has run it has moved and
     * still reads the overworld's.
     *
     * @param helper the gametest helper
     */
    public static void lunarSunTurnsWithTheOverworldsClock(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().getLevel(Level.OVERWORLD);
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(overworld != null && lunar != null, LUNAR_REQUIRED);
        float lunarBefore = sunAngle(lunar);
        assertMatches(helper, sunAngle(overworld), lunarBefore);
        helper.runAfterDelay(CLOCK_RUN, () -> {
            float lunarAfter = sunAngle(lunar);
            assertMatches(helper, sunAngle(overworld), lunarAfter);
            helper.assertTrue(Math.abs(lunarAfter - lunarBefore) > TURNED,
                    String.format(MOVED, lunarBefore, lunarAfter));
            helper.succeed();
        });
    }

    private static void assertMatches(GameTestHelper helper, float overworld, float lunar) {
        helper.assertTrue(Math.abs(overworld - lunar) < SAME_ANGLE, String.format(MATCHES, overworld, lunar));
    }

    private static float sunAngle(ServerLevel level) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, SAMPLED_AT);
    }
}
