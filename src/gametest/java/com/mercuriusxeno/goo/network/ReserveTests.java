package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.reserve.Reserve;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Gametests for Jelly Reserve held through the real stream delivery on a
 * survival mock player: health and hunger drain silently into reserve hearts
 * and shanks behind the bars while held, stop on release, the reserve hearts
 * take hits before health, the reserve shanks refill hunger the bar loses,
 * and a reserve stands beside Barkskin's bark without either ending the other
 * (decisions reserve-hearts-sit-behind-the-bar and reserve-channels-on-jelly).
 */
public final class ReserveTests {

    private static final Identifier JELLY_RESERVE = Identifier.parse("goo:jelly_reserve");
    private static final Identifier LEAF_BARKSKIN = Identifier.parse("goo:leaf_barkskin");
    /** Four thousand mB more jelly, enough for every held tick these tests stream. */
    private static final int EXTRA_GOO = 4 * GooStacks.THOUSAND;
    private static final float FULL_HEALTH = 20f;
    /** One short of the eighteen food natural regeneration needs, so nothing heals the drain back. */
    private static final int FOOD_BELOW_REGEN = 17;
    /** jelly_reserve.json drains a twentieth of a heart, a tenth of a point, each held tick, and as much hunger. */
    private static final float DRAIN_POINTS_PER_TICK = 0.1f;
    /** jelly_reserve.json banks one reserve half for every two halves drained. */
    private static final float HALVES_PER_POINT = 0.5f;
    private static final int HOLD_TICKS = 40;
    private static final int RELEASED_TICKS = 20;
    /** A hold draining five hearts, which bank five reserve halves. */
    private static final int LONG_HOLD_TICKS = 100;
    private static final float FIRST_HIT = 3f;
    private static final float LAST_HIT = 4f;
    /** Exhaustion that costs the bar two food points at no saturation: a point each time more than four stands. */
    private static final float TWO_POINTS_OF_EXHAUSTION = 9f;
    private static final int TICKS_TO_SPEND_EXHAUSTION = 3;
    private static final float TOLERANCE = 1e-3f;
    private static final String SHOULD_DRAIN =
            "%d held ticks should leave health %.2f and %d reserve halves: health %.3f, %d halves, stands %b";
    private static final String SHOULD_BE_SILENT =
            "The drain should land no hit: hurt time %d, invulnerable time %d";
    private static final String SHOULD_STOP =
            "Release should stop the drain: health %.3f to %.3f, halves %d to %d";
    private static final String SHOULD_SPEND_RESERVE =
            "A %.1f hit should spend reserve at a full value, not health: health %.3f to %.3f, halves %d";
    private static final String SHOULD_SPEND_PAST =
            "A hit past the reserve should take the rest from health: expected %.3f, health %.3f, reserve stands %b";
    private static final String SHOULD_BANK_SHANKS =
            "%d held ticks should leave hunger %d and %d reserve half shanks: hunger %d, %d halves";
    private static final String SHOULD_REFILL =
            "Hunger the bar loses should come back from the shanks: hunger %d, expected %d, %d halves left";
    private static final String SHOULD_KEEP_BARK =
            "Reserve should stand beside Barkskin's bark: overlay %s standing %b, %d reserve halves";
    private static final String SHOULD_KEEP_RESERVE =
            "Barkskin should leave the banked reserve standing: %d reserve halves, expected %d";

    private ReserveTests() {
    }

