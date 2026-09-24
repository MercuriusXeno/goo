package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.lab.LabBox;
import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabOffset;
import com.mercuriusxeno.goo.lab.LabPlacement;
import com.mercuriusxeno.goo.lab.LabPlan;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametest for {@code /goo lab rebuild}'s routine: an altered lab returns to
 * its plan in place and the world outside the lab bounds stays as it was
 * (decision lab-save-is-disposable).
 */
public final class LabRebuildTests {

    /**
     * Blocks from the foundation, the bounds' lowest layer, up past the floor to the walkway air.
     */
    private static final int FOUNDATION_TO_WALKWAY = 2;
    private static final String REBUILD_FAILED = "Lab rebuild failed: ";
    private static final String FOREIGN_KEPT = "Rebuild should clear a block the plan does not name";
    private static final String PLANNED_MISSING = "Rebuild should restore a broken planned block";
    private static final String STRAY_KEPT = "Rebuild should remove a stray entity inside the lab bounds";
    private static final String STRAY_MISSING = "The stray entity should spawn inside the lab bounds";
    private static final String OUTSIDE_TOUCHED = "Rebuild should leave a block outside the lab bounds untouched";

    private LabRebuildTests() {
    }

    /**
     * Builds the plan, alters it inside and outside its bounds, rebuilds, and
     * asserts the foreign block gone, the planned block back, the stray entity
     * removed and the outside marker untouched.
     *
     * @param helper the gametest helper
     */
    public static void rebuild(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        LabTests.buildPlan(helper, origin, plan);
        LabBox bounds = plan.bounds();
        BlockPos foreign = LabBuilder.worldPos(origin, unplannedAir(plan));
        helper.getLevel().setBlock(foreign, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        LabPlacement broken = plan.placements().stream().filter(p -> p.offset().y() == 0).findFirst().orElseThrow();
        BlockPos brokenPos = LabBuilder.worldPos(origin, broken.offset());
        helper.getLevel().setBlock(brokenPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Pig stray = EntityType.PIG.spawn(helper.getLevel(), foreign.above(), EntitySpawnReason.COMMAND);
        helper.assertTrue(stray != null, STRAY_MISSING);
        BlockPos outside = LabBuilder.worldPos(origin, bounds.max().shifted(1, 0, 0));
        helper.getLevel().setBlock(outside, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        try {
            LabBuilder.rebuild(helper.getLevel(), origin, plan);
        } catch (CommandSyntaxException e) {
            helper.fail(REBUILD_FAILED + e.getMessage());
        }
        helper.assertTrue(helper.getLevel().getBlockState(foreign).isAir(), FOREIGN_KEPT);
        helper.assertTrue(helper.getLevel().getBlockState(brokenPos).is(LabTests.plannedBlock(broken)), PLANNED_MISSING);
        helper.assertTrue(stray == null || stray.isRemoved(), STRAY_KEPT);
        helper.assertTrue(helper.getLevel().getBlockState(outside).is(Blocks.GOLD_BLOCK), OUTSIDE_TOUCHED);
        helper.succeed();
    }

    /**
     * Answers an offset inside the lab bounds that the plan leaves as air: the
     * walkway just above the floor's north-west corner.
     *
     * @param plan the plan
     * @return the offset
     */
    private static LabOffset unplannedAir(LabPlan plan) {
        LabOffset candidate = plan.bounds().min().shifted(0, FOUNDATION_TO_WALKWAY, 0);
        boolean planned = plan.placements().stream().anyMatch(p -> p.offset().equals(candidate));
        return planned ? plan.bounds().max() : candidate;
    }
}
