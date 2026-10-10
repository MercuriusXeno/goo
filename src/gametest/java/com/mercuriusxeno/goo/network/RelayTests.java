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

/**
 * Gametest for Relay: a lamp at one relay prism follows a lever at another
 * 12 blocks away through air, and goes dark once a block stands between; a
 * relay standing diagonal to the sender never links, and a third relay linked
 * to the receiver alone lights its lamp too, two hops from the lever.
 * relay-prism-carries-the-signal-through-air
 */
public final class RelayTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String RELAY = "goo:pulse_relay";
    private static final BlockPos SENDER = new BlockPos(1, 1, 2);
    private static final BlockPos RECEIVER = SENDER.east(12);
    private static final BlockPos LEVER = SENDER.west();
    private static final BlockPos LAMP = RECEIVER.east();
    /** A cell on the line between the relays, where the block goes. */
    private static final BlockPos BETWEEN = SENDER.east(6);
    /** A relay off every axis through the sender, diagonal to it. */
    private static final BlockPos DIAGONAL = SENDER.east(6).south(4);
    private static final BlockPos DIAGONAL_LAMP = DIAGONAL.east();
    /** A third relay linked to the receiver alone, two hops from the sender. */
    private static final BlockPos THIRD = RECEIVER.south(5);
    private static final BlockPos THIRD_LAMP = THIRD.east();
    private static final int SETTLE = 3;
    private static final int PULL = SETTLE;
    private static final int LIT_CHECK = PULL + SETTLE;
    private static final int BLOCKED = LIT_CHECK + 1;
    /** Past the lamp's own delay in going dark. */
    private static final int DARK_CHECK = BLOCKED + 8;
    private static final String COMBO_TOOK = "Both prisms should hold the relay combo";
    private static final String UNLIT_BEFORE = "The lamp should stand unlit before the lever is pulled";
    private static final String LIT_THROUGH_AIR = "The lamp should light once the lever 12 blocks away is pulled";
    private static final String UNLIT_DIAGONALLY = "The lamp at the diagonal relay should stay unlit: relays link along an axis only";
    private static final String LIT_TWO_HOPS = "The lamp at the third relay should light: the signal crosses the network hop by hop";
    private static final String DARK_WHEN_BLOCKED = "The lamp should go dark once a block stands between the relays";

    private RelayTests() {
    }

    /**
     * Two relay prisms 12 blocks apart through air, a lever at one and a lamp
     * at the other: the lamp lights with the lever, and goes dark once a
     * block stands between them though the lever holds.
     *
     * @param helper the gametest helper
     */
    public static void relayCarriesThroughAir(GameTestHelper helper) {
        for (BlockPos cell : BlockPos.betweenClosed(LEVER, LAMP)) {
            helper.setBlock(cell.below(), Blocks.STONE);
            helper.setBlock(cell, Blocks.AIR);
        }
        relayAt(helper, SENDER);
        relayAt(helper, RECEIVER);
        helper.setBlock(DIAGONAL.below(), Blocks.STONE);
        helper.setBlock(DIAGONAL_LAMP.below(), Blocks.STONE);
        relayAt(helper, DIAGONAL);
        helper.setBlock(DIAGONAL_LAMP, Blocks.REDSTONE_LAMP);
        for (BlockPos cell : BlockPos.betweenClosed(RECEIVER.south(), THIRD_LAMP)) {
            helper.setBlock(cell.below(), Blocks.STONE);
            helper.setBlock(cell, Blocks.AIR);
        }
        relayAt(helper, THIRD);
        helper.setBlock(THIRD_LAMP, Blocks.REDSTONE_LAMP);
        helper.setBlock(LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
        helper.setBlock(LAMP, Blocks.REDSTONE_LAMP);
        helper.assertTrue(helper.getBlockEntity(SENDER, PrismBlockEntity.class).hasCombo()
                && helper.getBlockEntity(RECEIVER, PrismBlockEntity.class).hasCombo(), COMBO_TOOK);
        helper.runAfterDelay(PULL, () -> {
            helper.assertFalse(lit(helper, LAMP), UNLIT_BEFORE);
            helper.pullLever(LEVER);
        });
        helper.runAfterDelay(LIT_CHECK, () -> {
            helper.assertTrue(lit(helper, LAMP), LIT_THROUGH_AIR);
            helper.assertFalse(lit(helper, DIAGONAL_LAMP), UNLIT_DIAGONALLY);
            helper.assertTrue(lit(helper, THIRD_LAMP), LIT_TWO_HOPS);
        });
        helper.runAfterDelay(BLOCKED, () -> helper.setBlock(BETWEEN, Blocks.STONE));
        helper.runAfterDelay(DARK_CHECK, () -> {
            helper.assertFalse(lit(helper, LAMP), DARK_WHEN_BLOCKED);
            helper.succeed();
        });
    }

    private static void relayAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, GooBlocks.PRISM.get().defaultBlockState().setValue(PrismBlock.FACING, Direction.UP));
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.PULSE,
                NO_ENTITY, helper.absolutePos(pos), Direction.UP, RELAY));
    }

    private static boolean lit(GameTestHelper helper, BlockPos lamp) {
        return helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT);
    }
}
