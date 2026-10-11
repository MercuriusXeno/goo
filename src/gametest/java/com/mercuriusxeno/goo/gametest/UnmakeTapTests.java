package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.ability.program.UnmakeRule;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapDripGrade;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.Map;

/**
 * Gametest for Unmake's tap: an unstable tap over cobblestone leaves it
 * standing one drip short of the unstable crucible's melt time for it, and
 * on that last drip melts it into the full goo the crucible would give.
 * decision unmake-drip-dissolves-the-block-below
 */
public final class UnmakeTapTests {

    private static final BlockPos TAP_POS = new BlockPos(1, 2, 1);
    private static final BlockPos BELOW_POS = TAP_POS.below();
    /** unstable_unmake_tap.json's work_per_goo and yield. */
    /** Ticks for the drips of one fill to fall and land at one mB a tick, with slack. */
    private static final int LAND_TICKS = 40;
    private static final double DROP_REACH = 2;
    private static final String SHORT_GONE = "The cobblestone should stand one drip short of its %d drips of work";
    private static final String NOT_GONE = "The cobblestone should be gone after its %d drips of work";
    private static final String WRONG_YIELD = "The cobblestone should leave %s, left %s";

    private UnmakeTapTests() {
    }

    /**
     * Fills an unstable tap with one drip fewer than the cobblestone below
     * takes, asserts it stands once they land, adds the last drip, and
     * asserts the cobblestone gone and its yield dropped.
     *
     * @param helper the gametest helper
     */
    public static void unmakeTapDissolvesBelow(GameTestHelper helper) {
        helper.setBlock(BELOW_POS, Blocks.COBBLESTONE);
        GooValue cobblestone = GooValues.of(helper.getLevel()).lookup(new ItemStack(Blocks.COBBLESTONE));
        int drips = UnmakeRule.workToUnmake(cobblestone.totalGoo(), GooConfig.UNSTABLE_MELT_EXPONENT.get(), 1);
        TapBlockEntity tap = unstableTap(helper);
        tap.insertGoo(GooTypes.UNSTABLE, drips - 1);
        helper.runAfterDelay(LAND_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(BELOW_POS).is(Blocks.COBBLESTONE), String.format(SHORT_GONE, drips));
            tap.insertGoo(GooTypes.UNSTABLE, 1);
        });
        helper.runAfterDelay(2L * LAND_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(BELOW_POS).isAir(), String.format(NOT_GONE, drips));
            GooContents expected = cobblestone.toGooContents();
            Map<ResourceKey<GooTypeDefinition>, Integer> dropped = droppedGoo(helper);
            helper.assertTrue(expected.getAll().equals(dropped), String.format(WRONG_YIELD, expected.getAll(), dropped));
            helper.succeed();
        });
    }

    /**
     * Stands an open tap dripping one mB a tick, so each mB is one drip, its canister in.
     *
     * @param helper the gametest helper
     * @return the tap
     */
    private static TapBlockEntity unstableTap(GameTestHelper helper) {
        helper.setBlock(TAP_POS, GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.OPEN, true));
        TapBlockEntity tap = helper.getBlockEntity(TAP_POS, TapBlockEntity.class);
        tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
        tap.setDripGrade(TapDripGrade.ONE_PER_TICK);
        return tap;
    }

    /**
     * Sums the goo dropped near the cobblestone, by type.
     *
     * @param helper the gametest helper
     * @return the dropped goo per type
     */
    private static Map<ResourceKey<GooTypeDefinition>, Integer> droppedGoo(GameTestHelper helper) {
        AABB reach = new AABB(helper.absolutePos(BELOW_POS)).inflate(DROP_REACH);
        Map<ResourceKey<GooTypeDefinition>, Integer> dropped = new HashMap<>();
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, reach)) {
            ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(item.getItem());
            if (type != null) {
                dropped.merge(type, GooStacks.volumeOf(item.getItem()), Integer::sum);
            }
        }
        return dropped;
    }
}
