package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.end.EndDestination;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.zone.ConjuredDoors;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Ender's End, channelled like Void's Zone: the hold stands a
 * conjured gate for its caster, and a hold let go early collapses it and
 * leaves the caster where they stood
 * (decision end-summons-a-gate-like-zone-that-takes-you-to-the-end-and-back).
 */
@RegistersGameTests
public final class EndTests {
    private static final String END_GATE_STANDS_AND_COLLAPSES = "end_gate_stands_while_channelled_and_collapses";
    private static final String END_GRAYED_OUT_REFUSES = "end_grayed_out_in_the_end_refuses_the_channel";

    private static final Identifier ENDER_END = Identifier.parse("goo:ender_end");
    /** The hold End's gate asks before it carries its caster: a second. */
    private static final int CHARGE_TICKS = 20;
    /** The tick of the hold the test reads the gate standing, halfway through the charge. */
    private static final int MID_CHANNEL = CHARGE_TICKS / 2;
    /** Ticks after a channel is let go by which its gate is gone. */
    private static final int GATE_GONE_BY = 4;
    /** Ender goo enough for a whole charge and more, so a share the hold drains never stops it. */
    private static final int ENDER_HELD = 8 * GooStacks.THOUSAND;
    private static final String ABILITY_REQUIRED = "goo:ender_end must be loaded";
    private static final String SHOULD_HOLD_THE_END = "The server should hold the End";
    private static final String SHOULD_COUNT_NONE = "A channel of End grayed out in the End should count no hold "
            + "tick, so the next advance reads 1, reads %d";
    private static final String SHOULD_STAND_NONE = "A channel of End grayed out in the End should stand no gate";
    private static final String SHOULD_KEEP_GOO = "A channel of End grayed out in the End should leave the goo at %d, "
            + "leaves %d";
    private static final String SHOULD_STAND = "A channel of End should stand a conjured gate at tick %d, stands none";
    private static final String SHOULD_COLLAPSE = "A channel let go early should leave no gate, leaves %s";
    private static final String SHOULD_STAY = "A channel let go early should leave the caster at %s in %s, stands at "
            + "%s in %s";

    private EndTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, END_GATE_STANDS_AND_COLLAPSES,
                EndTests::gateStandsWhileChannelledAndCollapsesWhenLetGo);
        GooTestFunctions.registerTest(registrar, END_GRAYED_OUT_REFUSES, EndTests::grayedOutInTheEndRefusesTheChannel);
    }

    /**
     * A caster channelling End stands a conjured gate halfway through the
     * charge; let go there, the gate is gone and the caster stands where they
     * stood, in the level they stood in
     * (decision end-summons-a-gate-like-zone-that-takes-you-to-the-end-and-back).
     *
     * @param helper the gametest helper
     */
    public static void gateStandsWhileChannelledAndCollapsesWhenLetGo(GameTestHelper helper) {
        ServerPlayer caster = enderCaster(helper);
        ServerLevel level = helper.getLevel();
        Vec3 castFrom = caster.position();
        for (int held = 1; held <= MID_CHANNEL; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(caster, holdOf(caster)));
        }
        // Read the tick after the last hold: the gate outlasts its last hold by a tick, so it still stands.
        helper.runAfterDelay(MID_CHANNEL + 1, () -> helper.assertTrue(
                ConjuredDoors.doorOf(caster.getUUID()).isPresent(), String.format(SHOULD_STAND, MID_CHANNEL)));
        helper.runAfterDelay(MID_CHANNEL + GATE_GONE_BY, () -> {
            helper.assertTrue(ConjuredDoors.doorOf(caster.getUUID()).isEmpty(),
                    String.format(SHOULD_COLLAPSE, ConjuredDoors.doorOf(caster.getUUID())));
            helper.assertTrue(caster.level() == level && caster.position().equals(castFrom),
                    String.format(SHOULD_STAY, castFrom, level.dimension().identifier(), caster.position(),
                            caster.level().dimension().identifier()));
            helper.succeed();
        });
    }

    /**
     * A caster standing in the End who came by no End gate, holding no way
     * back, channels End for a whole charge: the server counted none of the
     * hold's ticks, so the next advance of their hold reads a first tick
     * where an accepted channel reads one past the charge; no gate stands for
     * them and their goo reads as it did
     * (decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability).
     *
     * @param helper the gametest helper
     */
    public static void grayedOutInTheEndRefusesTheChannel(GameTestHelper helper) {
        ServerPlayer caster = enderCaster(helper);
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, SHOULD_HOLD_THE_END);
        EndPlatformFeature.createEndPlatform(end, BlockPos.containing(EndDestination.END_ENTRANCE), true);
        caster.teleport(new TeleportTransition(end, EndDestination.END_ENTRANCE, Vec3.ZERO, 0f, 0f,
                TeleportTransition.DO_NOTHING));
        int before = enderHeld(caster);
        boolean[] stood = new boolean[1];
        for (int held = 1; held <= CHARGE_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(caster, holdOf(caster));
                stood[0] |= ConjuredDoors.doorOf(caster.getUUID()).isPresent();
            });
        }
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            // Within the continuing gap of the last tick sent, so a hold the server counted continues here.
            int next = GooServerState.of(end.getServer()).streamHolds().advance(caster.getUUID(),
                    end.getServer().getTickCount());
            helper.assertTrue(next == ChannelAim.FIRST_TICK, String.format(SHOULD_COUNT_NONE, next));
            helper.assertTrue(!stood[0], SHOULD_STAND_NONE);
            helper.assertTrue(enderHeld(caster) == before,
                    String.format(SHOULD_KEEP_GOO, before, enderHeld(caster)));
            helper.succeed();
        });
    }

    private static int enderHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ENDER, 0);
    }

    /** A mock player holding a glove, the ender goo a charge drains, and the dragon's breath End requires. */
    private static ServerPlayer enderCaster(GameTestHelper helper) {
        AbilityDefinition end = AbilityRegistry.of(helper.getLevel()).getAbility(ENDER_END);
        helper.assertTrue(end != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ENDER);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.ENDER, ENDER_HELD));
        KnownRecipes.teachRequires(caster, end);
        return caster;
    }

    private static GooStreamPayload holdOf(ServerPlayer caster) {
        return new GooStreamPayload(GooTypes.id(GooTypes.ENDER), ENDER_END.toString(), caster.getEyePosition(),
                caster.getEyePosition().add(caster.getLookAngle()), caster.blockPosition().below(),
                GooStreamPayload.NO_FACE);
    }
}
