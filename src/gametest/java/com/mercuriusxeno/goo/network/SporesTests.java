package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SporesStep;
import com.mercuriusxeno.goo.block.ability.GoologgedFungus;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Shroom's Spores as a world goo through the real paths: one of its
 * stream's drips lays a one-layer Spores cell, a zombie standing in the goo
 * carries the mycosis effect and the spores; the goo grows a goologged
 * bud in its own space and buds on the open floor in its reach, and tends
 * the nether wart there, and spreads the mycelium it lies on. Each test walls its cells in stone so no cell
 * pours its layers away.
 * mycosis-is-the-residual-goo-that-infests-with-mushrooms
 * mycosis-goo-infects-what-touches-it-as-the-spray-did
 * mushrooms-and-buds-are-goo-logged-with-shroom-goo
 * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
 * mycosis-grows-and-reaps-nether-wart
 * colonize-blob-grows-the-network
 */
@RegistersGameTests
public final class SporesTests {

    private static final String DRIP_LAYS_A_SHROOM_CELL = "spores_drip_lays_a_shroom_cell_of_one_layer";
    private static final String GOO_INFECTS_A_ZOMBIE = "spores_goo_infects_a_zombie_standing_in_it";
    private static final String GROWS_BUDS = "spores_goo_grows_a_goologged_bud_and_one_beside_it";
    private static final String TENDS_WART = "spores_goo_grows_and_reaps_nether_wart";
    private static final String SPREADS_MYCELIUM = "spores_goo_on_mycelium_spreads_it_to_the_dirt_beside";
    /** The full cell the network test lays on mycelium, sealed in stone but for the dirt beside its floor. */
    private static final BlockPos NETWORK_CELL = new BlockPos(2, 2, 2);
    /** The dirt beside the mycelium the network cell lies on, under glass so no bud grows over it. */
    private static final BlockPos DIRT_BESIDE = NETWORK_CELL.below().east();
    /** The full cell the growth test lays, sealed in stone but for one open spot in its reach. */
    private static final BlockPos GROWING_CELL = new BlockPos(2, 2, 2);
    /** The one open spot over stone in the growing cell's reach, two blocks east. */
    private static final BlockPos BUD_SPOT = GROWING_CELL.east(2);
    /** How far around the growing cell the stone seals, the reach Spores ships. */
    private static final int SEAL_REACH = 2;
    /** The full cell the tending test lays, with nether wart two blocks either side. */
    private static final BlockPos TENDING_CELL = new BlockPos(2, 2, 2);
    private static final BlockPos YOUNG_WART = TENDING_CELL.east(2);
    private static final BlockPos RIPE_WART = TENDING_CELL.west(2);
    private static final double ITEM_SEARCH_RADIUS = 1.5;
    private static final Identifier SPORES = Identifier.parse("goo:shroom_spores");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    /** The full cell the zombie stands on. */
    private static final BlockPos INFECTING_CELL = new BlockPos(1, 2, 1);
    private static final String ABILITY_REQUIRED = "The ability registry must hold shroom_spores";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Spores of 1 layer, stands %s";
    private static final String NOT_INFECTED = "The zombie standing in Spores should carry the mycosis effect";
    private static final String NOT_SPORED = "The zombie standing in Spores should carry spores";
    private static final String NOT_GROWN = "The cell should hold a goologged bud at %d layers and a bud should stand"
            + " beside it; the cell stands %s and the spot %s";
    private static final String NOT_SPREAD = "The dirt beside should be mycelium and the cell stand at %d layers;"
            + " the dirt stands %s and the cell %s";
    private static final String NOT_TENDED = "The young wart should age and the ripe one stand replanted at age 0 with"
            + " its wart dropped; the young stands %s and the ripe %s";

