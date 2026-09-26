package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.lab.LabBays;
import com.mercuriusxeno.goo.lab.LabBox;
import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabLayout;
import com.mercuriusxeno.goo.lab.LabMachine;
import com.mercuriusxeno.goo.lab.LabOffset;
import com.mercuriusxeno.goo.lab.LabPen;
import com.mercuriusxeno.goo.lab.LabPlacement;
import com.mercuriusxeno.goo.lab.LabPlan;
import com.mercuriusxeno.goo.lab.LabPlot;
import com.mercuriusxeno.goo.lab.LabRigs;
import com.mercuriusxeno.goo.lab.LabSpawn;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.UUID;

/**
 * Gametests that build one Goo Lab bay or zone into a test instance through
 * {@link LabBuilder#buildWithin} and read its state back through the block
 * entities' own accessors (decision lab-holds-bays-supply-pens-kit).
 */
public final class LabBayTests {

    /**
     * Where a single bay's north-west corner lands inside the test instance.
     */
    private static final BlockPos BAY_CORNER = new BlockPos(1, 0, 1);
    private static final int HUB_FILLED_SLOTS = 4;
    private static final String NO_TAP = "Tap bay should stand a tap";
    private static final String TAP_EMPTY = "Tap should hold a slotted canister of goo";
    private static final String NO_GAP = "Tap should hang over air";
    private static final String NO_CATCH = "Tap should hang over a catch surface";
    private static final String HUB_SLOTS = "Hub should hold a filled canister in each rigged slot";
    private static final String NO_SOURCE_GASKET = "Gasket run source should carry a bottom gasket";
    private static final String NO_RECEIVER_GASKET = "Gasket run receiver should carry a top gasket";
    private static final String NOT_PAIRED = "Registry should pair the source gasket to the receiver gasket";
    private static final String NO_PARTNER = "Receiver should name the source as its top partner";
    private static final String RING_OPEN = "Pen fence ring should be closed at ";
    private static final String MOB_MISSING = "Pen should hold one ";
    private static final String TARGET_MISSING = "Range target missing at ";
    private static final String LINE_MISSING = "Firing line should stand in the floor at ";

    private LabBayTests() {
    }

    /**
     * The tap bay holds a filled, slotted canister over an air gap and a catch surface.
     *
     * @param helper the gametest helper
     */
    public static void tapBay(GameTestHelper helper) {
        LabPlan plan = LabLayout.plan();
        LabPlot plot = plot(plan, LabMachine.TAP);
        BlockPos origin = buildBay(helper, plan, plot);
        BlockPos tapPos = LabBuilder.worldPos(origin, plot.machineOffset());
        helper.assertTrue(helper.getLevel().getBlockState(tapPos).is(GooBlocks.TAP.get()), NO_TAP);
        TapBlockEntity tap = (TapBlockEntity) helper.getLevel().getBlockEntity(tapPos);
        helper.assertTrue(tap != null && !tap.getCanister().isEmpty()
                && tap.getFluidContent().amount() == LabRigs.CANISTER_FILL, TAP_EMPTY);
        helper.assertTrue(helper.getLevel().getBlockState(tapPos.below()).isAir(), NO_GAP);
        BlockPos catchPos = tapPos.below(LabBays.TAP_AIR_GAP + 1);
        helper.assertFalse(helper.getLevel().getBlockState(catchPos).getCollisionShape(
                helper.getLevel(), catchPos).isEmpty(), NO_CATCH);
        helper.succeed();
    }

    /**
     * The hub bay holds a filled canister in each rigged slot.
     *
     * @param helper the gametest helper
     */
    public static void hubBay(GameTestHelper helper) {
        LabPlan plan = LabLayout.plan();
        LabPlot plot = plot(plan, LabMachine.HUB);
        BlockPos origin = buildBay(helper, plan, plot);
        HubBlockEntity hub = (HubBlockEntity) helper.getLevel().getBlockEntity(
                LabBuilder.worldPos(origin, plot.machineOffset()));
        int filled = 0;
        for (int slot = 0; hub != null && slot < HubBlockEntity.MAX_CANISTERS; slot++) {
            filled += hub.getSlotFluidContent(slot).amount() == LabRigs.CANISTER_FILL ? 1 : 0;
        }
        helper.assertTrue(filled == HUB_FILLED_SLOTS, HUB_SLOTS);
        helper.succeed();
    }

    /**
     * The choral gasket bay pairs its two canister blocks: the registry links
     * the source's bottom gasket to the receiver's top gasket, and the receiver names its partner.
     *
     * @param helper the gametest helper
     */
    public static void gasketBay(GameTestHelper helper) {
        LabPlan plan = LabLayout.plan();
        LabPlot plot = plot(plan, LabMachine.CHORAL_GASKET);
        BlockPos origin = buildBay(helper, plan, plot);
        BlockPos sourcePos = LabBuilder.worldPos(origin, LabBays.gasketSource(plot));
        CanisterBlockEntity source = (CanisterBlockEntity) helper.getLevel().getBlockEntity(sourcePos);
        CanisterBlockEntity receiver = (CanisterBlockEntity) helper.getLevel().getBlockEntity(
                LabBuilder.worldPos(origin, LabBays.gasketReceiver(plot)));
        CanisterMetadata sent = source == null ? CanisterMetadata.EMPTY : source.getSlotMetadata(CanisterBlock.CENTER_SLOT);
        CanisterMetadata taken = receiver == null ? CanisterMetadata.EMPTY
                : receiver.getSlotMetadata(CanisterBlock.CENTER_SLOT);
        helper.assertTrue(sent.bottomGasketId() != null, NO_SOURCE_GASKET);
        helper.assertTrue(taken.topGasketId() != null, NO_RECEIVER_GASKET);
        UUID target = GasketRegistry.get(helper.getLevel()).getTarget(sent.bottomGasketId());
        helper.assertTrue(taken.topGasketId().equals(target), NOT_PAIRED);
        helper.assertTrue(taken.topPartner() != null && sourcePos.equals(taken.topPartner().pos()), NO_PARTNER);
        helper.succeed();
    }

