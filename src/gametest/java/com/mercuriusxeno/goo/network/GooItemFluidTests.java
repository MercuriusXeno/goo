package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * A dropped goo item survives lava that burns a stick away, its goo whole,
 * and rises through water to rest at its surface.
 * goo-items-are-fire-immune-and-do-not-sink-in-water
 */
@RegistersGameTests
public final class GooItemFluidTests {

    private static final String GOO_SURVIVES_LAVA = "goo_item_survives_lava";
    private static final String GOO_FLOATS_ON_WATER = "goo_item_floats_on_water";
    /** The bed of the fluid, in the middle of the bay. */
    private static final BlockPos BED = new BlockPos(2, 1, 2);
    /** The water column's top block, the second over the bed's floor. */
    private static final BlockPos WATER_TOP = BED.above();
    /** How many blocks over the water's top the item is thrown down from. */
    private static final int THROWN_FROM = 3;
    /** The item's downward throw, blocks a tick. */
    private static final double THROWN_DOWN = -0.5;
    /** The vertical speed, blocks a tick, under which the item reads as resting rather than bouncing through. */
    private static final double REST_SPEED = 0.01;
    /** The goo the dropped item holds. */
    private static final int HELD_MB = 100;
    /** Ticks well past the time a goo item takes to rise two blocks of water and settle. */
    private static final int SETTLE_TICKS = 200;
    private static final String GOO_BURNT = "The goo item should stand in the lava with its %d mb, stands %s";
    private static final String STICK_STANDS = "The stick should burn away in the lava";
    private static final String GOO_SUNK = "The goo item should rest within the water's top block %s, stands at y %s,"
            + " on the ground %s, moving %s";

    private GooItemFluidTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GOO_SURVIVES_LAVA, GooItemFluidTests::gooItemSurvivesLava);
        GooTestFunctions.registerTest(registrar, GOO_FLOATS_ON_WATER, GooItemFluidTests::gooItemFloatsOnWater);
    }

    /**
     * A goo item and a stick dropped into a lava source: once the stick has
     * burnt away, the goo item still stands in the lava, its goo unchanged.
     *
     * @param helper the gametest helper
     */
    public static void gooItemSurvivesLava(GameTestHelper helper) {
        walledColumn(helper, Blocks.LAVA, 1);
        ItemEntity goo = drop(helper, GooStacks.createForOutput(GooTypes.BLAZE, HELD_MB));
        ItemEntity stick = drop(helper, new ItemStack(Items.STICK));
        helper.succeedWhen(() -> {
            helper.assertTrue(!stick.isAlive(), STICK_STANDS);
            boolean whole = goo.isAlive() && GooStacks.volumeOf(goo.getItem()) == HELD_MB
                    && goo.blockPosition().equals(helper.absolutePos(BED));
            helper.assertTrue(whole, String.format(GOO_BURNT, HELD_MB, goo));
        });
    }

    /**
     * A goo item thrown down into a two block deep water column, of Kinetic's
     * goo, the most elastic, comes to rest within the water's top block.
     *
     * @param helper the gametest helper
     */
    public static void gooItemFloatsOnWater(GameTestHelper helper) {
        walledColumn(helper, Blocks.WATER, 2);
        Vec3 over = Vec3.atBottomCenterOf(helper.absolutePos(WATER_TOP.above(THROWN_FROM)));
        ItemEntity goo = new ItemEntity(helper.getLevel(), over.x, over.y, over.z,
                GooStacks.createForOutput(GooTypes.KINETIC, HELD_MB), 0, THROWN_DOWN, 0);
        helper.getLevel().addFreshEntity(goo);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            BlockPos top = helper.absolutePos(WATER_TOP);
            boolean resting = Math.abs(goo.getDeltaMovement().y) < REST_SPEED;
            helper.assertTrue(goo.isAlive() && goo.blockPosition().equals(top) && resting,
                    String.format(GOO_SUNK, top, goo.getY(), goo.onGround(), goo.getDeltaMovement()));
            helper.succeed();
        });
    }

    /**
     * Stands a fluid column on a stone floor, walled in stone, so its sources stay still.
     */
    private static void walledColumn(GameTestHelper helper, Block fluid, int depth) {
        helper.setBlock(BED.below(), Blocks.STONE);
        for (int up = 0; up < depth; up++) {
            BlockPos cell = BED.above(up);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                helper.setBlock(cell.relative(side), Blocks.STONE);
            }
            helper.setBlock(cell, fluid);
        }
    }

    /**
     * Drops an item at rest on the fluid's bed.
     */
    private static ItemEntity drop(GameTestHelper helper, ItemStack stack) {
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(BED));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack, 0, 0, 0);
        helper.getLevel().addFreshEntity(item);
        return item;
    }
}
