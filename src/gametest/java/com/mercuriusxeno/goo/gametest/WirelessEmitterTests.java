package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lunar.WirelessSignalBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the wireless emitter, a redstone relay: fed level 7 it powers the closest signal-taking block within
 * 16 Manhattan at level 7 and no farther one, and lets it go when its input drops to 0; a block 17 away takes nothing;
 * and an emitter broken while it transmits leaves its receiver unpowered with no power source standing.
 * decision wireless-emitter-beams-its-signal-to-the-closest-receiver
 */
@RegistersGameTests
public final class WirelessEmitterTests {
    private static final String EMITTER_POWERS_THE_CLOSEST_RECEIVER_AT_ITS_LEVEL =
            "emitter_powers_the_closest_receiver_at_its_level";
    private static final String EMITTER_REACHES_SIXTEEN_AND_CLEARS_WHEN_BROKEN =
            "emitter_reaches_sixteen_and_clears_when_broken";

    private static final int FLOOR_Y = 1;
    private static final int LEVEL = 7;
    /**
     * An emitter fed level 7 by a wireless signal beside it, as a chained emitter is fed, a lamp ten blocks east of it
     * and another fourteen blocks west.
     */
    private static final BlockPos FED_EMITTER = new BlockPos(20, FLOOR_Y, 3);
    private static final BlockPos FEED = new BlockPos(20, FLOOR_Y, 2);
    private static final BlockPos NEAR_LAMP = new BlockPos(30, FLOOR_Y, 3);
    private static final BlockPos FAR_LAMP = new BlockPos(6, FLOOR_Y, 3);
    /** An emitter whose only lamp stands seventeen blocks east; and one whose lamp stands ten blocks west. */
    private static final BlockPos OUT_OF_REACH_EMITTER = new BlockPos(2, FLOOR_Y, 1);
    private static final BlockPos OUT_OF_REACH_LAMP = new BlockPos(19, FLOOR_Y, 1);
    private static final BlockPos BROKEN_EMITTER = new BlockPos(36, FLOOR_Y, 5);
    private static final BlockPos BROKEN_EMITTERS_LAMP = new BlockPos(26, FLOOR_Y, 5);
    private static final int LIT_WITHIN_TICKS = 40;

    private static final String NEAR_DARK = "The lamp ten blocks from the emitter at %s should be lit, reads %s";
    private static final String NEAR_SIGNAL = "The lamp ten blocks from the emitter should take signal %d, takes %d";
    private static final String FAR_LIT = "The lamp fourteen blocks from the emitter at %s should stay dark, reads %s";
    private static final String NOT_RELEASED = "The lamp at %s should go dark once the emitter's input drops, reads %s";
    private static final String OUT_OF_REACH_LIT = "The lamp seventeen blocks from the emitter at %s should stay dark, "
            + "reads %s";
    private static final String NEVER_LIT = "The lamp ten blocks from the second emitter at %s should light before "
            + "the emitter breaks, reads %s";
    private static final String LEFT_POWERED = "The lamp at %s should be dark once its emitter breaks, reads %s";
    private static final String SOURCE_LEFT = "No goo power source should stand beside the lamp at %s, finds %s";

