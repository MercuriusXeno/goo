package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for a channeled stream converting a bare prism through the
 * stream handler's real path: Pulser held on a bare prism for one charge's
 * ticks unbroken converts it into the metronome combo, and a hold broken
 * before then leaves the prism bare, however long the two halves run together.
 * channeled-hosts-convert-prisms-over-the-hold
 */
@RegistersGameTests
public final class PulserPrismTests {
    private static final String PULSER_CONVERTS = "pulser_stream_converts_a_prism";
    private static final String PULSER_BROKEN_HOLD = "pulser_broken_hold_leaves_the_prism_bare";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PULSER_CONVERTS, PulserPrismTests::pulserHeldConvertsAPrism);
        GooTestFunctions.registerTest(registrar, PULSER_BROKEN_HOLD, PulserPrismTests::brokenHoldLeavesThePrismBare);
    }


    private static final Identifier PULSER = Identifier.parse("goo:pulse_pulser");
    private static final BlockPos PRISM = new BlockPos(4, 1, 2);
    private static final BlockPos STAND = new BlockPos(1, 1, 2);
    /** The tick the hold breaks for, a few ticks short of a full charge. */
    private static final int SHORT_OF_THE_HOLD = 5;
    /** The ticks the stream leaves the prism between the two halves of a broken hold. */
    private static final int BREAK = 2;
    private static final String ABILITY_REQUIRED = "The ability registry must hold pulse_pulser";
    private static final String SHOULD_CONVERT = "A full Pulser hold should make the prism a metronome, held '%s'";
    private static final String SHOULD_STAY_BARE = "A broken Pulser hold should leave the prism bare, held '%s'";

    private PulserPrismTests() {
    }

    /**
     * A player streams Pulser at a bare prism for one charge's ticks: the
     * prism holds the metronome combo, sourced from Pulser.
     *
     * @param helper the gametest helper
     */
    public static void pulserHeldConvertsAPrism(GameTestHelper helper) {
        int hold = hold(helper);
        ServerPlayer player = streamer(helper);
        streamFor(helper, player, 1, hold);
        helper.runAfterDelay(hold + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            String combo = helper.getBlockEntity(PRISM, PrismBlockEntity.class).getCombo();
            helper.assertTrue(PULSER.toString().equals(combo), String.format(SHOULD_CONVERT, combo));
            helper.succeed();
        });
    }

    /**
     * A player streams Pulser at a bare prism, looks away a moment short of
     * the charge, then streams again as long: the two halves together pass
     * the hold, but neither alone reaches it, so the prism stays bare.
     *
     * @param helper the gametest helper
     */
    public static void brokenHoldLeavesThePrismBare(GameTestHelper helper) {
        int half = hold(helper) - SHORT_OF_THE_HOLD;
        ServerPlayer player = streamer(helper);
        streamFor(helper, player, 1, half);
        streamFor(helper, player, half + BREAK + 1, half);
        helper.runAfterDelay(2 * half + BREAK + 2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            PrismBlockEntity prism = helper.getBlockEntity(PRISM, PrismBlockEntity.class);
            helper.assertFalse(prism.hasCombo(), String.format(SHOULD_STAY_BARE, prism.getCombo()));
            helper.succeed();
        });
    }

    /**
     * The hold a Pulser conversion takes: one charge's ticks, as pulse_pulser.json names them.
     */
    private static int hold(GameTestHelper helper) {
        AbilityDefinition pulser = AbilityRegistry.of(helper.getLevel()).getAbility(PULSER);
        helper.assertTrue(pulser != null, ABILITY_REQUIRED);
        return pulser.delivery().ticksPerCharge();
    }

    /**
     * A bare prism on the floor and a player three blocks west of it, holding
     * a glove and pulse, looking at the prism's middle.
     */
    private static ServerPlayer streamer(GameTestHelper helper) {
        helper.setBlock(PRISM.below(), Blocks.STONE);
        helper.setBlock(PRISM, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.PULSE);
        KnownRecipes.teachRequires(player, AbilityRegistry.of(helper.getLevel()).getAbility(PULSER));
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(PRISM)));
        return player;
    }

    /**
     * Sends one Pulser stream tick a game tick for a run of ticks, as a held glove does.
     */
    private static void streamFor(GameTestHelper helper, ServerPlayer player, int from, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.PULSE), PULSER.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int at = from; at < from + ticks; at++) {
            helper.runAfterDelay(at, () -> GooStreamHandler.streamTick(player, tick));
        }
    }
}
