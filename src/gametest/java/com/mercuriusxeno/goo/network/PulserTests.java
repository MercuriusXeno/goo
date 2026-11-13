package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.gametest.FedPlexer;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for Pulser: a hold aimed at a lever flips it again and again for
 * as long as the hold runs, and one aimed at a plexer, a reactor or dust
 * powers it on and off again and again.
 * pulser-toggles-rapidly-while-held
 * tick-pulses-anything-a-signal-runs-while-held
 */
@RegistersGameTests
public final class PulserTests {
    private static final String PULSER_FLIPS_REPEATEDLY = "pulser_flips_repeatedly";
    private static final String TICK_ON_A_PLEXER_CRAFTS_REPEATEDLY = "tick_on_a_plexer_crafts_repeatedly";
    private static final String TICK_ON_A_REACTOR_TRIGGERS_IT_REPEATEDLY = "tick_on_a_reactor_triggers_it_repeatedly";
    private static final String TICK_PULSES_DUST_REPEATEDLY = "tick_pulses_dust_repeatedly";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PULSER_FLIPS_REPEATEDLY, PulserTests::pulserFlipsRepeatedly);
        GooTestFunctions.registerTest(registrar, TICK_ON_A_PLEXER_CRAFTS_REPEATEDLY, PulserTests::tickOnAPlexerCraftsRepeatedly);
        GooTestFunctions.registerTest(registrar, TICK_ON_A_REACTOR_TRIGGERS_IT_REPEATEDLY,
                PulserTests::tickOnAReactorTriggersItRepeatedly);
        GooTestFunctions.registerTest(registrar, TICK_PULSES_DUST_REPEATEDLY, PulserTests::tickPulsesDustRepeatedly);
    }


    private static final Identifier PULSER = Identifier.parse("goo:pulse_pulser");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Three blocks ahead of the player, a lever on a stone post at eye height. */
    private static final BlockPos LEVER = STAND_POS.east(3).above();
    private static final float FACING_EAST = -90f;
    private static final float LEVEL_LOOK = 0f;
    private static final int HELD_GOO = 3;
    private static final int HOLD_TICKS = 40;
    /** More than one flip: the lever flips again, not once, whatever the toggle's cadence. */
    private static final int REPEATED_FLIPS = 2;
    private static final String ABILITY_REQUIRED = "Ability registry must hold pulse_pulser";
    private static final String FLIPS_SEVERAL = "The lever should flip again and again in the hold, flipped %d times";
    /** The fed plexer's goo pays for this many stones, more than a hold could craft. */
    private static final int PLEXER_CRAFTS = 16;
    /** Ticks past the hold for the plexer's last delayed craft to land. */
    private static final int PAST_THE_LAST_CRAFT = 10;
    private static final String CRAFTS_SEVERAL = "A Tick hold on a plexer should craft more than one stone, crafted %d";
    private static final String TRIGGERS_SEVERAL = "A Tick hold on a reactor should change TRIGGERED several times, changed %d";
    private static final String DUST_SEVERAL = "A Tick hold on dust should power and unpower it several times, changed %d";

    private PulserTests() {
    }

    /**
     * A mock player holds Pulser at a lever for forty ticks: the lever flips
     * again and again.
     *
     * @param helper the gametest helper
     */
    public static void pulserFlipsRepeatedly(GameTestHelper helper) {
        holdAtTheLever(helper, held -> 1);
    }

    private static void holdAtTheLever(GameTestHelper helper, IntUnaryOperator streamTicksAt) {
        ServerPlayer player = pulserAt(helper, LEVER);
        watchFlips(helper, player, GooStreamPayload.unplaned(GooTypes.id(GooTypes.PULSE), PULSER.toString(),
                player.getEyePosition(), player.getEyePosition()), LEVER, streamTicksAt);
    }

    /**
     * A mock player holds Pulser at a fed plexer for forty ticks: the plexer
     * crafts more than one stone in the hold.
     *
     * @param helper the gametest helper
     */
    public static void tickOnAPlexerCraftsRepeatedly(GameTestHelper helper) {
        helper.setBlock(LEVER.below(), Blocks.STONE);
        FedPlexer.place(helper, LEVER, Direction.SOUTH, PLEXER_CRAFTS);
        ServerPlayer player = holdAt(helper);
        helper.runAfterDelay(HOLD_TICKS + PAST_THE_LAST_CRAFT, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            int crafted = FedPlexer.ejected(helper);
            helper.assertTrue(crafted > 1, String.format(CRAFTS_SEVERAL, crafted));
            helper.succeed();
        });
    }

    /**
     * A mock player holds Pulser at a reactor for forty ticks: the reactor's
     * TRIGGERED state turns on and off several times.
     *
     * @param helper the gametest helper
     */
    public static void tickOnAReactorTriggersItRepeatedly(GameTestHelper helper) {
        helper.setBlock(LEVER.below(), Blocks.STONE);
        helper.setBlock(LEVER, GooBlocks.REACTOR.get());
        countChanges(helper, () -> helper.getBlockState(LEVER).getValue(ReactorBlock.TRIGGERED), TRIGGERS_SEVERAL);
    }

    /**
     * A mock player holds Pulser at dust on a stone post for forty ticks: the
     * dust powers and unpowers several times.
     *
     * @param helper the gametest helper
     */
    public static void tickPulsesDustRepeatedly(GameTestHelper helper) {
        helper.setBlock(LEVER.below(), Blocks.STONE);
        helper.setBlock(LEVER, Blocks.REDSTONE_WIRE);
        countChanges(helper, () -> helper.getBlockState(LEVER).getValue(RedStoneWireBlock.POWER) > 0, DUST_SEVERAL);
    }

    private static ServerPlayer holdAt(GameTestHelper helper) {
        AbilityDefinition pulser = AbilityRegistry.of(helper.getLevel()).getAbility(PULSER);
        helper.assertTrue(pulser != null, ABILITY_REQUIRED);
        ServerPlayer player = pulser(helper);
        KnownRecipes.teachRequires(player, pulser);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.PULSE), PULSER.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        return player;
    }

    private static void countChanges(GameTestHelper helper, BooleanSupplier reading, String several) {
        ServerPlayer player = holdAt(helper);
        int[] changes = {0};
        boolean[] last = {reading.getAsBoolean()};
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                boolean now = reading.getAsBoolean();
                if (now != last[0]) {
                    changes[0]++;
                    last[0] = now;
                }
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(changes[0] >= REPEATED_FLIPS, String.format(several, changes[0]));
            helper.succeed();
        });
    }

    private static ServerPlayer pulserAt(GameTestHelper helper, BlockPos lever) {
        AbilityDefinition pulser = AbilityRegistry.of(helper.getLevel()).getAbility(PULSER);
        helper.assertTrue(pulser != null, ABILITY_REQUIRED);
        helper.setBlock(lever.below(), Blocks.STONE);
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        ServerPlayer player = pulser(helper);
        KnownRecipes.teachRequires(player, pulser);
        return player;
    }

    private static void watchFlips(GameTestHelper helper, ServerPlayer player, GooStreamPayload tick,
                                   BlockPos lever, IntUnaryOperator streamTicksAt) {
        int[] flips = {0};
        boolean[] last = {powered(helper, lever)};
        for (int held = 1; held <= HOLD_TICKS; held++) {
            int at = held;
            helper.runAfterDelay(held, () -> {
                for (int sent = 0; sent < streamTicksAt.applyAsInt(at); sent++) {
                    GooStreamHandler.streamTick(player, tick);
                }
                boolean now = powered(helper, lever);
                if (now != last[0]) {
                    flips[0]++;
                    last[0] = now;
                }
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(flips[0] >= REPEATED_FLIPS, String.format(FLIPS_SEVERAL, flips[0]));
            helper.succeed();
        });
    }

    private static boolean powered(GameTestHelper helper, BlockPos lever) {
        return helper.getBlockState(lever).getValue(LeverBlock.POWERED);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer pulser(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LEVEL_LOOK);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.PULSE, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
