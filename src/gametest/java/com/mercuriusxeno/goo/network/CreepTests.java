package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.CreepStep;
import com.mercuriusxeno.goo.block.ability.CreepSpreaders;
import com.mercuriusxeno.goo.block.ability.FungalBudBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Sculk's Creep through the real paths: a drip striking stone lays a one-layer Creep cell; a sculk charge runs
 * along the goo whole and turns the block under it to sculk; a mob dying on it spreads sculk from the goo and
 * bursts no spores; and a full cell buds its edge spaces, spending a fixed count of layers a bud, while neither it
 * nor a mushroom or bud beside it displaces the other. Each test walls its cells in stone so no cell pours its
 * layers away.
 * host-and-spores-combine-into-one-residual
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * sculk-charge-runs-along-host-goo-without-weakening
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 * spores-goo-and-shrooms-never-displace-each-other
 */
@RegistersGameTests
public final class CreepTests {

    private static final String DRIP_LAYS_A_CELL = "creep_drip_lays_a_cell_on_stone";
    private static final String CHARGE_TURNS_THE_STONE = "creep_charge_turns_the_stone_under_it";
    private static final String DEATH_SPREADS_SCULK = "creep_death_spreads_sculk_and_bursts_no_spores";
    private static final String STRANDED_CHARGE_ENDS = "creep_stranded_charge_ends";
    private static final String BUDS_ITS_EDGES = "creep_cell_buds_its_edges_and_spends_their_layers";
    private static final String NO_BUD_ON_STONE = "creep_cell_walled_in_stone_in_daylight_buds_nothing";
    private static final String SPREADS_NO_MYCELIUM = "creep_goo_on_mycelium_spreads_none_to_the_dirt_beside";
    private static final String DRIPS_SPARE_SHROOMS = "creep_drips_on_a_mushroom_and_a_bud_leave_them_standing";
    private static final String POUR_SPARES_SHROOMS = "creep_cell_pours_around_a_mushroom_and_a_bud_beside_it";
    private static final String RIPENING_SPARES_GOO = "creep_bud_ripening_beside_the_goo_keeps_both_spaces";
    private static final String UNRIPE_KEEPS_ITS_SPACE = "creep_ripe_bud_whose_mushroom_cannot_stand_stays_a_bud";
    private static final Identifier CREEP = Identifier.parse("goo:sculk_creep");
    /** The floor block a drip strikes or a cell lies on, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The full cell the tests lay over the floor, walled in stone, its edge spaces the wall tops beside it. */
    private static final BlockPos CELL = FLOOR.above();
    /** The floor the red mushroom stands on for the drip test. */
    private static final BlockPos MUSHROOM_FLOOR = new BlockPos(1, 1, 1);
    /** The floor the fungal bud stands on for the drip test, apart from the mushroom. */
    private static final BlockPos BUD_FLOOR = new BlockPos(4, 1, 1);
    /** The dirt beside the mycelium the network test's cell lies on, under glass so no bud stands over it. */
    private static final BlockPos DIRT_BESIDE = FLOOR.east();
    /** Where the second zombie stands for the death test: on the wall east of the cell, within a burst's reach. */
    private static final BlockPos BYSTANDER = CELL.east().above();
    /** Ticks the pour and ripening tests wait, past several of the cell's pours and periods. */
    private static final int SETTLE_TICKS = 100;
    /** Ticks the negative tests wait, several of the goo's periods as shipped. */
    private static final int SEVERAL_PERIODS = 100;
    private static final String ABILITY_REQUIRED = "The ability registry must hold sculk_creep";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Creep, stands %s";
    private static final String CHARGE_NOT_STARTED = "The zombie's death on the Creep cell should start one charge there";
    private static final String CHARGE_HELD = "A stranded charge should end, %s still run in the bay";
    private static final String NOT_SCULK = "The ground under the Creep cell should be sculk, stands %s";
    private static final String LAYERS_LEFT_WRONG = "The Creep cell should stand at %s layers, stands %s";
    private static final String MYCELIUM_GROWN = "A death on Creep should grow no mycelium, it stands at %s";
    private static final String BYSTANDER_SPORED = "The zombie beside the death should carry no spores and no mycosis";
    private static final String NOT_BUDDED = "A bud should stand on an edge space, none inside the cell, and the cell"
            + " stand %d layers lower a bud: %d buds placed, the cell stands %s";
    private static final String BUDDED_ON_STONE = "A Creep cell walled in stone in daylight should bud nothing and"
            + " keep its %d layers: %d buds placed, the cell stands %s";
    private static final String SPREAD = "Creep should spread no mycelium; the dirt beside stands %s";
    private static final String MUSHROOM_DISPLACED = "%s should still stand with no Creep in its space, stands %s";
    private static final String NOT_RIPENED = "The bud beside the goo should ripen to a mushroom and stay, stands %s";
    private static final String NOT_A_BUD = "A ripe bud whose mushroom cannot stand on stone in daylight should stay"
            + " a bud with no Creep in its space, stands %s";
    private static final String CELL_DISPLACED = "The Creep cell should keep its space and its %d layers, stands %s";

