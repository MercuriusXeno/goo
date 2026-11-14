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
 * Gametests for a downed mob healed back from its negative health: a heal
 * short of 0 leaves it down with the count raised, a heal past 0 takes the
 * fall off it with a small hop at the health healed past 0, Jelly's Heal
 * stream counts it up heal by heal, and once up it takes hits and can be
 * targeted again
 * (decisions downed-mob-revives-only-healed-past-zero, healed-downed-mob-hops-back-up).
 */
@RegistersGameTests
public final class DownedRaiseTests {

    private static final String HEAL_SHORT_OF_ZERO_LEAVES_IT_DOWN = "downed_heal_short_of_zero_leaves_it_down";
    private static final String HEAL_PAST_ZERO_RAISES_THE_COW = "downed_heal_past_zero_raises_the_cow_with_a_hop";
    private static final String JELLY_HEAL_COUNTS_THE_COW_UP = "downed_jelly_heal_counts_the_cow_up_and_raises_it";
    private static final String RAISED_COW_IS_FAIR_GAME = "downed_raised_cow_takes_hits_and_aims";

    /** The health the cow stands at when the downing blow lands. */
    private static final float HEALTH_BEFORE_THE_BLOW = 4f;
    /** A blow leaving the cow at minus 3. */
    private static final float BLOW_PAST_ITS_HEALTH = 7f;
    /** The negative health the blow leaves. */
    private static final float DOWNED_COUNT = HEALTH_BEFORE_THE_BLOW - BLOW_PAST_ITS_HEALTH;
    /** A heal short of the count, leaving it at minus 2. */
    private static final float SHORT_HEAL = 1f;
    /** A heal past the count, standing the cow at 1 health. */
    private static final float PAST_ZERO_HEAL = 4f;
    /** The health the stream test's cow stands at when the downing blow lands. */
    private static final float STREAM_HEALTH_BEFORE_THE_BLOW = 2f;
    /**
     * A blow leaving the stream test's cow at minus 1 and a half: deeper
     * than one heal of either the per-tick stream or a pulse of 1, so the
     * first heal leaves it down.
     */
    private static final float STREAM_BLOW = 3.5f;
    /** A hit too light to down the raised cow again. */
    private static final float LIGHT_HIT = 0.5f;
    /** Past the hurt frames the downing blow leaves. */
    private static final int RAISED_AFTER_TICKS = 40;
    /** West of the downed cow, two blocks off, where the healer stands facing it. */
    private static final BlockPos HEALER_POS = new BlockPos(0, 1, 3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward a cow's middle two blocks off. */
    private static final float LOOKING_AT_THE_COW = 20f;
    private static final int HELD_GOO = 6;
    private static final Identifier JELLY_HEAL = Identifier.parse("goo:jelly_heal");

    private static final String HEAL_REQUIRED = "Ability registry must hold jelly_heal";
    private static final String NOT_DOWNED = "The cow should hold DOWNED after the blow";
    private static final String RAISED_SHORT = "A heal short of 0 should leave the cow holding DOWNED";
    private static final String WRONG_COUNT = "The cow's negative health should read %s, reads %s";
    private static final String STILL_DOWNED = "The cow healed past 0 should hold no DOWNED";
    private static final String NO_HOP = "The healed cow should rise on the tick of the heal, moves %s";
    private static final String WRONG_HEALTH = "The raised cow should stand at %s health, reads %s";
    private static final String DIED_RAISED = "The raised cow should outlive the 15 second death span";
    private static final String FIRST_HEAL_RAISED = "The stream's first heal should leave the cow holding DOWNED";
    private static final String FIRST_HEAL_UNCOUNTED = "The stream's first heal should raise the count from %s, reads %s";
    private static final String NO_FIRST_HEAL = "The stream should land a heal on the cow before it stands";
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
        GooTestFunctions.registerTest(registrar, HEAL_SHORT_OF_ZERO_LEAVES_IT_DOWN,
                DownedRaiseTests::healShortOfZeroLeavesItDown);
        GooTestFunctions.registerTest(registrar, HEAL_PAST_ZERO_RAISES_THE_COW,
                DownedRaiseTests::healPastZeroRaisesTheCow);
        GooTestFunctions.registerTest(registrar, JELLY_HEAL_COUNTS_THE_COW_UP,
                DownedRaiseTests::jellyHealCountsTheCowUp);
        GooTestFunctions.registerTest(registrar, RAISED_COW_IS_FAIR_GAME, DownedRaiseTests::raisedCowIsFairGame);
    }

    /**
     * A cow downed to minus 3 and healed by 1 holds DOWNED at minus 2.
     *
     * @param helper the gametest helper
     */
    public static void healShortOfZeroLeavesItDown(GameTestHelper helper) {
        Cow cow = downedCow(helper, HEALTH_BEFORE_THE_BLOW, BLOW_PAST_ITS_HEALTH);
        cow.heal(SHORT_HEAL);
        helper.assertTrue(cow.hasData(GooAttachments.DOWNED), RAISED_SHORT);
        float expected = DOWNED_COUNT + SHORT_HEAL;
        float count = cow.getData(GooAttachments.DOWNED).negativeHealth();
        helper.assertTrue(count == expected, String.format(WRONG_COUNT, expected, count));
        helper.succeed();
    }

