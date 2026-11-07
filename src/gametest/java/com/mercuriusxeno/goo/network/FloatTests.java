package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.ability.typhoon.AirbornEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametest for the server's half of typhoon Float eaten from the glove and
 * held: while it stands the player jumps higher and a built-up fall is
 * cleared each tick, and ending it takes both away (decision
 * tailwind-and-airborn-merge-into-float). The flight by the keys and the
 * slowed fall run on the player's own client, which a mock player lacks;
 * AirbornMotionTest covers them. The mock player has no connection ticking
 * it, so the test ticks it as the connection would.
 */
@RegistersGameTests
public final class FloatTests {
    private static final String FLOAT_HOLDS_JUMP = "float_holds_jump_and_soft_falls_until_ended";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, FLOAT_HOLDS_JUMP, FloatTests::floatHoldsJumpAndSoftFallsUntilEnded);
    }

    private static final Identifier TYPHOON_FLOAT = Identifier.parse("goo:typhoon_float");
    /** typhoon_float.json's jump boost. */
    private static final double JUMP_BOOST = 0.5;
    private static final float BUILT_UP_FALL = 10f;
    private static final String SHOULD_JUMP_HIGHER = "Held Float should add a %.1f jump boost, read %s";
    private static final String SHOULD_CLEAR_FALL = "Held Float should clear the built-up fall, read %.1f";
    private static final String SHOULD_END = "Ending Float should end its flight and drop the jump boost: "
            + "stands %b, boost %s";

    private FloatTests() {
    }

    /**
     * A player holds Float from the glove: a tick later its jump strength
     * carries the boost and its built-up fall is gone; ended, the boost goes.
     *
     * @param helper the gametest helper
     */
    public static void floatHoldsJumpAndSoftFallsUntilEnded(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.TYPHOON, TYPHOON_FLOAT);
        player.fallDistance = BUILT_UP_FALL;
        player.doTick();
        AttributeModifier held = jumpBoost(player);
        double fall = player.fallDistance;

        HeldEffectsEvents.end(player, TYPHOON_FLOAT);
        player.doTick();
        boolean stands = player.getData(GooAttachments.AIRBORN).standsAt(player.level().getGameTime());
        AttributeModifier ended = jumpBoost(player);
        helper.getLevel().getServer().getPlayerList().remove(player);

        helper.assertTrue(held != null && held.amount() == JUMP_BOOST, String.format(SHOULD_JUMP_HIGHER, JUMP_BOOST, held));
        helper.assertTrue(fall == 0, String.format(SHOULD_CLEAR_FALL, fall));
        helper.assertTrue(!stands && ended == null, String.format(SHOULD_END, stands, ended));
        helper.succeed();
    }

    private static AttributeModifier jumpBoost(ServerPlayer player) {
        return player.getAttribute(Attributes.JUMP_STRENGTH).getModifier(AirbornEvents.JUMP_ID);
    }
}
