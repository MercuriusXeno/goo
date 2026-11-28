package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for volatile crystal: a crystal mined while quiet drops whole and leaves the block beside it standing, and
 * a crystal a redstone signal reaches shatters, its blast breaking the block beside it.
 * decision volatile-crystals-are-rich-in-crystal-and-unstable
 */
@RegistersGameTests
public final class VolatileCrystalTests {
    private static final String VOLATILE_CRYSTAL_EXPLODES_ON_A_SIGNAL_AND_DROPS_WHOLE_WHEN_QUIET =
            "volatile_crystal_explodes_on_a_signal_and_drops_whole_when_quiet";

    /** The quiet crystal, on stone at one corner of the bay, glass beside it. */
    private static final BlockPos QUIET_FLOOR = new BlockPos(0, 1, 0);
    private static final BlockPos QUIET_CRYSTAL = QUIET_FLOOR.above();
    private static final BlockPos QUIET_NEIGHBOUR = QUIET_CRYSTAL.east();
    /** The powered crystal, on stone at the far corner, glass beside it and a redstone block to set it off. */
    private static final BlockPos LOUD_FLOOR = new BlockPos(5, 1, 5);
    private static final BlockPos LOUD_CRYSTAL = LOUD_FLOOR.above();
    private static final BlockPos LOUD_NEIGHBOUR = LOUD_CRYSTAL.west();
    private static final BlockPos SIGNAL = LOUD_CRYSTAL.north();
    private static final int BLAST_TICKS = 5;
    private static final double DROP_RANGE = 1.5;

    private static final String QUIET_NEIGHBOUR_FELL = "The glass beside a quiet crystal mined at %s should stand, "
            + "reads %s";
    private static final String CRYSTAL_STANDS = "A volatile crystal a redstone block powers at %s should be gone, "
            + "reads %s";
    private static final String LOUD_NEIGHBOUR_STANDS = "The glass beside a powered volatile crystal at %s should be "
            + "broken by its blast, reads %s";

    private VolatileCrystalTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VOLATILE_CRYSTAL_EXPLODES_ON_A_SIGNAL_AND_DROPS_WHOLE_WHEN_QUIET,
                VolatileCrystalTests::volatileCrystalExplodesOnASignalAndDropsWholeWhenQuiet);
    }

    /**
     * Breaks a quiet volatile crystal and finds its item dropped with the glass beside it standing; then powers a
     * second with a redstone block and finds its cell air and the glass beside it broken.
     *
     * @param helper the gametest helper
     */
    public static void volatileCrystalExplodesOnASignalAndDropsWholeWhenQuiet(GameTestHelper helper) {
        helper.setBlock(QUIET_FLOOR, Blocks.STONE);
        helper.setBlock(QUIET_CRYSTAL, GooBlocks.VOLATILE_CRYSTAL.get());
        helper.setBlock(QUIET_NEIGHBOUR, Blocks.GLASS);
        helper.setBlock(LOUD_FLOOR, Blocks.STONE);
        helper.setBlock(LOUD_CRYSTAL, GooBlocks.VOLATILE_CRYSTAL.get());
        helper.setBlock(LOUD_NEIGHBOUR, Blocks.GLASS);
        helper.getLevel().destroyBlock(helper.absolutePos(QUIET_CRYSTAL), true);
        helper.assertItemEntityPresent(GooItems.VOLATILE_CRYSTAL.get(), QUIET_CRYSTAL, DROP_RANGE);
        BlockState quietNeighbour = helper.getBlockState(QUIET_NEIGHBOUR);
        helper.assertTrue(quietNeighbour.is(Blocks.GLASS), String.format(QUIET_NEIGHBOUR_FELL, QUIET_CRYSTAL,
                quietNeighbour));
        helper.killAllEntities();
        helper.setBlock(SIGNAL, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(BLAST_TICKS, () -> {
            BlockState crystal = helper.getBlockState(LOUD_CRYSTAL);
            helper.assertTrue(!crystal.is(GooBlocks.VOLATILE_CRYSTAL.get()) && crystal.isAir(),
                    String.format(CRYSTAL_STANDS, LOUD_CRYSTAL, crystal));
            BlockState loudNeighbour = helper.getBlockState(LOUD_NEIGHBOUR);
            helper.assertTrue(!loudNeighbour.is(Blocks.GLASS), String.format(LOUD_NEIGHBOUR_STANDS, LOUD_NEIGHBOUR,
                    loudNeighbour));
            helper.succeed();
        });
    }
}
