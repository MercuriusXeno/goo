package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for molten iron, lunar's core liquid: it spreads as lava does; water cools a source of it to iron core
 * and flowing molten iron to anorthosite, as water cools lava to obsidian and cobblestone; and it burns what stands
 * in it harder and longer than lava.
 * decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core
 */
@RegistersGameTests
public final class MoltenIronTests {
    private static final String MOLTEN_IRON_FLOWS = "molten_iron_flows";
    private static final String WATER_COOLS_MOLTEN_IRON = "water_cools_molten_iron";
    private static final String MOLTEN_IRON_BURNS_HOTTER_THAN_LAVA = "molten_iron_burns_hotter_than_lava";

    private static final int FLOOR_Y = 0;
    private static final int LIQUID_Y = 1;
    private static final int FLOOR_EDGE = 8;
    private static final BlockPos SOURCE = new BlockPos(4, LIQUID_Y, 4);
    /** A flowing molten iron level, one step out from a source. */
    private static final int ONE_STEP_OUT = 2;
    private static final BlockPos FLOW_COOLED = new BlockPos(1, LIQUID_Y, 1);
    private static final BlockPos BESIDE_FLOW = new BlockPos(1, LIQUID_Y, 2);
    private static final BlockPos SOURCE_COOLED = new BlockPos(6, LIQUID_Y, 6);
    private static final BlockPos BESIDE_SOURCE = new BlockPos(6, LIQUID_Y, 5);
    private static final BlockPos LAVA_POOL = new BlockPos(2, LIQUID_Y, 2);
    private static final BlockPos MOLTEN_POOL = new BlockPos(6, LIQUID_Y, 6);
    /** Long enough for several hits apart by the golem's hurt cooldown, short of either golem dying. */
    private static final int BURN_TICKS = 41;

    private static final String SHOULD_FLOW = "Molten iron should spread beside its source to %s, reads %s";
    private static final String SHOULD_COOL = "Water should cool molten iron at %s to %s, reads %s";
    private static final String SHOULD_HURT_MORE = "Molten iron should hurt harder than lava: lava took %.1f, molten iron %.1f";
    private static final String SHOULD_BURN_LONGER = "Molten iron should set a longer fire than lava: lava %d ticks, molten iron %d";

    private MoltenIronTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_FLOWS, MoltenIronTests::moltenIronFlows);
        GooTestFunctions.registerTest(registrar, WATER_COOLS_MOLTEN_IRON, MoltenIronTests::waterCoolsMoltenIron);
        GooTestFunctions.registerTest(registrar, MOLTEN_IRON_BURNS_HOTTER_THAN_LAVA,
                MoltenIronTests::moltenIronBurnsHotterThanLava);
    }

    /**
     * A molten iron source on stone spreads flowing molten iron onto the stone beside it.
     *
     * @param helper the gametest helper
     */
    public static void moltenIronFlows(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(SOURCE, GooBlocks.MOLTEN_IRON.get());
        BlockPos beside = SOURCE.east();
        helper.succeedWhen(() -> {
            BlockState state = helper.getBlockState(beside);
            helper.assertTrue(state.getFluidState().getType() == GooFluids.MOLTEN_IRON_FLOWING.get(),
                    String.format(SHOULD_FLOW, beside, state));
        });
    }

    /**
     * Water laid beside a molten iron source cools it to iron core, and beside flowing molten iron cools it to
     * anorthosite.
     *
     * @param helper the gametest helper
     */
    public static void waterCoolsMoltenIron(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(SOURCE_COOLED, GooBlocks.MOLTEN_IRON.get());
        helper.setBlock(FLOW_COOLED, GooBlocks.MOLTEN_IRON.get().defaultBlockState()
                .setValue(LiquidBlock.LEVEL, ONE_STEP_OUT));
        helper.setBlock(BESIDE_SOURCE, Blocks.WATER);
        helper.setBlock(BESIDE_FLOW, Blocks.WATER);
        helper.succeedWhen(() -> {
            assertCooled(helper, SOURCE_COOLED, GooBlocks.IRON_CORE.get().defaultBlockState());
            assertCooled(helper, FLOW_COOLED, GooBlocks.ANORTHOSITE.get().defaultBlockState());
        });
    }

    /**
     * Of two iron golems held still, one in lava and one in molten iron, the one in molten iron has taken more
     * damage and carries a longer fire, read once several hits have landed and waited on through the test's ticks, so
     * a golem whose chunk starts ticking late still burns before the test reads it.
     *
     * @param helper the gametest helper
     */
    public static void moltenIronBurnsHotterThanLava(GameTestHelper helper) {
        layStoneFloor(helper);
        helper.setBlock(LAVA_POOL, Blocks.LAVA);
        helper.setBlock(MOLTEN_POOL, GooBlocks.MOLTEN_IRON.get());
        IronGolem inLava = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, LAVA_POOL);
        IronGolem inMolten = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, MOLTEN_POOL);
        float full = inLava.getMaxHealth();
        helper.succeedWhen(() -> {
            float lavaTook = full - inLava.getHealth();
            float moltenTook = full - inMolten.getHealth();
            helper.assertTrue(helper.getTick() >= BURN_TICKS, String.format(SHOULD_HURT_MORE, lavaTook, moltenTook));
            helper.assertTrue(moltenTook > lavaTook, String.format(SHOULD_HURT_MORE, lavaTook, moltenTook));
            helper.assertTrue(inMolten.getRemainingFireTicks() > inLava.getRemainingFireTicks(),
                    String.format(SHOULD_BURN_LONGER, inLava.getRemainingFireTicks(), inMolten.getRemainingFireTicks()));
        });
    }

    private static void assertCooled(GameTestHelper helper, BlockPos pos, BlockState cooled) {
        BlockState found = helper.getBlockState(pos);
        helper.assertTrue(found == cooled, String.format(SHOULD_COOL, pos, cooled, found));
    }

    private static void layStoneFloor(GameTestHelper helper) {
        for (int x = 0; x <= FLOOR_EDGE; x++) {
            for (int z = 0; z <= FLOOR_EDGE; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
            }
        }
    }
}
