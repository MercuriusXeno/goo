package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for typhoon Tailwind held through the real stream delivery: each held
 * tick drives the player along its look and drains one tick's share of the
 * cost (decision jet-pushes-along-the-look-while-held). The mock player has
 * no client moving it, so the test reads the velocity Tailwind sets.
 */
@RegistersGameTests
public final class TailwindTests {
    private static final String TAILWIND_FLIES_WHILE_HELD = "tailwind_flies_while_held";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, TAILWIND_FLIES_WHILE_HELD, TailwindTests::tailwindFliesWhileHeld);
    }


    private static final Identifier TYPHOON_TAILWIND = Identifier.parse("goo:typhoon_tailwind");
    private static final int HOLD_TICKS = 20;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch thirty degrees above level. */
    private static final float LOOKING_UP = -30f;
    private static final String ABILITY_REQUIRED = "Ability registry must hold typhoon_tailwind";
    private static final String SHOULD_FLY_ALONG_THE_LOOK =
            "Held tick %d should leave the player moving along its look %s faster than the tick before, "
                    + "read %s after %s";
    private static final String SHOULD_DRAIN_EACH_TICK = "%d held ticks should drain %d mB of typhoon, drained %d";

    private TailwindTests() {
    }

    /**
     * A mock player looking east and up holds Tailwind for twenty ticks: after
     * every held tick its speed along its look has grown, and the hold has
     * drained twenty ticks' shares of typhoon.
     *
     * @param helper the gametest helper
     */
    public static void tailwindFliesWhileHeld(GameTestHelper helper) {
        AbilityDefinition tailwind = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_TAILWIND);
        helper.assertTrue(tailwind != null, ABILITY_REQUIRED);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON, TYPHOON_TAILWIND);
        KnownRecipes.teachRequires(player, tailwind);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_UP);
        Vec3 look = player.getLookAngle();
        int heldBefore = typhoon(player);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.TYPHOON), TYPHOON_TAILWIND.toString(),
                player.getEyePosition(), player.getEyePosition());
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
            int expected = tailwind.cost() * HOLD_TICKS / tailwind.delivery().ticksPerCharge();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(drained == expected, String.format(SHOULD_DRAIN_EACH_TICK, HOLD_TICKS, expected, drained));
            helper.succeed();
        });
    }

    private static int typhoon(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.TYPHOON, 0);
    }
}
