package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
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
 * Gametests for Metal's Dart hold: holding right click forms one javelin per
 * 10 ticks up to 3 on the server's clock, letting go reverts them all and
 * spends nothing, and a throw before the first javelin forms is refused.
 * decision dart-stacks-three-javelins-one-per-ten-ticks
 * decision dart-forms-on-right-click-and-throws-on-left-click
 */
@RegistersGameTests
public final class DartHoldTests {
    private static final String DART_STACKS_ONE_JAVELIN_PER_TEN_TICKS_TO_THREE =
            "dart_stacks_one_javelin_per_ten_ticks_to_three";
    private static final String DART_RELEASE_REVERTS_THE_STACK_AND_SPENDS_NOTHING =
            "dart_release_reverts_the_stack_and_spends_nothing";
    private static final String DART_THROW_BEFORE_A_JAVELIN_FORMS_IS_REFUSED =
            "dart_throw_before_a_javelin_forms_is_refused";

    private static final String DART_THROW_WITH_NO_MOB_AHEAD_FLIES_A_JAVELIN =
            "dart_throw_with_no_mob_ahead_flies_a_javelin";
    private static final String DART_DOES_NOT_FOLLOW_A_MOB_THAT_MOVES = "dart_does_not_follow_a_mob_that_moves";
    private static final String DART_PASSES_BETWEEN_FENCE_RAILS = "dart_passes_between_fence_rails";
    private static final String DART_THROWS_ONE_JAVELIN_PER_FIVE_TICKS_WHILE_LEFT_CLICK_IS_HELD =
            "dart_throws_one_javelin_per_five_ticks_while_left_click_is_held";
    /** The read a tick past the 5th after the press, which sees the throw the 5th tick's server tick lands. */
    private static final int READ_FIVE_TICKS_ON = 6;
    private static final int TEN_TICKS = 10;
    private static final String SHOULD_THROW_ON_THE_HOLD = "Holding left click should throw 1 javelin on the press,"
            + " 2 by 5 ticks later, and none more in the 10 ticks after it lifts, thrown %s, spending %s mB, spent %s";
    /** A javelin's speed as it leaves, in blocks a tick (metal_dart.json). */
    private static final double JAVELIN_SPEED = 2.5;
    private static final double SPEED_WITHIN = 1e-6;
    /** The cosine a flight's heading must clear against the look to read as along it. */
    private static final double ALONG_THE_LOOK = 0.999;
    private static final int BAY_SIDE = 16;
    /** Where a husk or pig spawns, ahead of the caster on the look line. */
    private static final BlockPos FAR_AHEAD = new BlockPos(8, 1, 7);
    /** Blocks the husk steps sideways the tick after the throw. */
    private static final double SIDESTEP = 3;
    /** Ticks past the throw by which a javelin's flight has ended. */
    private static final int FLIGHT_TICKS = 30;
    /** Where the caster stands before the fence line: in block 8's west rail arm, clear of each post. */
    private static final Vec3 FENCE_CASTER = new Vec3(8.3, 1, 1.5);
    /** The pig behind the fence, on the javelin's path. */
    private static final Vec3 FENCE_PIG = new Vec3(8.1, 1, 5.5);
    private static final int FENCE_Z = 4;
    private static final int FENCE_WEST = 6;
    private static final int FENCE_EAST = 10;
    /** The middle of the gap between a fence's rails, 9 to 12 pixels up. */
    private static final double BETWEEN_THE_RAILS = 10.5 / 16.0;
    private static final double HALF_A_BLOCK = 0.5;
    private static final float DAMAGE_WITHIN = 1e-3f;
    private static final String SHOULD_FLY = "A throw with 1 javelin held should fly one javelin, flying %s";
    private static final String SHOULD_LEAVE_AT_SPEED = "The javelin should leave at 2.5 a tick along the look %s,"
            + " left at %s, and drop below its launch line two ticks on, at %s against %s";
    private static final String SHOULD_KEEP_ITS_PATH = "A javelin thrown at a husk that stepped aside should leave it"
            + " at %s health, read %s, its flight ended, still flying %s";
    private static final String SHOULD_PASS_THE_GAP = "A javelin thrown between the rails should take Dart's %s from"
            + " the pig behind the fence, took %s";
    private static final Identifier METAL_DART =Identifier.fromNamespaceAndPath(Goo.MODID, "metal_dart");
    private static final String ABILITY_REQUIRED = "Ability registry must hold metal_dart";
    /** The ticks one javelin takes to form. */
    private static final int ONE_JAVELIN_TICKS = 10;
    /** The ticks three javelins take to form, the cap. */
    private static final int THREE_JAVELINS_TICKS = 30;
    /** Twice the ticks the cap takes, which forms no javelin past it. */
    private static final int PAST_THE_CAP_TICKS = 60;
    private static final int THREE_JAVELINS = 3;
    /** Ticks into a hold before its first javelin forms. */
    private static final int HALF_A_JAVELIN_TICKS = 5;
    /** Goo for more throws than a hold could take. */
    private static final int PRICES = 5;
    private static final BlockPos CASTER_POS = new BlockPos(8, 1, 1);
    /** Where a husk stands ahead of the caster, which a throw before any javelin forms aims at. */
    private static final BlockPos AHEAD = new BlockPos(8, 1, 6);
    private static final String SHOULD_STACK = "The hold should form 1 javelin at 10 ticks, 3 at 30 and 3 at 60,"
            + " formed %s";
    private static final String SHOULD_REVERT = "A hold of 3 javelins let go should have formed 3, read %s, throw"
            + " nothing, %s in flight, and leave the goo at %s mB, read %s mB";
    private static final String SHOULD_REFUSE = "A throw 5 ticks into the hold should throw nothing, %s in flight,"
            + " and leave the goo at %s mB, read %s mB";

