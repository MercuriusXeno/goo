package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.reap.BladeFlights;
import com.mercuriusxeno.goo.ability.reap.ReapBlade;
import com.mercuriusxeno.goo.ability.reap.Reaping;
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
 * Gametests for Leaf Reap: a blade reaps the ripe plants it covers as it
 * reaches them, the wheat replanted at age zero and the bush picked back to
 * age one, and leaves the ripe plants clear of it standing; and every blade
 * that reaches a mob cuts it, through its immunity, once.
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
        GooTestFunctions.registerTest(registrar, REAP_SLASH_REAPS, ReapTests::reapSlashReapsThePlantsOnItsPath);
        GooTestFunctions.registerTest(registrar, REAP_SETTLES_SEEDS, ReapTests::reapSettlesSeedsAgainstTheReplant);
        GooTestFunctions.registerTest(registrar, REAP_SLASH_CUTS, ReapTests::reapSlashCutsTheMobOnItsPathEachTime);
    }

    private static final int GROUND_Y = 1;
    private static final int PATH_START_X = 1;
    private static final int PATH_END_X = 6;
    /** The row the blade flies along, its middle over the centre of its cells. */
    private static final int PATH_Z = 2;
    /** A row clear of the blade's width. */
    private static final int BESIDE_Z = 5;
    private static final BlockPos ON_PATH = new BlockPos(3, GROUND_Y + 1, PATH_Z);
    private static final BlockPos BESIDE_PATH = new BlockPos(3, GROUND_Y + 1, BESIDE_Z);
    private static final BlockPos BUSH = new BlockPos(4, GROUND_Y + 1, PATH_Z);
    /** leaf_reap.json's shallowest blade: forming 0.75 blocks out, drifting 3 more, 2.5 wide and 0.8 deep. */
    private static final ReapBlade.Shape SHAPE = new ReapBlade.Shape(0.75, 3, 2.5, 0.8, 6, 8, 3);
    /** East, the way the blade flies along its row. */
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Across the row, the way the blade's width spans. */
    private static final Vec3 ACROSS = new Vec3(0, 0, 1);
    /** The blade's life, and a margin for its last tick to run. */
    private static final int FLIGHT_DONE_TICKS = SHAPE.lifeTicks() + 3;
    private static final double ITEM_SEARCH_RADIUS = 2;
    private static final String WHEAT_REPLANTED = "Ripe wheat at %s should stand replanted at age zero, stands %s";
    private static final String WHEAT_LEFT_RIPE = "Ripe wheat clear of the blade at %s should stand ripe, stands %s";
    private static final double CUT_X = 3.5;
    /** How far above the floor the blade flies, through a cow's body. */
    private static final double CUT_HEIGHT = 0.8;
    private static final int NO_SLASHER = -1;
    private static final float CUT_DAMAGE = 1;
    /** Two swings, each of two blades, so a cut per blade would double the loss. */
    private static final int SWINGS = 2;
    /** The ticks from one swing to the next, past the first swing's second blade. */
    private static final int SWING_GAP_TICKS = 3;
    private static final float HEALTH_TOLERANCE = 0.01f;
    private static final String CUT_EACH = "The cow on the path should lose %s health, one cut a swing, lost %s";
    private static final String LEFT_WHOLE = "The cow clear of the blade should stand whole, lost %s";
    private static final String BUSH_PICKED = "The ripe bush should be picked back to age one, stands at %d";
    private static final String SEEDS_SETTLED = "Drops holding %s should settle to %d seeds beside one wheat, settled %s";

    private ReapTests() {
    }

    /**
     * A blade flown along a row reaps the ripe plants it covers: the wheat
     * under it drops its wheat and stands replanted at age zero, the bush
     * under it drops its berries and falls back to age one, and the ripe
     * wheat in a row clear of its width stands ripe.
     *
     * @param helper the gametest helper
     */
    public static void reapSlashReapsThePlantsOnItsPath(GameTestHelper helper) {
        for (int x = PATH_START_X; x <= PATH_END_X; x++) {
            for (int z = PATH_Z; z <= BESIDE_Z; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, GROUND_Y, z), Blocks.FARMLAND.defaultBlockState()
                        .setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
            }
        }
        BlockState ripe = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE);
        helper.setBlock(ON_PATH, ripe);
        helper.setBlock(BESIDE_PATH, ripe);
        helper.setBlock(BUSH.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(BUSH, Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                .setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE));
        ReapBlade blade = rowBlade(helper, GROUND_Y + 1.5);
        helper.runAfterDelay(1, () -> BladeFlights.launchSwing(helper.getLevel(), List.of(blade),
                helper.getLevel().getGameTime(), NO_SLASHER, CUT_DAMAGE));
        helper.runAfterDelay(1 + FLIGHT_DONE_TICKS, () -> {
            BlockState reaped = helper.getBlockState(ON_PATH);
            helper.assertTrue(reaped.is(Blocks.WHEAT) && reaped.getValue(CropBlock.AGE) == 0,
                    String.format(WHEAT_REPLANTED, ON_PATH, reaped));
            BlockState beside = helper.getBlockState(BESIDE_PATH);
            helper.assertTrue(beside.is(Blocks.WHEAT) && beside.getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    String.format(WHEAT_LEFT_RIPE, BESIDE_PATH, beside));
            int bushAge = helper.getBlockState(BUSH).getValue(SweetBerryBushBlock.AGE);
            helper.assertTrue(bushAge == 1, String.format(BUSH_PICKED, bushAge));
            helper.assertItemEntityPresent(Items.WHEAT, ON_PATH, ITEM_SEARCH_RADIUS);
            helper.assertItemEntityPresent(Items.SWEET_BERRIES, BUSH, ITEM_SEARCH_RADIUS);
            helper.succeed();
        });
    }

    /**
     * Two swings of two blades each, flown along a row, cut the cow standing
     * on the path once a swing, however many of a swing's blades reach it, the
     * second swing's cut landing through the immunity the first left, and
     * leave the cow standing clear of their width whole.
     *
     * @param helper the gametest helper
     */
    public static void reapSlashCutsTheMobOnItsPathEachTime(GameTestHelper helper) {
        for (int x = PATH_START_X; x <= PATH_END_X; x++) {
            for (int z = PATH_Z; z <= BESIDE_Z; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Mob onPath = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(CUT_X, GROUND_Y, PATH_Z + 0.5));
        Mob offPath = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(CUT_X, GROUND_Y, BESIDE_Z + 0.5));
        List<ReapBlade> swing = List.of(rowBlade(helper, GROUND_Y + CUT_HEIGHT), rowBlade(helper, GROUND_Y + CUT_HEIGHT));
        helper.runAfterDelay(1, () -> {
            long now = helper.getLevel().getGameTime();
            for (int launch = 0; launch < SWINGS; launch++) {
                BladeFlights.launchSwing(helper.getLevel(), swing, now + (long) launch * SWING_GAP_TICKS, NO_SLASHER,
                        CUT_DAMAGE);
            }
        });
        helper.runAfterDelay(1 + SWING_GAP_TICKS + FLIGHT_DONE_TICKS + swing.size(), () -> {
            float cut = onPath.getMaxHealth() - onPath.getHealth();
            helper.assertTrue(Math.abs(cut - CUT_DAMAGE * SWINGS) < HEALTH_TOLERANCE,
                    String.format(CUT_EACH, CUT_DAMAGE * SWINGS, cut));
            float whole = offPath.getMaxHealth() - offPath.getHealth();
            helper.assertTrue(whole < HEALTH_TOLERANCE, String.format(LEFT_WHOLE, whole));
            helper.succeed();
        });
    }

    /**
     * A level blade launched from the row's start, flying east along it, its
     * width spanning across the row and centred on it.
     *
     * @param helper the gametest helper
     * @param height how far above the test's floor it flies
     * @return the blade
     */
    private static ReapBlade rowBlade(GameTestHelper helper, double height) {
        Vec3 origin = helper.absoluteVec(new Vec3(PATH_START_X + 0.5, height, PATH_Z + 0.5));
        return new ReapBlade(origin, EAST, ACROSS, 0, 0, 1, Vec3.ZERO, SHAPE);
    }

    /**
     * A ripe crop's seeds settle against the seed it was replanted from: the
     * seeds split across its drops are tallied and one is paid out of the
     * tally, a lone seed drops none, and a drop holding no seed stays at none
     * rather than owing one; the other loot drops untouched.
     *
     * @param helper the gametest helper
     */
    public static void reapSettlesSeedsAgainstTheReplant(GameTestHelper helper) {
        List<ItemStack> split = new ArrayList<>(List.of(new ItemStack(Items.WHEAT),
                new ItemStack(Items.WHEAT_SEEDS, 1), new ItemStack(Items.WHEAT_SEEDS, 2)));
        List<ItemStack> lone = new ArrayList<>(List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS)));
        List<ItemStack> none = new ArrayList<>(List.of(new ItemStack(Items.WHEAT)));

        Reaping.settleSeeds(split, Items.WHEAT_SEEDS, 1);
        Reaping.settleSeeds(lone, Items.WHEAT_SEEDS, 1);
        Reaping.settleSeeds(none, Items.WHEAT_SEEDS, 1);

        helper.assertTrue(counted(split, Items.WHEAT_SEEDS) == 2 && counted(split, Items.WHEAT) == 1,
                String.format(SEEDS_SETTLED, "three split seeds", 2, split));
        helper.assertTrue(counted(lone, Items.WHEAT_SEEDS) == 0 && counted(lone, Items.WHEAT) == 1,
                String.format(SEEDS_SETTLED, "a lone seed", 0, lone));
        helper.assertTrue(counted(none, Items.WHEAT_SEEDS) == 0 && counted(none, Items.WHEAT) == 1,
                String.format(SEEDS_SETTLED, "no seed", 0, none));
        helper.succeed();
    }

    private static int counted(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
