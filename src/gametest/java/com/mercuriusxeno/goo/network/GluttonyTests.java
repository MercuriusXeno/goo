package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.gluttony.Gluttony;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for Gluttony through the glove: a heal past full health banks as
 * overheal hearts, a meal past full hunger banks as overhunger that refills
 * the bar as it drops, and the hearts leave with the gluttony once the goo
 * runs dry (decisions gluttony-overheals-and-overhungers and self-effects-trickle-until-ended).
 */
@RegistersGameTests
public final class GluttonyTests {
    private static final String GLUTTONY_BANKS_A_HEAL_PAST_FULL = "gluttony_banks_a_heal_past_full";
    private static final String GLUTTONY_BANKS_A_MEAL_AT_FULL = "gluttony_banks_a_meal_at_full";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GLUTTONY_BANKS_A_HEAL_PAST_FULL,
                GluttonyTests::gluttonyBanksAHealPastFull);
        GooTestFunctions.registerTest(registrar, GLUTTONY_BANKS_A_MEAL_AT_FULL,
                GluttonyTests::gluttonyBanksAMealAtFull);
    }

    private static final Identifier JELLY_GLUTTONY = Identifier.parse("goo:jelly_gluttony");
    private static final Identifier OVERHEAL_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "gluttony_overheal");
    /** The upkeep the eating player holds, at jelly_gluttony.json's one mB a tick: more than the meal spends. */
    private static final int LONG_UPKEEP = 520;
    /** The upkeep the healed player holds, run dry within the watch. */
    private static final int SHORT_UPKEEP = 20;
    private static final int RUN_DRY_WITHIN = SHORT_UPKEEP + 20;
    private static final float HEAL = 3f;
    private static final int FULL_FOOD = 20;
    /** Cooked beef's nutrition. */
    private static final int BEEF_NUTRITION = 8;
    /** More ticks than any food takes to eat. */
    private static final int EAT_LIMIT = 100;
    private static final String SHOULD_EAT = "Invoking Gluttony should start the player eating the glove";
    private static final String SHOULD_OVERHEAL = "A heal of %.1f on a full bar should bank %.1f absorption, banked %.2f";
    private static final String SHOULD_END = "Gluttony should end once the inventory runs dry, still stands";
    private static final String SHOULD_DROP_OVERHEAL = "Gluttony's overheal should leave with it, %.2f stands";
    private static final String SHOULD_RETURN_MAX = "Gluttony's lent max absorption should return with it";
    private static final String SHOULD_START_EATING = "The player should start eating the food it holds";
    private static final String SHOULD_FINISH_EATING = "The meal should finish within %d ticks";
    private static final String SHOULD_OVERHUNGER = "The meal from %d food should bank %d overhunger, banked %d";
    private static final String SHOULD_STAY_FULL = "Hunger should stand at %d, stands at %d";
    private static final String SHOULD_REFILL = "Banked overhunger should refill the dropped bar to %d and bank %d, "
            + "stands at %d with %d banked";

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

    /**
     * A full player under Gluttony starts eating cooked beef, which vanilla
     * refuses a full player, and banks its eight food points whole with the
     * bar still full; a bar then dropped to nineteen reads full a tick on and
     * the bank holds seven.
     *
     * @param helper the gametest helper
     */
    public static void gluttonyBanksAMealAtFull(GameTestHelper helper) {
        ServerPlayer player = glutton(helper, LONG_UPKEEP);
        player.getFoodData().setFoodLevel(FULL_FOOD);
        String eaten = eat(player, new ItemStack(Items.COOKED_BEEF));
        int banked = player.getData(GooAttachments.GLUTTONY).overhunger();
        int food = player.getFoodData().getFoodLevel();
        player.getFoodData().setFoodLevel(FULL_FOOD - 1);
        player.doTick();
        int refilledFood = player.getFoodData().getFoodLevel();
        int refilledBank = player.getData(GooAttachments.GLUTTONY).overhunger();
        helper.getLevel().getServer().getPlayerList().remove(player);
        if (eaten != null) {
            helper.fail(eaten);
            return;
        }
        helper.assertTrue(banked == BEEF_NUTRITION,
                String.format(SHOULD_OVERHUNGER, FULL_FOOD, BEEF_NUTRITION, banked));
        helper.assertTrue(food == FULL_FOOD, String.format(SHOULD_STAY_FULL, FULL_FOOD, food));
        helper.assertTrue(refilledFood == FULL_FOOD && refilledBank == BEEF_NUTRITION - 1,
                String.format(SHOULD_REFILL, FULL_FOOD, BEEF_NUTRITION - 1, refilledFood, refilledBank));
        helper.succeed();
    }

    /**
     * Has the player eat a food from its off hand through the item's own use,
     * ticking it until the meal finishes.
     *
     * @param player the player
     * @param food   the food
     * @return the failure, or null once the meal is eaten
     */
    private static String eat(ServerPlayer player, ItemStack food) {
        player.setItemInHand(InteractionHand.OFF_HAND, food);
        food.use(player.level(), player, InteractionHand.OFF_HAND);
        if (!player.isUsingItem()) {
            return SHOULD_START_EATING;
        }
        for (int tick = 0; tick < EAT_LIMIT && player.isUsingItem(); tick++) {
            player.doTick();
        }
        return player.isUsingItem() ? String.format(SHOULD_FINISH_EATING, EAT_LIMIT) : null;
    }
}
