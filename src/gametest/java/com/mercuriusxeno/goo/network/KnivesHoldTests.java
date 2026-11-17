package com.mercuriusxeno.goo.network;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.Variables;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Crystal's Knives hold: a hold the client begins forms one
 * knife per 6 ticks up to 10 on the server's clock, each knife paid as it
 * forms, and letting go ends it with nothing thrown and every knife refunded;
 * a left click throws a knife as a projectile along the look, which turns
 * toward no mob and passes between a fence's rails; and a fractional ability
 * cost paid twice banks its half in the payer's remainder.
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision knives-pay-per-knife-as-each-forms
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-and-dart-function-as-projectiles
 * decision knives-do-not-seek-you-just-throw-them
 */
@RegistersGameTests
public final class KnivesHoldTests {
    private static final String KNIVES_CHARGE_ONE_PER_SIX_TICKS_TO_TEN = "knives_charge_one_per_six_ticks_to_ten";
    private static final String FRACTIONAL_COST_BANKS_ITS_HALF = "fractional_cost_banks_its_half";
    private static final String KNIVES_PAY_PER_KNIFE_AS_EACH_FORMS = "knives_pay_per_knife_as_each_forms";
    private static final String KNIVES_THROW_WITH_NO_MOB_AHEAD_FLIES_A_KNIFE =
            "knives_throw_with_no_mob_ahead_flies_a_knife";
    private static final String KNIVES_DO_NOT_TURN_TOWARD_A_MOB_THAT_MOVES =
            "knives_do_not_turn_toward_a_mob_that_moves";
    private static final String KNIVES_PASS_BETWEEN_FENCE_RAILS_AND_STOP_ON_A_RAIL =
            "knives_pass_between_fence_rails_and_stop_on_a_rail";

