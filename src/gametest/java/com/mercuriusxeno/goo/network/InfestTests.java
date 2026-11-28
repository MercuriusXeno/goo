package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Zombie's Infest as a world goo through the real paths: its stream's drips
 * lay a layer of an Infest cell each, a cell gnaws the block under it a
 * stage along the goo:decay map, and it bites a living thing standing in it
 * and heals an undead one, each gnaw and each bite costing the cell a layer.
 * Each test walls its cells so no cell pours its layers away.
 * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
 * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 */
@RegistersGameTests
public final class InfestTests {

    private static final String DRIP_LAYS_A_LAYER = "infest_drip_lays_a_layer";
    private static final Identifier INFEST = Identifier.parse("goo:zombie_infest");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final String GNAWS_THE_BLOCK_UNDER_IT = "infest_gnaws_the_block_under_it";
    private static final String BITES_A_COW_AND_HEALS_A_ZOMBIE = "infest_bites_a_cow_and_heals_a_zombie";
    /** The full cell the gnaw test lays, on stone the goo:decay map steps to cobblestone. */
    private static final BlockPos GNAW_CELL = new BlockPos(2, 2, 2);
    /** The full cell the cow stands on. */
    private static final BlockPos COW_CELL = new BlockPos(1, 2, 1);
    /** The full cell the wounded zombie stands on. */
    private static final BlockPos ZOMBIE_CELL = new BlockPos(3, 2, 3);
    private static final String ABILITY_REQUIRED = "The ability registry must hold zombie_infest";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Infest of 1 layer, stands %s";
    private static final String NOT_GNAWED = "The stone under the Infest cell should be cobblestone, stands %s";
    private static final String COW_NOT_BITTEN = "The cow standing in Infest should fall from %.1f health, reads %.1f";
    private static final String ZOMBIE_NOT_HEALED = "The wounded zombie standing in Infest should rise from %.1f health,"
            + " reads %.1f";
    private static final String GNAW_COSTS_A_LAYER = "infest_gnaw_costs_a_layer";
    private static final String BITE_COSTS_A_LAYER = "infest_bite_costs_a_layer";
    /** The full cell each cost test lays, its one gnaw or its one bite the only cost a cadence takes. */
    private static final BlockPos COST_CELL = new BlockPos(2, 2, 2);
    /** A full cell less the one layer a single gnaw or bite costs. */
    private static final int ONE_LAYER_SPENT = VolumeCellBlock.MAX_LAYERS - 1;
    private static final String LAYERS_WRONG = "The Infest cell should read %d layers after its first act, read %d";
    private static final String NOT_ACTED = "The Infest cell has not gnawed or bitten yet";
    /** What a first act leaves to complain of when it left what it should. */
    private static final String FIRST_ACT_RIGHT = "";

