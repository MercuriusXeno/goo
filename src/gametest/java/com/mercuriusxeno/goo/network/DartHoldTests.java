package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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

    private static final Identifier METAL_DART = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_dart");
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
            boolean flying = state.gooEffects().hasPendingFrom(caster);
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
            GooThrowHandler.execute(caster, new GooThrowPayload(GooTypes.id(GooTypes.METAL), husk.getId(),
                    BlockPos.ZERO, -1, false, METAL_DART.toString(), caster.getEyePosition()));
            boolean flying = state.gooEffects().hasPendingFrom(caster);
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
