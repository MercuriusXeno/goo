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
import com.mercuriusxeno.goo.ability.program.DamageStep;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Crystal's Knives hold: a hold the client begins forms one
 * knife per 3 ticks up to 10 on the server's clock, each knife paid as it
 * forms, and letting go ends it with nothing thrown and every knife refunded;
 * and a fractional ability cost paid twice banks its half in the payer's
 * remainder.
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision knives-pay-per-knife-as-each-forms
 * decision knives-form-on-right-click-and-throw-on-left-click
 */
@RegistersGameTests
public final class KnivesHoldTests {
    private static final String KNIVES_CHARGE_ONE_PER_THREE_TICKS_TO_TEN = "knives_charge_one_per_three_ticks_to_ten";
    private static final String FRACTIONAL_COST_BANKS_ITS_HALF = "fractional_cost_banks_its_half";
    private static final String KNIVES_PAY_PER_KNIFE_AS_EACH_FORMS = "knives_pay_per_knife_as_each_forms";
    private static final String KNIVES_THROW_WITH_NOTHING_PAINTED_KEEPS_THE_FAN =
            "knives_throw_with_nothing_painted_keeps_the_fan";
    private static final String KNIVES_HOME_ON_A_MOB_THAT_MOVES = "knives_home_on_a_mob_that_moves";
    private static final String KNIVES_THROWN_STAY_PAID_AND_THE_FAN_REFUNDS =
            "knives_thrown_stay_paid_and_the_fan_refunds";
    private static final String KNIVES_REFILL_A_STAGE_AFTER_A_THROW = "knives_refill_a_stage_after_a_throw";

