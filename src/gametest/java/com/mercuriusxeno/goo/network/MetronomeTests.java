package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametest for Metronome: a metronome prism hearing two lever pulls 20 ticks
 * apart lights a lamp beside it every 20 ticks after, and two new pulls 10
 * ticks apart set it lighting every 10.
 * metronome-prism-pulses-at-the-learned-rate
 */
public final class MetronomeTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String METRONOME = "goo:pulse_metronome";
    private static final String ZAP = "goo:pulse_zap";
    private static final String STILL_A_METRONOME = "A Zap should leave the prism's metronome combo standing";
    private static final BlockPos PRISM = new BlockPos(2, 1, 2);
    private static final BlockPos LEVER = PRISM.west();
    private static final BlockPos LAMP = PRISM.east();
    private static final int FIRST_INTERVAL = 20;
    private static final int SECOND_INTERVAL = 10;
    /** The ticks the test pulls the lever on: on, off, on 20 after the first; later off, on, off, on 10 after. */
    private static final int FIRST_ON = 2;
    private static final int FIRST_OFF = 6;
    private static final int SECOND_ON = FIRST_ON + FIRST_INTERVAL;
    /** The first beat watch: three intervals after the second pull. */
    private static final int FIRST_WATCH_END = SECOND_ON + FIRST_INTERVAL * 3 + 2;
    private static final int THIRD_OFF = FIRST_WATCH_END + 1;
    private static final int THIRD_ON = THIRD_OFF + 3;
    private static final int FOURTH_OFF = THIRD_ON + 3;
    private static final int FOURTH_ON = THIRD_ON + SECOND_INTERVAL;
    /** The second beat watch: three intervals after the fourth pull. */
    private static final int SECOND_WATCH_END = FOURTH_ON + SECOND_INTERVAL * 3 + 2;
    private static final int BEATS = 3;
    private static final String COMBO_TOOK = "The prism should hold the metronome combo";
    private static final String BEATS_AT = "The lamp should light %d times %d ticks apart, lit on ticks %s";

    private MetronomeTests() {
    }

    /**
     * A metronome prism hears two pulls 20 ticks apart, then two 10 apart:
     * the lamp lights every 20 ticks, then every 10.
     *
     * @param helper the gametest helper
     */
    public static void metronomeLearnsTheInterval(GameTestHelper helper) {
        helper.setBlock(PRISM.below(), Blocks.STONE);
        helper.setBlock(LEVER.below(), Blocks.STONE);
        helper.setBlock(LAMP.below(), Blocks.STONE);
        helper.setBlock(PRISM, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        helper.setBlock(LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, helper.absolutePos(PRISM), Direction.UP, METRONOME));
        helper.assertTrue(helper.getBlockEntity(PRISM, PrismBlockEntity.class).hasCombo(), COMBO_TOOK);
        for (int pull : new int[] {FIRST_ON, FIRST_OFF, SECOND_ON, THIRD_OFF, THIRD_ON, FOURTH_OFF, FOURTH_ON}) {
            helper.runAfterDelay(pull, () -> helper.pullLever(LEVER));
        }
        List<Integer> firstBeats = watchLamp(helper, SECOND_ON + 1, FIRST_WATCH_END);
        List<Integer> secondBeats = watchLamp(helper, FOURTH_ON + 1, SECOND_WATCH_END);
        helper.runAfterDelay(SECOND_WATCH_END + 1, () -> {
            assertBeats(helper, firstBeats, FIRST_INTERVAL);
            assertBeats(helper, secondBeats, SECOND_INTERVAL);
            helper.succeed();
        });
    }

    /**
     * A metronome prism struck by two Zaps 20 ticks apart lights its lamp
     * every 20 ticks after: a Zap's pulse beside the prism is a signal it hears.
     * zap-ticks-the-device-and-stuns
     *
     * @param helper the gametest helper
     */
    public static void metronomeLearnsFromZaps(GameTestHelper helper) {
        helper.setBlock(PRISM.below(), Blocks.STONE);
        helper.setBlock(LAMP.below(), Blocks.STONE);
        helper.setBlock(PRISM, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, helper.absolutePos(PRISM), Direction.UP, METRONOME));
        helper.assertTrue(helper.getBlockEntity(PRISM, PrismBlockEntity.class).hasCombo(), COMBO_TOOK);
        for (int zap : new int[] {FIRST_ON, SECOND_ON}) {
            helper.runAfterDelay(zap, () -> GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(),
                    null, GooTypes.PULSE, NO_ENTITY, helper.absolutePos(PRISM), Direction.UP, ZAP)));
        }
        List<Integer> beats = watchLamp(helper, SECOND_ON + 1, FIRST_WATCH_END);
        helper.runAfterDelay(FIRST_WATCH_END + 1, () -> {
            helper.assertTrue(helper.getBlockEntity(PRISM, PrismBlockEntity.class).getCombo().equals(METRONOME),
                    STILL_A_METRONOME);
            assertBeats(helper, beats, FIRST_INTERVAL);
            helper.succeed();
        });
    }

    private static List<Integer> watchLamp(GameTestHelper helper, int from, int to) {
        List<Integer> onsets = new ArrayList<>();
        boolean[] lit = {false};
        for (int tick = from; tick <= to; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                boolean now = helper.getBlockState(LAMP).getValue(RedstoneLampBlock.LIT);
                if (now && !lit[0]) {
                    onsets.add(at);
                }
                lit[0] = now;
            });
        }
        return onsets;
    }

    private static void assertBeats(GameTestHelper helper, List<Integer> onsets, int interval) {
        boolean even = onsets.size() == BEATS;
        for (int i = 1; even && i < onsets.size(); i++) {
            even = onsets.get(i) - onsets.get(i - 1) == interval;
        }
        helper.assertTrue(even, String.format(BEATS_AT, BEATS, interval, onsets));
    }
}
