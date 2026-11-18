package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SporesStep;
import com.mercuriusxeno.goo.ability.spray.Spored;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Shroom's Spores goo through the real paths: one of its stream's drips
 * lays a one-layer Spores cell; a full cell places fungal buds on its edge
 * spaces and none inside itself, spending a fixed count of layers a bud; and
 * the goo spreads no shroom network and gives what stands in it no mycosis
 * effect, only spores, and a mob dying on or near it bursts Mycosis' spores
 * and turns the grass block, dirt and coarse dirt around it to mycelium. Each test walls its cells in stone so no cell pours its layers
 * away.
 * spores-is-shrooms-residual-a-passive-mycosis
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 * colonize-is-dropped-and-its-power-moves-into-mycosis-goo
 * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
 */
@RegistersGameTests
public final class SporesTests {

    private static final String DRIP_LAYS_A_SHROOM_CELL = "spores_drip_lays_a_shroom_cell_of_one_layer";
    private static final String BUDS_ITS_EDGES = "spores_cell_buds_its_edges_and_spends_their_layers";
    private static final String SPREADS_NO_MYCELIUM = "spores_goo_on_mycelium_spreads_none_to_the_dirt_beside";
    private static final String GIVES_NO_MYCOSIS = "spores_goo_gives_a_zombie_standing_in_it_no_mycosis";
    private static final String SPORES_A_ZOMBIE = "spores_goo_spores_a_zombie_standing_on_it";
    private static final String DEATH_BURSTS_AND_MYCELIUM = "spores_zombie_dying_off_the_goo_bursts_and_grows_mycelium";
    private static final String DEATH_NEAR_THE_GOO = "spores_zombie_dying_near_the_goo_grows_mycelium";
    private static final String STREAM_DEATH_CONVERTS_NOTHING = "spores_stream_spored_zombie_dying_converts_no_dirt";
    private static final Identifier MYCOSIS = Identifier.parse("goo:shroom_mycosis");
    /** Where the spored zombie is moved to die, its floor a patch of grass block, dirt, coarse dirt, stone and sand. */
    private static final BlockPos FELL = new BlockPos(5, 2, 5);
    /** The second zombie, standing inside the burst's radius of where the first falls. */
    private static final BlockPos BYSTANDER = FELL.south(2);
    /** Bare dirt two blocks south of the sealed cell, behind its wall, inside the goo's reach. */
    private static final BlockPos NEAR_THE_GOO = new BlockPos(2, 2, 4);
    /** Ticks the stream's spores last on the zombie the stream spored. */
    private static final int STREAM_SPORE_TICKS = 600;
    /** The radius the stream's spores burst at. */
    private static final float STREAM_SPORE_RADIUS = 3f;
    private static final Identifier SPORES = Identifier.parse("goo:shroom_spores");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    /** The full cell the tests lay, walled in stone, its edge spaces the wall tops beside it. */
    private static final BlockPos CELL = new BlockPos(2, 2, 2);
    /** The dirt beside the mycelium the network test's cell lies on, under glass so no bud stands over it. */
    private static final BlockPos DIRT_BESIDE = CELL.below().east();
    /** Ticks the negative tests wait, several of the goo's periods as shipped. */
    private static final int SEVERAL_PERIODS = 100;
    private static final String ABILITY_REQUIRED = "The ability registry must hold shroom_spores";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Spores of 1 layer, stands %s";
    private static final String NOT_BUDDED = "A bud should stand on an edge space, none inside the cell, and the cell"
            + " stand %d layers lower a bud: %d buds placed, the cell stands %s";
    private static final String SPREAD = "Spores should spread no mycelium; the dirt beside stands %s";
    private static final String INFECTED = "A zombie standing in Spores should carry no mycosis effect";
    private static final String NOT_SPORED = "A zombie standing on Spores should carry spores bursting %s, carries %s";
    private static final String BYSTANDER_CLEAN = "The burst from the corpse should poison the bystander";
    private static final String NOT_CONVERTED = "%s under the corpse should be mycelium, stands %s";
    private static final String CONVERTED = "%s under the corpse should stand as it stood, stands %s";
    private static final String CELL_SPENT = "The Spores cell should hold its %d layers after the death, stands %s";

