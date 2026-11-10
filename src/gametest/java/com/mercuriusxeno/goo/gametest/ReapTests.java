package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.program.SlashStep;
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
 * Gametests for Leaf Reap: a slash reaps the ripe plants its path crosses as
 * it reaches them, the wheat replanted at age zero and the bush picked back to
 * age one, and leaves the ripe plants beside its path standing.
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
        GooTestFunctions.registerTest(registrar, REAP_SLASH_CUTS, ReapTests::reapSlashCutsTheMobOnItsPathOnce);
    }

    private static final int GROUND_Y = 1;
    private static final int PATH_START_X = 1;
    private static final int PATH_END_X = 6;
    /** The row the slash flies along, through the centre of its cells. */
    private static final int PATH_Z = 2;
    /** The row beside it, which the slash never crosses. */
    private static final int BESIDE_Z = 3;
    private static final BlockPos ON_PATH = new BlockPos(3, GROUND_Y + 1, PATH_Z);
    private static final BlockPos BESIDE_PATH = new BlockPos(3, GROUND_Y + 1, BESIDE_Z);
    private static final BlockPos BUSH = new BlockPos(5, GROUND_Y + 1, PATH_Z);
    private static final int FLIGHT_TICKS = 5;
    /** The slash's flight, and a margin for the queue to run. */
    private static final int FLIGHT_DONE_TICKS = FLIGHT_TICKS + 3;
    private static final double ITEM_SEARCH_RADIUS = 2;
    private static final String WHEAT_REPLANTED = "Ripe wheat at %s should stand replanted at age zero, stands %s";
    private static final String WHEAT_LEFT_RIPE = "Ripe wheat beside the path at %s should stand ripe, stands %s";
    /** The row a cow stands in clear of the slash and its blade. */
    private static final int OFF_PATH_Z = 5;
    private static final double CUT_X = 3.5;
    /** How far above the floor the slash flies, through a cow's body. */
    private static final double CUT_HEIGHT = 0.8;
    private static final int NO_SLASHER = -1;
    private static final float CUT_DAMAGE = 3;
    /** Wider than the second slash's whole flight, so every tick it crosses the cow falls inside it. */
    private static final int CUT_WINDOW = 10;
    private static final float HEALTH_TOLERANCE = 0.01f;
    private static final String CUT_ONCE = "The cow on the path should lose %s health to one cut, lost %s";
    private static final String LEFT_WHOLE = "The cow off the path should stand whole, lost %s";
    private static final String BUSH_PICKED = "The ripe bush should be picked back to age one, stands at %d";
    private static final String SEEDS_SETTLED = "Drops holding %s should settle to %d seeds beside one wheat, settled %s";

    private ReapTests() {
    }

    /**
     * A slash flown along a row reaps the ripe plants its path crosses: the
     * wheat on the path drops its wheat and stands replanted at age zero, the
     * bush on the path drops its berries and falls back to age one, and the
     * ripe wheat in the row beside stands ripe.
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
        Vec3 origin = helper.absoluteVec(new Vec3(PATH_START_X + 0.5, GROUND_Y + 1.5, PATH_Z + 0.5));
        Vec3 path = new Vec3(PATH_END_X - PATH_START_X, 0, 0);
        helper.runAfterDelay(1, () -> SlashStep.reapAlong(helper.getLevel(), origin, path,
                helper.getLevel().getGameTime(), FLIGHT_TICKS));
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
     * Two slashes flown along a row inside one cow's invulnerability window
     * cut the cow standing on the path once, for the slash's damage, and
     * leave the cow standing off the path whole.
     *
     * @param helper the gametest helper
     */
    public static void reapSlashCutsTheMobOnItsPathOnce(GameTestHelper helper) {
        for (int x = PATH_START_X; x <= PATH_END_X; x++) {
            for (int z = PATH_Z; z <= OFF_PATH_Z; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        Mob onPath = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(CUT_X, GROUND_Y, PATH_Z + 0.5));
        Mob offPath = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(CUT_X, GROUND_Y, OFF_PATH_Z + 0.5));
        Vec3 origin = helper.absoluteVec(new Vec3(PATH_START_X + 0.5, GROUND_Y + CUT_HEIGHT, PATH_Z + 0.5));
        Vec3 path = new Vec3(PATH_END_X - PATH_START_X, 0, 0);
        helper.runAfterDelay(1, () -> {
            long now = helper.getLevel().getGameTime();
            SlashCuts.launch(helper.getLevel(), new SlashCuts.Cut(NO_SLASHER, origin, path, now, FLIGHT_TICKS,
                    CUT_DAMAGE, CUT_WINDOW));
            SlashCuts.launch(helper.getLevel(), new SlashCuts.Cut(NO_SLASHER, origin, path, now + 1, FLIGHT_TICKS,
                    CUT_DAMAGE, CUT_WINDOW));
        });
        helper.runAfterDelay(1 + FLIGHT_DONE_TICKS, () -> {
            float cut = onPath.getMaxHealth() - onPath.getHealth();
            helper.assertTrue(Math.abs(cut - CUT_DAMAGE) < HEALTH_TOLERANCE, String.format(CUT_ONCE, CUT_DAMAGE, cut));
            float whole = offPath.getMaxHealth() - offPath.getHealth();
            helper.assertTrue(whole < HEALTH_TOLERANCE, String.format(LEFT_WHOLE, whole));
            helper.succeed();
        });
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
