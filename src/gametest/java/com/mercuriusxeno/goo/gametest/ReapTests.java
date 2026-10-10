package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.reap.Reaping;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for Leaf Reap: a blob struck into a field of ripe wheat beside a
 * ripe sweet berry bush reaps them as its swell reaches them: the wheat drops
 * its wheat and stands replanted at age zero, the bush drops its berries and
 * falls back to age one.
 * reap-breeze-harvests-and-replants
 */
public final class ReapTests {

    private static final Identifier REAP = Identifier.fromNamespaceAndPath(Goo.MODID, "leaf_reap");
    private static final String ABILITY_REQUIRED = "Ability registry must hold leaf_reap";
    private static final int FIELD_MIN = 2;
    private static final int FIELD_MAX = 4;
    private static final int GROUND_Y = 1;
    private static final BlockPos BUSH = new BlockPos(5, GROUND_Y + 1, 3);
    /** Bare farmland at the field's edge, so the blob lands in the open cell above it. */
    private static final BlockPos STRUCK = new BlockPos(3, GROUND_Y, FIELD_MIN - 1);
    /** leaf_reap.json's swell ticks, and a margin for the queue to run. */
    private static final int SWELL_DONE_TICKS = 13;
    private static final double ITEM_SEARCH_RADIUS = 4;
    private static final String WHEAT_REPLANTED = "Ripe wheat at %s should stand replanted at age zero, stands %s";
    private static final String BUSH_PICKED = "The ripe bush should be picked back to age one, stands at %d";
    private static final String SEEDS_SETTLED = "Drops holding %s should settle to %d seeds beside one wheat, settled %s";

    private ReapTests() {
    }

    /**
     * A blob struck into a ripe wheat field beside a ripe sweet berry bush
     * reaps both: wheat and berries drop, every wheat stands at age zero, and
     * the bush stands at age one.
     *
     * @param helper the gametest helper
     */
    public static void reapHarvestsAndReplants(GameTestHelper helper) {
        for (int x = FIELD_MIN - 1; x <= FIELD_MAX + 2; x++) {
            for (int z = FIELD_MIN - 1; z <= FIELD_MAX + 1; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, GROUND_Y, z), Blocks.FARMLAND.defaultBlockState()
                        .setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
            }
        }
        for (int x = FIELD_MIN; x <= FIELD_MAX; x++) {
            for (int z = FIELD_MIN; z <= FIELD_MAX; z++) {
                helper.setBlock(new BlockPos(x, GROUND_Y + 1, z),
                        Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE));
            }
        }
        helper.setBlock(BUSH.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(BUSH, Blocks.SWEET_BERRY_BUSH.defaultBlockState()
                .setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE));
        AbilityDefinition reap = AbilityRegistry.of(helper.getLevel()).getAbility(REAP);
        helper.assertTrue(reap != null, ABILITY_REQUIRED);
        Vec3 aim = Vec3.atCenterOf(helper.absolutePos(STRUCK)).add(0, 0.5, 0);
        helper.runAfterDelay(1, () -> AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK),
                reap.gooType(), Direction.UP, reap, aim));
        helper.runAfterDelay(1 + SWELL_DONE_TICKS, () -> {
            for (int x = FIELD_MIN; x <= FIELD_MAX; x++) {
                for (int z = FIELD_MIN; z <= FIELD_MAX; z++) {
                    BlockPos wheat = new BlockPos(x, GROUND_Y + 1, z);
                    var state = helper.getBlockState(wheat);
                    helper.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 0,
                            String.format(WHEAT_REPLANTED, wheat, state));
                }
            }
            int bushAge = helper.getBlockState(BUSH).getValue(SweetBerryBushBlock.AGE);
            helper.assertTrue(bushAge == 1, String.format(BUSH_PICKED, bushAge));
            helper.assertItemEntityPresent(Items.WHEAT, STRUCK.above(), ITEM_SEARCH_RADIUS);
            helper.assertItemEntityPresent(Items.SWEET_BERRIES, BUSH, ITEM_SEARCH_RADIUS);
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