    private static final Identifier CRYSTAL_KNIVES = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_knives");
    /** The ticks one knife takes to form. */
    private static final int ONE_KNIFE_TICKS = 3;
    /** The ticks ten knives take to form, the cap. */
    private static final int TEN_KNIVES_TICKS = 30;
    /** Twice the ticks the cap takes, which forms no knife past it. */
    private static final int PAST_THE_CAP_TICKS = 60;
    private static final int TEN_KNIVES = 10;
    private static final int TWO_KNIVES = 2;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_knives";
    private static final String SHOULD_FORM = "The hold should form 1 knife at 3 ticks, 10 at 30 and 10 at 60,"
            + " formed %s";
    private static final String SHOULD_END = "Letting go should leave the hold with no knives, read %s";
    private static final String SHOULD_THROW_NOTHING = "Letting go should queue no knife to launch";
    /** Goo for more knives than the cap, so only the cap stops the paying. */
    private static final int TWELVE_PRICES = 12;
    private static final String SHOULD_PAY = "30 ticks of hold should spend 10 prices, %s mB, spent %s mB;"
            + " letting go should return the goo to %s mB, read %s mB";
    private static final String SHOULD_KEEP_THE_FAN = "A throw with nothing painted should leave 2 knives in the fan,"
            + " left %s";
    private static final String SHOULD_STRIKE = "The knife should land %s damage on the husk that moved, landed %s";
    private static final String SHOULD_LEAVE_TWO = "Throwing 2 of 4 knives should leave 2 in the fan, left %s";
    private static final String SHOULD_REFUND = "The dismiss should leave the holdings at %s, 2 prices under %s,"
            + " read %s";
    private static final int BAY_SIDE = 16;
    private static final BlockPos CASTER_POS = new BlockPos(8, 1, 1);
    /** A husk, which no daylight burns, so only the knives hurt it. */
    private static final BlockPos HUSK_POS = new BlockPos(8, 1, 7);
    private static final float FACING_SOUTH = 0f;
    /** Blocks the husk steps sideways the tick after the throw. */
    private static final double SIDESTEP = 3;
    /** The ticks four knives take to form. */
    private static final int FOUR_KNIVES_TICKS = 12;
    /** The ticks two knives take to form. */
    private static final int TWO_KNIVES_TICKS = 6;
    private static final int FOUR_KNIVES = 4;
    private static final int TWO_KNIVES_THROWN = 2;
    private static final int PLENTY_OF_PRICES = 10;
    /** A knife's flight of six blocks at 1.5 a tick, with slack for the sidestep. */
    private static final int FLIGHT_TICKS = 20;
    private static final float DAMAGE_WITHIN = 1e-3f;
    private static final float ONE_AND_A_HALF_KNIVES = 1.5f;
    private static final String SHOULD_WAIT = "The 2 ticks after throwing 1 of 4 knives should hold 3, held %s";
    private static final String SHOULD_REFILL = "The 3rd tick after the throw should fill the fan to 4 and pay 1 more"
            + " price, %s mB; held %s and paid %s mB";
    private static final int THREE_LEFT = 3;
    private static final int ONE_REFILLED = 1;
    private static final float HALF_A_KNIFE = 0.5f;
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
        GooTestFunctions.registerTest(registrar, KNIVES_CHARGE_ONE_PER_THREE_TICKS_TO_TEN,
                KnivesHoldTests::knivesChargeOnePerThreeTicksToTen);
        GooTestFunctions.registerTest(registrar, FRACTIONAL_COST_BANKS_ITS_HALF,
                KnivesHoldTests::fractionalCostBanksItsHalf);
        GooTestFunctions.registerTest(registrar, KNIVES_PAY_PER_KNIFE_AS_EACH_FORMS,
                KnivesHoldTests::knivesPayPerKnifeAsEachForms);
        GooTestFunctions.registerTest(registrar, KNIVES_THROW_WITH_NOTHING_PAINTED_KEEPS_THE_FAN,
                KnivesHoldTests::throwWithNothingPaintedKeepsTheFan);
        GooTestFunctions.registerTest(registrar, KNIVES_HOME_ON_A_MOB_THAT_MOVES,
                KnivesHoldTests::knivesHomeOnAMobThatMoves);
        GooTestFunctions.registerTest(registrar, KNIVES_THROWN_STAY_PAID_AND_THE_FAN_REFUNDS,
                KnivesHoldTests::thrownKnivesStayPaidAndTheFanRefunds);
        GooTestFunctions.registerTest(registrar, KNIVES_REFILL_A_STAGE_AFTER_A_THROW,
                KnivesHoldTests::knivesRefillAStageAfterAThrow);
    }

    /**
     * A mock player begins a Knives hold through the hold payload's handler:
     * the server's count reads 1 at 3 ticks, 10 at 30 and still 10 at 60, and
     * ending the hold leaves the count at 0 with no knife queued to launch.
     * decision knives-charge-one-per-three-ticks-up-to-ten
     *
     * @param helper the gametest helper
     */
    public static void knivesChargeOnePerThreeTicksToTen(GameTestHelper helper) {
        ServerPlayer caster = casterHolding(helper, TWELVE_PRICES);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        List<Integer> formed = new java.util.ArrayList<>();
        holding(caster, true);

        helper.runAfterDelay(ONE_KNIFE_TICKS, () -> formed.add(state.knifeHolds().formed(caster)));
        helper.runAfterDelay(TEN_KNIVES_TICKS, () -> formed.add(state.knifeHolds().formed(caster)));
        helper.runAfterDelay(PAST_THE_CAP_TICKS, () -> {
            formed.add(state.knifeHolds().formed(caster));
            holding(caster, false);
            int afterLettingGo = state.knifeHolds().formed(caster);
            boolean queued = state.homingKnives().hasPending();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(formed.equals(List.of(1, TEN_KNIVES, TEN_KNIVES)), String.format(SHOULD_FORM, formed));
            helper.assertTrue(afterLettingGo == 0, String.format(SHOULD_END, afterLettingGo));
            helper.assertFalse(queued, SHOULD_THROW_NOTHING);
            helper.succeed();
        });
    }

    /**
     * A mock player holding crystal goo for 12 knives holds Knives for 30
     * ticks: 10 knives form, each paid as it forms, and letting go refunds
     * every one, the goo reading its starting amount.
     * decision knives-pay-per-knife-as-each-forms
     *
     * @param helper the gametest helper
     */
    public static void knivesPayPerKnifeAsEachForms(GameTestHelper helper) {
        int price = priceOf(helper);
        ServerPlayer caster = casterHolding(helper, TWELVE_PRICES);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        int start = crystalHeld(caster);
        holding(caster, true);

        helper.runAfterDelay(TEN_KNIVES_TICKS, () -> {
            state.knifeHolds().formed(caster);
            int spent = start - crystalHeld(caster);
            holding(caster, false);
            int end = crystalHeld(caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(spent == TEN_KNIVES * price && end == start,
                    String.format(SHOULD_PAY, TEN_KNIVES * price, spent, start, end));
            helper.succeed();
        });
    }

    /**
     * A mock player holding 2 knives left clicks with nothing painted: no
     * knife is thrown and the server's count still reads 2.
     * decision knives-with-no-target-stay-in-the-fan
     *
     * @param helper the gametest helper
     */
    public static void throwWithNothingPaintedKeepsTheFan(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(caster, knives(helper));
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        holding(caster, true);

        helper.runAfterDelay(TWO_KNIVES_TICKS, () -> {
            KnivesThrowHandler.throwKnives(caster, new KnivesThrowPayload(List.of()));
            int left = state.knifeHolds().formed(caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(left == TWO_KNIVES, String.format(SHOULD_KEEP_THE_FAN, left));
            helper.succeed();
        });
    }

    /**
     * A mock player forms one knife, paints a husk ahead and throws; the
     * tick after, the husk steps 3 blocks sideways, and the knife homes on
     * it and lands the Knives damage.
     * decision knives-home-on-their-painted-mob
     *
     * @param helper the gametest helper
     */
    public static void knivesHomeOnAMobThatMoves(GameTestHelper helper) {
        layTheFloor(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, HUSK_POS);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, FACING_SOUTH, 0f);
        AbilityDefinition knives = knives(helper);
        KnownRecipes.teachRequires(caster, knives);
        float landed = damageOf(knives).landedOn(husk, caster);
        holding(caster, true);

        helper.runAfterDelay(ONE_KNIFE_TICKS, () -> {
            KnivesThrowHandler.throwKnives(caster, new KnivesThrowPayload(List.of(husk.getId())));
            helper.runAfterDelay(1, () -> husk.teleportTo(husk.getX() + SIDESTEP, husk.getY(), husk.getZ()));
        });
        helper.runAfterDelay(ONE_KNIFE_TICKS + FLIGHT_TICKS, () -> {
            float fell = husk.getMaxHealth() - husk.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(Math.abs(fell - landed) < DAMAGE_WITHIN, String.format(SHOULD_STRIKE, landed, fell));
            helper.succeed();
        });
    }

    /**
     * A mock player forms 4 knives, throws them at a painted husk needing
     * 2, then lets go: the throw leaves 2 in the fan, and the dismiss refunds
     * those 2, the holdings ending 2 prices under their start.
     * decision knives-pay-per-knife-as-each-forms
     * decision knives-split-evenly-capped-at-the-kill
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void thrownKnivesStayPaidAndTheFanRefunds(GameTestHelper helper) {
        layTheFloor(helper);
        AbilityDefinition knives = knives(helper);
        int price = knives.cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, FACING_SOUTH, 0f);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.CRYSTAL, PLENTY_OF_PRICES * price));
        KnownRecipes.teachRequires(caster, knives);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, HUSK_POS);
        husk.setHealth(damageOf(knives).landedOn(husk, caster) * ONE_AND_A_HALF_KNIVES);
        int start = crystalHeld(caster);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        holding(caster, true);

        helper.runAfterDelay(FOUR_KNIVES_TICKS, () -> {
            boolean fourFormed = state.knifeHolds().formed(caster) == FOUR_KNIVES;
            KnivesThrowHandler.throwKnives(caster, new KnivesThrowPayload(List.of(husk.getId())));
            int left = state.knifeHolds().formed(caster);
            holding(caster, false);
            int end = crystalHeld(caster);
            int expected = start - TWO_KNIVES_THROWN * price;
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(fourFormed && left == FOUR_KNIVES - TWO_KNIVES_THROWN,
                    String.format(SHOULD_LEAVE_TWO, left));
            helper.assertTrue(end == expected, String.format(SHOULD_REFUND, expected, start, end));
            helper.succeed();
        });
    }

    /**
     * A mock player forms 4 knives and throws 1 at a painted husk needing 1:
     * the fan holds 3 through the 2 ticks after the throw, and on the 3rd it
     * fills to 4, paying for the knife refilled.
     * decision knives-refill-from-the-stage-the-fan-rounds-up-to
     * decision knives-charge-one-per-three-ticks-up-to-ten
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void knivesRefillAStageAfterAThrow(GameTestHelper helper) {
        layTheFloor(helper);
        AbilityDefinition knives = knives(helper);
        int price = knives.cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, FACING_SOUTH, 0f);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.CRYSTAL, PLENTY_OF_PRICES * price));
        KnownRecipes.teachRequires(caster, knives);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, HUSK_POS);
        husk.setHealth(damageOf(knives).landedOn(husk, caster) * HALF_A_KNIFE);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        holding(caster, true);
        List<Integer> waiting = new java.util.ArrayList<>();
        int[] atTheThrow = new int[1];

        helper.runAfterDelay(FOUR_KNIVES_TICKS, () -> {
            state.knifeHolds().formed(caster);
            KnivesThrowHandler.throwKnives(caster, new KnivesThrowPayload(List.of(husk.getId())));
            atTheThrow[0] = crystalHeld(caster);
            for (int tick = 1; tick < ONE_KNIFE_TICKS; tick++) {
                helper.runAfterDelay(tick, () -> waiting.add(state.knifeHolds().formed(caster)));
            }
            helper.runAfterDelay(ONE_KNIFE_TICKS, () -> {
                int refilled = state.knifeHolds().formed(caster);
                int paid = atTheThrow[0] - crystalHeld(caster);
                helper.getLevel().getServer().getPlayerList().remove(caster);
                helper.assertTrue(waiting.stream().allMatch(held -> held == THREE_LEFT), String.format(SHOULD_WAIT,
                        waiting));
                helper.assertTrue(refilled == FOUR_KNIVES && paid == ONE_REFILLED * price,
                        String.format(SHOULD_REFILL, ONE_REFILLED * price, refilled, paid));
                helper.succeed();
            });
        });
    }

    private static AbilityDefinition knives(GameTestHelper helper) {
        AbilityDefinition knives = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_KNIVES);
        helper.assertTrue(knives != null, ABILITY_REQUIRED);
        return knives;
    }

    private static int priceOf(GameTestHelper helper) {
        return knives(helper).cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
    }

    // A mock player with a glove in hand, Knives' requires taught and crystal goo for some knives' prices.
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer casterHolding(GameTestHelper helper, int prices) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.CRYSTAL, prices * priceOf(helper)));
        KnownRecipes.teachRequires(caster, knives(helper));
        return caster;
    }

    private static DamageStep damageOf(AbilityDefinition knives) {
        return knives.behaviors().stream().filter(DamageStep.class::isInstance).map(DamageStep.class::cast)
                .findFirst().orElseThrow();
    }

    private static void holding(ServerPlayer caster, boolean holding) {
        KnivesHoldHandler.hold(caster, new KnivesHoldPayload(holding, GooTypes.id(GooTypes.CRYSTAL),
                CRYSTAL_KNIVES.toString()));
    }

    private static void layTheFloor(GameTestHelper helper) {
        for (int x = 0; x < BAY_SIDE; x++) {
            for (int z = 0; z < BAY_SIDE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
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