    /**
     * A full-health player holds Reserve for forty ticks: four health points
     * leave the bar with no hit landing and bank two reserve halves, one
     * reserve heart; released, health and reserve stay put.
     *
     * @param helper the gametest helper
     */
    public static void reserveDrainsWhileHeld(GameTestHelper helper) {
        ServerPlayer player = reserver(helper);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            float expected = FULL_HEALTH - HOLD_TICKS * DRAIN_POINTS_PER_TICK;
            int expectedHalves = Math.round(HOLD_TICKS * DRAIN_POINTS_PER_TICK * HALVES_PER_POINT);
            Reserve held = reserve(player);
            float heldHealth = player.getHealth();
            helper.assertTrue(Math.abs(heldHealth - expected) < TOLERANCE && held.heartHalves() == expectedHalves
                            && held.stands(),
                    String.format(SHOULD_DRAIN, HOLD_TICKS, expected, expectedHalves, heldHealth,
                            held.heartHalves(), held.stands()));
            helper.assertTrue(player.hurtTime == 0 && player.invulnerableTime == 0,
                    String.format(SHOULD_BE_SILENT, player.hurtTime, player.invulnerableTime));
            helper.runAfterDelay(RELEASED_TICKS, () -> {
                Reserve released = reserve(player);
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.assertTrue(player.getHealth() == heldHealth
                                && released.heartHalves() == held.heartHalves() && released.stands(),
                        String.format(SHOULD_STOP, heldHealth, player.getHealth(), held.heartHalves(),
                                released.heartHalves()));
                helper.succeed();
            });
        });
    }

    /**
     * A player holds Reserve until five hearts have banked five reserve
     * halves, then takes hits: the first spends reserve halves a point each
     * with health untouched, and one past the last reserve half spends the
     * reserve hearts away and takes only the rest from health.
     *
     * @param helper the gametest helper
     */
    public static void reserveDrainsFirst(GameTestHelper helper) {
        ServerPlayer player = reserver(helper);
        hold(helper, player, LONG_HOLD_TICKS);
        helper.runAfterDelay(LONG_HOLD_TICKS + 1, () -> {
            float drained = player.getHealth();
            int banked = reserve(player).heartHalves();
            HeartOverlayTests.hurt(helper, player, player.damageSources().generic(), FIRST_HIT);
            float afterFirst = player.getHealth();
            int spending = reserve(player).heartHalves();
            HeartOverlayTests.hurt(helper, player, player.damageSources().generic(), LAST_HIT);
            float overflow = FIRST_HIT + LAST_HIT - banked;
            int heartsLeft = reserve(player).heartHalves();
            helper.getLevel().getServer().getPlayerList().remove(player);

            helper.assertTrue(afterFirst == drained && spending == banked - (int) FIRST_HIT,
                    String.format(SHOULD_SPEND_RESERVE, FIRST_HIT, drained, afterFirst, spending));
            helper.assertTrue(Math.abs(player.getHealth() - (drained - overflow)) < TOLERANCE && heartsLeft == 0,
                    String.format(SHOULD_SPEND_PAST, drained - overflow, player.getHealth(), heartsLeft > 0));
            helper.succeed();
        });
    }

    /**
     * A player holds Reserve for forty ticks: four food points leave the
     * hunger bar and bank two reserve half shanks; exhaustion then costs the
     * bar two points, and the shanks put them back.
     *
     * @param helper the gametest helper
     */
    public static void reserveBanksShanks(GameTestHelper helper) {
        ServerPlayer player = reserver(helper);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int expectedFood = FOOD_BELOW_REGEN - Math.round(HOLD_TICKS * DRAIN_POINTS_PER_TICK);
            int expectedHalves = Math.round(HOLD_TICKS * DRAIN_POINTS_PER_TICK * HALVES_PER_POINT);
            int food = player.getFoodData().getFoodLevel();
            int halves = reserve(player).shankHalves();
            helper.assertTrue(food == expectedFood && halves == expectedHalves,
                    String.format(SHOULD_BANK_SHANKS, HOLD_TICKS, expectedFood, expectedHalves, food, halves));
            player.causeFoodExhaustion(TWO_POINTS_OF_EXHAUSTION);
            for (int tick = 0; tick < TICKS_TO_SPEND_EXHAUSTION; tick++) {
                player.doTick();
            }
            int refilled = player.getFoodData().getFoodLevel();
            int left = reserve(player).shankHalves();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(refilled == expectedFood && left == 0,
                    String.format(SHOULD_REFILL, refilled, expectedFood, left));
            helper.succeed();
        });
    }

    /**
     * A player holding Barkskin's bark holds Reserve for forty ticks: the bark
     * still stands and a reserve heart banks beside it; then Barkskin cast
     * again over the banked reserve leaves the reserve as it stood.
     *
     * @param helper the gametest helper
     */
    public static void reserveCoexistsWithBarkskin(GameTestHelper helper) {
        ServerPlayer player = reserver(helper);
        player.getInventory().add(GooStacks.createForOutput(GooTypes.LEAF, EXTRA_GOO));
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(LEAF_BARKSKIN));
        HeartOverlayTests.invoke(player, GooTypes.LEAF, LEAF_BARKSKIN);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            HeartOverlay bark = player.getData(GooAttachments.HEART_OVERLAY);
            int banked = reserve(player).heartHalves();
            helper.assertTrue(bark.stands() && bark.kind() == HeartKind.BARKSKIN && banked > 0,
                    String.format(SHOULD_KEEP_BARK, bark.kind(), bark.stands(), banked));
            HeartOverlayTests.invoke(player, GooTypes.LEAF, LEAF_BARKSKIN);
            int after = reserve(player).heartHalves();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(after == banked, String.format(SHOULD_KEEP_RESERVE, after, banked));
            helper.succeed();
        });
    }

    /**
     * A survival, hungry mock player holding a glove set to Reserve, with the
     * jelly goo to stream it and the recipes it requires.
     */
    private static ServerPlayer reserver(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.JELLY, JELLY_RESERVE);
        // the mock player helper makes a creative player, whom no hit lands on
        player.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        player.connection.markClientLoaded();
        player.getInventory().add(GooStacks.createForOutput(GooTypes.JELLY, EXTRA_GOO));
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(JELLY_RESERVE));
        player.getFoodData().setFoodLevel(FOOD_BELOW_REGEN);
        player.getFoodData().setSaturation(0);
        return player;
    }

    /**
     * Streams Reserve one tick at a time for the hold, as the glove does while right click stays down.
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY), JELLY_RESERVE.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    private static Reserve reserve(ServerPlayer player) {
        return player.getData(GooAttachments.RESERVE);
    }
}
