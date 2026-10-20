package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.gluttony.Gluttony;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for Gluttony through the glove: a hungry, hurt player under
 * Gluttony stays hungry and hurt, a heal past full health banks as overheal
 * hearts, and the hearts leave with the gluttony once the goo runs dry
 * (decisions gluttony-overheals-and-overhungers and self-effects-trickle-until-ended).
 */
@RegistersGameTests
public final class GluttonyTests {
    private static final String GLUTTONY_LANDS_NO_HEAL_OR_FEED = "gluttony_lands_no_heal_or_feed";
    private static final String GLUTTONY_BANKS_A_HEAL_PAST_FULL = "gluttony_banks_a_heal_past_full";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GLUTTONY_LANDS_NO_HEAL_OR_FEED,
                GluttonyTests::gluttonyLandsNoHealOrFeed);
        GooTestFunctions.registerTest(registrar, GLUTTONY_BANKS_A_HEAL_PAST_FULL,
                GluttonyTests::gluttonyBanksAHealPastFull);
    }

    private static final Identifier JELLY_GLUTTONY = Identifier.parse("goo:jelly_gluttony");
    private static final Identifier OVERHEAL_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "gluttony_overheal");
    /** Three short of a full bar and below natural regeneration's eighteen, so only Gluttony could feed or heal. */
    private static final int HUNGRY_FOOD = 17;
    private static final float HURT_BY = 1f;
    /** The ticks the hungry, hurt player is watched under Gluttony. */
    private static final int WATCHED = 420;
    /** The upkeep the hungry player holds, at jelly_gluttony.json's one mB a tick: more than the watch spends. */
    private static final int LONG_UPKEEP = WATCHED + 100;
    /** The upkeep the healed player holds, run dry within the watch. */
    private static final int SHORT_UPKEEP = 20;
    private static final int RUN_DRY_WITHIN = SHORT_UPKEEP + 20;
    private static final float HEAL = 3f;
    private static final String SHOULD_EAT = "Invoking Gluttony should start the player eating the glove";
    private static final String SHOULD_STAND = "Gluttony should still stand while the goo lasts";
    private static final String SHOULD_NOT_FEED = "Gluttony should feed nothing: food should stay %d, stands at %d";
    private static final String SHOULD_NOT_HEAL = "Gluttony should heal nothing: health should stay %.1f, stands at %.2f";
    private static final String SHOULD_OVERHEAL = "A heal of %.1f on a full bar should bank %.1f absorption, banked %.2f";
    private static final String SHOULD_END = "Gluttony should end once the inventory runs dry, still stands";
    private static final String SHOULD_DROP_OVERHEAL = "Gluttony's overheal should leave with it, %.2f stands";
    private static final String SHOULD_RETURN_MAX = "Gluttony's lent max absorption should return with it";

    private GluttonyTests() {
    }

    private static ServerPlayer glutton(GameTestHelper helper, int upkeep) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        player.setGameMode(GameType.SURVIVAL);
        SelfDeliveryTests.invoke(player, GooTypes.JELLY, JELLY_GLUTTONY);
        // self-brew-goos-eat-before-the-effect: Gluttony starts the eat the client plays
        helper.assertTrue(player.isUsingItem(), SHOULD_EAT);
        SelfDeliveryTests.eatThrough(player);
        GooSourceScanner.deplete(player, GooTypes.JELLY,
                GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.JELLY, 0) - upkeep);
        return player;
    }

    /**
     * A hungry, half-heart-hurt player holding Gluttony past four hundred
     * ticks stands exactly as hungry and hurt as it started.
     *
     * @param helper the gametest helper
     */
    public static void gluttonyLandsNoHealOrFeed(GameTestHelper helper) {
        ServerPlayer player = glutton(helper, LONG_UPKEEP);
        player.getFoodData().setFoodLevel(HUNGRY_FOOD);
        player.getFoodData().setSaturation(0);
        float hurt = player.getMaxHealth() - HURT_BY;
        player.setHealth(hurt);
        for (int tick = 1; tick <= WATCHED; tick++) {
            helper.runAfterDelay(tick, player::doTick);
        }
        helper.runAfterDelay(WATCHED + 1, () -> {
            boolean stands = player.getData(GooAttachments.GLUTTONY).stands();
            int food = player.getFoodData().getFoodLevel();
            float health = player.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(stands, SHOULD_STAND);
            helper.assertTrue(food == HUNGRY_FOOD, String.format(SHOULD_NOT_FEED, HUNGRY_FOOD, food));
            helper.assertTrue(health == hurt, String.format(SHOULD_NOT_HEAL, hurt, health));
            helper.succeed();
        });
    }

    /**
     * A full-health player under Gluttony healed by three holds three
     * absorption hearts; once the goo runs dry the gluttony, its hearts and
     * the max absorption it lent are gone.
     *
     * @param helper the gametest helper
     */
    public static void gluttonyBanksAHealPastFull(GameTestHelper helper) {
        ServerPlayer player = glutton(helper, SHORT_UPKEEP);
        player.setHealth(player.getMaxHealth());
        player.heal(HEAL);
        float banked = player.getAbsorptionAmount();
        if (banked != HEAL) {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.fail(String.format(SHOULD_OVERHEAL, HEAL, HEAL, banked));
            return;
        }
        for (int tick = 1; tick <= RUN_DRY_WITHIN; tick++) {
            helper.runAfterDelay(tick, player::doTick);
        }
        helper.runAfterDelay(RUN_DRY_WITHIN + 1, () -> {
            boolean stands = player.getData(GooAttachments.GLUTTONY).stands();
            float absorption = player.getAbsorptionAmount();
            AttributeInstance maxAbsorption = player.getAttribute(Attributes.MAX_ABSORPTION);
            boolean lent = maxAbsorption != null && maxAbsorption.hasModifier(OVERHEAL_ID);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(stands, SHOULD_END);
            helper.assertTrue(absorption == 0f, String.format(SHOULD_DROP_OVERHEAL, absorption));
            helper.assertFalse(lent, SHOULD_RETURN_MAX);
            helper.succeed();
        });
    }
}
