package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.fluid.GooBucketItem;
import com.mercuriusxeno.goo.fluid.GooFluidBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Gametests for the one generic goo fluid (decision generic-goo-fluids): a
 * placed goo block keeps the type its bucket stamped on its block entity,
 * two types side by side stay themselves, and the fluid type's positional
 * density, viscosity, temperature and extinguishing read the stamped type.
 */
public final class GooFluidTests {

    private static final BlockPos BLAZE_POS = new BlockPos(1, 2, 1);
    private static final BlockPos FROST_POS = BLAZE_POS.east();
    private static final BlockPos BEYOND_POS = FROST_POS.east();
    /**
     * Rock stands in for the viscosity comparison, since the bundled blaze
     * and frost share one viscosity.
     */
    private static final BlockPos ROCK_POS = BLAZE_POS.south();
    /**
     * Ticks a placed goo sits before the assertions, past the fluid tick
     * delay a spread would need.
     */
    private static final int SETTLE_TICKS = 20;
    private static final int FIRE_TICKS = 200;

    private static final String NOT_GOO_BLOCK = "Bucket should place the goo fluid block at ";
    private static final String WRONG_TYPE = "Goo fluid block entity should carry the type its bucket stamped at ";
    private static final String SPREAD_BEYOND = "Goo should not have spread into the air beyond the frost block";
    private static final String SAME_TEMPERATURE = "Blaze and frost goo should answer different temperatures";
    private static final String SAME_VISCOSITY = "Rock and frost goo should answer different viscosities";
    private static final String SAME_DENSITY = "Blaze and frost goo should answer different densities";
    private static final String FROST_NOT_EXTINGUISHING = "Frost goo should extinguish at its position";
    private static final String BLAZE_EXTINGUISHING = "Blaze goo should not extinguish at its position";
    private static final String COW_IN_FROST_BURNS = "A burning cow standing in frost goo should be put out";
    private static final String COW_IN_BLAZE_OUT = "A burning cow standing in blaze goo should keep burning";

    private GooFluidTests() {
    }

    /**
     * Blaze and frost buckets emptied side by side place two goo fluid
     * blocks whose block entities carry their own types, and after the
     * settle window each still carries its own and no goo stands beyond.
     *
     * @param helper the gametest helper
     */
    public static void placedTypesStaySideBySide(GameTestHelper helper) {
        placeFloor(helper);
        emptyBucket(helper, GooTypes.BLAZE, BLAZE_POS);
        emptyBucket(helper, GooTypes.FROST, FROST_POS);
        assertStamped(helper, BLAZE_POS, GooTypes.BLAZE);
        assertStamped(helper, FROST_POS, GooTypes.FROST);

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            assertStamped(helper, BLAZE_POS, GooTypes.BLAZE);
            assertStamped(helper, FROST_POS, GooTypes.FROST);
            helper.assertTrue(helper.getBlockState(BEYOND_POS).isAir(), SPREAD_BEYOND);
            helper.succeed();
        });
    }

    /**
     * The positional fluid type overloads answer the stamped type's fields,
     * so blaze and frost differ in temperature and density, rock and frost
     * differ in viscosity, and only frost extinguishes; a burning cow in
     * frost goo is put out while one in blaze goo keeps burning.
     *
     * @param helper the gametest helper
     */
    public static void fluidFieldsReadStampedType(GameTestHelper helper) {
        placeFloor(helper);
        emptyBucket(helper, GooTypes.BLAZE, BLAZE_POS);
        emptyBucket(helper, GooTypes.FROST, FROST_POS);
        emptyBucket(helper, GooTypes.ROCK, ROCK_POS);
        assertPositionalFields(helper);

        Mob blazeCow = spawnBurningCow(helper, BLAZE_POS);
        Mob frostCow = spawnBurningCow(helper, FROST_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(!frostCow.isOnFire(), COW_IN_FROST_BURNS);
            helper.assertTrue(blazeCow.isOnFire(), COW_IN_BLAZE_OUT);
            helper.succeed();
        });
    }

    private static void assertPositionalFields(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos blaze = helper.absolutePos(BLAZE_POS);
        BlockPos frost = helper.absolutePos(FROST_POS);
        BlockPos rock = helper.absolutePos(ROCK_POS);
        FluidState blazeState = level.getFluidState(blaze);
        FluidState frostState = level.getFluidState(frost);
        FluidState rockState = level.getFluidState(rock);
        FluidType type = blazeState.getFluidType();
        helper.assertTrue(type.getTemperature(blazeState, level, blaze) != type.getTemperature(frostState, level, frost),
                SAME_TEMPERATURE);
        helper.assertTrue(type.getDensity(blazeState, level, blaze) != type.getDensity(frostState, level, frost),
                SAME_DENSITY);
        helper.assertTrue(type.getViscosity(rockState, level, rock) != type.getViscosity(frostState, level, frost),
                SAME_VISCOSITY);
        helper.assertTrue(type.canExtinguish(frostState, level, frost), FROST_NOT_EXTINGUISHING);
        helper.assertTrue(!type.canExtinguish(blazeState, level, blaze), BLAZE_EXTINGUISHING);
    }

    private static Mob spawnBurningCow(GameTestHelper helper, BlockPos pos) {
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, pos);
        cow.setRemainingFireTicks(FIRE_TICKS);
        return cow;
    }

    /**
     * Lays stone under every goo position and the air beyond, so the goo
     * and the cows have a floor.
     *
     * @param helper the gametest helper
     */
    private static void placeFloor(GameTestHelper helper) {
        helper.setBlock(BLAZE_POS.below(), Blocks.STONE);
        helper.setBlock(FROST_POS.below(), Blocks.STONE);
        helper.setBlock(BEYOND_POS.below(), Blocks.STONE);
        helper.setBlock(ROCK_POS.below(), Blocks.STONE);
    }

    /**
     * Empties the type's bucket at a position the way a player would, which
     * places the generic fluid block and stamps the type on its block entity.
     *
     * @param helper the gametest helper
     * @param type   the goo type whose bucket to empty, bundled or datapack-added
     * @param pos    the structure-relative position to place into
     */
    private static void emptyBucket(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, BlockPos pos) {
        ItemStack bucket = GooBucketItem.of(type);
        GooItems.GOO_BUCKET.get().emptyContents(null, helper.getLevel(), helper.absolutePos(pos), null, bucket);
        helper.assertBlockPresent(GooBlocks.GOO_FLUID.get(), pos);
    }

    private static void assertStamped(GameTestHelper helper, BlockPos pos, ResourceKey<GooTypeDefinition> type) {
        helper.assertTrue(helper.getBlockState(pos).is(GooBlocks.GOO_FLUID.get()), NOT_GOO_BLOCK + pos);
        ResourceKey<GooTypeDefinition> stamped = GooFluidBlockEntity.typeAt(helper.getLevel(), helper.absolutePos(pos));
        helper.assertTrue(type.equals(stamped), WRONG_TYPE + pos);
    }
}
