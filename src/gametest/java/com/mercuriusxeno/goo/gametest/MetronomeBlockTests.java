package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.alientech.MetronomeBlock;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the metronome plate: it registers and drops itself unrestricted from melting, pulses out its front
 * at the cadence its gap switch dials, falls silent under a signal at its back, and pulses only under one once its
 * button inverts it. Each test watches the redstone dust at the plate's front, which carries a pulse the tick it
 * starts.
 * decision metronome-prism-pulses-at-the-learned-rate
 * decision utility-blocks-found-in-lunar-are-learned-and-recreated-at-will
 */
@RegistersGameTests
public final class MetronomeBlockTests {
    private static final String METRONOME_REGISTERS_AND_DROPS = "metronome_registers_and_drops";
    private static final String METRONOME_GAP_SWITCH_STEPS_THE_CADENCE = "metronome_gap_switch_steps_the_cadence";
    private static final String METRONOME_BACK_SIGNAL_SILENCES = "metronome_back_signal_silences";
    private static final String METRONOME_BUTTON_INVERTS = "metronome_button_inverts";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, METRONOME_REGISTERS_AND_DROPS, MetronomeBlockTests::registersAndDrops);
        GooTestFunctions.registerTest(registrar, METRONOME_GAP_SWITCH_STEPS_THE_CADENCE,
                MetronomeBlockTests::gapSwitchStepsTheCadence);
        GooTestFunctions.registerTest(registrar, METRONOME_BACK_SIGNAL_SILENCES, MetronomeBlockTests::backSignalSilences);
        GooTestFunctions.registerTest(registrar, METRONOME_BUTTON_INVERTS, MetronomeBlockTests::buttonInverts);
    }

    /** The plate's back faces west, so its pulse leaves east into the dust. */
    private static final Direction BACK = Direction.WEST;
    private static final BlockPos PLATE = new BlockPos(2, 1, 2);
    private static final BlockPos FRONT = PLATE.relative(BACK.getOpposite());
    private static final BlockPos REAR = PLATE.relative(BACK);
    private static final double DROP_REACH = 2.0;
    /** How far into the plate's half a click lands from its center, in blocks. */
    private static final double HALF_CENTER = 0.25;
    /** The plate's top face, two pixels up. */
    private static final double PLATE_TOP = 2.0 / 16.0;
    /** Ticks after a click or a placement left for the beat to settle before it is counted. */
    private static final int SETTLE = 10;
    /** Every pulse the dust shows in a window is counted; three gaps are the fewest that show a steady beat. */
    private static final int FEWEST_GAPS = 3;
    /** The cadences the gap switch steps through from placement, in ticks between pulses, and back to the first. */
    private static final int[] CADENCES = {2, 4, 8, 16, 32, 2};
    /** Ticks each cadence is watched: long enough, past its settle, for three gaps of its slowest, 32 ticks. */
    private static final int WINDOW = 150;
    /** Ticks after a click on the gap switch left for the old cadence's last beat to land. */
    private static final int STEP_SETTLE = 20;
    /** The tick the back is powered in the silence test, after the plate has pulsed freely. */
    private static final int BACK_POWERED_AT = 20;
    private static final int SILENT_WATCH = 30;

    private static final String NOT_REGISTERED = "goo:metronome should be a MetronomeBlock with its BlockItem";
    private static final String WRONG_DROP = "A broken metronome should drop one metronome, but dropped %s";
    private static final String RESTRICTED = "goo:metronome should be neither restricted nor denied from melting";
    private static final String WRONG_CADENCE = "Window %d should pulse every %d ticks; pulses began on ticks %s";
    private static final String NOT_FREE = "The metronome should pulse every 2 ticks before its back is powered; "
            + "pulses began on ticks %s";
    private static final String NOT_SILENT = "The front should stay unpowered while %s; it powered on ticks %s";

    private MetronomeBlockTests() {
    }

    /**
     * The registries hold goo:metronome and its BlockItem, a broken metronome drops one metronome, and the live goo
     * value table reads it neither restricted nor denied.
     *
     * @param helper the gametest helper
     */
    public static void registersAndDrops(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getValue(GooBlocks.METRONOME.getId());
        helper.assertTrue(block instanceof MetronomeBlock
                && BuiltInRegistries.ITEM.getValue(GooItems.METRONOME.getId()) instanceof BlockItem item
                && item.getBlock() == block, NOT_REGISTERED);
        standPlate(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(PLATE);
        Block.dropResources(level.getBlockState(pos), level, pos, null, null, ItemStack.EMPTY);
        List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, PLATE, DROP_REACH);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GooItems.METRONOME.get())
                && drops.getFirst().getItem().getCount() == 1,
                String.format(WRONG_DROP, drops.stream().map(ItemEntity::getItem).toList()));
        drops.forEach(ItemEntity::discard);
        IGooValueLookup values = GooValues.of(level);
        helper.assertFalse(values.isRestricted(GooItems.METRONOME.getId())
                || values.isDenied(GooItems.METRONOME.getId()), RESTRICTED);
        helper.succeed();
    }

    /**
     * A placed metronome pulses every 2 ticks, and each right-click on its gap switch steps it to 4, 8, 16, 32 and
     * back to 2 (operator ruling 2026-11-28).
     *
     * @param helper the gametest helper
     */
    public static void gapSwitchStepsTheCadence(GameTestHelper helper) {
        standPlate(helper);
        List<Integer> onsets = watchFront(helper, 1, WINDOW * CADENCES.length);
        for (int step = 1; step < CADENCES.length; step++) {
            helper.runAfterDelay((long) WINDOW * step, () -> click(helper, BACK.getOpposite()));
        }
        helper.runAfterDelay(WINDOW * CADENCES.length + 1, () -> {
            for (int window = 0; window < CADENCES.length; window++) {
                List<Integer> counted = within(onsets, WINDOW * window + STEP_SETTLE, WINDOW * (window + 1));
                helper.assertTrue(beatsEvery(counted, CADENCES[window]),
                        String.format(WRONG_CADENCE, window, CADENCES[window], counted));
            }
            helper.succeed();
        });
    }

    /**
     * A metronome pulsing freely stops once a signal reaches its back: the front stays unpowered.
     *
     * @param helper the gametest helper
     */
    public static void backSignalSilences(GameTestHelper helper) {
        standPlate(helper);
        int end = BACK_POWERED_AT + SETTLE + SILENT_WATCH;
        List<Integer> onsets = watchFront(helper, 1, end);
        List<Integer> powered = watchPowered(helper, BACK_POWERED_AT + SETTLE, end);
        helper.runAfterDelay(BACK_POWERED_AT, () -> helper.setBlock(REAR, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(end + 1, () -> {
            List<Integer> free = within(onsets, SETTLE, BACK_POWERED_AT);
            helper.assertTrue(beatsEvery(free, CADENCES[0]), String.format(NOT_FREE, free));
            helper.assertTrue(powered.isEmpty(), String.format(NOT_SILENT, "its back is powered", powered));
            helper.succeed();
        });
    }

    /**
     * After a right-click on its button the metronome stays silent with no signal at its back, and pulses every 2
     * ticks once one reaches it.
     *
     * @param helper the gametest helper
     */
    public static void buttonInverts(GameTestHelper helper) {
        standPlate(helper);
        helper.runAfterDelay(1, () -> click(helper, BACK));
        int unpoweredEnd = SETTLE + SILENT_WATCH;
        int end = unpoweredEnd + WINDOW;
        List<Integer> powered = watchPowered(helper, SETTLE, unpoweredEnd);
        List<Integer> onsets = watchFront(helper, unpoweredEnd, end);
        helper.runAfterDelay(unpoweredEnd, () -> helper.setBlock(REAR, Blocks.REDSTONE_BLOCK));
        helper.runAfterDelay(end + 1, () -> {
            helper.assertTrue(powered.isEmpty(), String.format(NOT_SILENT, "inverted with no signal", powered));
            List<Integer> counted = within(onsets, unpoweredEnd + SETTLE, end);
            helper.assertTrue(beatsEvery(counted, CADENCES[0]),
                    String.format(WRONG_CADENCE, 1, CADENCES[0], counted));
            helper.succeed();
        });
    }

    /**
     * Stands the plate on stone with its back west and dust at its front, as a player placing it facing east does.
     */
    private static void standPlate(GameTestHelper helper) {
        for (BlockPos pos : new BlockPos[] {PLATE, FRONT, REAR}) {
            helper.setBlock(pos.below(), Blocks.STONE);
        }
        helper.setBlock(FRONT, Blocks.REDSTONE_WIRE);
        helper.setBlock(PLATE, GooBlocks.METRONOME.get().defaultBlockState().setValue(MetronomeBlock.FACING, BACK));
    }

    /**
     * Right-clicks the plate's top in the half lying toward {@code half}: the back half holds the button, the front
     * half the gap switch.
     */
    private static void click(GameTestHelper helper, Direction half) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(PLATE);
        Vec3 at = Vec3.atBottomCenterOf(abs).add(half.getStepX() * HALF_CENTER, PLATE_TOP,
                half.getStepZ() * HALF_CENTER);
        helper.useBlock(PLATE, player, new BlockHitResult(at, Direction.UP, abs, false));
    }

    /** The ticks in {@code [from, to]} on which the front dust turns from unpowered to powered. */
    private static List<Integer> watchFront(GameTestHelper helper, int from, int to) {
        List<Integer> onsets = new ArrayList<>();
        boolean[] lit = {false};
        for (int tick = from; tick <= to; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                boolean now = frontPowered(helper);
                if (now && !lit[0]) {
                    onsets.add(at);
                }
                lit[0] = now;
            });
        }
        return onsets;
    }

    /** The ticks in {@code [from, to]} on which the front dust reads powered. */
    private static List<Integer> watchPowered(GameTestHelper helper, int from, int to) {
        List<Integer> powered = new ArrayList<>();
        for (int tick = from; tick <= to; tick++) {
            int at = tick;
            helper.runAfterDelay(tick, () -> {
                if (frontPowered(helper)) {
                    powered.add(at);
                }
            });
        }
        return powered;
    }

    private static boolean frontPowered(GameTestHelper helper) {
        return helper.getBlockState(FRONT).getValue(RedstoneWireBlock.POWER) > 0;
    }

    private static List<Integer> within(List<Integer> onsets, int from, int to) {
        return onsets.stream().filter(tick -> tick >= from && tick < to).toList();
    }

    /** Whether the onsets show at least three gaps, each exactly {@code cadence} ticks. */
    private static boolean beatsEvery(List<Integer> onsets, int cadence) {
        boolean even = onsets.size() > FEWEST_GAPS;
        for (int i = 1; even && i < onsets.size(); i++) {
            even = onsets.get(i) - onsets.get(i - 1) == cadence;
        }
        return even;
    }
}