    /**
     * The pens stand closed fence rings holding one of each planned mob, and
     * the range holds each target and its firing line.
     *
     * @param helper the gametest helper
     */
    public static void pensAndRange(GameTestHelper helper) {
        LabPlan plan = LabLayout.plan();
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        for (LabPen pen : plan.pens()) {
            LabTests.buildRegion(helper, origin, plan, pen.bounds());
        }
        LabTests.buildRegion(helper, origin, plan, plan.range().bounds());
        plan.pens().forEach(pen -> assertPen(helper, origin, plan, pen));
        for (LabPlacement target : plan.range().targets()) {
            BlockPos pos = LabBuilder.worldPos(origin, target.offset());
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(LabTests.plannedBlock(target)),
                    TARGET_MISSING + target.offset());
        }
        LabBox line = plan.range().firingLine();
        for (int x = line.min().x(); x <= line.max().x(); x++) {
            BlockPos pos = LabBuilder.worldPos(origin, new LabOffset(x, line.min().y(), line.min().z()));
            helper.assertTrue(helper.getLevel().getBlockState(pos).is(Blocks.RED_CONCRETE), LINE_MISSING + x);
        }
        helper.succeed();
    }

    /**
     * Asserts a pen's fence ring is closed and each of its mobs stands inside it.
     *
     * @param helper the gametest helper
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan
     * @param pen    the pen
     */
    private static void assertPen(GameTestHelper helper, BlockPos origin, LabPlan plan, LabPen pen) {
        LabBox bounds = pen.bounds();
        int fenceY = bounds.min().y() + 1;
        for (int x = bounds.min().x(); x <= bounds.max().x(); x++) {
            assertFence(helper, origin.offset(x, fenceY, bounds.min().z()));
            assertFence(helper, origin.offset(x, fenceY, bounds.max().z()));
        }
        for (int z = bounds.min().z(); z <= bounds.max().z(); z++) {
            assertFence(helper, origin.offset(bounds.min().x(), fenceY, z));
            assertFence(helper, origin.offset(bounds.max().x(), fenceY, z));
        }
        assertMobsInside(helper, origin, plan, pen);
    }

    /**
     * Asserts a fence stands at a ring position.
     *
     * @param helper the gametest helper
     * @param pos    the world position
     */
    private static void assertFence(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(helper.getLevel().getBlockState(pos).getBlock() instanceof FenceBlock, RING_OPEN + pos);
    }

    /**
     * Asserts one entity of each of a pen's planned mob types stands inside its interior.
     *
     * @param helper the gametest helper
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan
     * @param pen    the pen
     */
    private static void assertMobsInside(GameTestHelper helper, BlockPos origin, LabPlan plan, LabPen pen) {
        BlockPos low = LabBuilder.worldPos(origin, pen.interior().min());
        BlockPos high = LabBuilder.worldPos(origin, pen.interior().max());
        AABB inside = new AABB(low.getX(), low.getY(), low.getZ(), high.getX() + 1, high.getY() + 1, high.getZ() + 1);
        List<LabSpawn> penSpawns = plan.spawns().stream().filter(s -> pen.interior().contains(s.offset())).toList();
        for (LabSpawn spawn : penSpawns) {
            EntityType<?> type = EntityType.byString(spawn.entityId()).orElseThrow();
            List<Entity> found = helper.getLevel().getEntities((Entity) null, inside, e -> e.getType() == type);
            helper.assertTrue(found.size() == 1, MOB_MISSING + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
    }

    /**
     * Answers a machine's plot.
     *
     * @param plan    the plan
     * @param machine the machine
     * @return the machine's plot
     */
    private static LabPlot plot(LabPlan plan, LabMachine machine) {
        return plan.plots().stream().filter(p -> p.machine() == machine).findFirst().orElseThrow();
    }

    /**
     * Builds one plot's bay with its north-west corner at {@link #BAY_CORNER} in the test instance.
     *
     * @param helper the gametest helper
     * @param plan   the plan
     * @param plot   the plot to build
     * @return the world position of the plan's zero offset
     */
    private static BlockPos buildBay(GameTestHelper helper, LabPlan plan, LabPlot plot) {
        LabBox bounds = plot.bounds();
        BlockPos origin = helper.absolutePos(BAY_CORNER).offset(-bounds.min().x(), -bounds.min().y(), -bounds.min().z());
        LabTests.buildRegion(helper, origin, plan, bounds);
        return origin;
    }
}