    private static final Identifier CRYSTAL_KNIVES = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_knives");
    /** The ticks one knife takes to form. */
    private static final int ONE_KNIFE_TICKS = 6;
    /** The ticks two knives take to form. */
    private static final int TWO_KNIVES_TICKS = 12;
    /** The ticks ten knives take to form, the cap. */
    private static final int TEN_KNIVES_TICKS = 60;
    /** Twice the ticks the cap takes, which forms no knife past it. */
    private static final int PAST_THE_CAP_TICKS = 120;
    private static final int TEN_KNIVES = 10;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_knives";
    private static final String SHOULD_FORM = "The hold should form 1 knife at 6 ticks, 10 at 60 and 10 at 120,"
            + " formed %s";
    private static final String SHOULD_END = "Letting go should leave the hold with no knives, read %s";
    private static final String SHOULD_THROW_NOTHING = "Letting go should leave no knife of the caster's in flight";
    /** Goo for more knives than the cap, so only the cap stops the paying. */
    private static final int TWELVE_PRICES = 12;
    /** Goo for two knives, so no third forms to shift the slots the throws leave from. */
    private static final int TWO_PRICES = 2;
    private static final String SHOULD_PAY = "30 ticks of hold should spend 10 prices, %s mB, spent %s mB;"
            + " letting go should return the goo to %s mB, read %s mB";
    private static final String SHOULD_FLY = "A throw with 1 knife held and no mob ahead should leave 0 held and one"
            + " knife flying along the look %s; held %s, flying %s";
    private static final String SHOULD_KEEP_ITS_PATH = "A knife thrown at a husk that stepped aside should leave it"
            + " at %s health, read %s, and end its flight, %s still flying";
    private static final String SHOULD_PASS_THE_GAP = "A knife thrown between the rails should hurt the husk behind"
            + " the fence, its health %s of %s";
    private static final String SHOULD_STOP_ON_THE_RAIL = "A knife thrown at a rail should leave the husk at %s"
            + " health, read %s";
    private static final int BAY_SIDE = 16;
    private static final BlockPos CASTER_POS = new BlockPos(8, 1, 1);
    /** A husk, which no daylight burns, so only the knives hurt it. */
    private static final BlockPos HUSK_POS = new BlockPos(8, 1, 7);
    private static final float FACING_SOUTH = 0f;
    /** Blocks the husk steps sideways the tick after the throw. */
    private static final double SIDESTEP = 3;
    /** Ticks past the throw by which a knife's flight has ended: it falls to the floor well inside them. */
    private static final int FLIGHT_TICKS = 30;
    /** The cosine a flight's heading must clear against the look to read as along it. */
    private static final double ALONG_THE_LOOK = 0.999;
    /** Where the caster stands before the fence line: in block 8's west rail arm, clear of each post. */
    private static final Vec3 FENCE_CASTER = new Vec3(8.2, 1, 1.5);
    /** The husk behind the fence, on the knives' path. */
    private static final Vec3 FENCE_HUSK = new Vec3(8.2, 1, 5.5);
    /** The fence line, joined east to west across the knives' path. */
    private static final int FENCE_Z = 4;
    private static final int FENCE_WEST = 6;
    private static final int FENCE_EAST = 10;
    private static final double PIXEL = 1 / 16.0;
    /** The middle of the gap between a fence's rails, 9 to 12 pixels up. */
    private static final double BETWEEN_THE_RAILS = 10.5 * PIXEL;
    /** The middle of a fence's lower rail, 6 to 9 pixels up. */
    private static final double ON_THE_LOWER_RAIL = 7.5 * PIXEL;
    private static final double HALF_A_BLOCK = 0.5;
    /** A knife's speed as it leaves, in blocks a tick (crystal_knives.json). */
    private static final double KNIFE_SPEED = 1.5;
    private static final String KNIVES_THROW_ONE_PER_THREE_TICKS_WHILE_LEFT_CLICK_IS_HELD =
            "knives_throw_one_per_three_ticks_while_left_click_is_held";
    private static final String KNIVES_THREE_TICKS_APART_BOTH_DAMAGE_ONE_MOB =
            "knives_three_ticks_apart_both_damage_one_mob";
    /** The read a tick past the 6th after the press, which sees the throw the 6th tick's server tick lands. */
    private static final int READ_SIX_TICKS_ON = 7;
    private static final int NINE_TICKS = 9;
    private static final int THREE_THROWN = 3;
    private static final String SHOULD_THROW_ON_THE_HOLD = "Holding left click should throw 1 knife on the press,"
            + " 3 by 6 ticks later, and none more in the 9 ticks after it lifts; thrown %s";
    /** 10 held, 3 thrown, and the 1 the clock formed 6 ticks after the first throw left a full hold. */
    private static final int HELD_BENEATH_THE_THROW = 8;
    private static final String SHOULD_CHARGE_BENEATH = "The charge should run on beneath the throw, 8 held"
            + " 6 ticks on; held %s";
    /** An unarmored pig 3 blocks ahead, in the knives' path. */
    private static final BlockPos PIG_POS = new BlockPos(8, 1, 4);
    private static final float DAMAGE_WITHIN = 1e-3f;
    private static final String SHOULD_BOTH_DAMAGE = "Two knives 3 ticks apart should take %s from the pig,"
            + " twice one knife's %s; it lost %s";
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
        GooTestFunctions.registerTest(registrar, KNIVES_CHARGE_ONE_PER_SIX_TICKS_TO_TEN,
                KnivesHoldTests::knivesChargeOnePerSixTicksToTen);
        GooTestFunctions.registerTest(registrar, FRACTIONAL_COST_BANKS_ITS_HALF,
                KnivesHoldTests::fractionalCostBanksItsHalf);
        GooTestFunctions.registerTest(registrar, KNIVES_PAY_PER_KNIFE_AS_EACH_FORMS,
                KnivesHoldTests::knivesPayPerKnifeAsEachForms);
        GooTestFunctions.registerTest(registrar, KNIVES_THROW_WITH_NO_MOB_AHEAD_FLIES_A_KNIFE,
                KnivesHoldTests::knivesThrowWithNoMobAheadFliesAKnife);
        GooTestFunctions.registerTest(registrar, KNIVES_DO_NOT_TURN_TOWARD_A_MOB_THAT_MOVES,
                KnivesHoldTests::knivesDoNotTurnTowardAMobThatMoves);
        GooTestFunctions.registerTest(registrar, KNIVES_PASS_BETWEEN_FENCE_RAILS_AND_STOP_ON_A_RAIL,
                KnivesHoldTests::knivesPassBetweenFenceRailsAndStopOnARail);
        GooTestFunctions.registerTest(registrar, KNIVES_THROW_ONE_PER_THREE_TICKS_WHILE_LEFT_CLICK_IS_HELD,
                KnivesHoldTests::knivesThrowOnePerThreeTicksWhileLeftClickIsHeld);
        GooTestFunctions.registerTest(registrar, KNIVES_THREE_TICKS_APART_BOTH_DAMAGE_ONE_MOB,
                KnivesHoldTests::knivesThreeTicksApartBothDamageOneMob);
    }

    /**
     * A mock player holding 10 knives, the charge stopped at its cap, holds
     * left click: the hold has thrown 1 knife on the press and 3 by 6 ticks
     * later, the server's tick landing the 6th tick's throw before the 7th
     * tick reads, the charge running on beneath the throw to hold 8 then;
     * once left click lifts, it throws none in the 9 ticks after.
     * decision knives-throw-one-per-two-ticks-while-left-click-is-held
     *
     * @param helper the gametest helper
     */
    public static void knivesThrowOnePerThreeTicksWhileLeftClickIsHeld(GameTestHelper helper) {
        ServerPlayer caster = casterHolding(helper, TWELVE_PRICES);
        standAt(helper, caster, Vec3.atBottomCenterOf(CASTER_POS), 0f);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        List<Integer> thrown = new ArrayList<>();
        holding(caster, true);

        helper.runAfterDelay(TEN_KNIVES_TICKS, () -> {
            pressLeftClick(caster, true);
            thrown.add(state.knifeHolds().thrownBy(caster));
        });
        int[] heldSixTicksOn = new int[1];
        helper.runAfterDelay(TEN_KNIVES_TICKS + READ_SIX_TICKS_ON, () -> {
            thrown.add(state.knifeHolds().thrownBy(caster));
            heldSixTicksOn[0] = state.knifeHolds().formed(caster);
            pressLeftClick(caster, false);
        });
        helper.runAfterDelay(TEN_KNIVES_TICKS + READ_SIX_TICKS_ON + NINE_TICKS, () -> {
            thrown.add(state.knifeHolds().thrownBy(caster));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(thrown.equals(List.of(1, THREE_THROWN, THREE_THROWN)),
                    String.format(SHOULD_THROW_ON_THE_HOLD, thrown));
            helper.assertTrue(heldSixTicksOn[0] == HELD_BENEATH_THE_THROW,
                    String.format(SHOULD_CHARGE_BENEATH, heldSixTicksOn[0]));
            helper.succeed();
        });
    }

    /**
     * A mock player holding 2 knives holds left click at an unarmored pig in
     * the path: both knives strike it 3 ticks apart, and it loses twice one
     * knife's damage, the second knife landing through the first's i-frames.
     * decision knives-throw-one-per-two-ticks-while-left-click-is-held
     *
     * @param helper the gametest helper
     */
    public static void knivesThreeTicksApartBothDamageOneMob(GameTestHelper helper) {
        layTheFloor(helper);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, PIG_POS);
        ServerPlayer caster = casterHolding(helper, TWO_PRICES);
        standAt(helper, caster, Vec3.atBottomCenterOf(CASTER_POS), 0f);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        float oneKnife = damageOf(knives(helper)).amount().evaluateFloat(Variables.NONE);
        holding(caster, true);

        helper.runAfterDelay(TWO_KNIVES_TICKS, () -> {
            aimThrough(state, caster, pig.getZ(), pig.getBoundingBox().getCenter().y);
            pressLeftClick(caster, true);
        });
        helper.runAfterDelay(TWO_KNIVES_TICKS + FLIGHT_TICKS, () -> {
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(Math.abs(lost - 2 * oneKnife) < DAMAGE_WITHIN,
                    String.format(SHOULD_BOTH_DAMAGE, 2 * oneKnife, oneKnife, lost));
            helper.succeed();
        });
    }

    // Presses or lifts left click during the caster's hold, as the client tells the server.
    private static void pressLeftClick(ServerPlayer caster, boolean down) {
        KnivesThrowHandler.leftClick(caster, down);
    }

    private static DamageStep damageOf(AbilityDefinition knives) {
        return knives.behaviors().stream().filter(DamageStep.class::isInstance).map(DamageStep.class::cast)
                .findFirst().orElseThrow();
    }

    /**
     * A mock player begins a Knives hold through the hold payload's handler:
     * the server's count reads 1 at 6 ticks, 10 at 60 and still 10 at 120, and
     * ending the hold leaves the count at 0 with no knife of its in flight.
     * decision knives-charge-one-per-three-ticks-up-to-ten
     *
     * @param helper the gametest helper
     */
    public static void knivesChargeOnePerSixTicksToTen(GameTestHelper helper) {
        ServerPlayer caster = casterHolding(helper, TWELVE_PRICES);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        List<Integer> formed = new ArrayList<>();
        holding(caster, true);

        helper.runAfterDelay(ONE_KNIFE_TICKS, () -> formed.add(state.knifeHolds().formed(caster)));
        helper.runAfterDelay(TEN_KNIVES_TICKS, () -> formed.add(state.knifeHolds().formed(caster)));
        helper.runAfterDelay(PAST_THE_CAP_TICKS, () -> {
            formed.add(state.knifeHolds().formed(caster));
            holding(caster, false);
            int afterLettingGo = state.knifeHolds().formed(caster);
            boolean flying = !headingsFrom(state, caster).isEmpty();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(formed.equals(List.of(1, TEN_KNIVES, TEN_KNIVES)), String.format(SHOULD_FORM, formed));
            helper.assertTrue(afterLettingGo == 0, String.format(SHOULD_END, afterLettingGo));
            helper.assertFalse(flying, SHOULD_THROW_NOTHING);
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
     * A mock player holding 1 knife left clicks with no mob anywhere ahead:
     * the hold holds 0 and the server flies one knife heading along the look.
     * decision knives-and-dart-function-as-projectiles
     *
     * @param helper the gametest helper
     */
    public static void knivesThrowWithNoMobAheadFliesAKnife(GameTestHelper helper) {
        layTheFloor(helper);
        ServerPlayer caster = casterHolding(helper, TWELVE_PRICES);
        standAt(helper, caster, Vec3.atBottomCenterOf(CASTER_POS), 0f);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        holding(caster, true);

        helper.runAfterDelay(ONE_KNIFE_TICKS, () -> {
            KnivesThrowHandler.throwKnife(caster);
            int held = state.knifeHolds().formed(caster);
            Vec3 look = caster.getViewVector(1f);
            List<Vec3> headings = headingsFrom(state, caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(held == 0 && headings.size() == 1
                    && headings.getFirst().normalize().dot(look) > ALONG_THE_LOOK,
                    String.format(SHOULD_FLY, look, held, headings));
            helper.succeed();
        });
    }

    /**
     * A mock player throws a knife at a husk on the look line, and the tick
     * after, the husk steps 3 blocks sideways: the knife keeps its path, its
     * flight ends, and the husk takes no damage.
     * decision knives-do-not-seek-you-just-throw-them
     *
     * @param helper the gametest helper
     */
    public static void knivesDoNotTurnTowardAMobThatMoves(GameTestHelper helper) {
        layTheFloor(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, HUSK_POS);
        ServerPlayer caster = casterHolding(helper, TWELVE_PRICES);
        standAt(helper, caster, Vec3.atBottomCenterOf(CASTER_POS), 0f);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        holding(caster, true);

        helper.runAfterDelay(ONE_KNIFE_TICKS, () -> {
            KnivesThrowHandler.throwKnife(caster);
            helper.runAfterDelay(1, () -> husk.teleportTo(husk.getX() + SIDESTEP, husk.getY(), husk.getZ()));
        });
        helper.runAfterDelay(ONE_KNIFE_TICKS + FLIGHT_TICKS, () -> {
            List<Vec3> flying = headingsFrom(state, caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth() && flying.isEmpty(),
                    String.format(SHOULD_KEEP_ITS_PATH, husk.getMaxHealth(), husk.getHealth(), flying));
            helper.succeed();
        });
    }

    /**
     * A mock player before a fence line joined across its path, a husk behind
     * it, throws one knife aimed between the rails, which hurts the husk, then
     * one aimed at the lower rail, which stops at the fence.
     * decision knives-and-dart-function-as-projectiles
     *
     * @param helper the gametest helper
     */
    public static void knivesPassBetweenFenceRailsAndStopOnARail(GameTestHelper helper) {
        layTheFloor(helper);
        for (int x = FENCE_WEST; x <= FENCE_EAST; x++) {
            helper.setBlock(new BlockPos(x, 1, FENCE_Z), Blocks.OAK_FENCE);
        }
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, HUSK_POS);
        Vec3 behind = helper.absoluteVec(FENCE_HUSK);
        husk.snapTo(behind.x, behind.y, behind.z, 0f, 0f);
        ServerPlayer caster = casterHolding(helper, TWO_PRICES);
        standAt(helper, caster, FENCE_CASTER, 0f);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        double plane = helper.absoluteVec(new Vec3(0, 0, FENCE_Z + HALF_A_BLOCK)).z;
        double fenceBase = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        float[] healthAfterTheGap = new float[1];
        holding(caster, true);

        helper.runAfterDelay(TWO_KNIVES_TICKS, () -> {
            aimThrough(state, caster, plane, fenceBase + BETWEEN_THE_RAILS);
            KnivesThrowHandler.throwKnife(caster);
        });
        helper.runAfterDelay(TWO_KNIVES_TICKS + FLIGHT_TICKS, () -> {
            healthAfterTheGap[0] = husk.getHealth();
            aimThrough(state, caster, plane, fenceBase + ON_THE_LOWER_RAIL);
            KnivesThrowHandler.throwKnife(caster);
        });
        helper.runAfterDelay(TWO_KNIVES_TICKS + 2 * FLIGHT_TICKS, () -> {
            float healthAfterTheRail = husk.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(healthAfterTheGap[0] < husk.getMaxHealth(),
                    String.format(SHOULD_PASS_THE_GAP, healthAfterTheGap[0], husk.getMaxHealth()));
            helper.assertTrue(healthAfterTheRail == healthAfterTheGap[0],
                    String.format(SHOULD_STOP_ON_THE_RAIL, healthAfterTheGap[0], healthAfterTheRail));
            helper.succeed();
        });
    }

    // Pitches the caster's look so the knife the hold formed last crosses a plane of z at a height, straight ahead.
    private static void aimThrough(GooServerState state, ServerPlayer caster, double planeZ, double height) {
        ThrowAim.aimAt(caster, new Vec3(caster.getEyePosition().x, height, planeZ), state.knifeHolds().formed(caster),
                KNIFE_SPEED);
    }

    // The headings of the caster's knives in flight.
    private static List<Vec3> headingsFrom(GooServerState state, ServerPlayer caster) {
        return state.thrownKnives().flyingFrom(caster).stream().map(ThrownKnives.Flight::velocity).toList();
    }

    private static void standAt(GameTestHelper helper, ServerPlayer caster, Vec3 relative, float pitch) {
        Vec3 stand = helper.absoluteVec(relative);
        caster.snapTo(stand.x, stand.y, stand.z, FACING_SOUTH, pitch);
        caster.setYHeadRot(FACING_SOUTH);
        caster.yHeadRotO = FACING_SOUTH;
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
        AbilityDefinition knives = knives(helper);
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