    private SporesTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_SHROOM_CELL, SporesTests::dripLaysAShroomCell);
        GooTestFunctions.registerTest(registrar, BUDS_ITS_EDGES, SporesTests::cellBudsItsEdgesAndSpendsTheirLayers);
        GooTestFunctions.registerTest(registrar, SPREADS_NO_MYCELIUM, SporesTests::gooOnMyceliumSpreadsNone);
        GooTestFunctions.registerTest(registrar, GIVES_NO_MYCOSIS, SporesTests::gooGivesAZombieNoMycosis);
        GooTestFunctions.registerTest(registrar, SPORES_A_ZOMBIE, SporesTests::gooSporesAZombieStandingOnIt);
        GooTestFunctions.registerTest(registrar, DEATH_BURSTS_AND_MYCELIUM, SporesTests::deathOffTheGooBurstsAndGrowsMycelium);
        GooTestFunctions.registerTest(registrar, DEATH_NEAR_THE_GOO, SporesTests::deathNearTheGooGrowsMycelium);
        GooTestFunctions.registerTest(registrar, STREAM_DEATH_CONVERTS_NOTHING, SporesTests::streamSporedDeathConvertsNoDirt);
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
        AbilityDefinition spores = spores(helper);
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
     * A full Spores cell walled in stone places a fungal bud on an edge
     * space, a wall top beside it, places none inside itself, and stands
     * lower by the step's layers a bud for each bud it placed.
     * spores-goo-spends-on-edge-growth-and-grows-to-buds
     *
     * @param helper the gametest helper
     */
    public static void cellBudsItsEdgesAndSpendsTheirLayers(GameTestHelper helper) {
        layFullCell(helper, CELL);
        int perBud = SporesStep.in(spores(helper).behaviors()).orElseThrow().layersPerBud();
        helper.succeedWhen(() -> {
            long buds = edgeSpaces(CELL).filter(space -> helper.getBlockState(space).is(GooBlocks.FUNGAL_BUD.get()))
                    .count();
            BlockState cell = helper.getBlockState(CELL);
            int expected = VolumeCellBlock.MAX_LAYERS - (int) buds * perBud;
            helper.assertTrue(buds > 0 && cell.is(GooBlocks.SHROOM_SPORES.get()) && !GoologgedFungus.holdsFungus(cell)
                    && cell.getValue(VolumeCellBlock.LAYERS) == expected,
                    String.format(NOT_BUDDED, perBud, buds, cell));
        });
    }

    /**
     * A full Spores cell lying on mycelium, with dirt beside the mycelium
     * under glass, leaves the dirt as dirt after several of its periods.
     * colonize-is-dropped-and-its-power-moves-into-mycosis-goo
     *
     * @param helper the gametest helper
     */
    public static void gooOnMyceliumSpreadsNone(GameTestHelper helper) {
        layFullCell(helper, CELL);
        helper.setBlock(CELL.below(), Blocks.MYCELIUM);
        helper.setBlock(DIRT_BESIDE, Blocks.DIRT);
        helper.setBlock(DIRT_BESIDE.above(), Blocks.GLASS);
        helper.runAfterDelay(SEVERAL_PERIODS, () -> {
            BlockState dirt = helper.getBlockState(DIRT_BESIDE);
            helper.assertTrue(dirt.is(Blocks.DIRT), String.format(SPREAD, dirt));
            helper.succeed();
        });
    }

    /**
     * A zombie standing on a full Spores cell carries no mycosis effect after several of its periods.
     * spores-is-shrooms-residual-a-passive-mycosis
     *
     * @param helper the gametest helper
     */
    public static void gooGivesAZombieNoMycosis(GameTestHelper helper) {
        layFullCell(helper, CELL);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atBottomCenterOf(CELL.above()));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        helper.runAfterDelay(SEVERAL_PERIODS, () -> {
            helper.assertFalse(zombie.hasEffect(GooMobEffects.MYCOSIS), INFECTED);
            helper.succeed();
        });
    }

    /**
     * A zombie standing on a full Spores cell carries spores that burst
     * Mycosis, and no mycosis effect.
     * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
     *
     * @param helper the gametest helper
     */
    public static void gooSporesAZombieStandingOnIt(GameTestHelper helper) {
        laySealedCell(helper);
        Mob zombie = helmetedZombieAt(helper, CELL.above());
        helper.succeedWhen(() -> {
            Identifier burst = zombie.hasData(GooAttachments.SPORED) ? zombie.getData(GooAttachments.SPORED).burst()
                    : null;
            helper.assertTrue(MYCOSIS.equals(burst), String.format(NOT_SPORED, MYCOSIS, burst));
            helper.assertFalse(zombie.hasEffect(GooMobEffects.MYCOSIS), INFECTED);
        });
    }

    /**
     * A zombie spored on a sealed Spores cell, moved onto a patch of grass
     * block, dirt, coarse dirt, stone and sand and killed there, bursts the
     * Mycosis spore spray onto a second zombie, turns the grass block, dirt
     * and coarse dirt to mycelium, leaves the stone and sand, and the cell
     * keeps its layers.
     * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
     *
     * @param helper the gametest helper
     */
    public static void deathOffTheGooBurstsAndGrowsMycelium(GameTestHelper helper) {
        laySealedCell(helper);
        Map<BlockPos, Block> floor = Map.of(FELL.below(), Blocks.GRASS_BLOCK, FELL.below().east(), Blocks.DIRT,
                FELL.below().west(), Blocks.COARSE_DIRT, FELL.below().north(), Blocks.STONE,
                FELL.below().south(), Blocks.SAND);
        floor.forEach((pos, block) -> {
            helper.setBlock(pos, block);
            helper.setBlock(pos.above(), Blocks.AIR);
        });
        helper.setBlock(BYSTANDER.below(), Blocks.STONE);
        Mob spored = helmetedZombieAt(helper, CELL.above());
        Mob bystander = helmetedZombieAt(helper, BYSTANDER);
        AtomicBoolean killed = new AtomicBoolean();
        helper.succeedWhen(() -> {
            if (!killed.get()) {
                helper.assertTrue(spored.hasData(GooAttachments.SPORED), String.format(NOT_SPORED, MYCOSIS, null));
                Vec3 fell = Vec3.atBottomCenterOf(helper.absolutePos(FELL));
                spored.teleportTo(fell.x, fell.y, fell.z);
                spored.kill(helper.getLevel());
                killed.set(true);
            }
            helper.assertTrue(bystander.hasEffect(GooMobEffects.MYCOSIS), BYSTANDER_CLEAN);
            floor.forEach((pos, block) -> {
                BlockState now = helper.getBlockState(pos);
                boolean converts = block == Blocks.GRASS_BLOCK || block == Blocks.DIRT || block == Blocks.COARSE_DIRT;
                helper.assertTrue(converts ? now.is(Blocks.MYCELIUM) : now.is(block),
                        String.format(converts ? NOT_CONVERTED : CONVERTED, block, now));
            });
            BlockState cell = helper.getBlockState(CELL);
            helper.assertTrue(cell.is(GooBlocks.SHROOM_SPORES.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == VolumeCellBlock.MAX_LAYERS,
                    String.format(CELL_SPENT, VolumeCellBlock.MAX_LAYERS, cell));
        });
    }

    /**
     * A zombie that never touched the goo, killed on bare dirt behind a
     * sealed Spores cell's wall inside its reach, turns that dirt to mycelium.
     * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
     *
     * @param helper the gametest helper
     */
    public static void deathNearTheGooGrowsMycelium(GameTestHelper helper) {
        laySealedCell(helper);
        helper.setBlock(NEAR_THE_GOO.below(), Blocks.DIRT);
        helper.setBlock(NEAR_THE_GOO, Blocks.AIR);
        Mob zombie = helmetedZombieAt(helper, NEAR_THE_GOO);
        helper.runAfterDelay(1, () -> zombie.kill(helper.getLevel()));
        helper.succeedWhen(() -> {
            BlockState dirt = helper.getBlockState(NEAR_THE_GOO.below());
            helper.assertTrue(dirt.is(Blocks.MYCELIUM), String.format(NOT_CONVERTED, Blocks.DIRT, dirt));
        });
    }

    /**
     * A zombie carrying the stream's spores, dying on dirt with no Spores goo
     * in the structure, leaves the dirt as dirt.
     * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
     *
     * @param helper the gametest helper
     */
    public static void streamSporedDeathConvertsNoDirt(GameTestHelper helper) {
        helper.setBlock(FELL.below(), Blocks.DIRT);
        helper.setBlock(FELL, Blocks.AIR);
        Mob zombie = helmetedZombieAt(helper, FELL);
        zombie.setData(GooAttachments.SPORED, new Spored(MYCOSIS, STREAM_SPORE_RADIUS,
                helper.getLevel().getGameTime() + STREAM_SPORE_TICKS));
        zombie.kill(helper.getLevel());
        helper.runAfterDelay(SEVERAL_PERIODS, () -> {
            BlockState dirt = helper.getBlockState(FELL.below());
            helper.assertTrue(dirt.is(Blocks.DIRT), String.format(CONVERTED, Blocks.DIRT, dirt));
            helper.succeed();
        });
    }

    /**
     * Lays a full Spores cell walled in stone with stone over its walls, so it
     * has no edge space to bud and holds its layers.
     *
     * @param helper the gametest helper
     */
    private static void laySealedCell(GameTestHelper helper) {
        layFullCell(helper, CELL);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CELL.relative(side).above(), Blocks.STONE);
        }
    }

    private static Mob helmetedZombieAt(GameTestHelper helper, BlockPos at) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atBottomCenterOf(at));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /** The spaces beside a cell: its four sides at its level and the four just above them. */
    private static Stream<BlockPos> edgeSpaces(BlockPos cell) {
        return Direction.Plane.HORIZONTAL.stream().flatMap(side -> Stream.of(cell.relative(side),
                cell.relative(side).above()));
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
            helper.setBlock(cell.relative(side).above(), Blocks.AIR);
        }
    }
}
