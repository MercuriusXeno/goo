package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's world border on the server: the moon stands a quarter the size of the overworld, its border
 * at plus and minus 7.5 million on x and z, while the overworld's border stays as the server started it.
 * decision moon-is-a-quarter-the-overworld-bordered-at-seven-and-a-half-million
 */
@RegistersGameTests
public final class LunarBorderTests {

    private static final String LUNAR_BORDER_STANDS_AT_SEVEN_AND_A_HALF_MILLION =
            "lunar_border_stands_at_seven_and_a_half_million";
    /** The border's width across, plus and minus 7.5 million. */
    private static final double LUNAR_BORDER_SIZE = 15_000_000.0;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String LUNAR_CENTER = "Lunar's world border should stand centred on 0, 0, stands on %s, %s";
    private static final String LUNAR_SIZE = "Lunar's world border should stand %s blocks across, stands %s";
    private static final String OVERWORLD_SIZE = "The overworld's world border should stand %s blocks across as the"
            + " server started it, stands %s";

    private LunarBorderTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_BORDER_STANDS_AT_SEVEN_AND_A_HALF_MILLION,
                LunarBorderTests::lunarBorderStandsAtSevenAndAHalfMillion);
    }

    /**
     * Lunar's world border stands centred on 0, 0 and 15,000,000 blocks across, and the overworld's stands at the
     * size a new server gives it.
     *
     * @param helper the gametest helper
     */
    public static void lunarBorderStandsAtSevenAndAHalfMillion(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        WorldBorder lunarBorder = lunar.getWorldBorder();
        helper.assertTrue(lunarBorder.getCenterX() == 0.0 && lunarBorder.getCenterZ() == 0.0,
                String.format(LUNAR_CENTER, lunarBorder.getCenterX(), lunarBorder.getCenterZ()));
        helper.assertTrue(lunarBorder.getSize() == LUNAR_BORDER_SIZE,
                String.format(LUNAR_SIZE, LUNAR_BORDER_SIZE, lunarBorder.getSize()));
        double overworldSize = helper.getLevel().getServer().getLevel(Level.OVERWORLD).getWorldBorder().getSize();
        double startedSize = WorldBorder.Settings.DEFAULT.size();
        helper.assertTrue(overworldSize == startedSize, String.format(OVERWORLD_SIZE, startedSize, overworldSize));
        helper.succeed();
    }
}
