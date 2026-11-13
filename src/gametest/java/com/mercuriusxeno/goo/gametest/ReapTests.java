package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.reap.ReapSlash;
import com.mercuriusxeno.goo.ability.reap.Reaping;
import com.mercuriusxeno.goo.ability.reap.SlashCuts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Leaf Reap: a slash reaps the ripe plants its band reaches
 * inside its area, the wheat replanted at age zero and the bush picked back
 * to age one, and leaves the ripe plants outside it standing; and every
 * slash cuts each mob inside its area once, through its immunity.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@RegistersGameTests
public final class ReapTests {
    private static final String REAP_SLASH_REAPS = "reap_slash_reaps_the_plants_on_its_path";
    private static final String REAP_SETTLES_SEEDS = "reap_settles_seeds_against_the_replant";
    private static final String REAP_SLASH_CUTS = "reap_slash_cuts_the_mob_on_its_path_once";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, REAP_SLASH_REAPS, ReapTests::reapSlashReapsThePlantsInItsArea);
        GooTestFunctions.registerTest(registrar, REAP_SETTLES_SEEDS, ReapTests::reapSettlesSeedsAgainstTheReplant);
        GooTestFunctions.registerTest(registrar, REAP_SLASH_CUTS, ReapTests::reapSlashCutsTheMobInItsAreaEachTime);
    }

    private static final int GROUND_Y = 1;
    private static final int FIELD_START_X = 1;
    private static final int FIELD_END_X = 6;
    private static final int INSIDE_Z = 2;
    /** A row clear of the slash's area. */
    private static final int OUTSIDE_Z = 5;
    private static final BlockPos INSIDE = new BlockPos(3, GROUND_Y + 1, INSIDE_Z);
    private static final BlockPos OUTSIDE = new BlockPos(3, GROUND_Y + 1, OUTSIDE_Z);
    private static final BlockPos BUSH = new BlockPos(4, GROUND_Y + 1, INSIDE_Z);
    /** The test slash's own life, set here and not read from the ability. */
    private static final int LIFE_TICKS = 4;
    /**
     * A small slash's area and band: 2.4 wide reaching 3 blocks ahead, so
     * thrown along the row from just short of the crops and the near cow it
     * holds the wheat, the bush and the near cow and leaves the far row out.
     */
    private static final ReapSlash.Shape SMALL = new ReapSlash.Shape(1.2, 1.5, 0.5, 0.6, 1.5, 1.0, LIFE_TICKS);
    private static final double SLASH_HEIGHT = 1.17;
    private static final double THROWN_X = 2.0;
    /** The slash's life, and a margin for its last tick to run. */
    private static final int SLASH_DONE_TICKS = LIFE_TICKS + 3;
    private static final double ITEM_SEARCH_RADIUS = 2;
    private static final String WHEAT_REPLANTED = "Ripe wheat at %s should stand replanted at age zero, stands %s";
    private static final String WHEAT_LEFT_RIPE = "Ripe wheat outside the slash at %s should stand ripe, stands %s";
    private static final double CUT_X = 3.5;
    private static final int NO_SLASHER = -1;
    private static final float CUT_DAMAGE = 1;
    /** Two slashes over the same cow, the second while the first still stands. */
    private static final int SLASHES = 2;
    private static final int SLASH_GAP_TICKS = 2;
    private static final float HEALTH_TOLERANCE = 0.01f;
    private static final String CUT_EACH = "The cow inside should lose %s health, one cut a slash, lost %s";
    private static final String LEFT_WHOLE = "The cow outside the slash should stand whole, lost %s";
    private static final String BUSH_PICKED = "The ripe bush should be picked back to age one, stands at %d";
    private static final String SEEDS_SETTLED = "Drops holding %s should settle to %d seeds beside one wheat, settled %s";

    private ReapTests() {
    }

    /**
     * A slash over a crop row reaps the ripe plants inside its area: the wheat
     * there drops its wheat and stands replanted at age zero, the bush there
     * drops its berries and falls back to age one, and the ripe wheat in a
     * row outside the area stands ripe.
     *
     * @param helper the gametest helper
     */
    public static void reapSlashReapsThePlantsInItsArea(GameTestHelper helper) {
        for (int x = FIELD_START_X; x <= FIELD_END_X; x++) {
            for (int z = INSIDE_Z; z <= OUTSIDE_Z; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, GROUND_Y, z), Blocks.FARMLAND.defaultBlockState()
                        .setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
            }
        }
        BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
        helper.setBlock(INSIDE, ripe);
        helper.setBlock(OUTSIDE, ripe);
        helper.setBlock(BUSH.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(BUSH, Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                .setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE));
        SlashCuts.Throw thrown = thrownAlongTheRow(helper, GROUND_Y + 1);
        helper.runAfterDelay(1, () -> SlashCuts.launch(helper.getLevel(), thrown,
                helper.getLevel().getGameTime(), CUT_DAMAGE));
        helper.runAfterDelay(1 + SLASH_DONE_TICKS, () -> {
            BlockState reaped = helper.getBlockState(INSIDE);
            helper.assertTrue(reaped.is(Blocks.WHEAT) && reaped.getValue(CropBlock.AGE) == 0,
                    String.format(WHEAT_REPLANTED, INSIDE, reaped));
            BlockState outside = helper.getBlockState(OUTSIDE);
            helper.assertTrue(outside.is(Blocks.WHEAT) && outside.getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    String.format(WHEAT_LEFT_RIPE, OUTSIDE, outside));
            int bushAge = helper.getBlockState(BUSH).getValue(SweetBerryBushBlock.AGE);
            helper.assertTrue(bushAge == 1, String.format(BUSH_PICKED, bushAge));
            helper.assertItemEntityPresent(Items.WHEAT, INSIDE, ITEM_SEARCH_RADIUS);
            helper.assertItemEntityPresent(Items.SWEET_BERRIES, BUSH, ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    /**
     * Two slashes over a cow, the second while the first still stands, cut
     * it once each, the second through the immunity the first left, though
     * it stands inside each area for the whole of the slash's life; the cow
     * outside the areas stands whole.
     *
     * @param helper the gametest helper
     */
    public static void reapSlashCutsTheMobInItsAreaEachTime(GameTestHelper helper) {
        for (int x = FIELD_START_X; x <= FIELD_END_X; x++) {
            for (int z = INSIDE_Z; z <= OUTSIDE_Z; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Mob inside = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(CUT_X, GROUND_Y, INSIDE_Z + 0.5));
        Mob outside = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(CUT_X, GROUND_Y, OUTSIDE_Z + 0.5));
        SlashCuts.Throw thrown = thrownAlongTheRow(helper, GROUND_Y);
        helper.runAfterDelay(1, () -> {
            long now = helper.getLevel().getGameTime();
            for (int slash = 0; slash < SLASHES; slash++) {
                SlashCuts.launch(helper.getLevel(), thrown, now + (long) slash * SLASH_GAP_TICKS, CUT_DAMAGE);
            }
        });
        helper.runAfterDelay(1 + SLASH_GAP_TICKS + SLASH_DONE_TICKS, () -> {
            float cut = inside.getMaxHealth() - inside.getHealth();
            helper.assertTrue(Math.abs(cut - CUT_DAMAGE * SLASHES) < HEALTH_TOLERANCE,
                    String.format(CUT_EACH, CUT_DAMAGE * SLASHES, cut));
            float whole = outside.getMaxHealth() - outside.getHealth();
            helper.assertTrue(whole < HEALTH_TOLERANCE, String.format(LEFT_WHOLE, whole));
            helper.succeed();
        });
    }

    /**
     * A small level slash thrown along the inside row with no slasher
     * standing, from just short of the row's third cell, its feet at a height.
     *
     * @param helper the gametest helper
     * @param feetY  the height its slasher's feet would stand at, in the test's frame
     * @return the slash as thrown
     */
    private static SlashCuts.Throw thrownAlongTheRow(GameTestHelper helper, double feetY) {
        Vec3 from = helper.absoluteVec(new Vec3(THROWN_X, feetY, INSIDE_Z + 0.5));
        Vec3 alongTheRow = helper.absoluteVec(new Vec3(THROWN_X + 1, feetY, INSIDE_Z + 0.5)).subtract(from);
        return new SlashCuts.Throw(new ReapSlash(Vec3.ZERO, alongTheRow.normalize(), 0, SLASH_HEIGHT, SMALL), from,
                NO_SLASHER);
    }

    /**
     * A ripe crop's seeds settle against the seed it was replanted from: the
     * seeds split across its drops are tallied and one is paid out of the
     * tally, and the other loot drops untouched. ReapingSeedsTest holds the
     * count left, clamped at none; the unit suite cannot build an ItemStack,
     * so the drops settle here.
     *
     * @param helper the gametest helper
     */
    public static void reapSettlesSeedsAgainstTheReplant(GameTestHelper helper) {
        List<ItemStack> split = new ArrayList<>(List.of(new ItemStack(Items.WHEAT),
                new ItemStack(Items.WHEAT_SEEDS, 1), new ItemStack(Items.WHEAT_SEEDS, 2)));

        Reaping.settleSeeds(split, Items.WHEAT_SEEDS, 1);

        helper.assertTrue(counted(split, Items.WHEAT_SEEDS) == 2 && counted(split, Items.WHEAT) == 1,
                String.format(SEEDS_SETTLED, "three split seeds", 2, split));
        helper.succeed();
    }

    private static int counted(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