    private WirelessEmitterTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, EMITTER_POWERS_THE_CLOSEST_RECEIVER_AT_ITS_LEVEL,
                WirelessEmitterTests::emitterPowersTheClosestReceiverAtItsLevel);
        GooTestFunctions.registerTest(registrar, EMITTER_REACHES_SIXTEEN_AND_CLEARS_WHEN_BROKEN,
                WirelessEmitterTests::emitterReachesSixteenAndClearsWhenBroken);
    }

    /**
     * Feeds an emitter level 7 from a wireless signal beside it, with one lamp ten blocks off and another fourteen off;
     * the nearer lamp lights taking signal 7 and the farther stays dark; then the feed goes and the nearer lamp goes
     * dark.
     *
     * @param helper the gametest helper
     */
    public static void emitterPowersTheClosestReceiverAtItsLevel(GameTestHelper helper) {
        helper.setBlock(NEAR_LAMP, Blocks.REDSTONE_LAMP);
        helper.setBlock(FAR_LAMP, Blocks.REDSTONE_LAMP);
        helper.setBlock(FED_EMITTER, GooBlocks.WIRELESS_EMITTER.get());
        helper.setBlock(FEED, GooBlocks.WIRELESS_SIGNAL.get().defaultBlockState()
                .setValue(WirelessSignalBlock.POWER, LEVEL));
        helper.runAfterDelay(LIT_WITHIN_TICKS, () -> {
            BlockState near = helper.getBlockState(NEAR_LAMP);
            helper.assertTrue(near.getValue(RedstoneLampBlock.LIT), String.format(NEAR_DARK, NEAR_LAMP, near));
            int signal = helper.getLevel().getBestNeighborSignal(helper.absolutePos(NEAR_LAMP));
            helper.assertTrue(signal == LEVEL, String.format(NEAR_SIGNAL, LEVEL, signal));
            BlockState far = helper.getBlockState(FAR_LAMP);
            helper.assertFalse(far.getValue(RedstoneLampBlock.LIT), String.format(FAR_LIT, FAR_LAMP, far));
            helper.setBlock(FEED, Blocks.AIR);
            helper.succeedWhen(() -> {
                BlockState released = helper.getBlockState(NEAR_LAMP);
                helper.assertFalse(released.getValue(RedstoneLampBlock.LIT),
                        String.format(NOT_RELEASED, NEAR_LAMP, released));
            });
        });
    }

    /**
     * Powers an emitter whose only lamp stands seventeen blocks off, which stays dark; powers a second emitter with a
     * lamp ten off, which lights; breaks that emitter, and finds its lamp dark with no goo power source beside it.
     *
     * @param helper the gametest helper
     */
    public static void emitterReachesSixteenAndClearsWhenBroken(GameTestHelper helper) {
        helper.setBlock(OUT_OF_REACH_LAMP, Blocks.REDSTONE_LAMP);
        helper.setBlock(OUT_OF_REACH_EMITTER, GooBlocks.WIRELESS_EMITTER.get());
        helper.setBlock(OUT_OF_REACH_EMITTER.north(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(BROKEN_EMITTERS_LAMP, Blocks.REDSTONE_LAMP);
        helper.setBlock(BROKEN_EMITTER, GooBlocks.WIRELESS_EMITTER.get());
        helper.setBlock(BROKEN_EMITTER.south(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(LIT_WITHIN_TICKS, () -> {
            BlockState outOfReach = helper.getBlockState(OUT_OF_REACH_LAMP);
            helper.assertFalse(outOfReach.getValue(RedstoneLampBlock.LIT),
                    String.format(OUT_OF_REACH_LIT, OUT_OF_REACH_LAMP, outOfReach));
            BlockState lit = helper.getBlockState(BROKEN_EMITTERS_LAMP);
            helper.assertTrue(lit.getValue(RedstoneLampBlock.LIT), String.format(NEVER_LIT, BROKEN_EMITTERS_LAMP,
                    lit));
            helper.destroyBlock(BROKEN_EMITTER);
            helper.succeedWhen(() -> {
                BlockState dark = helper.getBlockState(BROKEN_EMITTERS_LAMP);
                helper.assertFalse(dark.getValue(RedstoneLampBlock.LIT),
                        String.format(LEFT_POWERED, BROKEN_EMITTERS_LAMP, dark));
                for (Direction side : Direction.values()) {
                    BlockState beside = helper.getBlockState(BROKEN_EMITTERS_LAMP.relative(side));
                    helper.assertFalse(beside.is(GooBlocks.WIRELESS_SIGNAL.get()) || beside.is(GooBlocks.ZAP_PULSE.get()),
                            String.format(SOURCE_LEFT, BROKEN_EMITTERS_LAMP, beside));
                }
            });
        });
    }
}
