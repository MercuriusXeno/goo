package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for a downed mob raised by a heal: a heal, Jelly's Heal stream
 * among them, takes the fall off it with a small hop, it outlives the death
 * span, and once up it takes hits and can be targeted again
 * (decision healed-downed-mob-hops-back-up).
 */
@RegistersGameTests
public final class DownedRaiseTests {

    private static final String HEAL_RAISES_THE_COW = "downed_heal_raises_the_cow_with_a_hop";
    private static final String JELLY_HEAL_RAISES_THE_COW = "downed_jelly_heal_raises_the_cow_with_a_hop";
    private static final String RAISED_COW_IS_FAIR_GAME = "downed_raised_cow_takes_hits_and_aims";

    /** A heal well short of a cow's health, so the raise, not a full mend, is what is seen. */
    private static final float HEAL = 4f;
    /** A hit too light to down the raised cow again. */
    private static final float LIGHT_HIT = 0.5f;
    /** Past the hurt frames the downing blow leaves, inside the 5 second immunity. */
    private static final int INSIDE_THE_WINDOW_TICKS = 40;
    /** West of the downed cow, two blocks off, where the healer stands facing it. */
    private static final BlockPos HEALER_POS = new BlockPos(0, 1, 3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward a cow's middle two blocks off. */
    private static final float LOOKING_AT_THE_COW = 20f;
    private static final int HELD_GOO = 6;
    private static final Identifier JELLY_HEAL = Identifier.parse("goo:jelly_heal");

    private static final String HEAL_REQUIRED = "Ability registry must hold jelly_heal";
    private static final String STILL_DOWNED = "The healed cow should hold no DOWNED";
    private static final String NO_HOP = "The healed cow should rise on the tick of the heal, moves %s";
    private static final String DIED_RAISED = "The raised cow should outlive the 15 second death span";
    private static final String UNHURT = "An attack on the raised cow should lower its health from %s, reads %s";
    private static final String UNAIMED = "A zombie should take the raised cow as its target";

    private DownedRaiseTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HEAL_RAISES_THE_COW,
                helper -> healRaisesTheCow(helper, cow -> cow.heal(HEAL)));
        GooTestFunctions.registerTest(registrar, JELLY_HEAL_RAISES_THE_COW,
                helper -> healRaisesTheCow(helper, streamedHeal(helper)));
        GooTestFunctions.registerTest(registrar, RAISED_COW_IS_FAIR_GAME, DownedRaiseTests::raisedCowIsFairGame);
    }

    /**
     * A downed cow healed holds no DOWNED, moves upward on the tick of the
     * heal and is alive past the death span from going down.
     *
     * @param helper the gametest helper
     * @param heal   the heal the cow takes
     */
    public static void healRaisesTheCow(GameTestHelper helper, Consumer<Cow> heal) {
        Zombie zombie = DownedTests.attacker(helper);
        Cow cow = DownedTests.ralliedCow(helper);
        DownedTests.strike(helper, cow, zombie);
        helper.runAfterDelay(1, () -> {
            heal.accept(cow);
            helper.assertFalse(cow.hasData(GooAttachments.DOWNED), STILL_DOWNED);
            helper.assertTrue(cow.getDeltaMovement().y > 0, String.format(NO_HOP, cow.getDeltaMovement()));
        });
        helper.runAfterDelay(DownedTests.PAST_THE_DEATH_TICKS, () -> {
            helper.assertTrue(cow.isAlive(), DIED_RAISED);
            helper.succeed();
        });
    }

    /**
     * A downed cow raised inside its 5 second window takes an attack's
     * damage, and a zombie takes it as a target.
     *
     * @param helper the gametest helper
     */
    public static void raisedCowIsFairGame(GameTestHelper helper) {
        Zombie zombie = DownedTests.attacker(helper);
        Cow cow = DownedTests.ralliedCow(helper);
        DownedTests.strike(helper, cow, zombie);
        helper.runAfterDelay(INSIDE_THE_WINDOW_TICKS, () -> {
            cow.heal(HEAL);
            float raised = cow.getHealth();
            cow.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), LIGHT_HIT);
            helper.assertTrue(cow.getHealth() < raised, String.format(UNHURT, raised, cow.getHealth()));
            zombie.setTarget(cow);
            helper.assertTrue(zombie.getTarget() == cow, UNAIMED);
            helper.succeed();
        });
    }

    /**
     * One tick of Jelly's Heal stream, from a healer standing west of the
     * cow and facing it, laid on the cow when called.
     *
     * @param helper the gametest helper
     * @return the heal
     */
    private static Consumer<Cow> streamedHeal(GameTestHelper helper) {
        return cow -> {
            ServerPlayer healer = healer(helper);
            GooStreamHandler.streamTick(healer, GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY),
                    JELLY_HEAL.toString(), healer.getEyePosition(), healer.getEyePosition()));
            helper.getLevel().getServer().getPlayerList().remove(healer);
        };
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer healer(GameTestHelper helper) {
        AbilityDefinition heal = AbilityRegistry.of(helper.getLevel()).getAbility(JELLY_HEAL);
        helper.assertTrue(heal != null, HEAL_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(HEALER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_THE_COW);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.JELLY, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, heal);
        return player;
    }
}
