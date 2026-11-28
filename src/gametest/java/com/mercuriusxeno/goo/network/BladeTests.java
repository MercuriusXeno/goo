package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.BladeStep;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for Osseous's Blade: a player holding right click through Blade's form time holds a bone shotel, each
 * strike landed with it hurts the mob and drains the shotel's per-strike Osseous, and a strike with too little
 * Osseous left dissolves the shotel.
 * decision blade-forms-a-bone-shotel-in-hand
 */
@RegistersGameTests
public final class BladeTests {
    private static final String BLADE_STRIKE_DRAINS_OSSEOUS_AND_HURTS_A_COW =
            "blade_strike_drains_osseous_and_hurts_a_cow";
    private static final String BLADE_DISSOLVES_ON_A_STRIKE_SHORT_OF_OSSEOUS =
            "blade_dissolves_on_a_strike_short_of_osseous";
    private static final String BLADE_REFUSES_ANOTHER_CAST_WHILE_IT_STANDS =
            "blade_refuses_another_cast_while_it_stands";
    private static final String BLADE_DISSOLVES_ON_THE_NEXT_RIGHT_CLICK = "blade_dissolves_on_the_next_right_click";

    private static final Identifier OSSEOUS_BLADE = Identifier.fromNamespaceAndPath(Goo.MODID, "osseous_blade");
    private static final Identifier OSSEOUS_SPEAR = Identifier.fromNamespaceAndPath(Goo.MODID, "osseous_spear");
    private static final BlockPos COW_POS = new BlockPos(1, 1, 2);

    private static final String NOT_LATCHED = "Blade held through its form time should latch a shotel";
    private static final String NOT_HURT = "A shotel strike should hurt the cow: health %s before, %s after";
    private static final String NOT_DRAINED = "A shotel strike should drain %d Osseous: %d before, %d after";
    private static final String STILL_STANDS = "A strike with %d Osseous, under the drain %d, should dissolve the"
            + " shotel";
    private static final String CAST_ANOTHER = "A hand holding the shotel should cast nothing else: Spear's hold"
            + " began %s";
    private static final String STANDS_PAST_THE_CLICK = "Blade's next right click should dissolve the shotel, and"
            + " it stands";