    private DartHoldTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DART_STACKS_ONE_JAVELIN_PER_TEN_TICKS_TO_THREE,
                DartHoldTests::dartStacksOneJavelinPerTenTicksToThree);
        GooTestFunctions.registerTest(registrar, DART_RELEASE_REVERTS_THE_STACK_AND_SPENDS_NOTHING,
                DartHoldTests::dartReleaseRevertsTheStackAndSpendsNothing);
        GooTestFunctions.registerTest(registrar, DART_THROW_BEFORE_A_JAVELIN_FORMS_IS_REFUSED,
                DartHoldTests::dartThrowBeforeAJavelinFormsIsRefused);
        GooTestFunctions.registerTest(registrar, DART_THROW_WITH_NO_MOB_AHEAD_FLIES_A_JAVELIN,
                DartHoldTests::dartThrowWithNoMobAheadFliesAJavelin);
        GooTestFunctions.registerTest(registrar, DART_DOES_NOT_FOLLOW_A_MOB_THAT_MOVES,
                DartHoldTests::dartDoesNotFollowAMobThatMoves);
        GooTestFunctions.registerTest(registrar, DART_PASSES_BETWEEN_FENCE_RAILS,
                DartHoldTests::dartPassesBetweenFenceRails);
        GooTestFunctions.registerTest(registrar, DART_THROWS_ONE_JAVELIN_PER_FIVE_TICKS_WHILE_LEFT_CLICK_IS_HELD,
                DartHoldTests::dartThrowsOneJavelinPerFiveTicksWhileLeftClickIsHeld);
    }

    /**
     * A mock player holding 3 javelins holds left click: the hold has thrown
     * 1 javelin on the press and 2 by 5 ticks later, the server's tick
     * landing the 5th tick's throw before the 6th tick reads; once left
     * click lifts, it throws none in the 10 ticks after, and the goo spent is
     * twice Dart's cost, one cost a javelin thrown.
     * decision dart-throws-one-javelin-per-five-ticks-while-left-click-is-held
     *
     * @param helper the gametest helper
     */
    public static void dartThrowsOneJavelinPerFiveTicksWhileLeftClickIsHeld(GameTestHelper helper) {
        ServerPlayer caster = casterHolding(helper);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        int price = dart(helper).cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
        int start = metalHeld(caster);
        List<Integer> thrown = new ArrayList<>();
        holding(caster, true);

        helper.runAfterDelay(THREE_JAVELINS_TICKS, () -> {
            KnivesThrowHandler.leftClick(caster, true);
            thrown.add(state.knifeHolds().thrownBy(caster));
        });
        helper.runAfterDelay(THREE_JAVELINS_TICKS + READ_FIVE_TICKS_ON, () -> {
            thrown.add(state.knifeHolds().thrownBy(caster));
            KnivesThrowHandler.leftClick(caster, false);
        });
        helper.runAfterDelay(THREE_JAVELINS_TICKS + READ_FIVE_TICKS_ON + TEN_TICKS, () -> {
            thrown.add(state.knifeHolds().thrownBy(caster));
            int spent = start - metalHeld(caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(thrown.equals(List.of(1, 2, 2)) && spent == 2 * price,
                    String.format(SHOULD_THROW_ON_THE_HOLD, thrown, 2 * price, spent));
            helper.succeed();
        });
    }

    /**
     * A mock player holding 1 javelin throws with no mob ahead: one javelin
     * flies, leaving along the look at 2.5 blocks a tick, and two ticks on it
     * stands lower than the straight line of its launch would put it.
     * decision knives-and-javelins-drop-like-an-arrow
     *
     * @param helper the gametest helper
     */
    public static void dartThrowWithNoMobAheadFliesAJavelin(GameTestHelper helper) {
        layTheFloor(helper);
        ServerPlayer caster = casterHolding(helper);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        ThrownKnives.Flight[] launched = new ThrownKnives.Flight[1];
        holding(caster, true);

        helper.runAfterDelay(ONE_JAVELIN_TICKS, () -> {
            throwDart(caster);
            List<ThrownKnives.Flight> flying = state.thrownKnives().flyingFrom(caster);
            launched[0] = flying.size() == 1 ? flying.getFirst() : null;
        });
        helper.runAfterDelay(ONE_JAVELIN_TICKS + 2, () -> {
            List<ThrownKnives.Flight> flying = state.thrownKnives().flyingFrom(caster);
            Vec3 look = caster.getViewVector(1f);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(launched[0] != null && flying.size() == 1, String.format(SHOULD_FLY, flying));
            Vec3 start = launched[0].position();
            Vec3 velocity = launched[0].velocity();
            double straight = start.add(velocity.scale(2)).y;
            helper.assertTrue(Math.abs(velocity.length() - JAVELIN_SPEED) < SPEED_WITHIN
                    && velocity.normalize().dot(look) > ALONG_THE_LOOK
                    && flying.getFirst().position().y < straight - SPEED_WITHIN,
                    String.format(SHOULD_LEAVE_AT_SPEED, velocity, look, flying.getFirst().position().y, straight));
            helper.succeed();
        });
    }

    /**
     * A mock player throws a javelin at a husk on the look line, and the tick
     * after, the husk steps 3 blocks sideways: the javelin keeps its path and
     * the husk takes no damage.
     * decision dart-does-not-seek-or-highlight
     *
     * @param helper the gametest helper
     */
    public static void dartDoesNotFollowAMobThatMoves(GameTestHelper helper) {
        layTheFloor(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR_AHEAD);
        ServerPlayer caster = casterHolding(helper);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        holding(caster, true);

        helper.runAfterDelay(ONE_JAVELIN_TICKS, () -> {
            ThrowAim.aimAt(caster, husk.getBoundingBox().getCenter(), 1, JAVELIN_SPEED);
            throwDart(caster);
            helper.runAfterDelay(1, () -> husk.teleportTo(husk.getX() + SIDESTEP, husk.getY(), husk.getZ()));
        });
        helper.runAfterDelay(ONE_JAVELIN_TICKS + FLIGHT_TICKS, () -> {
            boolean flying = !state.thrownKnives().flyingFrom(caster).isEmpty();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(husk.getHealth() == husk.getMaxHealth() && !flying,
                    String.format(SHOULD_KEEP_ITS_PATH, husk.getMaxHealth(), husk.getHealth(), flying));
            helper.succeed();
        });
    }

    /**
     * A mock player before a fence line joined across its path, an unarmored
     * pig behind it, throws a javelin aimed between the rails: the pig loses
     * Dart's damage.
     * decision knives-and-dart-function-as-projectiles
     *
     * @param helper the gametest helper
     */
    public static void dartPassesBetweenFenceRails(GameTestHelper helper) {
        layTheFloor(helper);
        for (int x = FENCE_WEST; x <= FENCE_EAST; x++) {
            helper.setBlock(new BlockPos(x, 1, FENCE_Z), Blocks.OAK_FENCE);
        }
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, FAR_AHEAD);
        Vec3 behind = helper.absoluteVec(FENCE_PIG);
        pig.snapTo(behind.x, behind.y, behind.z, 0f, 0f);
        ServerPlayer caster = casterHolding(helper);
        Vec3 stand = helper.absoluteVec(FENCE_CASTER);
        caster.snapTo(stand.x, stand.y, stand.z, 0f, 0f);
        float dartDamage = damageOf(dart(helper));
        holding(caster, true);

        helper.runAfterDelay(ONE_JAVELIN_TICKS, () -> {
            Vec3 gap = helper.absoluteVec(new Vec3(FENCE_CASTER.x, 1 + BETWEEN_THE_RAILS, FENCE_Z + HALF_A_BLOCK));
            ThrowAim.aimAt(caster, gap, 1, JAVELIN_SPEED);
            throwDart(caster);
        });
        helper.runAfterDelay(ONE_JAVELIN_TICKS + FLIGHT_TICKS, () -> {
            float lost = pig.getMaxHealth() - pig.getHealth();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(Math.abs(lost - dartDamage) < DAMAGE_WITHIN,
                    String.format(SHOULD_PASS_THE_GAP, dartDamage, lost));
            helper.succeed();
        });
    }

    // Throws a javelin of the caster's hold as the client throws it: left click goes down and lifts.
    private static void throwDart(ServerPlayer caster) {
        KnivesThrowHandler.leftClick(caster, true);
        KnivesThrowHandler.leftClick(caster, false);
    }

    private static AbilityDefinition dart(GameTestHelper helper) {
        AbilityDefinition dart = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_DART);
        helper.assertTrue(dart != null, ABILITY_REQUIRED);
        return dart;
    }

    private static float damageOf(AbilityDefinition dart) {
        return dart.behaviors().stream().filter(DamageStep.class::isInstance).map(DamageStep.class::cast)
                .findFirst().orElseThrow().amount().evaluateFloat(Variables.NONE);
    }

    private static void layTheFloor(GameTestHelper helper) {
        for (int x = 0; x < BAY_SIDE; x++) {
            for (int z = 0; z < BAY_SIDE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /**
     * A mock player holds Dart: the server's count reads 1 javelin at 10
     * ticks, 3 at 30 and still 3 at 60.
     * decision dart-stacks-three-javelins-one-per-ten-ticks
     *
     * @param helper the gametest helper
     */
    public static void dartStacksOneJavelinPerTenTicksToThree(GameTestHelper helper) {
        ServerPlayer caster = casterHolding(helper);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        List<Integer> formed = new ArrayList<>();
        holding(caster, true);

        helper.runAfterDelay(ONE_JAVELIN_TICKS, () -> formed.add(state.knifeHolds().formed(caster)));
        helper.runAfterDelay(THREE_JAVELINS_TICKS, () -> formed.add(state.knifeHolds().formed(caster)));
        helper.runAfterDelay(PAST_THE_CAP_TICKS, () -> {
            formed.add(state.knifeHolds().formed(caster));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(formed.equals(List.of(1, THREE_JAVELINS, THREE_JAVELINS)),
                    String.format(SHOULD_STACK, formed));
            helper.succeed();
        });
    }

    /**
     * A mock player forms 3 javelins and lets go: nothing is thrown and the
     * goo reads what it read before the hold.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param helper the gametest helper
     */
    public static void dartReleaseRevertsTheStackAndSpendsNothing(GameTestHelper helper) {
        ServerPlayer caster = casterHolding(helper);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        int start = metalHeld(caster);
        holding(caster, true);

        helper.runAfterDelay(THREE_JAVELINS_TICKS, () -> {
            int formed = state.knifeHolds().formed(caster);
            holding(caster, false);
            boolean flying = !state.thrownKnives().flyingFrom(caster).isEmpty();
            int end = metalHeld(caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(formed == THREE_JAVELINS && !flying && end == start,
                    String.format(SHOULD_REVERT, formed, flying, start, end));
            helper.succeed();
        });
    }

    /**
     * A mock player throws 5 ticks into a Dart hold, before its first javelin
     * forms: the server throws nothing and spends nothing.
     * decision dart-stacks-three-javelins-one-per-ten-ticks
     *
     * @param helper the gametest helper
     */
    public static void dartThrowBeforeAJavelinFormsIsRefused(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, AHEAD);
        ServerPlayer caster = casterHolding(helper);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        int start = metalHeld(caster);
        holding(caster, true);

        helper.runAfterDelay(HALF_A_JAVELIN_TICKS, () -> {
            throwDart(caster);
            boolean flying = !state.thrownKnives().flyingFrom(caster).isEmpty();
            int end = metalHeld(caster);
            holding(caster, false);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(!flying && end == start, String.format(SHOULD_REFUSE, flying, start, end));
            helper.succeed();
        });
    }

    // A mock player standing in the bay with a glove in hand, Dart's requires taught and metal goo for some throws.
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer casterHolding(GameTestHelper helper) {
        AbilityDefinition dart = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_DART);
        helper.assertTrue(dart != null, ABILITY_REQUIRED);
        int price = dart.cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, 0f, 0f);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.METAL, PRICES * price));
        KnownRecipes.teachRequires(caster, dart);
        return caster;
    }

    private static void holding(ServerPlayer caster, boolean holding) {
        KnivesHoldHandler.hold(caster, new KnivesHoldPayload(holding, GooTypes.id(GooTypes.METAL),
                METAL_DART.toString()));
    }

    private static int metalHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.METAL, 0);
    }
}
