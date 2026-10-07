package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.lab.LabBox;
import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabLayout;
import com.mercuriusxeno.goo.lab.LabMachine;
import com.mercuriusxeno.goo.lab.LabOffset;
import com.mercuriusxeno.goo.lab.LabPen;
import com.mercuriusxeno.goo.lab.LabPlacement;
import com.mercuriusxeno.goo.lab.LabPlan;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests that build the Goo Lab plan through {@link LabBuilder} into a
 * test instance and read the level back (decision lab-built-from-code). The
 * lab test instances carry padding wide enough to hold the whole plan.
 */
public final class LabTests {

    /**
     * The sign line the builder writes the machine name on.
     */
    private static final int NAME_LINE = 1;
    /**
     * Divisor that halves a span to centre the plan on the instance.
     */
    private static final int HALF = 2;
    private static final String WRONG_BLOCK = "Planned block missing at ";
    private static final String WRONG_SIGN_TEXT = "Sign should name its machine at ";
    private static final String MACHINE_UNREGISTERED = "Lab machine names no registered goo block: ";
    private static final String BUILD_FAILED = "Lab plan failed to build: ";
    private static final String COUNT_MISMATCH = "Build should report one placement per planned block";
    private static final String ZOMBIE_PEN = "Zombie";
    private static final String PASSIVE_PEN = "Passive";
    private static final String NOT_ONLY_ZOMBIES = "Zombie pen should hold zombies alone, found ";
    private static final String TOO_FEW_ZOMBIES = "Zombie pen should hold every planned zombie";
    private static final String ZOMBIE_PEN_OPEN = "Zombie pen should stand under tinted glass at ";
    private static final String PASSIVE_PEN_ROOFED = "Passive pen should stand open to the sky at ";
    private static final String PASSIVE_RING_OPEN = "Passive pen fence ring should stand at ";

    private LabTests() {
    }