    private BladeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLADE_STRIKE_DRAINS_OSSEOUS_AND_HURTS_A_COW,
                BladeTests::bladeStrikeDrainsOsseousAndHurtsACow);
        GooTestFunctions.registerTest(registrar, BLADE_DISSOLVES_ON_A_STRIKE_SHORT_OF_OSSEOUS,
                BladeTests::bladeDissolvesOnAStrikeShortOfOsseous);
        GooTestFunctions.registerTest(registrar, BLADE_REFUSES_ANOTHER_CAST_WHILE_IT_STANDS,
                BladeTests::bladeRefusesAnotherCastWhileItStands);
        GooTestFunctions.registerTest(registrar, BLADE_DISSOLVES_ON_THE_NEXT_RIGHT_CLICK,
                BladeTests::bladeDissolvesOnTheNextRightClick);
    }

    /**
     * A hand holding the shotel casts nothing else: Spear's hold, sent through the server's hold handler by a player
     * who knows Spear and holds its goo, begins no hold.
     *
     * @param helper the gametest helper
     */
    public static void bladeRefusesAnotherCastWhileItStands(GameTestHelper helper) {
        ServerPlayer player = bladed(helper);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(helper.getLevel()).getAbility(OSSEOUS_SPEAR));

        KnivesHoldHandler.hold(player, new KnivesHoldPayload(true, GooTypes.id(GooTypes.OSSEOUS),
                OSSEOUS_SPEAR.toString()));

        Optional<KnifeHolds.Held> spear = GooServerState.of(helper.getLevel().getServer()).knifeHolds().heldBy(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(spear.isEmpty(), String.format(CAST_ANOTHER, spear.map(held -> held.ability().id())));
        helper.succeed();
    }

    /**
     * Blade's next right click, held through its form time and let go through the server's charge handler as the
     * first was, dissolves the shotel and forms no other.
     *
     * @param helper the gametest helper
     */
    public static void bladeDissolvesOnTheNextRightClick(GameTestHelper helper) {
        ServerPlayer player = bladed(helper);

        releaseBlade(helper, player);

        boolean stands = blades(helper).bladeOf(player.getUUID()).isPresent();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(stands, STANDS_PAST_THE_CLICK);
        helper.succeed();
    }

    /**
     * A strike landed with the shotel hurts the cow and drains the per-strike Osseous, and the shotel stands.
     *
     * @param helper the gametest helper
     */
    public static void bladeStrikeDrainsOsseousAndHurtsACow(GameTestHelper helper) {
        ServerPlayer player = bladed(helper);
        int drain = drainOf(helper);
        Cow cow = helper.spawn(EntityTypes.COW, COW_POS);
        float healthBefore = cow.getHealth();
        int osseousBefore = osseousOf(player);

        player.attack(cow);

        float healthAfter = cow.getHealth();
        int osseousAfter = osseousOf(player);
        boolean stands = blades(helper).bladeOf(player.getUUID()).isPresent();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(healthAfter < healthBefore, String.format(NOT_HURT, healthBefore, healthAfter));
        helper.assertTrue(osseousBefore - osseousAfter == drain,
                String.format(NOT_DRAINED, drain, osseousBefore, osseousAfter));
        helper.assertTrue(stands, NOT_LATCHED);
        helper.succeed();
    }

    /**
     * A strike with less Osseous left than the drain dissolves the shotel.
     *
     * @param helper the gametest helper
     */
    public static void bladeDissolvesOnAStrikeShortOfOsseous(GameTestHelper helper) {
        ServerPlayer player = bladed(helper);
        int drain = drainOf(helper);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        int underTheDrain = drain - 1;
        player.getInventory().add(GooStacks.createForOutput(GooTypes.OSSEOUS, underTheDrain));
        Cow cow = helper.spawn(EntityTypes.COW, COW_POS);

        player.attack(cow);

        boolean stands = blades(helper).bladeOf(player.getUUID()).isPresent();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(stands, String.format(STILL_STANDS, underTheDrain, drain));
        helper.succeed();
    }

    /**
     * A survival player holding Blade's glove, right click held through Blade's form time and let go.
     *
     * @param helper the gametest helper
     * @return the player, a shotel latched in its hand
     */
    private static ServerPlayer bladed(GameTestHelper helper) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.OSSEOUS, OSSEOUS_BLADE);
        // the mock player helper makes a creative player, whom the game treats apart in combat
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        KnownRecipes.teachRequires(player, AbilityRegistry.of(helper.getLevel()).getAbility(OSSEOUS_BLADE));
        releaseBlade(helper, player);
        helper.assertTrue(blades(helper).bladeOf(player.getUUID()).isPresent(), NOT_LATCHED);
        return player;
    }

    // Blade's right click held through its form time and let go, through the server's charge handler.
    private static void releaseBlade(GameTestHelper helper, ServerPlayer player) {
        AbilityDefinition blade = AbilityRegistry.of(helper.getLevel()).getAbility(OSSEOUS_BLADE);
        GooThrowHandler.releaseCharge(player, new GooChargePayload(GooTypes.id(GooTypes.OSSEOUS),
                OSSEOUS_BLADE.toString(), blade.delivery().chargeTicks()));
    }

    private static int drainOf(GameTestHelper helper) {
        AbilityDefinition blade = AbilityRegistry.of(helper.getLevel()).getAbility(OSSEOUS_BLADE);
        return BladeStep.in(blade.behaviors()).orElseThrow().drain();
    }

    private static BoneBlades blades(GameTestHelper helper) {
        return GooServerState.of(helper.getLevel().getServer()).boneBlades();
    }

    private static int osseousOf(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.OSSEOUS, 0);
    }
}
