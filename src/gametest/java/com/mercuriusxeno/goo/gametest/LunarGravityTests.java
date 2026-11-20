package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Gametests for lunar's gravity: a player, a mob, an item and a thrown snowball each fall under one sixth of the
 * gravity they fall under in the overworld, and a landing in lunar is charged for one sixth of the blocks fallen.
 * decision lunar-gravity-is-one-sixth-of-the-overworlds
 */
@RegistersGameTests
public final class LunarGravityTests {
    private static final String LUNAR_SIXTHS_EVERY_FALLING_THINGS_GRAVITY =
            "lunar_sixths_every_falling_things_gravity";
    private static final String LUNAR_LANDINGS_ARE_CHARGED_FOR_A_SIXTH_OF_THE_FALL =
            "lunar_landings_are_charged_for_a_sixth_of_the_fall";

    private static final double ONE_SIXTH = 1.0 / 6.0;
    private static final double SAME = 1e-9;
    /** High over lunar's ground, so nothing placed there stands in a block. */
    private static final Vec3 HIGH_OVER_LUNAR = new Vec3(0.5, 300, 0.5);
    /** A drop the safe fall covers once cut to a sixth: three blocks, the safe fall distance. */
    private static final double SHORT_DROP = 18;
    /** A drop that, cut to a sixth, deals what the overworld drop deals. */
    private static final double LONG_DROP = 60;
    private static final double OVERWORLD_DROP = 10;
    private static final float FULL_DAMAGE = 1F;

    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String SIXTHED = "%s should fall under a sixth of its overworld gravity in lunar, %s there against %s";
    private static final String UNHURT = "A mob dropped %s blocks in lunar should keep full health, %s of %s";
    private static final String A_SIXTH_OF_THE_FALL = "A mob dropped %s blocks in lunar should lose what a %s block drop takes"
            + " in the overworld, lost %s against %s";

    private LunarGravityTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LUNAR_SIXTHS_EVERY_FALLING_THINGS_GRAVITY,
                LunarGravityTests::lunarSixthsEveryFallingThingsGravity);
        GooTestFunctions.registerTest(registrar, LUNAR_LANDINGS_ARE_CHARGED_FOR_A_SIXTH_OF_THE_FALL,
                LunarGravityTests::lunarLandingsAreChargedForASixthOfTheFall);
    }

    /**
     * A player, a pig, an item and a snowball each read a sixth in lunar of the gravity they read in the overworld.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void lunarSixthsEveryFallingThingsGravity(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel lunar = lunarOf(helper);
        assertSixthed(helper, "A pig", level -> EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND), overworld,
                lunar);
        assertSixthed(helper, "An item", level -> new ItemEntity(level, HIGH_OVER_LUNAR.x, HIGH_OVER_LUNAR.y,
                HIGH_OVER_LUNAR.z, new ItemStack(Items.STONE)), overworld, lunar);
        assertSixthed(helper, "A snowball", level -> new Snowball(level, HIGH_OVER_LUNAR.x, HIGH_OVER_LUNAR.y,
                HIGH_OVER_LUNAR.z, new ItemStack(Items.SNOWBALL)), overworld, lunar);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        double atHome = player.getGravity();
        player.teleport(new TeleportTransition(lunar, HIGH_OVER_LUNAR, Vec3.ZERO, 0F, 0F,
                TeleportTransition.DO_NOTHING));
        double onLunar = player.getGravity();
        overworld.getServer().getPlayerList().remove(player);
        helper.assertTrue(Math.abs(onLunar - atHome * ONE_SIXTH) < SAME, String.format(SIXTHED, "A player", onLunar, atHome));
        helper.succeed();
    }

    /**
     * A pig dropped eighteen blocks in lunar keeps full health, and one dropped sixty loses what a ten block drop takes
     * from a pig in the overworld.
     *
     * @param helper the gametest helper
     */
    public static void lunarLandingsAreChargedForASixthOfTheFall(GameTestHelper helper) {
        ServerLevel lunar = lunarOf(helper);
        Pig shortDrop = pigIn(lunar);
        Pig longDrop = pigIn(lunar);
        Pig overworldDrop = pigIn(helper.getLevel());
        float full = shortDrop.getHealth();
        land(shortDrop, SHORT_DROP);
        land(longDrop, LONG_DROP);
        land(overworldDrop, OVERWORLD_DROP);
        helper.assertTrue(shortDrop.getHealth() == full, String.format(UNHURT, SHORT_DROP, shortDrop.getHealth(), full));
        float lunarLoss = full - longDrop.getHealth();
        float overworldLoss = full - overworldDrop.getHealth();
        helper.assertTrue(lunarLoss > 0 && lunarLoss == overworldLoss,
                String.format(A_SIXTH_OF_THE_FALL, LONG_DROP, OVERWORLD_DROP, lunarLoss, overworldLoss));
        helper.succeed();
    }

    private static ServerLevel lunarOf(GameTestHelper helper) {
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        return lunar;
    }

    private static void assertSixthed(GameTestHelper helper, String what, Function<Level, Entity> make,
            ServerLevel overworld, ServerLevel lunar) {
        double atHome = make.apply(overworld).getGravity();
        double onLunar = make.apply(lunar).getGravity();
        helper.assertTrue(atHome > 0 && Math.abs(onLunar - atHome * ONE_SIXTH) < SAME,
                String.format(SIXTHED, what, onLunar, atHome));
    }

    private static Pig pigIn(ServerLevel level) {
        Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
        pig.setPos(HIGH_OVER_LUNAR);
        return pig;
    }

    private static void land(Pig pig, double fallen) {
        pig.causeFallDamage(fallen, FULL_DAMAGE, pig.damageSources().fall());
    }
}
