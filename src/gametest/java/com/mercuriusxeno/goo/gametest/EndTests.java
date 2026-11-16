package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.zone.ConjuredDoors;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