    private SporesTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_SHROOM_CELL, SporesTests::dripLaysAShroomCell);
        GooTestFunctions.registerTest(registrar, GOO_INFECTS_A_ZOMBIE, SporesTests::gooInfectsAZombie);
        GooTestFunctions.registerTest(registrar, GROWS_BUDS, SporesTests::gooGrowsAGoologgedBudAndOneBesideIt);
        GooTestFunctions.registerTest(registrar, TENDS_WART, SporesTests::gooGrowsAndReapsNetherWart);
        GooTestFunctions.registerTest(registrar, SPREADS_MYCELIUM, SporesTests::gooOnMyceliumSpreadsItToTheDirtBeside);
    }

    /**
     * One Spores drip striking a walled stone top, from a shroom-holding
     * caster who pays for its layer, lays a Spores cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysAShroomCell(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        wallIn(helper, cell);
        AbilityDefinition spores = AbilityRegistry.of(helper.getLevel()).getAbility(SPORES);
        helper.assertTrue(spores != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.SHROOM);
        KnownRecipes.teachRequires(caster, spores);
        LandedDrips.landOn(helper, caster, spores, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.SHROOM_SPORES.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * A zombie standing on a full Spores cell carries the mycosis effect
     * and the spores that burst Spores from its corpse.
     * mycosis-goo-infects-what-touches-it-as-the-spray-did
     *
     * @param helper the gametest helper
     */
    public static void gooInfectsAZombie(GameTestHelper helper) {
        layFullCell(helper, INFECTING_CELL);
        Mob zombie = helmetedZombieOn(helper, INFECTING_CELL);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.hasEffect(GooMobEffects.MYCOSIS), NOT_INFECTED);
            helper.assertTrue(zombie.hasData(GooAttachments.SPORED), NOT_SPORED);
        });
    }

    /**
     * A full Spores cell sealed in stone but for one open spot over stone
     * two blocks east grows a goologged bud in its own space, the goo still
     * there, then a bud on that spot, and stands two buds' layers lower.
     * mushrooms-and-buds-are-goo-logged-with-shroom-goo
     * mycosis-goo-disperses-spores-and-grows-shrooms-nearby-depleting
     *
     * @param helper the gametest helper
     */
    public static void gooGrowsAGoologgedBudAndOneBesideIt(GameTestHelper helper) {
        BlockPos.betweenClosedStream(GROWING_CELL.offset(-SEAL_REACH, -1, -SEAL_REACH),
                GROWING_CELL.offset(SEAL_REACH, 1, SEAL_REACH)).forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        helper.setBlock(BUD_SPOT, Blocks.AIR);
        helper.setBlock(GROWING_CELL, GooBlocks.SHROOM_SPORES.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        int perBud = SporesStep.in(spores(helper).behaviors()).orElseThrow().layersPerBud();
        int left = VolumeCellBlock.MAX_LAYERS - 2 * perBud;
        helper.succeedWhen(() -> {
            BlockState cell = helper.getBlockState(GROWING_CELL);
            BlockState spot = helper.getBlockState(BUD_SPOT);
            helper.assertTrue(cell.is(GooBlocks.SHROOM_SPORES.get()) && GoologgedFungus.in(cell).isBud()
                    && cell.getValue(VolumeCellBlock.LAYERS) == left && spot.is(GooBlocks.FUNGAL_BUD.get()),
                    String.format(NOT_GROWN, left, cell, spot));
        });
    }

    /**
     * A full Spores cell with a young nether wart two blocks east and a
     * ripe one two blocks west, each on soul sand, ages the young wart and
     * reaps the ripe one, dropping its wart and replanting it at age 0.
     * mycosis-grows-and-reaps-nether-wart
     *
     * @param helper the gametest helper
     */
    public static void gooGrowsAndReapsNetherWart(GameTestHelper helper) {
        layFullCell(helper, TENDING_CELL);
        helper.setBlock(YOUNG_WART.below(), Blocks.SOUL_SAND);
        helper.setBlock(YOUNG_WART, Blocks.NETHER_WART);
        helper.setBlock(RIPE_WART.below(), Blocks.SOUL_SAND);
        helper.setBlock(RIPE_WART, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,
                NetherWartBlock.MAX_AGE));
        helper.succeedWhen(() -> {
            BlockState young = helper.getBlockState(YOUNG_WART);
            BlockState ripe = helper.getBlockState(RIPE_WART);
            helper.assertTrue(young.is(Blocks.NETHER_WART) && young.getValue(NetherWartBlock.AGE) > 0
                    && ripe.is(Blocks.NETHER_WART) && ripe.getValue(NetherWartBlock.AGE) == 0,
                    String.format(NOT_TENDED, young, ripe));
            helper.assertItemEntityPresent(Items.NETHER_WART, RIPE_WART, ITEM_SEARCH_RADIUS);
        });
    }

    /**
     * A full Spores cell lying on mycelium, its own space already grown and
     * its reach sealed in stone but for dirt beside the mycelium under glass,
     * turns the dirt to mycelium and stands lower by a network block's layers.
     * colonize-blob-grows-the-network
     * colonize-is-dropped-and-its-power-moves-into-mycosis-goo
     *
     * @param helper the gametest helper
     */
    public static void gooOnMyceliumSpreadsItToTheDirtBeside(GameTestHelper helper) {
        BlockPos.betweenClosedStream(NETWORK_CELL.offset(-SEAL_REACH, -1, -SEAL_REACH),
                NETWORK_CELL.offset(SEAL_REACH, 1, SEAL_REACH)).forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        helper.setBlock(NETWORK_CELL.below(), Blocks.MYCELIUM);
        helper.setBlock(DIRT_BESIDE, Blocks.DIRT);
        helper.setBlock(DIRT_BESIDE.above(), Blocks.GLASS);
        helper.setBlock(NETWORK_CELL, GooBlocks.SHROOM_SPORES.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS)
                .setValue(GoologgedFungus.FUNGUS, GoologgedFungus.BROWN_MUSHROOM));
        int left = VolumeCellBlock.MAX_LAYERS
                - SporesStep.in(spores(helper).behaviors()).orElseThrow().layersPerNetwork();
        helper.succeedWhen(() -> {
            BlockState dirt = helper.getBlockState(DIRT_BESIDE);
            BlockState cell = helper.getBlockState(NETWORK_CELL);
            helper.assertTrue(dirt.is(Blocks.MYCELIUM) && cell.is(GooBlocks.SHROOM_SPORES.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == left, String.format(NOT_SPREAD, left, dirt, cell));
        });
    }

    private static AbilityDefinition spores(GameTestHelper helper) {
        AbilityDefinition spores = AbilityRegistry.of(helper.getLevel()).getAbility(SPORES);
        helper.assertTrue(spores != null, ABILITY_REQUIRED);
        return spores;
    }

    /**
     * Lays a full Spores cell on a stone floor, walled in stone on its four
     * sides so no layer pours away.
     *
     * @param helper the gametest helper
     * @param cell   the cell, relative to the test
     */
    private static void layFullCell(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        wallIn(helper, cell);
        helper.setBlock(cell, GooBlocks.SHROOM_SPORES.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
    }

    private static void wallIn(GameTestHelper helper, BlockPos cell) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
    }

    /**
     * A zombie standing on a cell's top, helmeted so daylight leaves it unburnt.
     *
     * @param helper the gametest helper
     * @param cell   the cell it stands on, relative to the test
     * @return the zombie
     */
    private static Mob helmetedZombieOn(GameTestHelper helper, BlockPos cell) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atBottomCenterOf(cell.above()));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }
}
