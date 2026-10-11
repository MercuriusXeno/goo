package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.ability.typhoon.AirbornEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Gametest for the server's half of typhoon Airborn held from the glove: while
 * it stands the player jumps higher and a built-up fall is cleared each tick,
 * and ending it takes both away (decision airborn-steerable-levitation-and-soft-falls).
 * The steering and the capped fall run on the player's own client, which a
 * mock player lacks; AirbornMotionTest covers them. The mock player has no
 * connection ticking it, so the test ticks it as the connection would.
 */
public final class AirbornTests {

    private static final Identifier TYPHOON_AIRBORN = Identifier.parse("goo:typhoon_airborn");
    /** typhoon_airborn.json's jump boost. */
    private static final double JUMP_BOOST = 0.5;
    private static final float BUILT_UP_FALL = 10f;
    private static final String SHOULD_JUMP_HIGHER = "Held Airborn should add a %.1f jump boost, read %s";
    private static final String SHOULD_CLEAR_FALL = "Held Airborn should clear the built-up fall, read %.1f";
    private static final String SHOULD_END = "Ending Airborn should end it and drop the jump boost: stands %b, boost %s";

    private AirbornTests() {
    }

    /**
     * A player holds Airborn from the glove: a tick later its jump strength
     * carries the boost and its built-up fall is gone; ended, the boost goes.
     *
     * @param helper the gametest helper
     */
    public static void airbornHoldsJumpAndSoftFalls(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.TYPHOON, TYPHOON_AIRBORN);
        player.fallDistance = BUILT_UP_FALL;
        player.doTick();
        AttributeModifier held = jumpBoost(player);
        double fall = player.fallDistance;

        HeldEffectsEvents.end(player, TYPHOON_AIRBORN);
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
