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
    private static final String KNIVES_THROW_WITH_NOTHING_PAINTED_KEEPS_THE_FAN =
            "knives_throw_with_nothing_painted_keeps_the_fan";
    private static final String KNIVES_HOME_ON_A_MOB_THAT_MOVES = "knives_home_on_a_mob_that_moves";
    private static final String KNIVES_THROWN_STAY_PAID_AND_THE_FAN_REFUNDS =
            "knives_thrown_stay_paid_and_the_fan_refunds";

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
    private static final String SHOULD_KEEP_THE_FAN = "A throw with nothing painted should leave 2 knives in the fan"
            + " and none flying, left %s";
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
    private static final int ONE_STAGE = 12;
    private static final int FOUR_KNIVES = 4;
    private static final int TWO_KNIVES_THROWN = 2;
    private static final int PLENTY_OF_PRICES = 10;
    /** A knife's flight of six blocks at 1.5 a tick, with slack for the sidestep. */
    private static final int FLIGHT_TICKS = 20;
    private static final float DAMAGE_WITHIN = 1e-3f;
    private static final float ONE_AND_A_HALF_KNIVES = 1.5f;
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
        GooTestFunctions.registerTest(registrar, KNIVES_THROW_WITH_NOTHING_PAINTED_KEEPS_THE_FAN,
                KnivesHoldTests::throwWithNothingPaintedKeepsTheFan);
        GooTestFunctions.registerTest(registrar, KNIVES_HOME_ON_A_MOB_THAT_MOVES,
                KnivesHoldTests::knivesHomeOnAMobThatMoves);
        GooTestFunctions.registerTest(registrar, KNIVES_THROWN_STAY_PAID_AND_THE_FAN_REFUNDS,
                KnivesHoldTests::thrownKnivesStayPaidAndTheFanRefunds);
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

        helper.runAfterDelay(TWO_STAGES, () -> {
            KnivesThrowHandler.throwKnives(caster, new KnivesThrowPayload(List.of()));
            int left = state.knifeHolds().formed(caster);
            boolean flying = state.homingKnives().hasPending();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(left == TWO_KNIVES && !flying, String.format(SHOULD_KEEP_THE_FAN, left));
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

        helper.runAfterDelay(ONE_STAGE, () -> {
            KnivesThrowHandler.throwKnives(caster, new KnivesThrowPayload(List.of(husk.getId())));
            helper.runAfterDelay(1, () -> husk.teleportTo(husk.getX() + SIDESTEP, husk.getY(), husk.getZ()));
        });
        helper.runAfterDelay(ONE_STAGE + FLIGHT_TICKS, () -> {
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

        helper.runAfterDelay(THREE_STAGES, () -> {
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

    private static AbilityDefinition knives(GameTestHelper helper) {
        AbilityDefinition knives = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_KNIVES);
        helper.assertTrue(knives != null, ABILITY_REQUIRED);
        return knives;
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