    /**
     * Builds the whole plan and asserts the floor plate and every plot's sign
     * stand at their planned offsets, each sign naming its machine.
     *
     * @param helper the gametest helper
     */
    public static void buildShell(GameTestHelper helper) {
        LabPlan plan = LabBuilder.planFor(helper.getLevel());
        BlockPos origin = centredOrigin(helper, plan.bounds());
        helper.assertTrue(buildPlan(helper, origin, plan) == plan.placements().size(), COUNT_MISMATCH);
        for (LabPlacement placement : plan.placements()) {
            assertPlaced(helper, origin, placement);
        }
        for (LabMachine machine : LabMachine.values()) {
            Identifier id = Identifier.fromNamespaceAndPath(Goo.MODID, machine.blockPath());
            helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), MACHINE_UNREGISTERED + id);
        }
        helper.succeed();
    }

    /**
     * Builds the zombie pen and asserts it holds every planned zombie and no
     * other entity, under its tinted glass roof.
     * decision lab-zombie-pen-and-open-passive-pens
     *
     * @param helper the gametest helper
     */
    public static void zombiePenHoldsOnlyZombies(GameTestHelper helper) {
        LabPlan plan = LabLayout.plan();
        LabPen pen = pen(plan, ZOMBIE_PEN);
        BlockPos origin = centredOrigin(helper, plan.bounds());
        buildRegion(helper, origin, plan, pen.bounds());
        List<Entity> inside = helper.getLevel().getEntities((Entity) null, interiorBox(origin, pen), entity -> true);
        for (Entity entity : inside) {
            helper.assertTrue(entity.getType() == EntityType.ZOMBIE, NOT_ONLY_ZOMBIES + entity.getType());
        }
        helper.assertTrue(inside.size() == pen.mobs().size(), TOO_FEW_ZOMBIES);
        forEachRoofColumn(pen, roof -> helper.assertTrue(
                helper.getLevel().getBlockState(LabBuilder.worldPos(origin, roof)).is(Blocks.TINTED_GLASS),
                ZOMBIE_PEN_OPEN + roof));
        helper.succeed();
    }

    /**
     * Builds the passive pen over a stale tinted glass roof through a clear and
     * build, and asserts its fence ring stands with air at roof height across the whole pen.
     * decision lab-zombie-pen-and-open-passive-pens
     *
     * @param helper the gametest helper
     */
    public static void passivePenHasNoRoof(GameTestHelper helper) {
        LabPlan plan = LabLayout.plan();
        LabPen pen = pen(plan, PASSIVE_PEN);
        BlockPos origin = centredOrigin(helper, plan.bounds());
        forEachRoofColumn(pen, roof ->
                helper.getLevel().setBlockAndUpdate(LabBuilder.worldPos(origin, roof), Blocks.TINTED_GLASS.defaultBlockState()));
        LabBuilder.clear(helper.getLevel(), origin, pen.bounds());
        buildRegion(helper, origin, plan, pen.bounds());
        forEachRoofColumn(pen, roof -> helper.assertTrue(
                helper.getLevel().getBlockState(LabBuilder.worldPos(origin, roof)).isAir(), PASSIVE_PEN_ROOFED + roof));
        LabOffset corner = pen.bounds().min();
        BlockPos fence = LabBuilder.worldPos(origin, corner.shifted(0, 1, 0));
        helper.assertTrue(helper.getLevel().getBlockState(fence).getBlock() instanceof FenceBlock, PASSIVE_RING_OPEN + fence);
        helper.succeed();
    }

    /**
     * Answers the pen the plan names by its sign.
     *
     * @param plan the plan
     * @param name the pen's display name
     * @return the pen
     */
    private static LabPen pen(LabPlan plan, String name) {
        return plan.pens().stream().filter(p -> name.equals(p.displayName())).findFirst().orElseThrow();
    }

    /**
     * Answers the world box over a pen's interior.
     *
     * @param origin the world position of the plan's zero offset
     * @param pen    the pen
     * @return the box
     */
    private static AABB interiorBox(BlockPos origin, LabPen pen) {
        BlockPos low = LabBuilder.worldPos(origin, pen.interior().min());
        BlockPos high = LabBuilder.worldPos(origin, pen.interior().max());
        return new AABB(low.getX(), low.getY(), low.getZ(), high.getX() + 1, high.getY() + 1, high.getZ() + 1);
    }

    /**
     * Visits every offset at a pen's roof height, across its whole footprint.
     *
     * @param pen     the pen
     * @param visitor what to do at each offset
     */
    private static void forEachRoofColumn(LabPen pen, Consumer<LabOffset> visitor) {
        LabOffset min = pen.bounds().min();
        LabOffset max = pen.bounds().max();
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                visitor.accept(new LabOffset(x, max.y(), z));
            }
        }
    }

    /**
     * Builds a plan through {@link LabBuilder}, failing the test when a state does not parse.
     *
     * @param helper the gametest helper
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan to build
     * @return the number of placements set
     */
    static int buildPlan(GameTestHelper helper, BlockPos origin, LabPlan plan) {
        return buildRegion(helper, origin, plan, plan.bounds());
    }

    /**
     * Builds the part of a plan inside a region, failing the test when a state does not parse.
     *
     * @param helper the gametest helper
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan to build from
     * @param region the part to build, in plan offsets
     * @return the number of placements set
     */
    static int buildRegion(GameTestHelper helper, BlockPos origin, LabPlan plan, LabBox region) {
        try {
            return LabBuilder.buildWithin(helper.getLevel(), origin, plan, region);
        } catch (CommandSyntaxException e) {
            helper.fail(BUILD_FAILED + e.getMessage());
            return 0;
        }
    }

    /**
     * Answers the world origin that centres a plan's footprint on the test instance.
     *
     * @param helper the gametest helper
     * @param bounds the plan's bounds
     * @return the world position of the plan's zero offset
     */
    static BlockPos centredOrigin(GameTestHelper helper, LabBox bounds) {
        int halfX = (bounds.max().x() - bounds.min().x()) / HALF;
        int halfZ = (bounds.max().z() - bounds.min().z()) / HALF;
        return helper.absolutePos(new BlockPos(-halfX, 0, -halfZ));
    }

    /**
     * Asserts one placement's block, and a sign's text, stand in the level.
     *
     * @param helper    the gametest helper
     * @param origin    the world position of the plan's zero offset
     * @param placement the placement to check
     */
    private static void assertPlaced(GameTestHelper helper, BlockPos origin, LabPlacement placement) {
        BlockPos pos = LabBuilder.worldPos(origin, placement.offset());
        BlockState state = helper.getLevel().getBlockState(pos);
        helper.assertTrue(state.is(plannedBlock(placement)), WRONG_BLOCK + placement.offset());
        if (placement.isSign()) {
            SignBlockEntity sign = (SignBlockEntity) helper.getLevel().getBlockEntity(pos);
            String written = sign == null ? null : sign.getFrontText().getMessage(NAME_LINE, false).getString();
            helper.assertTrue(placement.signText().equals(written), WRONG_SIGN_TEXT + placement.offset());
        }
    }

    /**
     * Answers the block a placement plans.
     *
     * @param placement the placement
     * @return the planned block
     */
    static Block plannedBlock(LabPlacement placement) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId(placement.blockState())));
    }

    /**
     * Strips a block state string's property list, leaving the block id.
     *
     * @param blockState the state in command syntax
     * @return the block id
     */
    private static String blockId(String blockState) {
        int bracket = blockState.indexOf('[');
        return bracket < 0 ? blockState : blockState.substring(0, bracket);
    }
}
