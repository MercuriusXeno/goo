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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
    private static final String COATS_A_WALL_AND_HOLDS = "nectar_drip_on_a_wall_coats_it_and_holds_what_hangs_there";
    private static final String LEAVING_GIVES_GRAVITY_BACK = "nectar_leaving_a_wall_coat_gives_gravity_back";
    private static final String SNEAKING_SINKS_DOWN_THE_COAT = "nectar_sneaking_sinks_down_a_wall_coat";
    private static final String SNEAKING_SINKS_PRESSED_TO_THE_WALL = "nectar_sneaking_sinks_pressed_against_a_wall";
    private static final String PILLAR_HOLDS_WHAT_PRESSES_IT = "nectar_pillar_holds_what_presses_against_it";
    private static final String HOLDS_ON_NEAR_THE_TOP = "nectar_stack_holds_a_climber_a_step_short_of_its_top";
    private static final String WALL_COAT_STAYS = "nectar_on_a_wall_stays_on_it";
    private static final String COATS_A_CLIMBABLE_COLUMN = "nectar_coats_a_wall_column_to_climb";
    private static final String COAT_OVER_FLOOR_GOO_STAYS = "nectar_on_a_wall_over_floor_goo_stays_on_it";
    private static final String HANGING_COAT_DRAWS_NO_FLOOR = "nectar_coating_a_hanging_wall_draws_no_floor";
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
    private static final int WALL_DRIPS = 12;
    /** The layers one wall holds of jelly's goo: a layer plus one for each point of its thick cohesion of 4. */
    private static final int ONE_WALL_HOLDS = 5;
    /** Ticks for the wall's goo to run down whatever its hold lets go, past a few of jelly's pours. */
    private static final int WALL_SETTLE_TICKS = 60;
    /** The column test's wall: three stone blocks stacked over the bay's floor. */
    private static final BlockPos COLUMN_FOOT = new BlockPos(2, 1, 2);
    private static final int COLUMN_HEIGHT = 3;
    /** The drips the column test lands on each face, bottom face first. */
    private static final int DRIPS_PER_FACE = 2;
    /** The over-floor test's stone floor, its wall standing on it two blocks tall, the floor goo at its foot. */
    private static final BlockPos FLOOR_UNDER_WALL = new BlockPos(2, 1, 2);
    /** The floor goo's layers at the wall's foot, short of a whole block so it has room. */
    private static final int FOOT_LAYERS = 3;
    /** The hanging wall's lowest block, over open air, so no block in front of the wall has a floor. */
    private static final BlockPos HANGING_WALL_FOOT = new BlockPos(2, 2, 2);
    private static final int HANGING_WALL_HEIGHT = 4;
    /** The drips the hanging wall test lands on each face, past one wall's hold, so the goo runs and piles. */
    private static final int DRIPS_ON_EACH_HANGING_FACE = 6;
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
    private static final String DROPPED_OFF_THE_WALL = "A zombie hanging in the coated space should stay put with"
            + " no gravity, dropped %.2f in %d ticks";
    /** The ticks the hang test watches a zombie in the coated space: long enough for a ladder's slide to show. */
    private static final int HANG_TICKS = 20;
    /** How far a hanging zombie may drift and still read as stuck: a fraction of a ladder slide's first tick. */
    private static final double HANG_SLACK = 0.05;
    /** The gravity modifier a thing stuck on a Nectar coated wall wears. */
    private static final Identifier NECTAR_STUCK = Identifier.parse("goo:nectar_stuck");
    /** The ticks a zombie hangs in the coat before it is moved off it, enough to be stuck. */
    private static final int STUCK_TICKS = 3;
    /** The ticks the zombie is watched after it leaves the coat, its gravity back and falling. */
    private static final int LEFT_TICKS = 10;
    private static final String NEVER_STUCK = "A zombie hanging in the coat should wear goo:nectar_stuck";
    /** The coated wall the sneak test sinks down, three blocks tall over open air. */
    private static final BlockPos TALL_WALL_FOOT = new BlockPos(2, 3, 2);
    private static final int TALL_WALL_HEIGHT = 3;
    /** The ticks the sneak test watches a sneaking zombie sink. */
    private static final int SNEAK_TICKS = 5;
    /** The least a sneaking zombie sinks in the watched ticks: half the nerfed climb's five ticks. */
    private static final double LEAST_SINK = 0.3;
    /** The stone block a column of Nectar stands on, in a slot walled west, north and south, open east over air. */
    private static final BlockPos PILLAR_FOOT = new BlockPos(2, 2, 2);
    private static final int PILLAR_HEIGHT = 3;
    /** How far east of the pillar's face a zombie pressed against it stands: half its width and a hair. */
    private static final double PRESSED_TO_THE_PILLAR = 0.3 + 0.01;
    /** The layers the top block of the climbed stack holds, a thin cap over a whole block. */
    private static final int THIN_CAP = 3;
    /** How far under the stack's top a climber's feet stand once moved up it: less than a step. */
    private static final double SHORT_OF_THE_TOP = 0.3;
    private static final String LET_GO_NEAR_THE_TOP = "A zombie stuck to a stack of Nectar a step short of its top should"
            + " hold on, still stuck %b, dropped %.2f in %d ticks";
    private static final String FELL_OFF_THE_PILLAR = "A zombie pressed against a pillar of Nectar should hang there,"
            + " dropped %.2f in %d ticks, the pillar beside it %s";
    /** How far off the wall a zombie pressed to a one-layer coat stands, its box against the coat's face. */
    private static final double PRESSED_TO_A_THIN_COAT = 1.0 / 15 + 0.3 + 0.01;
    private static final String NOT_SINKING = "A sneaking zombie stuck on a coated wall should sink, still stuck %b,"
            + " sank %.2f in %d ticks";
    private static final String STILL_FLOATING = "A zombie that left the coat should have its gravity back and fall:"
            + " still stuck %b, dropped %.2f in %d ticks";
    private static final String WALL_COAT_RAN = "The wall should keep %d of the %d layers landed on it, the cell"
            + " there stands %s";
    private static final String COLUMN_GAP = "Every face of the wall should keep Nectar attached so the run can"
            + " be climbed; the cells in front, bottom first, stand %s";
    private static final String COAT_DRAINED = "The coat on the wall over floor goo should keep %d layers, the"
            + " block there stands %s, the floor goo under it %s";
    private static final String FLOOR_IN_MIDAIR = "No block coating a wall over open air should draw a floor;"
            + " bottom first, each block's state, floor flag and floor share: %s";
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
        GooTestFunctions.registerTest(registrar, COATS_A_WALL_AND_HOLDS, NectarTests::coatsAWallAndHoldsWhatHangsThere);
        GooTestFunctions.registerTest(registrar, LEAVING_GIVES_GRAVITY_BACK, NectarTests::leavingAWallCoatGivesGravityBack);
        GooTestFunctions.registerTest(registrar, SNEAKING_SINKS_DOWN_THE_COAT, NectarTests::sneakingSinksDownAWallCoat);
        GooTestFunctions.registerTest(registrar, SNEAKING_SINKS_PRESSED_TO_THE_WALL,
                NectarTests::sneakingSinksPressedAgainstAWall);
        GooTestFunctions.registerTest(registrar, PILLAR_HOLDS_WHAT_PRESSES_IT, NectarTests::pillarHoldsWhatPressesAgainstIt);
        GooTestFunctions.registerTest(registrar, HOLDS_ON_NEAR_THE_TOP, NectarTests::stackHoldsAClimberAStepShortOfItsTop);
        GooTestFunctions.registerTest(registrar, WALL_COAT_STAYS, NectarTests::wallCoatStaysOnIt);
        GooTestFunctions.registerTest(registrar, COATS_A_CLIMBABLE_COLUMN, NectarTests::coatsAWallColumnToClimb);
        GooTestFunctions.registerTest(registrar, COAT_OVER_FLOOR_GOO_STAYS, NectarTests::coatOverFloorGooStays);
        GooTestFunctions.registerTest(registrar, HANGING_COAT_DRAWS_NO_FLOOR, NectarTests::hangingCoatDrawsNoFloor);
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
        Cow cow = helper.spawn(EntityTypes.COW, GOO_CELL.above().east());
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
        Mob first = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, GOO_CELL.above().east());
        Mob second = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, GOO_CELL.above().west());
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
        Cow calf = helper.spawnWithNoFreeWill(EntityTypes.COW, CALF_FLOOR.above());
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
     * stands Nectar attached to its west side, and a zombie hanging in that
     * cell, over open air, stays where it hangs with no gravity, stuck to the
     * wall as Spider-Man would be.
     * operator UAT 2026-11-17: on Nectar you are temporarily Spider-Man; gravity is off while you are stuck
     *
     * @param helper the gametest helper
     */
    public static void coatsAWallAndHoldsWhatHangsThere(GameTestHelper helper) {
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
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(coated).add(0, 0.2, 0));
        double hungAt = zombie.getY();
        helper.runAfterDelay(HANG_TICKS, () -> {
            double dropped = hungAt - zombie.getY();
            helper.assertTrue(Math.abs(dropped) <= HANG_SLACK, String.format(DROPPED_OFF_THE_WALL, dropped, HANG_TICKS));
            helper.succeed();
        });
    }

    /**
     * A zombie hanging in a Nectar wall coat over open air wears the stuck
     * gravity modifier; moved one block out from the coat over open air, it
     * gets its gravity back and falls.
     * operator UAT 2026-11-17: moving off the wall gets you down
     *
     * @param helper the gametest helper
     */
    public static void leavingAWallCoatGivesGravityBack(GameTestHelper helper) {
        helper.setBlock(WALL, Blocks.STONE);
        BlockPos coated = WALL.east();
        helper.setBlock(coated, Blocks.AIR);
        helper.setBlock(coated.east(), Blocks.AIR);
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.landOnSide(helper, caster, nectar, WALL, Direction.EAST, ONE_DRIP);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(coated).add(0, 0.2, 0));
        helper.runAfterDelay(STUCK_TICKS, () -> {
            helper.assertTrue(stuck(zombie), NEVER_STUCK);
            Vec3 out = helper.absoluteVec(Vec3.atBottomCenterOf(coated.east()).add(0, 0.2, 0));
            zombie.teleportTo(out.x, out.y, out.z);
            double leftAt = zombie.getY();
            helper.runAfterDelay(LEFT_TICKS, () -> {
                double dropped = leftAt - zombie.getY();
                helper.assertTrue(!stuck(zombie) && dropped > HANG_SLACK,
                        String.format(STILL_FLOATING, stuck(zombie), dropped, LEFT_TICKS));
                helper.succeed();
            });
        });
    }

    /**
     * A zombie stuck high on a three-high coated wall over open air sinks
     * while it sneaks, still stuck to the coat.
     * operator UAT 2026-11-17: sneaking descends
     *
     * @param helper the gametest helper
     */
    public static void sneakingSinksDownAWallCoat(GameTestHelper helper) {
        for (int up = 0; up < TALL_WALL_HEIGHT; up++) {
            helper.setBlock(TALL_WALL_FOOT.above(up), Blocks.STONE);
            helper.setBlock(TALL_WALL_FOOT.above(up).east(), Blocks.AIR);
        }
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        for (int up = 0; up < TALL_WALL_HEIGHT; up++) {
            LandedDrips.landOnSide(helper, caster, nectar, TALL_WALL_FOOT.above(up), Direction.EAST, ONE_DRIP);
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockPos top = TALL_WALL_FOOT.above(TALL_WALL_HEIGHT - 1).east();
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, Vec3.atBottomCenterOf(top).add(0, 0.2, 0));
        helper.runAfterDelay(STUCK_TICKS, () -> {
            helper.assertTrue(stuck(zombie), NEVER_STUCK);
            zombie.setShiftKeyDown(true);
            double sneakFrom = zombie.getY();
            helper.runAfterDelay(SNEAK_TICKS, () -> {
                double sank = sneakFrom - zombie.getY();
                helper.assertTrue(stuck(zombie) && sank >= LEAST_SINK,
                        String.format(NOT_SINKING, stuck(zombie), sank, SNEAK_TICKS));
                helper.succeed();
            });
        });
    }

    /**
     * A zombie pressed against the wall in a thin coat, a thicker coat on
     * the wall below it, sinks while it sneaks, as a player pressing into the
     * wall does.
     * operator UAT 2026-11-17: sneaking descends
     *
     * @param helper the gametest helper
     */
    public static void sneakingSinksPressedAgainstAWall(GameTestHelper helper) {
        for (int up = 0; up < TALL_WALL_HEIGHT; up++) {
            helper.setBlock(TALL_WALL_FOOT.above(up), Blocks.STONE);
            helper.setBlock(TALL_WALL_FOOT.above(up).east(), Blocks.AIR);
        }
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        for (int up = 0; up < TALL_WALL_HEIGHT; up++) {
            int drips = up == TALL_WALL_HEIGHT - 1 ? ONE_DRIP : ONE_WALL_HOLDS;
            LandedDrips.landOnSide(helper, caster, nectar, TALL_WALL_FOOT.above(up), Direction.EAST, drips);
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockPos top = TALL_WALL_FOOT.above(TALL_WALL_HEIGHT - 1).east();
        Vec3 pressed = new Vec3(top.getX() + PRESSED_TO_A_THIN_COAT, top.getY() + 0.2, top.getZ() + 0.5);
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pressed);
        helper.runAfterDelay(STUCK_TICKS, () -> {
            helper.assertTrue(stuck(zombie), NEVER_STUCK);
            zombie.setShiftKeyDown(true);
            double sneakFrom = zombie.getY();
            helper.runAfterDelay(SNEAK_TICKS, () -> {
                double sank = sneakFrom - zombie.getY();
                helper.assertTrue(stuck(zombie) && sank >= LEAST_SINK,
                        String.format(NOT_SINKING, stuck(zombie), sank, SNEAK_TICKS) + " pressed at x "
                                + zombie.getX() + " states " + helper.getBlockState(top) + " / "
                                + helper.getBlockState(top.below()));
                helper.succeed();
            });
        });
    }

    /**
     * A zombie pressed against the open side of a column of whole Nectar
     * blocks standing in a stone slot, over open air, hangs there: goo
     * standing taller than a step beside a thing counts as Nectar on a wall.
     * operator UAT 2026-11-17: touching the side of Nectar goo taller than a step counts as Nectar on a wall
     *
     * @param helper the gametest helper
     */
    public static void pillarHoldsWhatPressesAgainstIt(GameTestHelper helper) {
        helper.setBlock(PILLAR_FOOT, Blocks.STONE);
        for (int up = 1; up <= PILLAR_HEIGHT; up++) {
            BlockPos goo = PILLAR_FOOT.above(up);
            helper.setBlock(goo.west(), Blocks.STONE);
            helper.setBlock(goo.north(), Blocks.STONE);
            helper.setBlock(goo.south(), Blocks.STONE);
            helper.setBlock(goo.east(), Blocks.AIR);
            helper.setBlock(goo, GooBlocks.JELLY_NECTAR.get().defaultBlockState()
                    .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        }
        helper.setBlock(PILLAR_FOOT.east(), Blocks.AIR);
        BlockPos beside = PILLAR_FOOT.above(2).east();
        Vec3 pressed = new Vec3(beside.getX() + PRESSED_TO_THE_PILLAR, beside.getY() + 0.2, beside.getZ() + 0.5);
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pressed);
        double hungAt = zombie.getY();
        helper.runAfterDelay(HANG_TICKS, () -> {
            double dropped = hungAt - zombie.getY();
            helper.assertTrue(Math.abs(dropped) <= HANG_SLACK, String.format(FELL_OFF_THE_PILLAR, dropped, HANG_TICKS,
                    helper.getBlockState(PILLAR_FOOT.above(2))));
            helper.succeed();
        });
    }

    /**
     * A zombie stuck to a stack of Nectar, a whole block capped with a thin
     * block, in a stone slot over open air, then moved up until its feet
     * stand less than a step under the stack's top, holds on there, where it
     * would let go and hop if goo had to rise a step over its feet to keep it.
     * operator UAT 2026-11-17: a stack of Nectar a couple of blocks tall just had me hopping
     *
     * @param helper the gametest helper
     */
    public static void stackHoldsAClimberAStepShortOfItsTop(GameTestHelper helper) {
        helper.setBlock(PILLAR_FOOT, Blocks.STONE);
        for (int up = 1; up <= 2; up++) {
            BlockPos goo = PILLAR_FOOT.above(up);
            helper.setBlock(goo.west(), Blocks.STONE);
            helper.setBlock(goo.north(), Blocks.STONE);
            helper.setBlock(goo.south(), Blocks.STONE);
            helper.setBlock(goo.east(), Blocks.AIR);
            helper.setBlock(goo.above().east(), Blocks.AIR);
            helper.setBlock(goo, GooBlocks.JELLY_NECTAR.get().defaultBlockState()
                    .setValue(VolumeCellBlock.LAYERS, up == 2 ? THIN_CAP : VolumeCellBlock.MAX_LAYERS));
        }
        helper.setBlock(PILLAR_FOOT.east(), Blocks.AIR);
        double top = helper.absolutePos(PILLAR_FOOT.above(2)).getY() + (double) THIN_CAP / VolumeCellBlock.MAX_LAYERS;
        BlockPos beside = PILLAR_FOOT.above().east();
        Vec3 pressed = new Vec3(beside.getX() + PRESSED_TO_THE_PILLAR, beside.getY() + 0.2, beside.getZ() + 0.5);
        Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pressed);
        helper.runAfterDelay(STUCK_TICKS, () -> {
            helper.assertTrue(stuck(zombie), NEVER_STUCK);
            zombie.teleportTo(zombie.getX(), top - SHORT_OF_THE_TOP, zombie.getZ());
            double heldAt = zombie.getY();
            helper.runAfterDelay(LEFT_TICKS, () -> {
                double dropped = heldAt - zombie.getY();
                helper.assertTrue(stuck(zombie) && Math.abs(dropped) <= HANG_SLACK,
                        String.format(LET_GO_NEAR_THE_TOP, stuck(zombie), dropped, LEFT_TICKS));
                helper.succeed();
            });
        });
    }

    private static boolean stuck(Mob mob) {
        AttributeInstance gravity = mob.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(NECTAR_STUCK);
    }

    /**
     * Twelve Nectar drips striking a stone wall's east side, clear of any
     * floor, leave the wall coated: once the goo has had time to run, the
     * cell east of the wall still holds the five layers one wall holds of
     * jelly's thick goo, attached to its west side, the rest run down.
     * cohesion-is-the-step-the-goo-holds
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
     * Nectar coating a wall's face over floor goo at the wall's foot stays on
     * the wall: twelve drips struck on the face above the floor goo leave the
     * block there holding the nine layers one wall holds of jelly's goo,
     * attached, the floor goo under it taking only what slips past that hold.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper the gametest helper
     */
    public static void coatOverFloorGooStays(GameTestHelper helper) {
        helper.setBlock(FLOOR_UNDER_WALL, Blocks.STONE);
        helper.setBlock(FLOOR_UNDER_WALL.east(), Blocks.STONE);
        BlockPos wallFoot = FLOOR_UNDER_WALL.above();
        BlockPos wallFace = wallFoot.above();
        helper.setBlock(wallFoot, Blocks.STONE);
        helper.setBlock(wallFace, Blocks.STONE);
        BlockPos footGoo = wallFoot.east();
        BlockPos coated = wallFace.east();
        helper.setBlock(footGoo, GooBlocks.JELLY_NECTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, FOOT_LAYERS));
        helper.setBlock(coated, Blocks.AIR);
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.landOnSide(helper, caster, nectar, wallFace, Direction.EAST, WALL_DRIPS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(WALL_SETTLE_TICKS, () -> {
            BlockState cell = helper.getBlockState(coated);
            helper.assertTrue(cell.is(GooBlocks.JELLY_NECTAR.get())
                    && cell.getValue(VolumeCellBlock.WEST) == SideCoat.ATTACHED
                    && cell.getValue(VolumeCellBlock.LAYERS) == ONE_WALL_HOLDS,
                    String.format(COAT_DRAINED, ONE_WALL_HOLDS, cell, helper.getBlockState(footGoo)));
            helper.succeed();
        });
    }

    /**
     * Nectar coating a wall that hangs over open air draws no floor in midair:
     * six drips landed on each face of a four-high wall whose foot stands
     * over air, once the goo has run, leave no block in front of the wall
     * drawing a floor share, since nothing solid stands under any of them.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * attached-goo-draws-per-attachment-and-divides-its-volume
     *
     * @param helper the gametest helper
     */
    public static void hangingCoatDrawsNoFloor(GameTestHelper helper) {
        for (int up = -1; up < HANGING_WALL_HEIGHT; up++) {
            helper.setBlock(HANGING_WALL_FOOT.above(up).east(), Blocks.AIR);
        }
        for (int up = 0; up < HANGING_WALL_HEIGHT; up++) {
            helper.setBlock(HANGING_WALL_FOOT.above(up), Blocks.STONE);
        }
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        for (int up = 0; up < HANGING_WALL_HEIGHT; up++) {
            LandedDrips.landOnSide(helper, caster, nectar, HANGING_WALL_FOOT.above(up), Direction.EAST,
                    DRIPS_ON_EACH_HANGING_FACE);
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(WALL_SETTLE_TICKS, () -> {
            VolumeCellBlock nectarCell = GooBlocks.JELLY_NECTAR.get();
            List<String> blocks = new ArrayList<>();
            boolean anyFloor = false;
            for (int up = 0; up < HANGING_WALL_HEIGHT; up++) {
                BlockPos cell = helper.absolutePos(HANGING_WALL_FOOT.above(up).east());
                BlockState state = helper.getLevel().getBlockState(cell);
                if (!state.is(nectarCell)) {
                    blocks.add(state.toString());
                    continue;
                }
                boolean floor = nectarCell.onAFloor(helper.getLevel(), cell);
                double share = VolumeCellBlock.sharesOf(state, floor, nectarCell.cohesionIn(helper.getLevel())).floor();
                anyFloor |= share > 0;
                blocks.add(state + " floor " + floor + " share " + share);
            }
            helper.assertTrue(!anyFloor, String.format(FLOOR_IN_MIDAIR, blocks));
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
        Cow cow = helper.spawn(EntityTypes.COW, FAR_CORNER);
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
        Mob first = helper.spawn(EntityTypes.ZOMBIE, EAST_CORNER);
        Mob second = helper.spawn(EntityTypes.ZOMBIE, SOUTH_CORNER);
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
