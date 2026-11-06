package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.AirbornStep;
import com.mercuriusxeno.goo.ability.typhoon.AirbornEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for typhoon Float held through the real stream delivery: each held
 * tick drives the player along its look and drains one tick's share of the
 * cost, and while held the player jumps higher and a built-up fall is cleared,
 * both leaving once the hold lets go (decisions tailwind-and-airborn-merge-into-float,
 * jet-pushes-along-the-look-while-held). The mock player has no client moving
 * it, so the tests read the velocity Float sets, and no connection ticking it,
 * so they tick it as the connection would; the steering and the capped fall
 * run on the client, which AirbornMotionTest covers.
 */
@RegistersGameTests
public final class FloatTests {
    private static final String FLOAT_FLIES_WHILE_HELD = "float_flies_while_held";
    private static final String FLOAT_HOLDS_JUMP_UNTIL_RELEASED = "float_holds_jump_and_soft_falls_until_released";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, FLOAT_FLIES_WHILE_HELD, FloatTests::floatFliesWhileHeld);
        GooTestFunctions.registerTest(registrar, FLOAT_HOLDS_JUMP_UNTIL_RELEASED,
                FloatTests::floatHoldsJumpAndSoftFallsUntilReleased);
    }

    private static final Identifier TYPHOON_FLOAT = Identifier.parse("goo:typhoon_float");
    private static final int HOLD_TICKS = 20;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch thirty degrees above level. */
    private static final float LOOKING_UP = -30f;
    /** typhoon_float.json's jump boost. */
    private static final double JUMP_BOOST = 0.5;
    private static final float BUILT_UP_FALL = 10f;
    private static final String ABILITY_REQUIRED = "Ability registry must hold typhoon_float";
    private static final String SHOULD_FLY_ALONG_THE_LOOK =
            "Held tick %d should leave the player moving along its look %s faster than the tick before, "
                    + "read %s after %s";
    private static final String SHOULD_DRAIN_EACH_TICK = "%d held ticks should drain %d mB of typhoon, drained %d";
    private static final String SHOULD_JUMP_HIGHER = "Held Float should add a %.1f jump boost, read %s";
    private static final String SHOULD_CLEAR_FALL = "Held Float should clear the built-up fall, read %.1f";
    private static final String SHOULD_END = "Released Float should end its air control and drop the jump boost: "
            + "stands %b, boost %s";

    private FloatTests() {
    }

    /**
     * A mock player looking east and up holds Float for twenty ticks: after
     * every held tick its speed along its look has grown, and the hold has
     * drained twenty ticks' shares of typhoon.
     *
     * @param helper the gametest helper
     */
    public static void floatFliesWhileHeld(GameTestHelper helper) {
        AbilityDefinition flight = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_FLOAT);
        helper.assertTrue(flight != null, ABILITY_REQUIRED);
        ServerPlayer player = floatHolder(helper, flight);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_UP);
        Vec3 look = player.getLookAngle();
        int heldBefore = typhoon(player);
        GooStreamPayload tick = heldTick(player);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            int heldTick = held;
            helper.runAfterDelay(held, () -> {
                Vec3 before = player.getDeltaMovement();
                GooStreamHandler.streamTick(player, tick);
                Vec3 after = player.getDeltaMovement();
                helper.assertTrue(after.dot(look) > before.dot(look),
                        String.format(SHOULD_FLY_ALONG_THE_LOOK, heldTick, look, after, before));
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int drained = heldBefore - typhoon(player);
            int expected = flight.cost() * HOLD_TICKS / flight.delivery().ticksPerCharge();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(drained == expected, String.format(SHOULD_DRAIN_EACH_TICK, HOLD_TICKS, expected, drained));
            helper.succeed();
        });
    }

    /**
     * A player holds Float for one tick: its jump strength carries the boost
     * and its built-up fall is gone; once the grace after the last held tick
     * passes, the air control has ended and the boost has gone.
     *
     * @param helper the gametest helper
     */
    public static void floatHoldsJumpAndSoftFallsUntilReleased(GameTestHelper helper) {
        AbilityDefinition flight = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_FLOAT);
        helper.assertTrue(flight != null, ABILITY_REQUIRED);
        ServerPlayer player = floatHolder(helper, flight);
        helper.runAfterDelay(1, () -> {
            player.fallDistance = BUILT_UP_FALL;
            GooStreamHandler.streamTick(player, heldTick(player));
            player.doTick();
            AttributeModifier held = jumpBoost(player);
            double fall = player.fallDistance;
            helper.assertTrue(held != null && held.amount() == JUMP_BOOST,
                    String.format(SHOULD_JUMP_HIGHER, JUMP_BOOST, held));
            helper.assertTrue(fall == 0, String.format(SHOULD_CLEAR_FALL, fall));
        });
        helper.runAfterDelay(2 + AirbornStep.HELD_TICK_GRACE, () -> {
            player.doTick();
            boolean stands = player.getData(GooAttachments.AIRBORN).standsAt(player.level().getGameTime());
            AttributeModifier ended = jumpBoost(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(!stands && ended == null, String.format(SHOULD_END, stands, ended));
            helper.succeed();
        });
    }

    private static ServerPlayer floatHolder(GameTestHelper helper, AbilityDefinition flight) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON, TYPHOON_FLOAT);
        KnownRecipes.teachRequires(player, flight);
        return player;
    }

    private static GooStreamPayload heldTick(ServerPlayer player) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.TYPHOON), TYPHOON_FLOAT.toString(),
                player.getEyePosition(), player.getEyePosition());
    }

    private static AttributeModifier jumpBoost(ServerPlayer player) {
        return player.getAttribute(Attributes.JUMP_STRENGTH).getModifier(AirbornEvents.JUMP_ID);
    }

    private static int typhoon(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.TYPHOON, 0);
    }
}
