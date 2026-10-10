package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Gametest for Nourish through the glove: a hungry player eats the vital
 * self + brew ability and gains a food point every interval while it holds
 * goo to pay the upkeep, then no more once it runs dry (decisions
 * nourish-restores-hunger-over-time and self-effects-trickle-until-ended).
 */
public final class NourishTests {

    private static final Identifier VITAL_NOURISH = Identifier.parse("goo:vital_nourish");
    /** Low enough that the gained points never reach a full bar. */
    private static final int HUNGRY_FOOD = 4;
    /** vital_nourish.json's interval: a point every 80 ticks. */
    private static final int INTERVAL = 80;
    /** The ticks of upkeep the player holds goo for, at vital_nourish.json's one mB a tick. */
    private static final int DURATION = 400;
    private static final int EXPECTED_POINTS = DURATION / INTERVAL;
    /** Ticks past running dry the test keeps watching, to see no further point land. */
    private static final int AFTER_EXPIRY = INTERVAL + 2;
    private static final String SHOULD_FEED = "Nourish should add %d food points over its duration, added %d";
    private static final String SHOULD_END = "Nourish should end once the inventory runs dry, still stands";
    private static final String SHOULD_EAT = "Invoking Nourish should start the player eating the glove";

    private NourishTests() {
    }

    /**
     * A hungry mock player holding four hundred ticks of upkeep invokes
     * Nourish and ticks through them and an interval past: food rises by one
     * point per interval and the nourishment ends when the goo runs dry.
     *
     * @param helper the gametest helper
     */
    public static void nourishRefillsHunger(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.VITAL);
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(HUNGRY_FOOD);
        player.getFoodData().setSaturation(0);
        SelfDeliveryTests.invoke(player, GooTypes.VITAL, VITAL_NOURISH);
        // self-brew-goos-eat-before-the-effect: Nourish starts the eat the client plays
        helper.assertTrue(player.isUsingItem(), SHOULD_EAT);
        SelfDeliveryTests.eatThrough(player);
        GooSourceScanner.deplete(player, GooTypes.VITAL,
                GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VITAL, 0) - DURATION);
        int fedBefore = player.getFoodData().getFoodLevel();
        int watched = DURATION + AFTER_EXPIRY;
        for (int tick = 1; tick <= watched; tick++) {
            helper.runAfterDelay(tick, player::doTick);
        }
        helper.runAfterDelay(watched + 1, () -> {
            int added = player.getFoodData().getFoodLevel() - fedBefore;
            boolean stands = player.getData(GooAttachments.NOURISH).stands();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(added == EXPECTED_POINTS, String.format(SHOULD_FEED, EXPECTED_POINTS, added));
            helper.assertFalse(stands, SHOULD_END);
            helper.succeed();
        });
    }
}