    /**
     * A cow downed to minus 3 and healed by 4 holds no DOWNED, moves upward
     * on the tick of the heal, stands at 1 health and is alive past the
     * death span from going down.
     *
     * @param helper the gametest helper
     */
    public static void healPastZeroRaisesTheCow(GameTestHelper helper) {
        Cow cow = downedCow(helper, HEALTH_BEFORE_THE_BLOW, BLOW_PAST_ITS_HEALTH);
        helper.runAfterDelay(1, () -> {
            cow.heal(PAST_ZERO_HEAL);
            helper.assertFalse(cow.hasData(GooAttachments.DOWNED), STILL_DOWNED);
            helper.assertTrue(cow.getDeltaMovement().y > 0, String.format(NO_HOP, cow.getDeltaMovement()));
            float expected = PAST_ZERO_HEAL + DOWNED_COUNT;
            helper.assertTrue(cow.getHealth() == expected, String.format(WRONG_HEALTH, expected, cow.getHealth()));
        });
        helper.runAfterDelay(DownedTests.PAST_THE_DEATH_TICKS, () -> {
            helper.assertTrue(cow.isAlive(), DIED_RAISED);
            helper.succeed();
        });
    }

    /**
     * Jelly's Heal stream held on a cow downed to minus 1 and a half: the
     * first heal that lands leaves it holding DOWNED with the count raised,
     * and the stream stands it up once the heals landed carry the count
     * past 0. Counted by heals landed, whatever the stream's cadence.
     *
     * @param helper the gametest helper
     */
    public static void jellyHealCountsTheCowUp(GameTestHelper helper) {
        Cow cow = downedCow(helper, STREAM_HEALTH_BEFORE_THE_BLOW, STREAM_BLOW);
        // the blow's knockback would slide the cow out of the cone before the stream counts it past 0
        cow.setDeltaMovement(Vec3.ZERO);
        ServerPlayer healer = healer(helper);
        float[] lastCount = {cow.getData(GooAttachments.DOWNED).negativeHealth()};
        boolean[] firstHealLanded = {false};
        helper.onEachTick(() -> {
            if (!cow.hasData(GooAttachments.DOWNED)) {
                return;
            }
            streamHeal(healer);
            boolean standing = !cow.hasData(GooAttachments.DOWNED);
            if (!firstHealLanded[0] && (standing || cow.getData(GooAttachments.DOWNED).negativeHealth() != lastCount[0])) {
                firstHealLanded[0] = true;
                helper.assertFalse(standing, FIRST_HEAL_RAISED);
                float count = cow.getData(GooAttachments.DOWNED).negativeHealth();
                helper.assertTrue(count > lastCount[0], String.format(FIRST_HEAL_UNCOUNTED, lastCount[0], count));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firstHealLanded[0], NO_FIRST_HEAL);
            helper.assertFalse(cow.hasData(GooAttachments.DOWNED), STILL_DOWNED);
            helper.getLevel().getServer().getPlayerList().remove(healer);
        });
    }

    /**
     * A downed cow raised 2 seconds after going down takes an attack's
     * damage, and a zombie takes it as a target.
     *
     * @param helper the gametest helper
     */
    public static void raisedCowIsFairGame(GameTestHelper helper) {
        Zombie zombie = DownedTests.attacker(helper);
        Cow cow = DownedTests.ralliedCow(helper);
        DownedTests.downTo(helper, cow, zombie, HEALTH_BEFORE_THE_BLOW, BLOW_PAST_ITS_HEALTH);
        helper.runAfterDelay(RAISED_AFTER_TICKS, () -> {
            cow.heal(PAST_ZERO_HEAL);
            float raised = cow.getHealth();
            cow.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), LIGHT_HIT);
            helper.assertTrue(cow.getHealth() < raised, String.format(UNHURT, raised, cow.getHealth()));
            zombie.setTarget(cow);
            helper.assertTrue(zombie.getTarget() == cow, UNAIMED);
            helper.succeed();
        });
    }

    /**
     * A rallied cow struck down from a set health by a zombie's attack of a set damage.
     *
     * @param helper the gametest helper
     * @param health the health the cow stands at when struck
     * @param damage the attack's damage
     * @return the downed cow
     */
    private static Cow downedCow(GameTestHelper helper, float health, float damage) {
        Zombie zombie = DownedTests.attacker(helper);
        Cow cow = DownedTests.ralliedCow(helper);
        DownedTests.downTo(helper, cow, zombie, health, damage);
        helper.assertTrue(cow.hasData(GooAttachments.DOWNED), NOT_DOWNED);
        return cow;
    }

    /**
     * One tick of Jelly's Heal stream from a healer standing west of the cow and facing it.
     *
     * @param healer the healer
     */
    private static void streamHeal(ServerPlayer healer) {
        GooStreamHandler.streamTick(healer, GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY),
                JELLY_HEAL.toString(), healer.getEyePosition(), healer.getEyePosition()));
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
