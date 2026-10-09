package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;

/**
 * Gametest for Thumper: a thumper landed beside a redstone lamp lights it
 * on each period, then is gone once its duration ends.
 * thumper-blob-pulses-periodically-then-fades
 */
public final class ThumperTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String THUMPER = "goo:pulse_thumper";
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** Where the thumper stands, on the floor's top face. */
    private static final BlockPos BLOB = FLOOR.above();
    private static final BlockPos LAMP = BLOB.east();
    /** pulse_thumper.json's period and duration, in ticks. */
    private static final int PERIOD = 40;
    private static final int DURATION = 400;
    /** Watches three periods: the lamp should light at the start of each. */
    private static final int WATCH_TICKS = PERIOD * 3 - 1;
    private static final int LIGHTINGS = 3;
    /** A few ticks past the duration for the block to go. */
    private static final int PAST_THE_DURATION = DURATION + 5;
    private static final String BLOB_STANDS = "The thumper's block should stand where it landed";
    private static final String LIT_EACH_PERIOD = "The lamp should light once each period, lit %d times in %d periods";
    private static final String LAMP_STARTS_UNLIT = "The lamp should stand unlit before the thumper lands";
    private static final String BLOB_GONE = "The thumper should be gone after its duration";

    private ThumperTests() {
    }

    /**
     * A thumper lands on the floor beside a lamp: the lamp lights once at the
     * start of each of three periods, and the thumper is gone after its duration.
     *
     * @param helper the gametest helper
     */
    public static void thumperPulsesThenFades(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(LAMP.below(), Blocks.STONE);
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        helper.assertFalse(lit(helper), LAMP_STARTS_UNLIT);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, helper.absolutePos(FLOOR), Direction.UP, THUMPER));
        helper.assertTrue(helper.getBlockState(BLOB).is(GooBlocks.ABILITY_BLOCK.get()), BLOB_STANDS);
        int[] lightings = {0};
        boolean[] lit = {false};
        for (int tick = 1; tick <= WATCH_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                boolean now = lit(helper);
                if (now && !lit[0]) {
                    lightings[0]++;
                }
                lit[0] = now;
            });
        }
        helper.runAfterDelay(WATCH_TICKS + 1, () -> helper.assertTrue(lightings[0] == LIGHTINGS,
                String.format(LIT_EACH_PERIOD, lightings[0], LIGHTINGS)));
        helper.runAfterDelay(PAST_THE_DURATION, () -> {
            helper.assertFalse(helper.getBlockState(BLOB).is(GooBlocks.ABILITY_BLOCK.get()), BLOB_GONE);
            helper.succeed();
        });
    }

    private static boolean lit(GameTestHelper helper) {
        return helper.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT);
    }
}
