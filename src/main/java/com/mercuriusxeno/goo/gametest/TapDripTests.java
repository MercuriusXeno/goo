package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * Gametests for the tap's drip: what it draws, and from where.
 */
public final class TapDripTests {

    private static final BlockPos TAP_POS = new BlockPos(1, 1, 1);
    private static final ResourceKey<GooTypeDefinition> TYPE = GooTypes.ROCK;
    private static final int START_VOLUME = 1000;
    private static final int DRIPS = 3;
    /**
     * Ticks past the last counted drip, fewer than an interval, so timing
     * slack in the test's first tick cannot add or drop a drip.
     */
    private static final int SETTLE_TICKS = 5;
    private static final int NEIGHBOR_SLOT = 4;
    private static final String TAP_VOLUME = "tap canister volume";
    private static final String NEIGHBOR_VOLUME = "neighbor canister volume";

    private TapDripTests() {
    }

    /**
     * A tap over a filled canister, ringed by filled canister blocks, loses
     * 1 mB per interval from its own canister and from nowhere else.
     *
     * @param helper the gametest helper
     */
    public static void tapDripDrawsOneMb(GameTestHelper helper) {
        List<BlockPos> neighbors = Direction.Plane.HORIZONTAL.stream()
                .map(TAP_POS::relative).toList();
        for (BlockPos neighbor : neighbors) {
            helper.setBlock(neighbor, GooBlocks.CANISTER.get());
            CanisterBlockEntity canister = helper.getBlockEntity(neighbor, CanisterBlockEntity.class);
            canister.insertCanister(NEIGHBOR_SLOT, new ItemStack(GooItems.CANISTER.get()), false);
            canister.insertGoo(NEIGHBOR_SLOT, TYPE, START_VOLUME);
        }
        TapBlockEntity tap = filledTap(helper);

        helper.runAfterDelay(DRIPS * TapBlockEntity.DRIP_INTERVAL + SETTLE_TICKS, () -> {
            helper.assertValueEqual(tap.getFluidContent().amount(), START_VOLUME - DRIPS, TAP_VOLUME);
            for (BlockPos neighbor : neighbors) {
                CanisterBlockEntity canister = helper.getBlockEntity(neighbor, CanisterBlockEntity.class);
                helper.assertValueEqual(canister.getSlotFluidContent(NEIGHBOR_SLOT).amount(), START_VOLUME,
                        NEIGHBOR_VOLUME);
            }
            helper.succeed();
        });
    }

    private static TapBlockEntity filledTap(GameTestHelper helper) {
        helper.setBlock(TAP_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(TAP_POS, TapBlockEntity.class);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        tap.insertGoo(TYPE, START_VOLUME);
        return tap;
    }
}
