package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Gametests for Vital Reserve held through the real stream delivery on a
 * survival mock player: health drains silently into reserve hearts behind
 * the bar while held, stops on release, and the reserve takes hits before
 * health (decision reserve-hearts-sit-behind-the-bar).
 */
public final class ReserveTests {

    private static final Identifier VITAL_RESERVE = Identifier.parse("goo:vital_reserve");
    /** Four thousand mB more vital, enough for every held tick these tests stream. */
    private static final int EXTRA_GOO = 4 * GooStacks.THOUSAND;
    private static final float FULL_HEALTH = 20f;
    /** One short of the eighteen food natural regeneration needs, so nothing heals the drain back. */
    private static final int FOOD_BELOW_REGEN = 17;
    /** vital_reserve.json drains a twentieth of a heart, a tenth of a point, each held tick. */
    private static final float DRAIN_POINTS_PER_TICK = 0.1f;
    /** vital_reserve.json banks one reserve half for every two halves drained. */
    private static final float HALVES_PER_POINT = 0.5f;
    private static final int HOLD_TICKS = 40;
    private static final int RELEASED_TICKS = 20;
    /** A hold draining five hearts, which bank five reserve halves. */
    private static final int LONG_HOLD_TICKS = 100;
    private static final float FIRST_HIT = 3f;
    private static final float LAST_HIT = 4f;
    private static final float TOLERANCE = 1e-3f;
    private static final String SHOULD_DRAIN =
            "%d held ticks should leave health %.2f and %d reserve halves: health %.3f, %d halves, reserves %b";
    private static final String SHOULD_BE_SILENT =
            "The drain should land no hit: hurt time %d, invulnerable time %d";
    private static final String SHOULD_STOP =
            "Release should stop the drain: health %.3f to %.3f, halves %d to %d";
    private static final String SHOULD_SPEND_RESERVE =
            "A %.1f hit should spend reserve at a full value, not health: health %.3f to %.3f, halves %d";
    private static final String SHOULD_SPEND_PAST =
            "A hit past the reserve should take the rest from health: expected %.3f, health %.3f, reserve stands %b";

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
            HeartOverlay held = overlay(player);
            float heldHealth = player.getHealth();
            helper.assertTrue(Math.abs(heldHealth - expected) < TOLERANCE && held.shieldHalves() == expectedHalves
                            && held.reserves(),
                    String.format(SHOULD_DRAIN, HOLD_TICKS, expected, expectedHalves, heldHealth,
                            held.shieldHalves(), held.reserves()));
            helper.assertTrue(player.hurtTime == 0 && player.invulnerableTime == 0,
                    String.format(SHOULD_BE_SILENT, player.hurtTime, player.invulnerableTime));
            helper.runAfterDelay(RELEASED_TICKS, () -> {
                HeartOverlay released = overlay(player);
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.assertTrue(player.getHealth() == heldHealth
                                && released.shieldHalves() == held.shieldHalves() && released.reserves(),
                        String.format(SHOULD_STOP, heldHealth, player.getHealth(), held.shieldHalves(),
                                released.shieldHalves()));
                helper.succeed();
            });
        });
    }

    /**
     * A player holds Reserve until five hearts have banked five reserve
     * halves, then takes hits: the first spends reserve halves a point each
     * with health untouched, and one past the last reserve half spends the
     * reserve away and takes only the rest from health.
     *
     * @param helper the gametest helper
     */
    public static void reserveDrainsFirst(GameTestHelper helper) {
        ServerPlayer player = reserver(helper);
        hold(helper, player, LONG_HOLD_TICKS);
        helper.runAfterDelay(LONG_HOLD_TICKS + 1, () -> {
            float drained = player.getHealth();
            int banked = overlay(player).shieldHalves();
            HeartOverlayTests.hurt(helper, player, player.damageSources().generic(), FIRST_HIT);
            float afterFirst = player.getHealth();
            int spending = overlay(player).shieldHalves();
            HeartOverlayTests.hurt(helper, player, player.damageSources().generic(), LAST_HIT);
            float overflow = FIRST_HIT + LAST_HIT - banked;
            boolean stands = overlay(player).stands();
            helper.getLevel().getServer().getPlayerList().remove(player);

            helper.assertTrue(afterFirst == drained && spending == banked - (int) FIRST_HIT,
                    String.format(SHOULD_SPEND_RESERVE, FIRST_HIT, drained, afterFirst, spending));
            helper.assertTrue(Math.abs(player.getHealth() - (drained - overflow)) < TOLERANCE && !stands,
                    String.format(SHOULD_SPEND_PAST, drained - overflow, player.getHealth(), stands));
            helper.succeed();
        });
    }

    /**
     * A survival, hungry mock player holding a glove set to Reserve, with the
     * vital goo to stream it and the recipes it requires.
     */
    private static ServerPlayer reserver(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.VITAL, VITAL_RESERVE);
        // the mock player helper makes a creative player, whom no hit lands on
        player.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        player.connection.markClientLoaded();
        player.getInventory().add(GooStacks.createForOutput(GooTypes.VITAL, EXTRA_GOO));
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(VITAL_RESERVE));
        player.getFoodData().setFoodLevel(FOOD_BELOW_REGEN);
        player.getFoodData().setSaturation(0);
        return player;
    }

    /**
     * Streams Reserve one tick at a time for the hold, as the glove does while right click stays down.
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int ticks) {
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.VITAL), VITAL_RESERVE.toString(),
                player.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    private static HeartOverlay overlay(ServerPlayer player) {
        return player.getData(GooAttachments.HEART_OVERLAY);
    }
}
