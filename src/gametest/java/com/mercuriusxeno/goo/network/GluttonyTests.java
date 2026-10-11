package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.gluttony.Gluttony;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Gametest for Gluttony through the glove: a hungry, hurt player eats the
 * jelly self + brew ability; its points fill hunger and health, then bank
 * past the full bars into overhunger and overheal hearts while it holds goo
 * for the upkeep, and the overheal leaves with it once it runs dry
 * (decisions gluttony-overheals-and-overhungers and self-effects-trickle-until-ended).
 */
public final class GluttonyTests {

    private static final Identifier JELLY_GLUTTONY = Identifier.parse("goo:jelly_gluttony");
    /** Three short of a full bar and below natural regeneration's eighteen, so only Gluttony feeds or heals. */
    private static final int HUNGRY_FOOD = 17;
    private static final int FULL_FOOD = 20;
    /** jelly_gluttony.json's interval: a point every 80 ticks. */
    private static final int INTERVAL = 80;
    /** The ticks of upkeep the player holds goo for, at jelly_gluttony.json's one mB a tick. */
    private static final int DURATION = 400;
    /** Ten ticks past the fourth point, before the goo runs dry. */
    private static final int WATCH_BANKS_AT = 4 * INTERVAL + 10;
    /** Four points: three fill hunger and the fourth banks; one heals the half heart short and three bank. */
    private static final int EXPECTED_OVERHUNGER = 1;
    private static final float EXPECTED_OVERHEAL = 3f;
    /** Ticks past running dry the test keeps watching, to see the gluttony end. */
    private static final int AFTER_EXPIRY = INTERVAL + 2;
    private static final String SHOULD_FILL_HUNGER = "Gluttony should fill hunger to %d, stands at %d";
    private static final String SHOULD_FILL_HEALTH = "Gluttony should fill health to %.1f, stands at %.2f";
    private static final String SHOULD_OVERHUNGER = "Gluttony should bank %d overhunger, banked %d";
    private static final String SHOULD_OVERHEAL = "Gluttony should stand %.1f overheal hearts, stands %.2f";
    private static final String SHOULD_END = "Gluttony should end once the inventory runs dry, still stands";
    private static final String SHOULD_DROP_OVERHEAL = "Gluttony's overheal should leave with it, %.2f stands";
    private static final String SHOULD_EAT = "Invoking Gluttony should start the player eating the glove";

    private GluttonyTests() {
    }

    /**
     * A hungry, half-heart-hurt mock player holding four hundred ticks of
     * upkeep invokes Gluttony: four points on, hunger and health stand full,
     * one point stands banked as overhunger and three health as absorption
     * hearts; an interval past running dry, the gluttony and its hearts are gone.
     *
     * @param helper the gametest helper
     */
    public static void gluttonyFillsPastTheCap(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        player.setGameMode(GameType.SURVIVAL);
        SelfDeliveryTests.invoke(player, GooTypes.JELLY, JELLY_GLUTTONY);
        // self-brew-goos-eat-before-the-effect: Gluttony starts the eat the client plays
        helper.assertTrue(player.isUsingItem(), SHOULD_EAT);
        SelfDeliveryTests.eatThrough(player);
        player.getFoodData().setFoodLevel(HUNGRY_FOOD);
        player.getFoodData().setSaturation(0);
        player.setHealth(player.getMaxHealth() - Gluttony.HEALTH_PER_POINT);
        GooSourceScanner.deplete(player, GooTypes.JELLY,
                GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.JELLY, 0) - DURATION);
        int watched = DURATION + AFTER_EXPIRY;
        for (int tick = 1; tick <= watched; tick++) {
            helper.runAfterDelay(tick, player::doTick);
        }
        helper.runAfterDelay(WATCH_BANKS_AT, () -> assertBanked(helper, player));
        helper.runAfterDelay(watched + 1, () -> {
            boolean stands = player.getData(GooAttachments.GLUTTONY).stands();
            float absorption = player.getAbsorptionAmount();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(stands, SHOULD_END);
            helper.assertTrue(absorption == 0f, String.format(SHOULD_DROP_OVERHEAL, absorption));
            helper.succeed();
        });
    }

    private static void assertBanked(GameTestHelper helper, ServerPlayer player) {
        Gluttony gluttony = player.getData(GooAttachments.GLUTTONY);
        int food = player.getFoodData().getFoodLevel();
        helper.assertTrue(food == FULL_FOOD, String.format(SHOULD_FILL_HUNGER, FULL_FOOD, food));
        helper.assertTrue(player.getHealth() == player.getMaxHealth(),
                String.format(SHOULD_FILL_HEALTH, player.getMaxHealth(), player.getHealth()));
        helper.assertTrue(gluttony.overhunger() == EXPECTED_OVERHUNGER,
                String.format(SHOULD_OVERHUNGER, EXPECTED_OVERHUNGER, gluttony.overhunger()));
        helper.assertTrue(player.getAbsorptionAmount() == EXPECTED_OVERHEAL,
                String.format(SHOULD_OVERHEAL, EXPECTED_OVERHEAL, player.getAbsorptionAmount()));
    }
}
