package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.NectarFeeds;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Jelly's Nectar, the jelly goo its held stream sprays: a drip
 * striking a block's top, from a jelly-holding caster who pays for its
 * layer, lays a Nectar cell of one layer there; an adult cow reaching the
 * goo falls in love, eating a layer of it; two zombies reaching it turn on
 * each other over it; a cow and two zombies standing out of the goo's
 * feeding reach walk to it, the cow eating and the zombies fighting; a drip striking a calf grows it, spent on it; and a
 * drip striking a wall's side coats it, a mob in the coated space climbing
 * it as a ladder.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 * each-feeding-eats-a-layer-of-nectar
 */
@RegistersGameTests
public final class NectarTests {

    private static final String DRIP_LAYS_A_JELLY_CELL = "nectar_drip_lays_a_jelly_cell_of_one_layer";
    private static final String COURTS_AN_ADULT_COW = "nectar_goo_courts_an_adult_cow_for_a_layer";
    private static final String SETS_TWO_ZOMBIES_FIGHTING = "nectar_goo_sets_two_zombies_fighting";
    private static final String DRIP_FEEDS_A_STRUCK_CALF = "nectar_drip_feeds_a_struck_calf";
    private static final String COATS_A_WALL_AND_CLIMBS = "nectar_drip_on_a_wall_coats_it_and_climbs";
    private static final String WALL_COAT_STAYS = "nectar_on_a_wall_stays_on_it";
    private static final String COATS_A_CLIMBABLE_COLUMN = "nectar_coats_a_wall_column_to_climb";
    private static final String DRAWS_A_COW_FROM_RANGE = "nectar_goo_draws_an_adult_cow_from_range_for_a_layer";
    private static final String DRAWS_ZOMBIES_FROM_RANGE = "nectar_goo_draws_two_zombies_from_range_to_fight";
    private static final Identifier NECTAR = Identifier.parse("goo:jelly_nectar");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final int ONE_DRIP = 1;
    /** The cell the goo tests set into the stone's top course, walled on every side. */
    private static final BlockPos GOO_CELL = new BlockPos(2, 2, 2);
    private static final int FED_CELL_LAYERS = 5;
    /** The stone course the goo tests lay, its top one block over the bay's floor. */
    private static final int STONE_TOP_Y = 2;
    private static final int PEN_SPAN = 5;
    private static final int PEN_WALL_TOP_Y = 4;
    /** Where the calf stands for the strike test, on open stone. */
    private static final BlockPos CALF_FLOOR = new BlockPos(2, 1, 2);
    /** The drips the wall hold test lands on one wall, more than one wall holds. */
    private static final int WALL_DRIPS = 5;
    /** The layers one wall holds of jelly's goo: a layer plus one for its cohesion of 1. */
    private static final int ONE_WALL_HOLDS = 2;
    /** Ticks for the wall's goo to run down whatever its hold lets go, past a few of jelly's pours. */
    private static final int WALL_SETTLE_TICKS = 60;
    /** The column test's wall: three stone blocks stacked over the bay's floor. */
    private static final BlockPos COLUMN_FOOT = new BlockPos(2, 1, 2);
    private static final int COLUMN_HEIGHT = 3;
    /** The drips the column test lands on each face, bottom face first. */
    private static final int DRIPS_PER_FACE = 2;
    /** The range tests' pen, six blocks across, its cell in one corner of the floor inside the rim. */
    private static final int RANGE_PEN_SPAN = 6;
    private static final BlockPos RANGE_CELL = new BlockPos(1, 2, 1);
    /** The far corner of the range pen's floor, where the cow starts. */
    private static final BlockPos FAR_CORNER = new BlockPos(4, 3, 4);
    /** The two corners beside the cell's, where the zombies start. */
    private static final BlockPos EAST_CORNER = new BlockPos(4, 3, 1);
    private static final BlockPos SOUTH_CORNER = new BlockPos(1, 3, 4);
    /** jelly_nectar.json's reach, the blocks out to which a Nectar cell draws mobs. */
    private static final double SHIPPED_REACH = 12;
    /** The stone the wall test's drip strikes, standing clear of the bay's floor. */
    private static final BlockPos WALL = new BlockPos(2, 3, 2);
    private static final String ABILITY_REQUIRED = "The ability registry must hold jelly_nectar";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Nectar of one layer, stands %s";
    private static final String COW_NOT_FED = "The cow should fall in love eating a layer: in love %b, the cell stands %s";
    private static final String ZOMBIES_NOT_FIGHTING = "Two zombies at Nectar goo should target each other: %s and %s";
    private static final String WALL_NOT_COATED = "The cell east of the wall should be Nectar attached to its west side,"
            + " stands %s";
    private static final String NOT_CLIMBING = "A zombie in the coated space should read on a climbable";
    private static final String WALL_COAT_RAN = "The wall should keep %d of the %d layers landed on it, the cell"
            + " there stands %s";
    private static final String COLUMN_GAP = "Every face of the wall should keep Nectar attached so the run can"
            + " be climbed; the cells in front, bottom first, stand %s";
    private static final String STARTS_IN_REACH = "The mob should start past the feeding reach %.1f and inside"
            + " the reach %.1f, starts %.2f off";
    private static final String CALF_NOT_FED = "A struck calf should grow to an adult, the drip spent: baby %b, under it %s";

