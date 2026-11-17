package com.mercuriusxeno.goo.network;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Crystal's Knives hold: a hold the client begins forms knives
 * on the server's stage clock, each knife paid as it forms, and letting go
 * ends it with nothing thrown; and a fractional ability cost paid twice
 * banks its half in the payer's remainder.
 * decision knives-form-in-fibonacci-stages-to-twelve
 * decision knives-form-on-right-click-and-throw-on-left-click
 */
@RegistersGameTests
public final class KnivesHoldTests {
    private static final String KNIVES_HOLD_FORMS_ON_THE_SERVER_CLOCK = "knives_hold_forms_on_the_server_clock";
    private static final String FRACTIONAL_COST_BANKS_ITS_HALF = "fractional_cost_banks_its_half";
    private static final String KNIVES_PAY_PER_KNIFE_AS_EACH_FORMS = "knives_pay_per_knife_as_each_forms";

    private static final Identifier CRYSTAL_KNIVES = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_knives");
    /** Two stages of twelve ticks: the first knife, then the second. */
    private static final int TWO_STAGES = 24;
    private static final int TWO_KNIVES = 2;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_knives";
    private static final String SHOULD_FORM = "Two stages into the hold the server should have formed %s knives,"
            + " formed %s";
    private static final String SHOULD_END = "Letting go should leave the hold with no knives, read %s";
    private static final String SHOULD_THROW_NOTHING = "Letting go should queue no knife to launch";
    /** Three stages of twelve ticks, which form four knives where the goo covers them. */
    private static final int THREE_STAGES = 36;
    private static final int THREE_KNIVES = 3;
    private static final String SHOULD_PAY = "Goo for 3 knives held through tick 36 should form 3 knives and leave"
            + " 0 mB, formed %s and left %s mB";
    private static final String FIELD_COST = "cost";
    /** A cost of 12 1/2 mB, written as the decimal an ability file may write. */
    private static final double TWELVE_AND_A_HALF = 12.5;
    private static final String SHOULD_CHARGE = "Payment %s of a 12 1/2 mB cost should take %s mB and bank %s,"
            + " took %s and banked %s";

    private KnivesHoldTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, KNIVES_HOLD_FORMS_ON_THE_SERVER_CLOCK,
                KnivesHoldTests::holdFormsOnTheServerClock);
        GooTestFunctions.registerTest(registrar, FRACTIONAL_COST_BANKS_ITS_HALF,
                KnivesHoldTests::fractionalCostBanksItsHalf);
        GooTestFunctions.registerTest(registrar, KNIVES_PAY_PER_KNIFE_AS_EACH_FORMS,
                KnivesHoldTests::knivesPayPerKnifeAsEachForms);
    }

    /**
     * A mock player begins a Knives hold through the hold payload's handler:
     * 24 ticks on the server has formed 2 knives, and ending the hold leaves
     * the count at 0 with no knife queued to launch.
     *
     * @param helper the gametest helper
     */
    public static void holdFormsOnTheServerClock(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        AbilityDefinition knives = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_KNIVES);
        helper.assertTrue(knives != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(caster, knives);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        String crystal = GooTypes.id(GooTypes.CRYSTAL);

        KnivesHoldHandler.hold(caster, new KnivesHoldPayload(true, crystal, CRYSTAL_KNIVES.toString()));

        helper.runAfterDelay(TWO_STAGES, () -> {
            int formed = state.knifeHolds().formed(caster);
            KnivesHoldHandler.hold(caster, new KnivesHoldPayload(false, crystal, CRYSTAL_KNIVES.toString()));
            int afterLettingGo = state.knifeHolds().formed(caster);
            boolean queued = state.fleckLaunches().hasPending();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(formed == TWO_KNIVES, String.format(SHOULD_FORM, TWO_KNIVES, formed));
            helper.assertTrue(afterLettingGo == 0, String.format(SHOULD_END, afterLettingGo));
            helper.assertFalse(queued, SHOULD_THROW_NOTHING);
            helper.succeed();
        });
    }

    /**
     * A mock player holding crystal goo for exactly 3 knives holds Knives
     * through tick 36, the clock's fourth knife: 3 knives form, each paid as
     * it forms, and the fourth, which the goo cannot cover, does not.
     * decision knives-pay-per-knife-as-each-forms
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void knivesPayPerKnifeAsEachForms(GameTestHelper helper) {
        AbilityDefinition knives = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_KNIVES);
        helper.assertTrue(knives != null, ABILITY_REQUIRED);
        int price = knives.cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.CRYSTAL, THREE_KNIVES * price));
        KnownRecipes.teachRequires(caster, knives);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        String crystal = GooTypes.id(GooTypes.CRYSTAL);

        KnivesHoldHandler.hold(caster, new KnivesHoldPayload(true, crystal, CRYSTAL_KNIVES.toString()));

        helper.runAfterDelay(THREE_STAGES, () -> {
            int formed = state.knifeHolds().formed(caster);
            int left = crystalHeld(caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(formed == THREE_KNIVES && left == 0, String.format(SHOULD_PAY, formed, left));
            helper.succeed();
        });
    }

    /**
     * An ability priced at 12 1/2 mB, paid twice: the first payment takes 13 mB and banks 1/2 in the payer's
     * remainder, the second takes 12 and the remainder pays the rest, 25 mB across the two.
     * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
     *
     * @param helper the gametest helper
     */
    public static void fractionalCostBanksItsHalf(GameTestHelper helper) {
        AbilityDefinition knives = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_KNIVES);
        helper.assertTrue(knives != null, ABILITY_REQUIRED);
        AbilityDefinition halved = costing(knives, TWELVE_AND_A_HALF);
        ServerPlayer payer = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        GooFraction price = halved.cost().exactIn(GooValues.of(helper.getLevel()));

        int[] took = {13, 12};
        GooFraction[] banked = {GooFraction.of(1, 2), GooFraction.ZERO};
        for (int payment = 0; payment < took.length; payment++) {
            int before = crystalHeld(payer);
            AbilityCharges.charge(payer, GooTypes.CRYSTAL, price);
            int spent = before - crystalHeld(payer);
            GooFraction kept = GooCapabilities.remainderLedgerOf(payer).remainders().of(GooTypes.CRYSTAL);
            helper.assertTrue(spent == took[payment] && kept.equals(banked[payment]),
                    String.format(SHOULD_CHARGE, payment + 1, took[payment], banked[payment], spent, kept));
        }
        helper.getLevel().getServer().getPlayerList().remove(payer);
        helper.succeed();
    }

    // The ability as its file would read with another cost.
    private static AbilityDefinition costing(AbilityDefinition ability, double cost) {
        Codec<AbilityDefinition> codec = AbilityDefinition.codecFor(ability.id());
        JsonObject file = codec.encodeStart(JsonOps.INSTANCE, ability).getOrThrow().getAsJsonObject();
        file.addProperty(FIELD_COST, cost);
        return codec.parse(JsonOps.INSTANCE, file).getOrThrow();
    }

    private static int crystalHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.CRYSTAL, 0);
    }
}