    private CreepTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_CELL, CreepTests::dripLaysACellOnStone);
        GooTestFunctions.registerTest(registrar, CHARGE_TURNS_THE_STONE, CreepTests::chargeCrossingCreepTurnsTheStoneUnderIt);
        GooTestFunctions.registerTest(registrar, DEATH_SPREADS_SCULK, CreepTests::deathOnCreepSpreadsSculkAndBurstsNoSpores);
        GooTestFunctions.registerTest(registrar, STRANDED_CHARGE_ENDS, CreepTests::strandedChargeEnds);
        GooTestFunctions.registerTest(registrar, BUDS_ITS_EDGES, CreepTests::cellBudsItsEdgesAndSpendsTheirLayers);
        GooTestFunctions.registerTest(registrar, NO_BUD_ON_STONE, CreepTests::cellOnStoneInDaylightBudsNothing);
        GooTestFunctions.registerTest(registrar, SPREADS_NO_MYCELIUM, CreepTests::gooOnMyceliumSpreadsNone);
        GooTestFunctions.registerTest(registrar, DRIPS_SPARE_SHROOMS, CreepTests::dripsOnShroomsLeaveThemStanding);
        GooTestFunctions.registerTest(registrar, POUR_SPARES_SHROOMS, CreepTests::cellPoursAroundShroomsBesideIt);
        GooTestFunctions.registerTest(registrar, RIPENING_SPARES_GOO, CreepTests::budRipeningBesideTheGooKeepsBothSpaces);
        GooTestFunctions.registerTest(registrar, UNRIPE_KEEPS_ITS_SPACE, CreepTests::budWhoseMushroomCannotStandStaysABud);
    }

    /**
     * One Creep drip striking a stone block's top, from a sculk-holding caster who pays for its layer, lays a Creep
     * cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysACellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(CELL, Blocks.AIR);
        AbilityDefinition creep = creep(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.SCULK);
        KnownRecipes.teachRequires(caster, creep);
        LandedDrips.landOn(helper, caster, creep, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(CELL);
        boolean oneLayer = state.is(GooBlocks.SCULK_CREEP.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * A sculk charge from a catalyst crossing a full Creep cell turns the stone under the cell to sculk, and the cell
     * stands lower by the layers Creep's step names a block of sculk spends. The cell stands walled on three sides
     * and against the catalyst on the fourth, so it cannot pour; a zombie killed on top of it starts the catalyst's
     * charge there.
     * sculk-charge-runs-along-host-goo-without-weakening
     *
     * @param helper the gametest helper
     */
    public static void chargeCrossingCreepTurnsTheStoneUnderIt(GameTestHelper helper) {
        int sculkLayers = CreepStep.in(creep(helper).behaviors()).orElseThrow().sculkLayers();
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(FLOOR.west(), Blocks.STONE);
        helper.setBlock(CELL.west(), Blocks.SCULK_CATALYST);
        for (Direction side : List.of(Direction.EAST, Direction.NORTH, Direction.SOUTH)) {
            helper.setBlock(CELL.relative(side), Blocks.STONE);
        }
        layFullCellOn(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, CELL.above());
        zombie.kill(helper.getLevel());
        helper.succeedWhen(() -> {
            BlockState under = helper.getBlockState(FLOOR);
            helper.assertTrue(under.is(Blocks.SCULK), String.format(NOT_SCULK, under));
            BlockState cell = helper.getBlockState(CELL);
            boolean spent = cell.is(GooBlocks.SCULK_CREEP.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == VolumeCellBlock.MAX_LAYERS - sculkLayers;
            helper.assertTrue(spent, String.format(LAYERS_LEFT_WRONG, VolumeCellBlock.MAX_LAYERS - sculkLayers, cell));
        });
    }

    /**
     * A zombie a player kills on a walled full Creep cell over dirt, with no catalyst in the bay, turns the dirt
     * under the cell to sculk and drops no experience, the goo taking the death as a catalyst would; the death grows
     * no mycelium anywhere in the bay, and a second zombie standing beside it carries no spores and no mycosis.
     * host-and-spores-combine-into-one-residual
     * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
     *
     * @param helper the gametest helper
     */
    public static void deathOnCreepSpreadsSculkAndBurstsNoSpores(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.DIRT);
        wallIn(helper, CELL);
        layFullCellOn(helper);
        ServerPlayer killer = SelfDeliveryTests.invoker(helper, GooTypes.SCULK);
        Mob bystander = helmetedZombieAt(helper, BYSTANDER);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, CELL.above());
        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), Float.MAX_VALUE);
        helper.getLevel().getServer().getPlayerList().remove(killer);
        helper.assertTrue(zombie.isDeadOrDying(), "The zombie should die of the blow");
        helper.succeedWhen(() -> {
            BlockState under = helper.getBlockState(FLOOR);
            helper.assertTrue(under.is(Blocks.SCULK), String.format(NOT_SCULK, under));
            helper.assertEntityNotPresent(EntityType.EXPERIENCE_ORB);
            AABB bay = helper.getBounds();
            BlockPos.betweenClosedStream(bay).filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.MYCELIUM))
                    .findFirst().ifPresent(pos -> helper.fail(String.format(MYCELIUM_GROWN, pos)));
            helper.assertFalse(bystander.hasEffect(GooMobEffects.MYCOSIS) || bystander.hasData(GooAttachments.SPORED),
                    BYSTANDER_SPORED);
        });
    }

    /**
     * A charge started on a lone one-layer Creep cell walled in stone, too thin to turn the stone under it and with no
     * step off the goo, ends rather than holding its charge for as long as the server runs.
     * sculk-charge-runs-along-host-goo-without-weakening
     * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
     *
     * @param helper the gametest helper
     */
    public static void strandedChargeEnds(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CELL.relative(side), Blocks.STONE);
        }
        helper.setBlock(CELL, GooBlocks.SCULK_CREEP.get().defaultBlockState());
        ServerPlayer killer = SelfDeliveryTests.invoker(helper, GooTypes.SCULK);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, CELL);
        zombie.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(killer), Float.MAX_VALUE);
        helper.getLevel().getServer().getPlayerList().remove(killer);
        CreepSpreaders spreaders = GooServerState.of(helper.getLevel().getServer()).creepSpreaders();
        AABB bay = helper.getBounds();
        helper.assertTrue(spreaders.chargesWithin(helper.getLevel(), bay) == 1, CHARGE_NOT_STARTED);
        helper.succeedWhen(() -> helper.assertTrue(spreaders.chargesWithin(helper.getLevel(), bay) == 0,
                String.format(CHARGE_HELD, spreaders.chargesWithin(helper.getLevel(), bay))));
    }

    /**
     * A full Creep cell walled in mycelium places a fungal bud on an edge space, a wall top beside it, places none
     * inside itself, and stands lower by the step's layers a bud for each bud it placed.
     * spores-goo-spends-on-edge-growth-and-grows-to-buds
     *
     * @param helper the gametest helper
     */
    public static void cellBudsItsEdgesAndSpendsTheirLayers(GameTestHelper helper) {
        layFullCell(helper);
        // a bud grows only where its mushroom can, so the wall tops it buds are mycelium
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CELL.relative(side), Blocks.MYCELIUM);
        }
        int perBud = CreepStep.in(creep(helper).behaviors()).orElseThrow().layersPerBud();
        helper.succeedWhen(() -> {
            long buds = budsBeside(helper);
            BlockState cell = helper.getBlockState(CELL);
            int expected = VolumeCellBlock.MAX_LAYERS - (int) buds * perBud;
            helper.assertTrue(buds > 0 && cell.is(GooBlocks.SCULK_CREEP.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == expected,
                    String.format(NOT_BUDDED, perBud, buds, cell));
        });
    }

    /**
     * A full Creep cell walled in stone in daylight, whose wall tops no mushroom could grow on, places no bud on them
     * and keeps every layer after several of its periods.
     * operator ruling 2026-11-18: Mycosis should fizzle instead of plant buds where mushrooms aren't allowed to grow
     *
     * @param helper the gametest helper
     */
    public static void cellOnStoneInDaylightBudsNothing(GameTestHelper helper) {
        layFullCell(helper);
        helper.runAfterDelay(SEVERAL_PERIODS, () -> {
            long buds = budsBeside(helper);
            BlockState cell = helper.getBlockState(CELL);
            helper.assertTrue(buds == 0 && cell.getValue(VolumeCellBlock.LAYERS) == VolumeCellBlock.MAX_LAYERS,
                    String.format(BUDDED_ON_STONE, VolumeCellBlock.MAX_LAYERS, buds, cell));
            helper.succeed();
        });
    }

    /**
     * A full Creep cell lying on mycelium, with dirt beside the mycelium under glass, leaves the dirt as dirt after
     * several of its periods.
     * colonize-is-dropped-and-its-power-moves-into-mycosis-goo
     *
     * @param helper the gametest helper
     */
    public static void gooOnMyceliumSpreadsNone(GameTestHelper helper) {
        layFullCell(helper);
        helper.setBlock(FLOOR, Blocks.MYCELIUM);
        helper.setBlock(DIRT_BESIDE, Blocks.DIRT);
        helper.setBlock(DIRT_BESIDE.above(), Blocks.GLASS);
        helper.runAfterDelay(SEVERAL_PERIODS, () -> {
            BlockState dirt = helper.getBlockState(DIRT_BESIDE);
            helper.assertTrue(dirt.is(Blocks.DIRT), String.format(SPREAD, dirt));
            helper.succeed();
        });
    }

    /**
     * One Creep drip lands on a red mushroom and one on a fungal bud, each standing on stone, and both blocks still
     * stand with no Creep goo in their spaces.
     * spores-goo-and-shrooms-never-displace-each-other
     *
     * @param helper the gametest helper
     */
    public static void dripsOnShroomsLeaveThemStanding(GameTestHelper helper) {
        Map<BlockPos, Block> shrooms = Map.of(MUSHROOM_FLOOR.above(), Blocks.RED_MUSHROOM,
                BUD_FLOOR.above(), GooBlocks.FUNGAL_BUD.get());
        shrooms.forEach((pos, block) -> {
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, block);
        });
        AbilityDefinition creep = creep(helper);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.SCULK);
        KnownRecipes.teachRequires(caster, creep);
        LandedDrips.landOn(helper, caster, creep, MUSHROOM_FLOOR, 1);
        LandedDrips.landOn(helper, caster, creep, BUD_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            shrooms.forEach((pos, block) -> {
                BlockState now = helper.getBlockState(pos);
                helper.assertTrue(now.is(block), String.format(MUSHROOM_DISPLACED, block, now));
            });
            helper.succeed();
        });
    }

    /**
     * A full Creep cell on a stone floor with a brown mushroom on mycelium, where vanilla keeps a mushroom standing in
     * any light, on one side and a fungal bud on stone on another, its other sides walled, pours none of its goo into
     * either space, and both still stand.
     * spores-goo-and-shrooms-never-displace-each-other
     *
     * @param helper the gametest helper
     */
    public static void cellPoursAroundShroomsBesideIt(GameTestHelper helper) {
        layFullCell(helper);
        Map<BlockPos, Block> shrooms = Map.of(CELL.east(), Blocks.BROWN_MUSHROOM, CELL.west(),
                GooBlocks.FUNGAL_BUD.get());
        shrooms.forEach((pos, block) -> {
            helper.setBlock(pos.below(), block == Blocks.BROWN_MUSHROOM ? Blocks.MYCELIUM : Blocks.STONE);
            helper.setBlock(pos, block);
        });
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            shrooms.forEach((pos, block) -> {
                BlockState now = helper.getBlockState(pos);
                helper.assertTrue(now.is(block), String.format(MUSHROOM_DISPLACED, block, now));
            });
            helper.succeed();
        });
    }

    /**
     * A fungal bud at its last age on mycelium beside a full Creep cell ripens into its mushroom in its own space;
     * past several of the cell's periods the mushroom still stands and the cell keeps its space, lower only by the
     * layers of the buds it placed on its edges.
     * spores-goo-and-shrooms-never-displace-each-other
     *
     * @param helper the gametest helper
     */
    public static void budRipeningBesideTheGooKeepsBothSpaces(GameTestHelper helper) {
        layFullCell(helper);
        BlockPos budAt = ripeBudBeside(helper, Blocks.MYCELIUM);
        int perBud = CreepStep.in(creep(helper).behaviors()).orElseThrow().layersPerBud();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            BlockState ripened = helper.getBlockState(budAt);
            helper.assertTrue(ripened.is(Blocks.BROWN_MUSHROOM) || ripened.is(Blocks.RED_MUSHROOM),
                    String.format(NOT_RIPENED, ripened));
            int left = VolumeCellBlock.MAX_LAYERS - (int) budsBeside(helper) * perBud;
            BlockState cell = helper.getBlockState(CELL);
            helper.assertTrue(cell.is(GooBlocks.SCULK_CREEP.get()) && cell.getValue(VolumeCellBlock.LAYERS) == left,
                    String.format(CELL_DISPLACED, left, cell));
            helper.succeed();
        });
    }

    /**
     * A fungal bud at its last age on stone in daylight, beside a full Creep cell, whose mushroom could not stand
     * there, stays a bud when it ripens, and past several of the cell's periods the goo has poured none of itself
     * into its space.
     * spores-goo-and-shrooms-never-displace-each-other
     *
     * @param helper the gametest helper
     */
    public static void budWhoseMushroomCannotStandStaysABud(GameTestHelper helper) {
        layFullCell(helper);
        BlockPos budAt = ripeBudBeside(helper, Blocks.STONE);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            BlockState bud = helper.getBlockState(budAt);
            helper.assertTrue(bud.is(GooBlocks.FUNGAL_BUD.get()), String.format(NOT_A_BUD, bud));
            helper.succeed();
        });
    }

    /**
     * Stands a fungal bud at its last age east of the cell on the given floor, and ripens it on the next tick as a
     * random tick would.
     *
     * @param helper the gametest helper
     * @param floor  the block the bud stands on
     * @return the bud's space, relative to the test
     */
    private static BlockPos ripeBudBeside(GameTestHelper helper, Block floor) {
        BlockPos budAt = CELL.east();
        helper.setBlock(budAt.below(), floor);
        helper.setBlock(budAt, GooBlocks.FUNGAL_BUD.get().defaultBlockState().setValue(FungalBudBlock.AGE,
                FungalBudBlock.MAX_AGE));
        helper.runAfterDelay(1, () -> helper.getBlockState(budAt).randomTick(helper.getLevel(),
                helper.absolutePos(budAt), helper.getLevel().getRandom()));
        return budAt;
    }

    private static Mob helmetedZombieAt(GameTestHelper helper, BlockPos at) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atBottomCenterOf(at));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /** The fungal buds standing in the cell's edge spaces: its four sides at its level and the four just above. */
    private static long budsBeside(GameTestHelper helper) {
        return Direction.Plane.HORIZONTAL.stream().flatMap(side -> Stream.of(CELL.relative(side),
                CELL.relative(side).above())).filter(space -> helper.getBlockState(space).is(GooBlocks.FUNGAL_BUD.get()))
                .count();
    }

    private static AbilityDefinition creep(GameTestHelper helper) {
        AbilityDefinition creep = AbilityRegistry.of(helper.getLevel()).getAbility(CREEP);
        helper.assertTrue(creep != null, ABILITY_REQUIRED);
        return creep;
    }

    /**
     * Lays a full Creep cell on a stone floor, walled in stone on its four sides so no layer pours away.
     *
     * @param helper the gametest helper
     */
    private static void layFullCell(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        wallIn(helper, CELL);
        layFullCellOn(helper);
    }

    /** Lays a full Creep cell over whatever floor the test set. */
    private static void layFullCellOn(GameTestHelper helper) {
        helper.setBlock(CELL, GooBlocks.SCULK_CREEP.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
    }

    private static void wallIn(GameTestHelper helper, BlockPos cell) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
            helper.setBlock(cell.relative(side).above(), Blocks.AIR);
        }
    }
}