    private NectarTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_JELLY_CELL, NectarTests::dripLaysAJellyCellOfOneLayer);
        GooTestFunctions.registerTest(registrar, COURTS_AN_ADULT_COW, NectarTests::courtsAnAdultCowForALayer);
        GooTestFunctions.registerTest(registrar, SETS_TWO_ZOMBIES_FIGHTING, NectarTests::setsTwoZombiesFighting);
        GooTestFunctions.registerTest(registrar, DRIP_FEEDS_A_STRUCK_CALF, NectarTests::dripFeedsAStruckCalf);
        GooTestFunctions.registerTest(registrar, COATS_A_WALL_AND_CLIMBS, NectarTests::coatsAWallAndClimbs);
        GooTestFunctions.registerTest(registrar, WALL_COAT_STAYS, NectarTests::wallCoatStaysOnIt);
        GooTestFunctions.registerTest(registrar, COATS_A_CLIMBABLE_COLUMN, NectarTests::coatsAWallColumnToClimb);
        GooTestFunctions.registerTest(registrar, DRAWS_A_COW_FROM_RANGE, NectarTests::drawsAnAdultCowFromRange);
        GooTestFunctions.registerTest(registrar, DRAWS_ZOMBIES_FROM_RANGE, NectarTests::drawsTwoZombiesFromRange);
    }

    /**
     * One Nectar drip striking a walled stone floor, from a jelly-holding
     * caster who pays for its layer, lays a Nectar cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysAJellyCellOfOneLayer(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.landOn(helper, caster, nectar, DRIP_FLOOR, ONE_DRIP);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(NECTAR)
                && state.getValue(VolumeCellBlock.LAYERS) == ONE_DRIP, String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * An adult cow penned beside a five-layer Nectar cell walks to it and
     * eats: it falls in love, and the cell stands a layer lower.
     *
     * @param helper the gametest helper
     */
    public static void courtsAnAdultCowForALayer(GameTestHelper helper) {
        layPennedGoo(helper);
        Cow cow = helper.spawn(EntityType.COW, GOO_CELL.above().east());
        helper.succeedWhen(() -> {
            BlockState cell = helper.getBlockState(GOO_CELL);
            helper.assertTrue(cow.isInLove() && cell.is(GooBlocks.JELLY_NECTAR.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == FED_CELL_LAYERS - 1,
                    String.format(COW_NOT_FED, cow.isInLove(), cell));
        });
    }

    /**
     * Two zombies on either side of a Nectar cell, with no player in the
     * structure, turn on each other over it.
     *
     * @param helper the gametest helper
     */
    public static void setsTwoZombiesFighting(GameTestHelper helper) {
        layPennedGoo(helper);
        Mob first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, GOO_CELL.above().east());
        Mob second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, GOO_CELL.above().west());
        helper.succeedWhen(() -> helper.assertTrue(first.getTarget() == second && second.getTarget() == first,
                String.format(ZOMBIES_NOT_FIGHTING, first.getTarget(), second.getTarget())));
    }

    /**
     * A Nectar drip falling onto a calf strikes it and grows it to an adult,
     * spent on it, so no goo lands under it.
     *
     * @param helper the gametest helper
     */
    public static void dripFeedsAStruckCalf(GameTestHelper helper) {
        helper.setBlock(CALF_FLOOR, Blocks.STONE);
        Cow calf = helper.spawnWithNoFreeWill(EntityType.COW, CALF_FLOOR.above());
        calf.setBaby(true);
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.strike(helper, caster, nectar, calf);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState under = helper.getBlockState(CALF_FLOOR.above());
        helper.assertTrue(!calf.isBaby() && under.isAir(), String.format(CALF_NOT_FED, calf.isBaby(), under));
        helper.succeed();
    }

    /**
     * One Nectar drip striking a stone wall's east side, from a
     * jelly-holding caster, coats that side: the cell east of the wall
     * stands Nectar attached to its west side, and a zombie in that cell
     * reads on a climbable.
     *
     * @param helper the gametest helper
     */
    public static void coatsAWallAndClimbs(GameTestHelper helper) {
        helper.setBlock(WALL, Blocks.STONE);
        BlockPos coated = WALL.east();
        helper.setBlock(coated, Blocks.AIR);
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.landOnSide(helper, caster, nectar, WALL, Direction.EAST, ONE_DRIP);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState cell = helper.getBlockState(coated);
        helper.assertTrue(cell.is(GooBlocks.JELLY_NECTAR.get())
                && cell.getValue(VolumeCellBlock.WEST) == SideCoat.ATTACHED, String.format(WALL_NOT_COATED, cell));
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, coated);
        helper.assertTrue(zombie.onClimbable(), NOT_CLIMBING);
        helper.succeed();
    }

    /**
     * Five Nectar drips striking a stone wall's east side, clear of any
     * floor, leave the wall coated: once the goo has had time to run, the
     * cell east of the wall still holds the two layers one wall holds of
     * jelly's goo, attached to its west side, the rest run down.
     * operator ruling 2026-11-17: a cohesion of 0 is too low for Nectar to stay on walls; jelly takes 1
     *
     * @param helper the gametest helper
     */
    public static void wallCoatStaysOnIt(GameTestHelper helper) {
        helper.setBlock(WALL, Blocks.STONE);
        BlockPos coated = WALL.east();
        helper.setBlock(coated, Blocks.AIR);
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.landOnSide(helper, caster, nectar, WALL, Direction.EAST, WALL_DRIPS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(WALL_SETTLE_TICKS, () -> {
            BlockState cell = helper.getBlockState(coated);
            helper.assertTrue(cell.is(GooBlocks.JELLY_NECTAR.get())
                    && cell.getValue(VolumeCellBlock.WEST) == SideCoat.ATTACHED
                    && cell.getValue(VolumeCellBlock.LAYERS) == ONE_WALL_HOLDS,
                    String.format(WALL_COAT_RAN, ONE_WALL_HOLDS, WALL_DRIPS, cell));
            helper.succeed();
        });
    }

    /**
     * Nectar drips striking each face of a three-high stone wall, bottom face
     * first, coat the whole column: once the goo has had time to run, the
     * cell in front of every face stands Nectar attached to the wall, a
     * contiguous run to climb.
     * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
     *
     * @param helper the gametest helper
     */
    public static void coatsAWallColumnToClimb(GameTestHelper helper) {
        for (int up = 0; up < COLUMN_HEIGHT; up++) {
            helper.setBlock(COLUMN_FOOT.above(up), Blocks.STONE);
            helper.setBlock(COLUMN_FOOT.above(up).east(), Blocks.AIR);
        }
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        for (int up = 0; up < COLUMN_HEIGHT; up++) {
            LandedDrips.landOnSide(helper, caster, nectar, COLUMN_FOOT.above(up), Direction.EAST, DRIPS_PER_FACE);
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(WALL_SETTLE_TICKS, () -> {
            List<BlockState> cells = new ArrayList<>();
            for (int up = 0; up < COLUMN_HEIGHT; up++) {
                cells.add(helper.getBlockState(COLUMN_FOOT.above(up).east()));
            }
            helper.assertTrue(cells.stream().allMatch(cell -> cell.is(GooBlocks.JELLY_NECTAR.get())
                    && cell.getValue(VolumeCellBlock.WEST) == SideCoat.ATTACHED), String.format(COLUMN_GAP, cells));
            helper.succeed();
        });
    }

    /**
     * An adult cow with its AI, standing in the far corner of a pen from a
     * five-layer Nectar cell, out of its feeding reach and inside its reach,
     * walks to the cell and eats: it falls in love, and the cell stands a
     * layer lower.
     *
     * @param helper the gametest helper
     */
    public static void drawsAnAdultCowFromRange(GameTestHelper helper) {
        layRangePen(helper);
        Cow cow = helper.spawn(EntityType.COW, FAR_CORNER);
        assertStartsInRange(helper, cow);
        helper.succeedWhen(() -> {
            BlockState cell = helper.getBlockState(RANGE_CELL);
            helper.assertTrue(cow.isInLove() && cell.is(GooBlocks.JELLY_NECTAR.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == FED_CELL_LAYERS - 1,
                    String.format(COW_NOT_FED, cow.isInLove(), cell));
        });
    }

    /**
     * Two zombies with their AI, standing in the corners beside a Nectar
     * cell's, out of its feeding reach and inside its reach, with no player
     * in the structure, walk to the cell and turn on each other over it.
     *
     * @param helper the gametest helper
     */
    public static void drawsTwoZombiesFromRange(GameTestHelper helper) {
        layRangePen(helper);
        Mob first = helper.spawn(EntityType.ZOMBIE, EAST_CORNER);
        Mob second = helper.spawn(EntityType.ZOMBIE, SOUTH_CORNER);
        assertStartsInRange(helper, first);
        assertStartsInRange(helper, second);
        helper.succeedWhen(() -> helper.assertTrue(first.getTarget() == second && second.getTarget() == first,
                String.format(ZOMBIES_NOT_FIGHTING, first.getTarget(), second.getTarget())));
    }

    /**
     * Asserts a mob starts past the range cell's feeding reach and inside its reach.
     *
     * @param helper the gametest helper
     * @param mob    the mob
     */
    private static void assertStartsInRange(GameTestHelper helper, Mob mob) {
        Vec3 top = helper.absoluteVec(Vec3.atBottomCenterOf(RANGE_CELL))
                .add(0, (double) FED_CELL_LAYERS / VolumeCellBlock.MAX_LAYERS, 0);
        double off = mob.position().distanceTo(top);
        helper.assertTrue(off > NectarFeeds.REACH && off < SHIPPED_REACH,
                String.format(STARTS_IN_REACH, NectarFeeds.REACH, SHIPPED_REACH, off));
    }

    /**
     * Lays stone across the range pen and sets a five-layer Nectar cell into
     * its top course in one corner, walled on every side, with a stone wall
     * around the pen's rim two blocks tall.
     *
     * @param helper the gametest helper
     */
    private static void layRangePen(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 1, 0, RANGE_PEN_SPAN - 1, STONE_TOP_Y, RANGE_PEN_SPAN - 1)
                .forEach(pos -> helper.setBlock(pos.immutable(), Blocks.STONE));
        BlockPos.betweenClosed(0, STONE_TOP_Y + 1, 0, RANGE_PEN_SPAN - 1, PEN_WALL_TOP_Y, RANGE_PEN_SPAN - 1)
                .forEach(pos -> {
                    boolean rim = pos.getX() == 0 || pos.getZ() == 0 || pos.getX() == RANGE_PEN_SPAN - 1
                            || pos.getZ() == RANGE_PEN_SPAN - 1;
                    helper.setBlock(pos.immutable(), rim ? Blocks.STONE : Blocks.AIR);
                });
        helper.setBlock(RANGE_CELL, GooBlocks.JELLY_NECTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, FED_CELL_LAYERS));
    }

    /**
     * Lays stone across the pen and sets a five-layer Nectar cell into its
     * top course, walled on every side, with a stone wall around the pen's
     * rim two blocks tall.
     *
     * @param helper the gametest helper
     */
    private static void layPennedGoo(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 1, 0, PEN_SPAN - 1, STONE_TOP_Y, PEN_SPAN - 1)
                .forEach(pos -> helper.setBlock(pos.immutable(), Blocks.STONE));
        BlockPos.betweenClosed(0, STONE_TOP_Y + 1, 0, PEN_SPAN - 1, PEN_WALL_TOP_Y, PEN_SPAN - 1)
                .forEach(pos -> {
                    boolean rim = pos.getX() == 0 || pos.getZ() == 0 || pos.getX() == PEN_SPAN - 1
                            || pos.getZ() == PEN_SPAN - 1;
                    helper.setBlock(pos.immutable(), rim ? Blocks.STONE : Blocks.AIR);
                });
        helper.setBlock(GOO_CELL, GooBlocks.JELLY_NECTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, FED_CELL_LAYERS));
    }
}
