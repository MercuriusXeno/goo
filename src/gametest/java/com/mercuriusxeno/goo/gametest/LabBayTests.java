package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.lab.LabBays;
import com.mercuriusxeno.goo.lab.LabBox;
import com.mercuriusxeno.goo.lab.LabBuilder;
import com.mercuriusxeno.goo.lab.LabLayout;
import com.mercuriusxeno.goo.lab.LabMachine;
import com.mercuriusxeno.goo.lab.LabPen;
import com.mercuriusxeno.goo.lab.LabPlan;
import com.mercuriusxeno.goo.lab.LabPlot;
import com.mercuriusxeno.goo.lab.LabRigs;
import com.mercuriusxeno.goo.lab.LabSpawn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests that build one Goo Lab bay or zone into a test instance through
 * {@link LabBuilder#buildWithin} and read its state back through the block
 * entities' own accessors (decision lab-holds-bays-supply-pens-kit).
 */
@RegistersGameTests
public final class LabBayTests {
    private static final String LAB_BAY_HUB = "lab_bay_hub";
    private static final String LAB_BAY_GASKET = "lab_bay_gasket";
    private static final String LAB_PENS_AND_RANGE = "lab_pens_and_range";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LAB_BAY_HUB, LabBayTests::hubBay);
        GooTestFunctions.registerTest(registrar, LAB_BAY_GASKET, LabBayTests::gasketBay);
        GooTestFunctions.registerTest(registrar, LAB_PENS_AND_RANGE, LabBayTests::pensAndRange);
    }


    /**
     * Where a single bay's north-west corner lands inside the test instance.
     */
    private static final BlockPos BAY_CORNER = new BlockPos(1, 0, 1);
    private static final int HUB_FILLED_SLOTS = 4;
    private static final String HUB_SLOTS = "Hub should hold a filled canister in each rigged slot";
    private static final String NO_SOURCE_GASKET = "Gasket run source should carry a bottom gasket";
    private static final String NO_RECEIVER_GASKET = "Gasket run receiver should carry a top gasket";
    private static final String NOT_PAIRED = "Registry should pair the source gasket to the receiver gasket";
    private static final String NO_PARTNER = "Receiver should name the source as its top partner";
    private static final String MOB_MISSING = "Pen should hold each planned ";

    private LabBayTests() {
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
            filled += hub.getSlotFluidContent(slot).totalVolume() == LabRigs.CANISTER_FILL ? 1 : 0;
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
     * Each pen holds as many of each planned mob as it plans, the one observation of LabBuilder's spawns; the fence
     * rings, range targets and firing line stand in LabLayoutTest and LabTests#buildShell.
     *
     * @param helper the gametest helper
     */
    public static void pensAndRange(GameTestHelper helper) {
        LabPlan plan = LabLayout.plan();
        BlockPos origin = LabTests.centredOrigin(helper, plan.bounds());
        for (LabPen pen : plan.pens()) {
            LabTests.buildRegion(helper, origin, plan, pen.bounds());
        }
        plan.pens().forEach(pen -> assertMobsInside(helper, origin, plan, pen));
        helper.succeed();
    }
    /**
     * Asserts as many entities of each of a pen's planned mob types stand inside its interior as the pen plans.
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
            long planned = penSpawns.stream().filter(s -> s.entityId().equals(spawn.entityId())).count();
            List<Entity> found = helper.getLevel().getEntities((Entity) null, inside, e -> e.getType() == type);
            helper.assertTrue(found.size() == planned, MOB_MISSING + BuiltInRegistries.ENTITY_TYPE.getKey(type));
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
