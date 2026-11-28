package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for lunar's world border on the server: the crossing carries bodies across the seam at plus and minus
 * 7.5 million in place of a wall, so lunar's border stands clear of the seam at vanilla's default, as the overworld's
 * stands as the server started it.
 * decision moon-is-a-quarter-the-overworld-bordered-at-seven-and-a-half-million
 * decision moon-wraps-at-its-border-like-the-zone
 */
@RegistersGameTests
public final class LunarBorderTests {

    private static final String LUNAR_BORDER_STANDS_CLEAR_OF_THE_SEAM = "lunar_border_stands_clear_of_the_seam";

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String LUNAR_CENTER = "Lunar's world border should stand centred on 0, 0, stands on %s, %s";
    private static final String LUNAR_SIZE = "Lunar's world border should stand %s blocks across, clear of the seam,"
            + " stands %s";
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
        GooTestFunctions.registerTest(registrar, LUNAR_BORDER_STANDS_CLEAR_OF_THE_SEAM,
                LunarBorderTests::lunarBorderStandsClearOfTheSeam);
    }

    /**
     * Lunar's world border stands centred on 0, 0 at the size a new server gives a border, wide of the seam, as the
     * overworld's does.
     *
     * @param helper the gametest helper
     */
    public static void lunarBorderStandsClearOfTheSeam(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        WorldBorder lunarBorder = lunar.getWorldBorder();
        helper.assertTrue(lunarBorder.getCenterX() == 0.0 && lunarBorder.getCenterZ() == 0.0,
                String.format(LUNAR_CENTER, lunarBorder.getCenterX(), lunarBorder.getCenterZ()));
        double startedSize = WorldBorder.Settings.DEFAULT.size();
        helper.assertTrue(lunarBorder.getSize() == startedSize,
                String.format(LUNAR_SIZE, startedSize, lunarBorder.getSize()));
        double overworldSize = helper.getLevel().getServer().getLevel(Level.OVERWORLD).getWorldBorder().getSize();
        helper.assertTrue(overworldSize == startedSize, String.format(OVERWORLD_SIZE, startedSize, overworldSize));
        helper.succeed();
    }
}
