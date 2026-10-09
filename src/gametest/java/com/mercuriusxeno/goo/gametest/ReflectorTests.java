package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.ability.LightRailBlock;
import com.mercuriusxeno.goo.block.ability.RailLine;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

/**
 * Gametest for glow's Reflector through the real landing path: glow goo
 * naming Reflector lands on two prisms across the bay's long diagonal, and
 * the line between them fills with light rail that lights its cells and
 * burns a zombie standing in it; a block set across the line breaks the
 * link, and its rail goes (decision reflector-rails-carry-the-brightest-light).
 */
public final class ReflectorTests {

    private static final BlockPos LOW_PRISM = new BlockPos(0, 1, 0);
    private static final BlockPos HIGH_PRISM = new BlockPos(5, 5, 5);
    private static final String REFLECTOR = "goo:glow_reflector";
    private static final int NO_ENTITY = -1;
    /** A reflector refreshes every 20 ticks; this many see both refresh after both land. */
    private static final int LINKED_TICKS = 45;
    private static final int BROKEN_TICKS = LINKED_TICKS * 2;
    private static final int FLOOR_Y = 1;

    private static final String NO_RAIL = "Every cell between the reflectors should be light rail, %s is %s";
    private static final String UNLIT = "A rail cell should light to its rail's level %d, it reads %d";
    private static final String NOT_BURNED = "A zombie standing in the rail should be burned by it";
    private static final String RAIL_LEFT = "The broken link's rail should be gone, %s still stands";

    private ReflectorTests() {
    }

    /**
     * Two reflectors link by a lit rail that burns a zombie standing in it;
     * a block across the line breaks the link and clears its rail.
     *
     * @param helper the gametest helper
     */
    public static void reflectorsLinkLightAndBurn(GameTestHelper helper) {
        List<BlockPos> line = RailLine.between(LOW_PRISM, HIGH_PRISM);
        BlockPos standing = line.stream().filter(cell -> cell.getY() == FLOOR_Y).findFirst().orElseThrow();
        BlockPos breaking = line.get(line.size() / 2);
        standPrism(helper, LOW_PRISM);
        standPrism(helper, HIGH_PRISM);
        helper.setBlock(standing.below(), Blocks.STONE);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, standing);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float before = zombie.getHealth();
        landReflector(helper, LOW_PRISM);
        landReflector(helper, HIGH_PRISM);
        helper.runAfterDelay(LINKED_TICKS, () -> {
            for (BlockPos cell : line) {
                helper.assertTrue(helper.getBlockState(cell).is(GooBlocks.LIGHT_RAIL.get()),
                        String.format(NO_RAIL, cell, helper.getBlockState(cell)));
            }
            int level = helper.getBlockState(breaking).getValue(LightRailBlock.LEVEL);
            int light = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(breaking));
            helper.assertTrue(light == level, String.format(UNLIT, level, light));
            helper.assertTrue(zombie.getHealth() < before, NOT_BURNED);
            helper.setBlock(breaking, Blocks.STONE);
        });
        helper.runAfterDelay(BROKEN_TICKS, () -> {
            for (BlockPos cell : line) {
                helper.assertFalse(helper.getBlockState(cell).is(GooBlocks.LIGHT_RAIL.get()),
                        String.format(RAIL_LEFT, cell));
            }
            helper.succeed();
        });
    }

    private static void standPrism(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at.below(), Blocks.STONE);
        helper.setBlock(at, GooBlocks.PRISM.get());
    }

    private static void landReflector(GameTestHelper helper, BlockPos prism) {
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), null, GooTypes.GLOW, NO_ENTITY,
                helper.absolutePos(prism), Direction.UP, REFLECTOR));
        arrivals.drainArrivedEffects(arrivalTick);
    }
}
