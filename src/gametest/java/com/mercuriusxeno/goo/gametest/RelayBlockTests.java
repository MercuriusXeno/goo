package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.alientech.FaceMountedBlock;
import com.mercuriusxeno.goo.block.alientech.MetronomeBlock;
import com.mercuriusxeno.goo.block.alientech.RelayBlock;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the relay block: it registers, drops itself and is unrestricted from melting; two floor relays with
 * air between them carry a redstone signal from the dust feeding one to the dust beside the other at its strength;
 * a lit torch or a running metronome's front beside one relay powers the other; and a solid block between two relays
 * ends the link.
 * decision relay-prism-carries-the-signal-through-air
 * decision diagnose-and-correct-relay-ignoring-torch-and-metronome
 * decision utility-blocks-found-in-lunar-are-learned-and-recreated-at-will
 */
@RegistersGameTests
public final class RelayBlockTests {
    private static final String RELAY_REGISTERS_AND_DROPS = "relay_registers_and_drops";
    private static final String RELAYS_CARRY_THE_SIGNAL = "relays_carry_the_signal_at_its_strength";
    private static final String SOLID_BLOCK_ENDS_THE_LINK = "relay_solid_block_ends_the_link";
    private static final String GOES_DARK_ONCE_CUT = "relays_go_dark_once_the_feed_is_cut";
    private static final String TORCH_BESIDE_POWERS = "relay_torch_beside_powers_the_linked_relay";
    private static final String METRONOME_FRONT_POWERS = "relay_metronome_front_powers_the_linked_relay";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RELAY_REGISTERS_AND_DROPS, RelayBlockTests::registersAndDrops);
        GooTestFunctions.registerTest(registrar, RELAYS_CARRY_THE_SIGNAL, RelayBlockTests::carryTheSignal);
        GooTestFunctions.registerTest(registrar, SOLID_BLOCK_ENDS_THE_LINK, RelayBlockTests::solidBlockEndsTheLink);
        GooTestFunctions.registerTest(registrar, GOES_DARK_ONCE_CUT, RelayBlockTests::goDarkOnceTheFeedIsCut);
        GooTestFunctions.registerTest(registrar, TORCH_BESIDE_POWERS, RelayBlockTests::torchBesidePowersTheLinkedRelay);
        GooTestFunctions.registerTest(registrar, METRONOME_FRONT_POWERS,
                RelayBlockTests::metronomeFrontPowersTheLinkedRelay);
    }

    private static final BlockPos NEAR = new BlockPos(1, 1, 2);
    private static final BlockPos FAR = new BlockPos(4, 1, 2);
    private static final BlockPos BETWEEN = new BlockPos(2, 1, 2);
    /** The feed: a redstone block, then two dust cells, the second beside the near relay one step under full. */
    private static final BlockPos SOURCE = new BlockPos(0, 1, 0);
    private static final BlockPos FEED_FIRST = new BlockPos(1, 1, 0);
    private static final BlockPos FEED_BESIDE = NEAR.north();
    private static final BlockPos OUT_BESIDE = FAR.south();
    private static final double DROP_REACH = 2.0;
    private static final int WATCH_TICKS = 40;
    private static final int FULL_SIGNAL = 15;

    private static final String NOT_REGISTERED = "goo:relay should be a RelayBlock with its BlockItem";
    private static final String WRONG_DROP = "A broken relay should drop one relay, but dropped %s";
    private static final String RESTRICTED = "goo:relay should be neither restricted nor denied from melting";
    private static final String NOT_CARRIED = "The dust beside the far relay should carry the fed strength %d, it carries %d";
    private static final String UNDER_FULL = "The feed should reach the near relay under full strength, it carries %d";
    private static final String STILL_LIT = "Once the feed is cut the pair should go dark: far dust %d, near relay %d, "
            + "far relay %d";
    private static final String CARRIED_ACROSS = "No signal should cross stone between relays, the far dust carries %d";
    private static final String TORCH_NOT_CARRIED = "A lit torch beside the near relay should make the far relay give "
            + "15, it gives %d";
    private static final String PULSE_NOT_CARRIED = "The far relay should give a signal while the metronome's front is "
            + "powered and none while it rests; metronome powered %b, far relay %d";
    /** The metronome's cadence step: its front holds each half of the beat this many ticks, long enough to read. */
    private static final int SLOW_CADENCE = 4;

    private RelayBlockTests() {
    }

    /**
     * The registries hold goo:relay and its BlockItem, a broken relay drops one relay, and the live goo value table
     * reads it neither restricted nor denied.
     *
     * @param helper the gametest helper
     */
    public static void registersAndDrops(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getValue(GooBlocks.RELAY.getId());
        helper.assertTrue(block instanceof RelayBlock
                && BuiltInRegistries.ITEM.getValue(GooItems.RELAY.getId()) instanceof BlockItem item
                && item.getBlock() == block, NOT_REGISTERED);
        standOnFloor(helper, NEAR);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(NEAR);
        Block.dropResources(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), null, ItemStack.EMPTY);
        List<ItemEntity> drops = helper.getEntities(EntityTypes.ITEM, NEAR, DROP_REACH);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(GooItems.RELAY.get())
                && drops.getFirst().getItem().getCount() == 1,
                String.format(WRONG_DROP, drops.stream().map(ItemEntity::getItem).toList()));
        drops.forEach(ItemEntity::discard);
        IGooValueLookup values = GooValues.of(level);
        helper.assertFalse(values.isRestricted(GooItems.RELAY.getId()) || values.isDenied(GooItems.RELAY.getId()),
                RESTRICTED);
        helper.succeed();
    }

    /**
     * Dust one step under full feeds the near relay; the dust beside the far relay carries that same strength.
     *
     * @param helper the gametest helper
     */
    public static void carryTheSignal(GameTestHelper helper) {
        standPair(helper);
        helper.succeedWhen(() -> {
            int fed = power(helper, FEED_BESIDE);
            helper.assertTrue(fed > 0 && fed < FULL_SIGNAL, String.format(UNDER_FULL, fed));
            int carried = power(helper, OUT_BESIDE);
            helper.assertTrue(carried == fed, String.format(NOT_CARRIED, fed, carried));
        });
    }

    /**
     * The same pair fed the same way, with stone between them: the dust beside the far relay stays unpowered.
     *
     * @param helper the gametest helper
     */
    public static void solidBlockEndsTheLink(GameTestHelper helper) {
        helper.setBlock(BETWEEN, Blocks.STONE);
        standPair(helper);
        helper.runAfterDelay(WATCH_TICKS, () -> {
            int carried = power(helper, OUT_BESIDE);
            helper.assertTrue(carried == 0, String.format(CARRIED_ACROSS, carried));
            helper.succeed();
        });
    }

    /**
     * The pair carries its feed, then the redstone block feeding it goes: within a bounded wait the dust beside the
     * far relay reads 0 and both relays give no signal, rather than holding each other on through the dust each powers.
     *
     * @param helper the gametest helper
     */
    public static void goDarkOnceTheFeedIsCut(GameTestHelper helper) {
        standPair(helper);
        boolean[] cut = {false};
        helper.succeedWhen(() -> {
            if (!cut[0]) {
                int fed = power(helper, FEED_BESIDE);
                helper.assertTrue(fed > 0 && power(helper, OUT_BESIDE) == fed, String.format(NOT_CARRIED, fed,
                        power(helper, OUT_BESIDE)));
                helper.setBlock(SOURCE, Blocks.AIR);
                cut[0] = true;
            }
            int near = helper.getBlockState(NEAR).getValue(RelayBlock.POWER);
            int far = helper.getBlockState(FAR).getValue(RelayBlock.POWER);
            helper.assertTrue(power(helper, OUT_BESIDE) == 0 && near == 0 && far == 0,
                    String.format(STILL_LIT, power(helper, OUT_BESIDE), near, far));
        });
    }

    /**
     * A lit redstone torch standing on the floor beside the near relay: the far relay gives 15.
     * decision diagnose-and-correct-relay-ignoring-torch-and-metronome
     *
     * @param helper the gametest helper
     */
    public static void torchBesidePowersTheLinkedRelay(GameTestHelper helper) {
        standOnFloor(helper, NEAR);
        standOnFloor(helper, FAR);
        helper.setBlock(FEED_BESIDE.below(), Blocks.STONE);
        helper.setBlock(FEED_BESIDE, Blocks.REDSTONE_TORCH);
        helper.succeedWhen(() -> {
            int far = helper.getBlockState(FAR).getValue(RelayBlock.POWER);
            helper.assertTrue(far == FULL_SIGNAL, String.format(TORCH_NOT_CARRIED, far));
        });
    }

    /**
     * A running metronome whose front faces the near relay: on every tick its front has held its state since the last,
     * the far relay gives a signal while the front is powered and none while it rests, and both halves are seen.
     * decision diagnose-and-correct-relay-ignoring-torch-and-metronome
     *
     * @param helper the gametest helper
     */
    public static void metronomeFrontPowersTheLinkedRelay(GameTestHelper helper) {
        standOnFloor(helper, NEAR);
        standOnFloor(helper, FAR);
        helper.setBlock(FEED_BESIDE.below(), Blocks.STONE);
        helper.setBlock(FEED_BESIDE, GooBlocks.METRONOME.get().defaultBlockState()
                .setValue(MetronomeBlock.FACING, Direction.NORTH).setValue(MetronomeBlock.CADENCE, SLOW_CADENCE));
        boolean[] last = {false};
        boolean[] seen = {false, false};
        helper.onEachTick(() -> {
            boolean powered = helper.getBlockState(FEED_BESIDE).getValue(MetronomeBlock.POWERED);
            int far = helper.getBlockState(FAR).getValue(RelayBlock.POWER);
            if (powered == last[0]) {
                helper.assertTrue(powered == far > 0, String.format(PULSE_NOT_CARRIED, powered, far));
                seen[powered ? 1 : 0] = true;
            }
            last[0] = powered;
        });
        helper.succeedWhen(() -> helper.assertTrue(seen[0] && seen[1], "Both halves of the beat should be seen"));
    }

    /** Stands the two floor relays, the feed beside the near one and the dust beside the far one. */
    private static void standPair(GameTestHelper helper) {
        standOnFloor(helper, NEAR);
        standOnFloor(helper, FAR);
        for (BlockPos dust : new BlockPos[] {FEED_FIRST, FEED_BESIDE, OUT_BESIDE}) {
            helper.setBlock(dust.below(), Blocks.STONE);
            helper.setBlock(dust, Blocks.REDSTONE_WIRE);
        }
        helper.setBlock(SOURCE, Blocks.REDSTONE_BLOCK);
    }

    private static void standOnFloor(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, GooBlocks.RELAY.get().defaultBlockState().setValue(FaceMountedBlock.FACING, Direction.UP));
    }

    private static int power(GameTestHelper helper, BlockPos dust) {
        return helper.getBlockState(dust).getValue(RedstoneWireBlock.POWER);
    }
}
