package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.reserve.Reserve;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Jelly Reserve held through the real stream delivery on a
 * survival mock player: health and hunger drain silently into reserve hearts
 * and shanks behind the bars while held, stop on release, the reserve hearts
 * take hits before health, the reserve shanks refill hunger the bar loses,
 * and a reserve stands beside Barkskin's bark without either ending the other
 * (decisions reserve-hearts-sit-behind-the-bar and reserve-channels-on-jelly).
 */
@RegistersGameTests
public final class ReserveTests {
    private static final String RESERVE_DRAINS_FIRST = "reserve_drains_first";
    private static final String RESERVE_DRAINS_WHILE_HELD = "reserve_drains_while_held";

    private static final String RESERVE_BANKS_SHANKS = "reserve_banks_shanks";
    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RESERVE_DRAINS_FIRST, ReserveTests::reserveDrainsFirst);
        GooTestFunctions.registerTest(registrar, RESERVE_DRAINS_WHILE_HELD, ReserveTests::reserveDrainsWhileHeld);
        GooTestFunctions.registerTest(registrar, RESERVE_BANKS_SHANKS, ReserveTests::reserveBanksShanks);
    }


    private static final Identifier JELLY_RESERVE = Identifier.parse("goo:jelly_reserve");
    /** Four thousand mB more jelly, enough for every held tick these tests stream. */
    private static final int EXTRA_GOO = 4 * GooStacks.THOUSAND;
    private static final float FULL_HEALTH = 20f;
    /** One short of the eighteen food natural regeneration needs, so nothing heals the drain back. */
    private static final int FOOD_BELOW_REGEN = 17;
    private static final int HOLD_TICKS = 40;
    /** A hold draining five hearts, which bank five reserve halves. */
    private static final int LONG_HOLD_TICKS = 100;
    private static final float FIRST_HIT = 3f;
    /** Exhaustion that costs the bar two food points at no saturation: a point each time more than four stands. */
    private static final float TWO_POINTS_OF_EXHAUSTION = 9f;
    private static final int TICKS_TO_SPEND_EXHAUSTION = 3;
    private static final String SHOULD_DRAIN =
            "%d held ticks should drain health into reserve halves: health %.3f, %d halves, stands %b";
    private static final String SHOULD_SPEND_RESERVE =
            "A %.1f hit should spend reserve at a full value, not health: health %.3f to %.3f, halves %d";
    private static final String SHOULD_REFILL =
            "Hunger the bar loses should come back from the shanks: hunger %d, expected %d, %d halves left";

    private ReserveTests() {
    }

    /**
     * A full-health player holds Reserve for forty ticks: health leaves the
     * bar and banks reserve halves.
     *
     * @param helper the gametest helper
     */
    public static void reserveDrainsWhileHeld(GameTestHelper helper) {
        ServerPlayer player = reserver(helper);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            Reserve held = reserve(player);
            float heldHealth = player.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(heldHealth < FULL_HEALTH && held.heartHalves() > 0 && held.stands(),
                    String.format(SHOULD_DRAIN, HOLD_TICKS, heldHealth, held.heartHalves(), held.stands()));
            helper.succeed();
        });
    }

    /**
     * A player holds Reserve until five hearts have banked five reserve
     * halves, then takes a hit: it spends reserve halves a point each with
     * health untouched.
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

            helper.getLevel().getServer().getPlayerList().remove(player);

            helper.assertTrue(afterFirst == drained && spending == banked - (int) FIRST_HIT,
                    String.format(SHOULD_SPEND_RESERVE, FIRST_HIT, drained, afterFirst, spending));

            helper.succeed();
        });
    }

    /**
     * A player holds Reserve for forty ticks, banking half shanks from the
     * hunger bar; exhaustion then costs the bar two points, and the shanks put
     * them back.
     *
     * @param helper the gametest helper
     */
    public static void reserveBanksShanks(GameTestHelper helper) {
        ServerPlayer player = reserver(helper);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int food = player.getFoodData().getFoodLevel();
            player.causeFoodExhaustion(TWO_POINTS_OF_EXHAUSTION);
            for (int tick = 0; tick < TICKS_TO_SPEND_EXHAUSTION; tick++) {
                player.doTick();
            }
            int refilled = player.getFoodData().getFoodLevel();
            int left = reserve(player).shankHalves();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(refilled == food && left == 0, String.format(SHOULD_REFILL, refilled, food, left));
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
