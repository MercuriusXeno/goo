package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarSun;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's day: goo:lunar's sun angle at a spot reads the sun over the spot's x, which follows the phase
 * cycle on the overworld's clock, so two spots far apart along x see the sun apart, and each sees it turn as the clock
 * runs.
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 * decision sun-follows-the-phase-and-lights-the-far-side-too
 */
@RegistersGameTests
public final class LunarDayTests {
    private static final String LUNAR_SUN_TURNS_WITH_THE_OVERWORLDS_CLOCK = "lunar_sun_turns_with_the_overworlds_clock";

    /** The ticks the overworld's clock runs between the two reads: the sun turns 0.75 degrees over them. */
    private static final int CLOCK_RUN = 400;
    /** How far a read sun angle may stand off the sun over its x, in degrees. */
    private static final float SAME_ANGLE = 0.01F;
    /** How far lunar's sun must turn across the clock run, in degrees. */
    private static final float TURNED = 0.5F;
    /** Two spots far apart along x, the light side's middle and well onto the dark side. */
    private static final Vec3 LIGHT_SIDE = Vec3.ZERO;
    private static final Vec3 DARK_SIDE = new Vec3(6_000_000, 0, 0);

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String MATCHES = "Lunar's sun angle at %s should read the sun over its x, %s, reads %s";
    private static final String MOVED = "Lunar's sun angle at %s should move as the overworld's clock runs, read %s"
            + " then %s";

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
     * Lunar's sun angle at a light-side spot and at a dark-side spot each reads the sun over that spot's x, and once
     * the overworld's clock has run each has turned and still reads the sun over its x.
     *
     * @param helper the gametest helper
     */
    public static void lunarSunTurnsWithTheOverworldsClock(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        float lightSideBefore = assertTheSunOver(helper, lunar, LIGHT_SIDE);
        float darkSideBefore = assertTheSunOver(helper, lunar, DARK_SIDE);
        helper.runAfterDelay(CLOCK_RUN, () -> {
            assertTurned(helper, LIGHT_SIDE, lightSideBefore, assertTheSunOver(helper, lunar, LIGHT_SIDE));
            assertTurned(helper, DARK_SIDE, darkSideBefore, assertTheSunOver(helper, lunar, DARK_SIDE));
            helper.succeed();
        });
    }

    private static float assertTheSunOver(GameTestHelper helper, ServerLevel lunar, Vec3 spot) {
        float read = lunar.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, spot);
        float sun = (float) LunarSun.sunAngleDegrees(spot.x, lunar.getOverworldClockTime());
        helper.assertTrue(Math.abs(Mth.wrapDegrees(read - sun)) < SAME_ANGLE,
                String.format(MATCHES, spot, sun, read));
        return read;
    }

    private static void assertTurned(GameTestHelper helper, Vec3 spot, float before, float after) {
        helper.assertTrue(Math.abs(Mth.wrapDegrees(after - before)) > TURNED,
                String.format(MOVED, spot, before, after));
    }
}
