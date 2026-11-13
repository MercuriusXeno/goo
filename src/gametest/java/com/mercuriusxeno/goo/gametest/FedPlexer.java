package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A plexer targeting stone with a canister block above it holding the goo
 * for a number of crafts, the way a player feeds one, and the count of the
 * stone it has ejected.
 */
public final class FedPlexer {

    /** The item every fed plexer reconstitutes. */
    public static final Item TARGET = Items.STONE;
    private static final String TARGET_HAS_VALUE = "Stone should hold a goo value for the plexer to pay";

    private FedPlexer() {
    }

    /**
     * Places a plexer whose hatch opens toward the side given, a canister
     * block above it, and one canister per goo type stone's value names,
     * each holding that type for the crafts asked.
     *
     * @param helper the gametest helper
     * @param pos    the plexer's position, relative to the test
     * @param hatch  the side the plexer's hatch opens toward
     * @param crafts how many stones the goo pays for
     * @return the plexer's block entity
     */
    public static PlexerBlockEntity place(GameTestHelper helper, BlockPos pos, Direction hatch, int crafts) {
        helper.setBlock(pos, GooBlocks.PLEXER.get().defaultBlockState().setValue(PlexerBlock.FACING, hatch.getOpposite()));
        PlexerBlockEntity plexer = helper.getBlockEntity(pos, PlexerBlockEntity.class);
        plexer.setTargetItem(new ItemStack(TARGET));
        GooValue value = GooValues.of(helper.getLevel()).lookup(BuiltInRegistries.ITEM.getKey(TARGET));
        helper.assertTrue(value != null && !value.isEmpty(), TARGET_HAS_VALUE);
        helper.setBlock(pos.above(), GooBlocks.CANISTER.get());
        CanisterBlockEntity canisters = helper.getBlockEntity(pos.above(), CanisterBlockEntity.class);
        int slot = 0;
        for (var type : value.fabricateCeilingAll().entrySet()) {
            ItemStack canister = new ItemStack(GooItems.CANISTER.get());
            CanisterItem.addGoo(canister, type.getKey(), type.getValue() * crafts);
            canisters.insertCanister(slot++, canister, false);
        }
        return plexer;
    }

    /**
     * Counts the stone standing as item entities anywhere in the test.
     *
     * @param helper the gametest helper
     * @return the stones ejected
     */
    public static int ejected(GameTestHelper helper) {
        return helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(TARGET)).mapToInt(ItemStack::getCount).sum();
    }
}