    private InfestTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_LAYER, InfestTests::dripLaysALayer);
        GooTestFunctions.registerTest(registrar, GNAWS_THE_BLOCK_UNDER_IT, InfestTests::gnawsTheBlockUnderIt);
        GooTestFunctions.registerTest(registrar, BITES_A_COW_AND_HEALS_A_ZOMBIE, InfestTests::bitesACowAndHealsAZombie);
        GooTestFunctions.registerTest(registrar, GNAW_COSTS_A_LAYER, InfestTests::gnawCostsALayer);
        GooTestFunctions.registerTest(registrar, BITE_COSTS_A_LAYER, InfestTests::biteCostsALayer);
    }

    /**
     * A full Infest cell coating no side, on stone and holding no mob, gnaws
     * the stone under it to cobblestone and stands one layer lower for it.
     * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
     *
     * @param helper the gametest helper
     */
    public static void gnawCostsALayer(GameTestHelper helper) {
        layBareCostCell(helper, Blocks.STONE);
        succeedOnFirstAct(helper, () -> !helper.getBlockState(COST_CELL.below()).is(Blocks.STONE), () -> {
            BlockState under = helper.getBlockState(COST_CELL.below());
            return under.is(Blocks.COBBLESTONE) ? FIRST_ACT_RIGHT : String.format(NOT_GNAWED, under);
        });
    }

    /**
     * A full Infest cell coating no side, on obsidian the goo:decay map
     * leaves alone, bites the cow standing in it and stands one layer lower
     * for the bite.
     * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
     *
     * @param helper the gametest helper
     */
    public static void biteCostsALayer(GameTestHelper helper) {
        layBareCostCell(helper, Blocks.OBSIDIAN);
        Mob cow = helper.spawnWithNoFreeWill(EntityTypes.COW, Vec3.atBottomCenterOf(COST_CELL.above()));
        float cowStart = cow.getHealth();
        succeedOnFirstAct(helper, () -> cow.getHealth() < cowStart, () -> FIRST_ACT_RIGHT);
    }

    /**
     * Grades the cost cell on the first tick it acts, so a later cadence
     * cannot stand in for the first: the act must have left what it should
     * and the cell must read a full cell less one layer.
     *
     * @param helper    the gametest helper
     * @param acted     whether the cell has gnawed or bitten yet
     * @param actProblem what is wrong with what the act left, FIRST_ACT_RIGHT where nothing is
     */
    private static void succeedOnFirstAct(GameTestHelper helper, BooleanSupplier acted, Supplier<String> actProblem) {
        AtomicReference<String> firstActProblem = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (firstActProblem.get() == null && acted.getAsBoolean()) {
                int layers = layersOf(helper.getBlockState(COST_CELL));
                String problem = actProblem.get();
                firstActProblem.set(!problem.isEmpty() || layers == ONE_LAYER_SPENT ? problem
                        : String.format(LAYERS_WRONG, ONE_LAYER_SPENT, layers));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(firstActProblem.get() != null, NOT_ACTED);
            helper.assertTrue(firstActProblem.get().isEmpty(), firstActProblem.get());
        });
    }

    /**
     * The layers an Infest cell stands at, none where the cell is gone.
     *
     * @param state the block state at the cell
     * @return the cell's layers, 0 where no Infest stands
     */
    private static int layersOf(BlockState state) {
        return state.is(GooBlocks.ZOMBIE_INFEST.get()) ? state.getValue(VolumeCellBlock.LAYERS) : 0;
    }

    /**
     * A full Infest cell laid on stone, which the goo:decay map steps to
     * cobblestone, leaves cobblestone under it once its cadence has passed.
     * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
     *
     * @param helper the gametest helper
     */
    public static void gnawsTheBlockUnderIt(GameTestHelper helper) {
        layFullCell(helper, GNAW_CELL);
        helper.succeedWhen(() -> {
            BlockState under = helper.getBlockState(GNAW_CELL.below());
            helper.assertTrue(under.is(Blocks.COBBLESTONE), String.format(NOT_GNAWED, under));
        });
    }

    /**
     * A cow standing in a full Infest cell loses health, and a wounded zombie
     * standing in another gains it, once the cells' cadence has passed.
     * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
     * decay-and-hive-heal-an-undead-target
     *
     * @param helper the gametest helper
     */
    public static void bitesACowAndHealsAZombie(GameTestHelper helper) {
        layFullCell(helper, COW_CELL);
        layFullCell(helper, ZOMBIE_CELL);
        Mob cow = helper.spawnWithNoFreeWill(EntityTypes.COW, Vec3.atBottomCenterOf(COW_CELL.above()));
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(ZOMBIE_CELL.above()));
        zombie.setHealth(zombie.getMaxHealth() / 2);
        float cowStart = cow.getHealth();
        float zombieStart = zombie.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.getHealth() < cowStart, String.format(COW_NOT_BITTEN, cowStart, cow.getHealth()));
            helper.assertTrue(zombie.getHealth() > zombieStart,
                    String.format(ZOMBIE_NOT_HEALED, zombieStart, zombie.getHealth()));
        });
    }

    /**
     * Lays a full Infest cell on a stone floor, walled in stone on its four
     * sides so no layer pours away.
     *
     * @param helper the gametest helper
     * @param cell   the cell, relative to the test
     */
    private static void layFullCell(GameTestHelper helper, BlockPos cell) {
        layFullCell(helper, cell, Blocks.STONE, Blocks.STONE);
    }

    /**
     * Lays a full Infest cell coating no side on the floor given, walled in
     * iron bars, which hold its layers in and offer no sturdy face for its
     * goo to cling to.
     *
     * @param helper the gametest helper
     * @param floor  the block under the cell
     */
    private static void layBareCostCell(GameTestHelper helper, Block floor) {
        layFullCell(helper, COST_CELL, floor, Blocks.IRON_BARS);
    }

    /**
     * Lays a full Infest cell on the floor given, walled on its four sides
     * so no layer pours away.
     *
     * @param helper the gametest helper
     * @param cell   the cell, relative to the test
     * @param floor  the block under the cell
     * @param wall   the block on each of its four sides
     */
    private static void layFullCell(GameTestHelper helper, BlockPos cell, Block floor, Block wall) {
        helper.setBlock(cell.below(), floor);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), wall);
        }
        helper.setBlock(cell, GooBlocks.ZOMBIE_INFEST.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
    }

    /**
     * One Infest drip striking a walled stone top, from a zombie-holding
     * caster who pays for its layer, lays an Infest cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysALayer(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition infest = AbilityRegistry.of(helper.getLevel()).getAbility(INFEST);
        helper.assertTrue(infest != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOMBIE);
        KnownRecipes.teachRequires(caster, infest);
        LandedDrips.landOn(helper, caster, infest, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.ZOMBIE_INFEST.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }
}
