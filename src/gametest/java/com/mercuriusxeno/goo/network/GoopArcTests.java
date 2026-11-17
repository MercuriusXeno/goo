package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Goop's drips flying real arcs, each held through the real
 * stream tick: a level shot lands about where the old range ended, a shot
 * down from a height lands past it, and drips still falling after the hold
 * ends lay their coat when they strike, and a drip striking a mob wobbles it.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-and-slick-throw-keeps-todays-level-reach
 * goop-wobbles-a-mob-its-drip-strikes
 */
@RegistersGameTests
public final class GoopArcTests {

    private static final Identifier GOOP = Identifier.parse("goo:weird_goop");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:weird_goop";
    private static final String GOOP_LANDS_LEVEL_AHEAD = "goop_lands_about_eight_blocks_ahead_on_flat_ground";
    private static final String GOOP_COATS_BELOW_A_PERCH = "goop_coats_the_floor_below_a_high_perch";
    private static final String GOOP_LANDS_AFTER_RELEASE = "goop_lands_its_coat_after_the_hold_ends";
    private static final String GOOP_WOBBLES_A_FAR_MOB = "goop_wobbles_a_mob_its_drip_strikes_past_eight_blocks";
    private static final String DRIP_STANDS_A_CELL = "goop_drip_stands_a_one_layer_cell_in_the_tick_it_strikes";
    private static final String DRIPS_ADD_A_LAYER_EACH = "goop_drips_add_a_layer_each_up_to_fifteen";
    private static final String GOOP_LEAVES_ALONG_A_FAR_LOOK = "goop_drips_leave_within_the_cone_of_a_far_look";
    private static final String GOOP_IGNORES_THE_CROSSHAIR_BLOCK = "goop_drips_leave_alike_whatever_the_look_meets";
    /** How far across the ground the far look meets the floor, where the crosshair solve pitched 28 degrees up. */
    private static final double FAR_LOOK_ACROSS = 18;
    /** The glove hand under the eye, so it stands 1.4 blocks over the floor as a standing caster's does. */
    private static final double HAND_BELOW_THE_EYE = 0.22;
    /** How far along the look the block the crosshair may meet stands, in blocks. */
    private static final int CROSSHAIR_BLOCK_AHEAD = 5;
    /** Ticks between the two throws, past the gap a hold continues over, so each throw is a hold's first tick. */
    private static final int NEW_HOLD_GAP = StreamHolds.LATEST_CONTINUING_GAP + 2;
    /** The slack a launch's angle off the look may carry over the cone's rim, radians: rounding alone. */
    private static final double RIM_SLACK = 1e-6;
    private static final String DRIP_OFF_THE_CONE = "Drip %d of hold tick %d leaves %.2f degrees off the look,"
            + " past the cone's half of %.2f";
    private static final String NO_DRIPS_THROWN = "Holding Goop should throw a drip each tick";
    private static final String CROSSHAIR_CHANGED_DRIPS = "The block on the look should change no drip's launch:"
            + " with air %s, with a block %s";
    /** The cell the landed drips strike, walled in stone on a stone floor so nothing pours. */
    private static final BlockPos DRIPPED_CELL = new BlockPos(4, 1, 4);
    private static final String HELD_AT_A_WALL = "goop_held_at_a_wall_leaves_a_film";
    private static final String DRIP_ON_A_WALL_COSTS = "goop_drip_on_a_wall_costs_one_layer";
    private static final String FILM_DROPS_ITS_LAYER = "goop_film_drops_its_layer_when_its_wall_breaks";
    private static final String GLASS_RING_KEEPS_EVERY_LAYER = "goop_held_into_a_glass_ring_keeps_every_layer_paid_for";
    /** A one-block hollow six blocks east of the streamer, in the floor's row, ringed by glass. */
    private static final BlockPos HOLLOW = new BlockPos(6, 1, 8);
    private static final String DRIPS_UNLANDED = "Each of the %d drips the hold threw should stand as a layer,"
            + " %d layers stand";
    private static final String OVERHANG_BESIDE_A_WALL = "goop_held_up_at_an_overhang_beside_a_wall_ends_at_its_foot";
    private static final String OVERHANG_OVER_OPEN_FLOOR = "goop_held_up_at_an_overhang_over_open_floor_drops_to_it";
    /** The open cell under the overhang, a block over the floor's cell, three blocks east of the streamer. */
    private static final BlockPos UNDER_THE_OVERHANG = new BlockPos(4, 2, 8);
    /** Where the overhang's streamer stands, two blocks west of the cell under it. */
    private static final BlockPos OVERHANG_STREAMER_POS = new BlockPos(2, 1, 8);
    private static final String NO_FILL_UNDER = "The cell at %s should hold the underside's layers as fill, holds %s";
    private static final String LEDGE_POUR_FALLS = "goop_poured_off_a_ledge_lands_when_its_drip_arrives";
    private static final String UNDERSIDE_STRIKE_FALLS = "goop_struck_under_an_overhang_falls_when_its_drip_arrives";
    /** The layers the pillar's Goop starts at: one past Goop's step over an open ledge, so it pours one. */
    private static final int POURING_LAYERS = 6;
    /** Ticks a fall is watched, past any three-block fall's arrival. */
    private static final int FALL_WATCH_TICKS = 40;
    private static final String FALL_PRICE_WRONG = "The underside strike should cost one layer's %d mb, cost %d";
    private static final String WALL_OVER_OPEN_AIR_HOLDS = "goop_sprayed_at_a_wall_over_open_air_holds_what_stuck";
    private static final String BLOCK_FALLS_WHOLE = "goop_block_falls_whole_when_its_last_wall_breaks";
    private static final String HELD_BLOCK_FALLS_WHOLE = "goop_block_holding_its_walls_layers_falls_whole";
    private static final String RESTING_BESIDE_A_WALL_ADHERES = "goop_resting_beside_a_wall_adheres_to_it";
    /** The layers a block of Goop resting beside a wall holds, under Goop's pour gap so it stays put. */
    private static final int RESTING_VOLUME = 4;
    /** Ticks a resting block's first tick comes within. */
    private static final int ADHERE_TICKS = 20;
    private static final String CEILING_BLOCK_FALLS_WHOLE = "goop_on_a_ceiling_falls_whole_when_its_ceiling_breaks";
    /** The lowest face of the wall over open air, a block over the floor cells at its foot. */
    private static final int OPEN_AIR_WALL_BOTTOM = 2;
    /** A wall standing over open air, three blocks east of the streamer. */
    private static final int OPEN_AIR_WALL_EAST = 3;
    /** The volume of the Goop block whose last wall breaks. */
    private static final int FALLING_VOLUME = 3;
    private static final String STUCK_WRONG = "Each Goop block beside the wall should be attached and hold at most %d"
            + " layers, one holding that many, and the wall's foot should hold fill: attached %s, foot %d";
    private static final String FELL_WRONG = "The floor under the broken wall should gain the block's %d layers,"
            + " holds %d";
    private static final String LAYERS_UNPAID = "The %d layers of slime standing should cost %s, the weird goo"
            + " drained, %d mb";
    /** The wall Goop is held at, four blocks east of the streamer. */
    private static final int WALL_EAST = 4;
    private static final int WALL_HEIGHT = 5;
    /** A stone block standing alone over open air, its west face struck. */
    private static final BlockPos FLOATING_WALL = new BlockPos(6, 4, 4);
    /** Ticks a film is watched standing before its wall breaks, past several pours' pace. */
    private static final int FILM_WATCH_TICKS = 20;
    /** Ticks a broken film's goo item is given to fall to the floor. */
    private static final int FILM_FALL_TICKS = 30;
    /** How far from the film's cell its dropped goo item is looked for. */
    private static final double FILM_DROP_REACH = 6.0;
    private static final String NO_FILM_ON_THE_WALL = "Goop held at a wall should leave a film on a cell beside it";
    private static final String NO_FILM_AT = "The cell at %s should hold a film facing the wall east, holds %s";
    private static final String FILM_PRICE_WRONG = "One drip on a wall should drain one layer's price %s, drained %d";
    private static final String FILM_DROP_WRONG = "A film whose wall broke should fall, its layer standing as fill or"
            + " dropped as %d mb of weird goo: %d mb dropped, %d layers standing";
    /** The layers a whole block of slime holds. */
    private static final int WHOLE_BLOCK_LAYERS = 15;
    private static final String LAYERS_WRONG = "The dripped cell should hold %d layers of slime, holds %s";
    /** The zombie stands on the perched floor this far east of the perch, well past eight blocks from the eye. */
    private static final int ZOMBIE_EAST = 6;
    /**
     * Ticks Goop is held on the zombie: its one drip a tick scatters over the cone, so enough drips for one
     * to strike a mob thirteen blocks off.
     */
    private static final int WOBBLE_HOLD_TICKS = 40;
    private static final String NOT_WOBBLED = "A zombie %.1f blocks from the streamer, struck by Goop's drips,"
            + " should hold the wobble";
    private static final String NOT_FAR = "The zombie should stand past eight blocks from the eye, stands %.1f off";
    private static final double OLD_REACH = 8;
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    /** Pitched well down, so the cone's drips clear the perch's edge and fall to the floor east of it. */
    private static final float DOWN_OFF_THE_PERCH = 60f;
    /** Where the level shot's streamer stands in the light bay, at its west wall, on the floor's first stone. */
    private static final BlockPos LEVEL_STREAMER_POS = new BlockPos(0, 1, 8);
    /** The light bay is sixteen blocks across and holds no floor of its own, so the floor runs to its east wall. */
    private static final int FLOOR_EAST = 15;
    /** The floor runs this far north and south of the streamer's row, wider than the cone's spread. */
    private static final int FLOOR_HALF_WIDTH = 4;
    /** The nearest and farthest the level shot's coat may stand ahead of the streamer, in cells. */
    private static final int NEAREST_AHEAD = 7;
    private static final int FARTHEST_AHEAD = 9;
    /** The perched floor stands over the bay's barrier roof, in open air the bay's roof does not cut. */
    private static final int PERCHED_FLOOR_Y = 10;
    /** How far the low perch stands over the floor's top, past the eight blocks Goop's range reached. */
    private static final int LOW_PERCH = 11;
    /** How far the high perch stands over the floor's top: its drips fall longer than the hold lasts. */
    private static final int HIGH_PERCH = 30;
    /** A hold one drip a tick sprays the whole fan in, a few ticks over, so every share of the cone has flown. */
    private static final int SPRAY_HOLD_TICKS = CoatRay.FAN_SHARES + 5;
    /** A hold long enough that the wall's most struck blocks fill to all their wall holds. */
    private static final int WALL_HOLD_TICKS = SPRAY_HOLD_TICKS * 5;
    /** Ticks given the last drips of a hold to fall onto the floor, past the longest fall these tests make. */
    private static final int FALL_TICKS = 60;
    private static final String NO_COAT_AHEAD = "A level Goop shot should coat a floor cell 7 to 9 blocks ahead,"
            + " coated cells are %s";
    private static final String NO_COAT_BELOW = "Goop aimed down from %d blocks up should coat the floor below,"
            + " coated cells are %s";
    private static final String LANDED_BEFORE_RELEASE = "The high perch's first drips should still be falling when"
            + " the hold ends, coated cells are %s";

    private GoopArcTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GOOP_LANDS_LEVEL_AHEAD, GoopArcTests::goopLandsAboutEightBlocksAhead);
        GooTestFunctions.registerTest(registrar, GOOP_COATS_BELOW_A_PERCH, GoopArcTests::goopCoatsTheFloorBelowAPerch);
        GooTestFunctions.registerTest(registrar, GOOP_LANDS_AFTER_RELEASE, GoopArcTests::goopLandsItsCoatAfterRelease);
        GooTestFunctions.registerTest(registrar, GOOP_WOBBLES_A_FAR_MOB, GoopArcTests::goopWobblesAFarMobItsDripStrikes);
        GooTestFunctions.registerTest(registrar, DRIP_STANDS_A_CELL, GoopArcTests::goopDripStandsAOneLayerCell);
        GooTestFunctions.registerTest(registrar, DRIPS_ADD_A_LAYER_EACH, GoopArcTests::goopDripsAddALayerEach);
        GooTestFunctions.registerTest(registrar, GOOP_LEAVES_ALONG_A_FAR_LOOK,
                GoopArcTests::goopDripsLeaveWithinTheConeOfAFarLook);
        GooTestFunctions.registerTest(registrar, GOOP_IGNORES_THE_CROSSHAIR_BLOCK,
                GoopArcTests::goopDripsLeaveAlikeWhateverTheLookMeets);
        GooTestFunctions.registerTest(registrar, HELD_AT_A_WALL, GoopArcTests::goopHeldAtAWallLeavesAFilm);
        GooTestFunctions.registerTest(registrar, DRIP_ON_A_WALL_COSTS, GoopArcTests::goopDripOnAWallCostsOneLayer);
        GooTestFunctions.registerTest(registrar, FILM_DROPS_ITS_LAYER, GoopArcTests::goopFilmDropsItsLayerWhenItsWallBreaks);
        GooTestFunctions.registerTest(registrar, OVERHANG_BESIDE_A_WALL,
                GoopArcTests::goopHeldUpAtAnOverhangBesideAWallEndsAtItsFoot);
        GooTestFunctions.registerTest(registrar, OVERHANG_OVER_OPEN_FLOOR,
                GoopArcTests::goopHeldUpAtAnOverhangOverOpenFloorDropsToIt);
        GooTestFunctions.registerTest(registrar, LEDGE_POUR_FALLS,
                GoopArcTests::goopPouredOffALedgeLandsWhenItsDripArrives);
        GooTestFunctions.registerTest(registrar, UNDERSIDE_STRIKE_FALLS,
                GoopArcTests::goopStruckUnderAnOverhangFallsWhenItsDripArrives);
        GooTestFunctions.registerTest(registrar, WALL_OVER_OPEN_AIR_HOLDS,
                GoopArcTests::goopSprayedAtAWallOverOpenAirHoldsWhatStuck);
        GooTestFunctions.registerTest(registrar, BLOCK_FALLS_WHOLE, GoopArcTests::goopBlockFallsWholeWhenItsLastWallBreaks);
        GooTestFunctions.registerTest(registrar, CEILING_BLOCK_FALLS_WHOLE,
                GoopArcTests::goopOnACeilingFallsWholeWhenItsCeilingBreaks);
        GooTestFunctions.registerTest(registrar, RESTING_BESIDE_A_WALL_ADHERES,
                GoopArcTests::goopRestingBesideAWallAdheresToIt);
        GooTestFunctions.registerTest(registrar, HELD_BLOCK_FALLS_WHOLE,
                GoopArcTests::goopBlockHoldingItsWallsLayersFallsWhole);
        GooTestFunctions.registerTest(registrar, GLASS_RING_KEEPS_EVERY_LAYER,
                GoopArcTests::goopHeldIntoAGlassRingKeepsEveryLayerPaidFor);
    }

    /**
     * One Goop drip striking a bare stone floor stands a slime cell of one
     * layer over it in the tick it strikes.
     * drip-creates-a-volume-cell-immediately
     *
     * @param helper the gametest helper
     */
    public static void goopDripStandsAOneLayerCell(GameTestHelper helper) {
        wallInOnStone(helper, DRIPPED_CELL);
        ServerPlayer caster = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        LandedDrips.landOn(helper, caster, goop(helper), DRIPPED_CELL, 1);
        assertLayers(helper, DRIPPED_CELL, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /**
     * Goop drips striking a standing slime cell add a layer each: two in one
     * tick raise a one-layer cell to three, and a cell of fifteen layers, a
     * whole block, takes no more.
     * drip-on-a-standing-cell-increases-it
     * world-goo-fills-a-block-space-in-one-to-fifteen-layers
     *
     * @param helper the gametest helper
     */
    public static void goopDripsAddALayerEach(GameTestHelper helper) {
        wallInOnStone(helper, DRIPPED_CELL);
        ServerPlayer caster = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        AbilityDefinition goop = goop(helper);
        LandedDrips.landOn(helper, caster, goop, DRIPPED_CELL, 1);
        LandedDrips.landOn(helper, caster, goop, DRIPPED_CELL, 2);
        assertLayers(helper, DRIPPED_CELL, 3);
        helper.setBlock(DRIPPED_CELL, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, WHOLE_BLOCK_LAYERS));
        LandedDrips.landOn(helper, caster, goop, DRIPPED_CELL, 1);
        assertLayers(helper, DRIPPED_CELL, WHOLE_BLOCK_LAYERS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /**
     * Lays a stone floor under a cell and stone on its four sides, so slime in it cannot pour.
     *
     * @param helper the gametest helper
     * @param cell   the cell
     */
    private static void wallInOnStone(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
    }

    private static void assertLayers(GameTestHelper helper, BlockPos cell, int layers) {
        BlockState state = helper.getBlockState(cell);
        Object held = state.is(GooBlocks.SLIME_COAT.get()) ? state.getValue(VolumeCellBlock.LAYERS) : state;
        helper.assertTrue(Integer.valueOf(layers).equals(held), String.format(LAYERS_WRONG, layers, held));
    }

    /**
     * A streamer standing on a flat stone floor holds Goop level, then lets
     * go: once the drips have fallen a slime coat stands in a floor cell 7 to
     * 9 blocks ahead along the look.
     * goop-and-slick-throw-keeps-todays-level-reach
     *
     * @param helper the gametest helper
     */
    public static void goopLandsAboutEightBlocksAhead(GameTestHelper helper) {
        BlockPos floor = LEVEL_STREAMER_POS.below();
        layFloor(helper, floor.getY(), floor.getZ());
        ServerPlayer streamer = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        int hold = SPRAY_HOLD_TICKS;
        holdGoopFor(helper, streamer, hold);
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<BlockPos> coated = coatedOver(helper, floor.getY(), floor.getZ());
            boolean ahead = coated.stream().anyMatch(cell -> cell.getZ() == floor.getZ()
                    && cell.getX() - LEVEL_STREAMER_POS.getX() >= NEAREST_AHEAD
                    && cell.getX() - LEVEL_STREAMER_POS.getX() <= FARTHEST_AHEAD);
            helper.assertTrue(ahead, String.format(NO_COAT_AHEAD, coated));
            helper.succeed();
        });
    }

    /**
     * A streamer standing at a corner of a flat stone floor looks across it at
     * the floor 18 blocks off, where a solve onto the crosshair's point had
     * pitched the spray 28 degrees up, and holds Goop from a hand under the
     * eye: every drip of every hold tick leaves within half the cone of the look.
     * goo-spray-scatters-in-a-cone-around-the-look-vector
     *
     * @param helper the gametest helper
     */
    public static void goopDripsLeaveWithinTheConeOfAFarLook(GameTestHelper helper) {
        for (int east = 0; east <= FLOOR_EAST; east++) {
            for (int south = 0; south <= FLOOR_EAST; south++) {
                helper.setBlock(new BlockPos(east, 0, south), Blocks.STONE);
            }
        }
        BlockPos corner = new BlockPos(0, 1, 0);
        ServerPlayer streamer = goopStreamerAt(helper, corner, LEVEL);
        Vec3 across = new Vec3(1, 0, 1).normalize().scale(FAR_LOOK_ACROSS);
        streamer.lookAt(EntityAnchorArgument.Anchor.EYES,
                helper.absoluteVec(Vec3.atBottomCenterOf(corner).add(across)));
        Vec3 look = streamer.getLookAngle();
        double halfCone = Math.toRadians(goop(helper).delivery().coneDegrees() / 2);
        Vec3 hand = streamer.getEyePosition().subtract(0, HAND_BELOW_THE_EYE, 0);
        int[] thrown = {0};
        for (int held = 1; held <= SPRAY_HOLD_TICKS; held++) {
            int tick = held;
            helper.runAfterDelay(held, () -> {
                List<Vec3> launches = launchesOfOneTick(streamer, hand);
                for (int drip = 0; drip < launches.size(); drip++) {
                    double off = Math.acos(Math.min(1, launches.get(drip).normalize().dot(look)));
                    helper.assertTrue(off <= halfCone + RIM_SLACK, String.format(DRIP_OFF_THE_CONE, drip, tick,
                            Math.toDegrees(off), Math.toDegrees(halfCone)));
                }
                thrown[0] += launches.size();
            });
        }
        helper.runAfterDelay(SPRAY_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(thrown[0] > 0, NO_DRIPS_THROWN);
            helper.succeed();
        });
    }

    /**
     * A streamer standing on a flat stone floor looks level east and throws
     * a hold's first tick of Goop twice, once with open air along the look
     * and once with a stone block standing on it five blocks out: both
     * throws launch the same drip velocities.
     * goo-spray-scatters-in-a-cone-around-the-look-vector
     *
     * @param helper the gametest helper
     */
    public static void goopDripsLeaveAlikeWhateverTheLookMeets(GameTestHelper helper) {
        BlockPos floor = LEVEL_STREAMER_POS.below();
        layFloor(helper, floor.getY(), floor.getZ());
        ServerPlayer streamer = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        Vec3 hand = streamer.getEyePosition().subtract(0, HAND_BELOW_THE_EYE, 0);
        List<List<Vec3>> throwings = new ArrayList<>();
        helper.runAfterDelay(1, () -> throwings.add(launchesOfOneTick(streamer, hand)));
        helper.runAfterDelay(1 + NEW_HOLD_GAP, () -> {
            helper.setBlock(LEVEL_STREAMER_POS.above().east(CROSSHAIR_BLOCK_AHEAD), Blocks.STONE);
            throwings.add(launchesOfOneTick(streamer, hand));
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<Vec3> withAir = throwings.get(0);
            List<Vec3> withBlock = throwings.get(1);
            helper.assertTrue(!withAir.isEmpty(), NO_DRIPS_THROWN);
            boolean alike = withAir.size() == withBlock.size();
            for (int drip = 0; alike && drip < withAir.size(); drip++) {
                alike = withAir.get(drip).distanceToSqr(withBlock.get(drip)) == 0;
            }
            helper.assertTrue(alike, String.format(CROSSHAIR_CHANGED_DRIPS, withAir, withBlock));
            helper.succeed();
        });
    }

    /**
     * Runs one Goop stream tick from a hand and reads the launch velocity of
     * each drip the tick threw, before any server tick steps them.
     *
     * @param streamer the streaming player
     * @param hand     the glove hand the client sends
     * @return each thrown drip's launch velocity, blocks per tick, in the order thrown
     */
    private static List<Vec3> launchesOfOneTick(ServerPlayer streamer, Vec3 hand) {
        DripFlights flights = GooServerState.of(streamer.level().getServer()).dripFlights();
        int before = flights.flyingFrom(streamer.getUUID()).size();
        GooStreamHandler.streamTick(streamer, GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD),
                GOOP.toString(), hand, streamer.getEyePosition()));
        List<DripFlights.Flight> flying = flights.flyingFrom(streamer.getUUID());
        return flying.subList(before, flying.size()).stream().map(DripFlights.Flight::velocity).toList();
    }

    /**
     * A streamer on a perch eleven blocks over a stone floor holds Goop aimed
     * down off the perch: once the drips have fallen slime stands on the floor.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper the gametest helper
     */
    public static void goopCoatsTheFloorBelowAPerch(GameTestHelper helper) {
        layFloor(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
        ServerPlayer streamer = perchedStreamer(helper, LOW_PERCH);
        int hold = SPRAY_HOLD_TICKS;
        holdGoopFor(helper, streamer, hold);
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<BlockPos> coated = coatedOver(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
            helper.assertTrue(!coated.isEmpty(), String.format(NO_COAT_BELOW, LOW_PERCH, coated));
            helper.succeed();
        });
    }

    /**
     * A streamer on a perch thirty blocks over a stone floor holds Goop
     * aimed down for a coat's worth and lets go before the first drip lands:
     * the floor stands bare when the hold ends, and slime stands on it once
     * the drips have fallen.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper the gametest helper
     */
    public static void goopLandsItsCoatAfterRelease(GameTestHelper helper) {
        layFloor(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
        ServerPlayer streamer = perchedStreamer(helper, HIGH_PERCH);
        int hold = SPRAY_HOLD_TICKS;
        holdGoopFor(helper, streamer, hold);
        helper.runAfterDelay(hold + 1, () -> {
            List<BlockPos> coated = coatedOver(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
            helper.assertTrue(coated.isEmpty(), String.format(LANDED_BEFORE_RELEASE, coated));
        });
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<BlockPos> coated = coatedOver(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
            helper.assertTrue(!coated.isEmpty(), String.format(NO_COAT_BELOW, HIGH_PERCH, coated));
            helper.succeed();
        });
    }

    /**
     * A streamer on a perch eleven blocks over a stone floor holds Goop aimed
     * at a zombie standing on the floor below, farther than eight blocks
     * from the eye: once the drips have fallen the zombie holds the wobble.
     * goop-wobbles-a-mob-its-drip-strikes
     *
     * @param helper the gametest helper
     */
    public static void goopWobblesAFarMobItsDripStrikes(GameTestHelper helper) {
        layFloor(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                new BlockPos(LEVEL_STREAMER_POS.getX() + ZOMBIE_EAST, PERCHED_FLOOR_Y + 1, LEVEL_STREAMER_POS.getZ()));
        ServerPlayer streamer = perchedStreamer(helper, LOW_PERCH);
        DripAim.at(streamer, zombie.getBoundingBox().getCenter(), goop(helper).delivery().throwSpeed());
        double off = streamer.getEyePosition().distanceTo(zombie.getBoundingBox().getCenter());
        helper.assertTrue(off > OLD_REACH, String.format(NOT_FAR, off));
        holdGoopFor(helper, streamer, WOBBLE_HOLD_TICKS);
        helper.runAfterDelay(WOBBLE_HOLD_TICKS + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(zombie.hasData(GooAttachments.WOBBLED), String.format(NOT_WOBBLED, off));
            helper.succeed();
        });
    }

    /**
     * Lays a stone floor across the light bay at a height, east of its west
     * wall and either side of a row.
     *
     * @param helper the gametest helper
     * @param y      the floor's height
     * @param row    the row the floor centers on
     */
    private static void layFloor(GameTestHelper helper, int y, int row) {
        for (int east = 0; east <= FLOOR_EAST; east++) {
            for (int z = row - FLOOR_HALF_WIDTH; z <= row + FLOOR_HALF_WIDTH; z++) {
                helper.setBlock(new BlockPos(east, y, z), Blocks.STONE);
            }
        }
    }

    /**
     * The cells over a floor that hold slime.
     *
     * @param helper the gametest helper
     * @param y      the floor's height
     * @param row    the row the floor centers on
     * @return each slime cell over the floor
     */
    private static List<BlockPos> coatedOver(GameTestHelper helper, int y, int row) {
        List<BlockPos> coated = new ArrayList<>();
        for (int east = 0; east <= FLOOR_EAST; east++) {
            for (int z = row - FLOOR_HALF_WIDTH; z <= row + FLOOR_HALF_WIDTH; z++) {
                BlockPos cell = new BlockPos(east, y + 1, z);
                if (helper.getBlockState(cell).is(GooBlocks.SLIME_COAT.get())) {
                    coated.add(cell);
                }
            }
        }
        return coated;
    }

    /**
     * Stands a Goop streamer on a stone perch at the floor's west edge, a
     * height over the perched floor's top, aimed down and east off the perch.
     *
     * @param helper the gametest helper
     * @param height how far the perch's top stands over the floor's top
     * @return the streamer
     */
    private static ServerPlayer perchedStreamer(GameTestHelper helper, int height) {
        BlockPos stand = new BlockPos(LEVEL_STREAMER_POS.getX(), PERCHED_FLOOR_Y + 1 + height,
                LEVEL_STREAMER_POS.getZ());
        helper.setBlock(stand.below(), Blocks.STONE);
        return goopStreamerAt(helper, stand, DOWN_OFF_THE_PERCH);
    }

    /**
     * Stands a Goop streamer facing east, pitched, taught Goop.
     *
     * @param helper the gametest helper
     * @param stand  the cell the streamer stands in
     * @param pitch  the look's pitch, down from level
     * @return the streamer
     */
    private static ServerPlayer goopStreamerAt(GameTestHelper helper, BlockPos stand, float pitch) {
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.WEIRD);
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(stand));
        streamer.setPos(feet.x, feet.y, feet.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(pitch);
        KnownRecipes.teachRequires(streamer, goop(helper));
        return streamer;
    }

    /**
     * Streams Goop every tick of a hold from the first tick, aimed wherever the streamer looks.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param ticks    how many ticks the hold lasts
     */
    private static void holdGoopFor(GameTestHelper helper, ServerPlayer streamer, int ticks) {
        Vec3 eye = streamer.getEyePosition();
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), GOOP.toString(), eye, eye);
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
    }


    /**
     * A streamer on a flat stone floor holds Goop level at a stone wall four
     * blocks east: once the drips have fallen an open cell beside the wall's
     * west face holds a film on its side facing the wall.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper the gametest helper
     */
    public static void goopHeldAtAWallLeavesAFilm(GameTestHelper helper) {
        BlockPos floor = LEVEL_STREAMER_POS.below();
        layFloor(helper, floor.getY(), floor.getZ());
        for (int up = 1; up <= WALL_HEIGHT; up++) {
            for (int z = floor.getZ() - FLOOR_HALF_WIDTH; z <= floor.getZ() + FLOOR_HALF_WIDTH; z++) {
                helper.setBlock(new BlockPos(WALL_EAST, up, z), Blocks.STONE);
            }
        }
        ServerPlayer streamer = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        holdGoopFor(helper, streamer, SPRAY_HOLD_TICKS);
        helper.runAfterDelay(SPRAY_HOLD_TICKS + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<BlockPos> filmed = new ArrayList<>();
            for (int up = 1; up <= WALL_HEIGHT; up++) {
                for (int z = floor.getZ() - FLOOR_HALF_WIDTH; z <= floor.getZ() + FLOOR_HALF_WIDTH; z++) {
                    BlockPos cell = new BlockPos(WALL_EAST - 1, up, z);
                    BlockState state = helper.getBlockState(cell);
                    if (state.is(GooBlocks.SLIME_COAT.get())
                            && VolumeCellBlock.coatOn(state, Direction.EAST) == SideCoat.ATTACHED) {
                        filmed.add(cell);
                    }
                }
            }
            helper.assertTrue(!filmed.isEmpty(), NO_FILM_ON_THE_WALL);
            helper.succeed();
        });
    }

    /**
     * One Goop drip landed through the real landing on a wall's side costs
     * its caster one layer's price, the film it lays.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * world-goo-fills-a-block-space-in-one-to-fifteen-layers
     *
     * @param helper the gametest helper
     */
    public static void goopDripOnAWallCostsOneLayer(GameTestHelper helper) {
        helper.setBlock(FLOATING_WALL, Blocks.STONE);
        helper.setBlock(FLOATING_WALL.west(), Blocks.AIR);
        ServerPlayer caster = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        int before = weirdHeld(caster);
        LandedDrips.landOnSide(helper, caster, goop(helper), FLOATING_WALL, Direction.WEST, 1);
        int drained = before - weirdHeld(caster);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        GooFraction price = ResidualPrices.layerPrice(helper);
        helper.assertTrue(drained == price.ceiling(), String.format(FILM_PRICE_WRONG, price, drained));
        assertFilmFacingEast(helper, FLOATING_WALL.west());
        helper.succeed();
    }

    /**
     * A Goop film laid on a wall standing over open air stands with no top
     * under it while its wall stands; once the wall is broken the block has
     * no floor and no wall left, so its one layer falls: it stands as fill on
     * the first top below, or comes back as weird goo worth one layer's price
     * where no top stands below.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper the gametest helper
     */
    public static void goopFilmDropsItsLayerWhenItsWallBreaks(GameTestHelper helper) {
        helper.setBlock(FLOATING_WALL, Blocks.STONE);
        helper.setBlock(FLOATING_WALL.west(), Blocks.AIR);
        helper.setBlock(FLOATING_WALL.west().below(), Blocks.AIR);
        ServerPlayer caster = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        LandedDrips.landOnSide(helper, caster, goop(helper), FLOATING_WALL, Direction.WEST, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockPos film = FLOATING_WALL.west();
        helper.runAfterDelay(FILM_WATCH_TICKS, () -> {
            assertFilmFacingEast(helper, film);
            helper.getLevel().destroyBlock(helper.absolutePos(FLOATING_WALL), false);
        });
        helper.runAfterDelay(FILM_WATCH_TICKS + FILM_FALL_TICKS, () -> {
            int weird = helper.getEntities(EntityType.ITEM, film, FILM_DROP_REACH).stream()
                    .map(ItemEntity::getItem).filter(stack -> GooTypes.WEIRD.equals(GooStacks.keyOf(stack)))
                    .mapToInt(GooStacks::volumeOf).sum();
            int owed = ResidualPrices.layersRefund(helper, 1);
            int standing = layersStandingIn(helper);
            helper.assertBlockPresent(Blocks.AIR, film);
            helper.assertTrue(weird == owed && standing == 0 || weird == 0 && standing == 1,
                    String.format(FILM_DROP_WRONG, owed, weird, standing));
            helper.succeed();
        });
    }

    /**
     * A streamer holds Goop aimed into a one-block hollow in a stone floor
     * ringed by eight glass blocks: once every drip has landed and every
     * runny layer has come down, the layers of fill, film and runny goo
     * standing in the test, times one layer's price, come to the weird goo
     * the streamer's gauge lost, and number the drips the hold threw, so no
     * struck drip vanished and no paid layer either.
     * runny-goo-runs-down-the-wall-to-the-first-top-below
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper the gametest helper
     */
    public static void goopHeldIntoAGlassRingKeepsEveryLayerPaidFor(GameTestHelper helper) {
        BlockPos floor = LEVEL_STREAMER_POS.below();
        layFloor(helper, floor.getY(), floor.getZ());
        for (int east = -1; east <= 1; east++) {
            for (int south = -1; south <= 1; south++) {
                helper.setBlock(HOLLOW.offset(east, 0, south), east == 0 && south == 0 ? Blocks.AIR : Blocks.GLASS);
            }
        }
        ServerPlayer streamer = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        DripAim.at(streamer, Vec3.atBottomCenterOf(helper.absolutePos(HOLLOW)), goop(helper).delivery().throwSpeed());
        int before = weirdHeld(streamer);
        holdGoopFor(helper, streamer, SPRAY_HOLD_TICKS);
        helper.runAfterDelay(SPRAY_HOLD_TICKS + FALL_TICKS, () -> {
            int drained = before - weirdHeld(streamer);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            int layers = layersStandingIn(helper);
            int thrown = SPRAY_HOLD_TICKS * goop(helper).delivery().dripsPerTick();
            GooFraction paid = ResidualPrices.layerPrice(helper).times(layers);
            helper.assertTrue(layers == thrown, String.format(DRIPS_UNLANDED, thrown, layers));
            helper.assertTrue(paid.ceiling() == drained, String.format(LAYERS_UNPAID, layers, paid, drained));
            helper.succeed();
        });
    }

    /**
     * A streamer holds Goop up at the underside of an overhang whose cell
     * below stands beside a stone wall running down to the floor: goo holds
     * no ceiling, so the drips striking the underside run down the wall, and
     * once every drip has landed the cell at the wall's foot holds fill and
     * the layers standing, times one layer's price, come to the goo drained.
     * drip-striking-a-ceiling-runs-down-a-wall-or-drops
     *
     * @param helper the gametest helper
     */
    public static void goopHeldUpAtAnOverhangBesideAWallEndsAtItsFoot(GameTestHelper helper) {
        layOverhang(helper);
        helper.setBlock(UNDER_THE_OVERHANG.east(), Blocks.STONE);
        helper.setBlock(UNDER_THE_OVERHANG.east().below(), Blocks.STONE);
        holdGoopUpAtTheOverhang(helper, UNDER_THE_OVERHANG.below());
    }

    /**
     * A streamer holds Goop up at the underside of an overhang standing over
     * open floor: the drips striking the underside drop to the floor below,
     * and once every drip has landed the floor cell under the overhang holds
     * fill and the layers standing, times one layer's price, come to the goo drained.
     * drip-striking-a-ceiling-runs-down-a-wall-or-drops
     *
     * @param helper the gametest helper
     */
    public static void goopHeldUpAtAnOverhangOverOpenFloorDropsToIt(GameTestHelper helper) {
        layOverhang(helper);
        holdGoopUpAtTheOverhang(helper, UNDER_THE_OVERHANG.below());
    }

    /**
     * Lays the stone floor and a lone stone block hung two blocks over it, the overhang.
     *
     * @param helper the gametest helper
     */
    private static void layOverhang(GameTestHelper helper) {
        BlockPos floor = LEVEL_STREAMER_POS.below();
        layFloor(helper, floor.getY(), floor.getZ());
        helper.setBlock(UNDER_THE_OVERHANG.above(), Blocks.STONE);
    }

    /**
     * Holds Goop aimed at the overhang's underside from beside it, lets
     * every drip land, and asserts the cell its layers end in holds fill,
     * every drip the hold threw stands as a layer, and every layer standing
     * was paid for.
     *
     * @param helper the gametest helper
     * @param ending the cell the underside's layers should end as fill in
     */
    private static void holdGoopUpAtTheOverhang(GameTestHelper helper, BlockPos ending) {
        ServerPlayer streamer = goopStreamerAt(helper, OVERHANG_STREAMER_POS, LEVEL);
        Vec3 underside = Vec3.atBottomCenterOf(helper.absolutePos(UNDER_THE_OVERHANG.above()));
        DripAim.at(streamer, underside, goop(helper).delivery().throwSpeed());
        int before = weirdHeld(streamer);
        holdGoopFor(helper, streamer, SPRAY_HOLD_TICKS);
        helper.runAfterDelay(SPRAY_HOLD_TICKS + FALL_TICKS, () -> {
            int drained = before - weirdHeld(streamer);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            int layers = layersStandingIn(helper);
            GooFraction paid = ResidualPrices.layerPrice(helper).times(layers);
            int atTheEnd = VolumeCellBlock.layersOf(helper.getBlockState(ending));
            int thrown = SPRAY_HOLD_TICKS * goop(helper).delivery().dripsPerTick();
            helper.assertTrue(atTheEnd > 0, String.format(NO_FILL_UNDER, ending, helper.getBlockState(ending)));
            helper.assertTrue(layers == thrown, String.format(DRIPS_UNLANDED, thrown, layers));
            helper.assertTrue(paid.ceiling() == drained, String.format(LAYERS_UNPAID, layers, paid, drained));
            helper.succeed();
        });
    }

    /**
     * Six layers of Goop on a pillar three blocks over a walled floor cell
     * pour one layer off the ledge: the pillar's cell is one layer down in
     * the tick it pours, and the floor cell holds nothing new until the
     * fall's arrival tick and one layer on it; the test's layers stand
     * whole once it lands.
     * falling-layer-lands-when-its-drip-arrives
     * goo-moving-downward-falls-as-drips
     *
     * @param helper the gametest helper
     */
    public static void goopPouredOffALedgeLandsWhenItsDripArrives(GameTestHelper helper) {
        TallDrop.layPillar(helper);
        helper.setBlock(TallDrop.ON_THE_PILLAR, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, POURING_LAYERS));
        FallWatch watch = FallWatch.over(helper, FALL_WATCH_TICKS, TallDrop.ON_THE_PILLAR, TallDrop.AT_THE_FOOT);
        helper.runAfterDelay(FALL_WATCH_TICKS + 1, () -> {
            int poured = watch.firstChange(0);
            int ticks = FallWatch.ticksBetween(TallDrop.ON_THE_PILLAR.getY()
                    + (double) POURING_LAYERS / VolumeCellBlock.MAX_LAYERS, TallDrop.AT_THE_FOOT.getY());
            helper.assertTrue(poured > 0, "The pillar's Goop should pour a layer off its ledge");
            watch.assertLandsOn(1, poured + ticks);
            watch.assertLayersKept();
            helper.succeed();
        });
    }

    /**
     * One Goop drip striking the underside of a stone block hung three
     * blocks over open floor costs its caster one layer's price in the tick
     * it strikes and nothing more, and the floor cell under it holds nothing
     * new until the fall's arrival tick and one layer on it.
     * falling-layer-lands-when-its-drip-arrives
     * drip-striking-a-ceiling-runs-down-a-wall-or-drops
     *
     * @param helper the gametest helper
     */
    public static void goopStruckUnderAnOverhangFallsWhenItsDripArrives(GameTestHelper helper) {
        BlockPos floor = TallDrop.layOverOpenAir(helper, TallDrop.OVERHANG, Direction.DOWN);
        ServerPlayer caster = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        int before = weirdHeld(caster);
        FallWatch watch = FallWatch.over(helper, FALL_WATCH_TICKS, floor);
        LandedDrips.landOnSide(helper, caster, goop(helper), TallDrop.OVERHANG, Direction.DOWN, 1);
        int drainedAtTheStrike = before - weirdHeld(caster);
        int owed = ResidualPrices.layerPrice(helper).ceiling();
        helper.assertTrue(drainedAtTheStrike == owed, String.format(FALL_PRICE_WRONG, owed, drainedAtTheStrike));
        helper.runAfterDelay(FALL_WATCH_TICKS + 1, () -> {
            int drained = before - weirdHeld(caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(drained == owed, String.format(FALL_PRICE_WRONG, owed, drained));
            watch.assertLandsOn(0, FallWatch.ticksBetween(TallDrop.OVERHANG.getY(), floor.getY()));
            helper.succeed();
        });
    }

    /**
     * A streamer holds Goop level at a stone wall standing over open air, a
     * stone floor two blocks under its lowest face: each block beside the
     * face holding Goop is attached to it and holds at most one layer plus
     * Goop's cohesion, a block struck often enough holds exactly that many,
     * and the drips past that run down the wall and stand as fill at its foot.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper the gametest helper
     */
    /**
     * Goop resting on a floor beside a stone wall adheres to it, as Goop
     * striking the wall does: its side toward the wall stands attached, so
     * goo against a wall clings to it in place of lying beside it.
     * operator UAT 2026-11-17: the wall has horizontally laying goo instead of vertically adhered goo
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper the gametest helper
     */
    public static void goopRestingBesideAWallAdheresToIt(GameTestHelper helper) {
        BlockPos block = new BlockPos(OPEN_AIR_WALL_EAST - 1, 1, LEVEL_STREAMER_POS.getZ());
        helper.setBlock(block.below(), Blocks.STONE);
        helper.setBlock(block.east(), Blocks.STONE);
        helper.setBlock(block, GooBlocks.SLIME_COAT.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS,
                RESTING_VOLUME));
        helper.runAfterDelay(ADHERE_TICKS, () -> {
            BlockState resting = helper.getBlockState(block);
            helper.assertTrue(resting.is(GooBlocks.SLIME_COAT.get())
                            && VolumeCellBlock.coatOn(resting, Direction.EAST) == SideCoat.ATTACHED,
                    "Goop resting beside a stone wall should adhere to it, found " + resting);
            helper.succeed();
        });
    }

    public static void goopSprayedAtAWallOverOpenAirHoldsWhatStuck(GameTestHelper helper) {
        int z0 = LEVEL_STREAMER_POS.getZ();
        for (int z = z0 - 2; z <= z0 + 2; z++) {
            for (int east = 0; east < OPEN_AIR_WALL_EAST; east++) {
                helper.setBlock(new BlockPos(east, 0, z), Blocks.STONE);
            }
            for (int up = OPEN_AIR_WALL_BOTTOM; up <= OPEN_AIR_WALL_BOTTOM + WALL_HEIGHT; up++) {
                helper.setBlock(new BlockPos(OPEN_AIR_WALL_EAST, up, z), Blocks.STONE);
            }
        }
        int holds = 1 + cohesionOfGoop(helper);
        ServerPlayer streamer = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        holdGoopFor(helper, streamer, WALL_HOLD_TICKS);
        helper.runAfterDelay(WALL_HOLD_TICKS + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<Integer> attached = new ArrayList<>();
            int atTheFoot = 0;
            for (int z = z0 - 2; z <= z0 + 2; z++) {
                atTheFoot += layersIn(helper, new BlockPos(OPEN_AIR_WALL_EAST - 1, 1, z));
                for (int up = OPEN_AIR_WALL_BOTTOM; up <= OPEN_AIR_WALL_BOTTOM + WALL_HEIGHT; up++) {
                    BlockState beside = helper.getBlockState(new BlockPos(OPEN_AIR_WALL_EAST - 1, up, z));
                    if (beside.is(GooBlocks.SLIME_COAT.get())
                            && VolumeCellBlock.coatOn(beside, Direction.EAST) == SideCoat.ATTACHED) {
                        attached.add(VolumeCellBlock.layersOf(beside));
                    }
                }
            }
            boolean held = !attached.isEmpty() && attached.stream().allMatch(volume -> volume <= holds)
                    && attached.contains(holds);
            helper.assertTrue(held && atTheFoot > 0, String.format(STUCK_WRONG, holds, attached, atTheFoot));
            helper.succeed();
        });
    }

    /**
     * A block of Goop of volume 3 attached to the underside of a stone block
     * hung three blocks over a stone floor: once the stone is broken nothing
     * holds the block, so its whole volume falls and stands as fill on the
     * floor under it.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * drip-striking-a-ceiling-runs-down-a-wall-or-drops
     *
     * @param helper the gametest helper
     */
    public static void goopOnACeilingFallsWholeWhenItsCeilingBreaks(GameTestHelper helper) {
        BlockPos floor = TallDrop.layOverOpenAir(helper, TallDrop.OVERHANG, Direction.DOWN);
        BlockPos block = TallDrop.OVERHANG.below();
        helper.setBlock(block, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, FALLING_VOLUME).setValue(VolumeCellBlock.CEILING, true));
        helper.runAfterDelay(1, () -> helper.getLevel().destroyBlock(helper.absolutePos(TallDrop.OVERHANG), false));
        helper.runAfterDelay(FALL_WATCH_TICKS, () -> {
            helper.assertBlockPresent(Blocks.AIR, block);
            int landed = VolumeCellBlock.layersOf(helper.getBlockState(floor));
            helper.assertTrue(landed == FALLING_VOLUME, String.format(FELL_WRONG, FALLING_VOLUME, landed));
            helper.succeed();
        });
    }

    /**
     * Goop's cohesion, read from the goo type registry.
     *
     * @param helper the gametest helper
     * @return the cohesion
     */
    private static int cohesionOfGoop(GameTestHelper helper) {
        return helper.getLevel().registryAccess().get(GooTypes.WEIRD).map(type -> type.value().cohesion()).orElse(0);
    }

    private static int layersIn(GameTestHelper helper, BlockPos cell) {
        BlockState state = helper.getBlockState(cell);
        return state.getBlock() instanceof VolumeCellBlock ? VolumeCellBlock.layersOf(state) : 0;
    }

    /**
     * A block of Goop of volume 3 attached to a lone stone block standing
     * three blocks over a stone floor: once the stone is broken the block
     * has no floor and no wall left, so its whole volume falls and stands as
     * fill on the floor, the space it left open.
     * goo-cell-holds-a-fill-and-a-state-per-side
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param helper the gametest helper
     */
    public static void goopBlockFallsWholeWhenItsLastWallBreaks(GameTestHelper helper) {
        fallsWholeWhenItsWallBreaks(helper, FALLING_VOLUME);
    }

    /**
     * A block of Goop attached to a lone stone block over a stone floor,
     * holding one layer plus Goop's cohesion, the most its wall holds: once
     * the stone is broken every one of its layers falls and stands as fill
     * on the floor.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * goo-cell-holds-a-fill-and-a-state-per-side
     *
     * @param helper the gametest helper
     */
    public static void goopBlockHoldingItsWallsLayersFallsWhole(GameTestHelper helper) {
        fallsWholeWhenItsWallBreaks(helper, 1 + cohesionOfGoop(helper));
    }

    private static void fallsWholeWhenItsWallBreaks(GameTestHelper helper, int volume) {
        BlockPos floor = TallDrop.layOverOpenAir(helper, TallDrop.LONE_WALL, Direction.WEST);
        BlockPos block = TallDrop.LONE_WALL.west();
        helper.setBlock(block, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, volume)
                .setValue(VolumeCellBlock.coatProperty(Direction.EAST), SideCoat.ATTACHED));
        helper.runAfterDelay(1, () -> helper.getLevel().destroyBlock(helper.absolutePos(TallDrop.LONE_WALL), false));
        helper.runAfterDelay(FALL_WATCH_TICKS, () -> {
            helper.assertBlockPresent(Blocks.AIR, block);
            int landed = VolumeCellBlock.layersOf(helper.getBlockState(floor));
            helper.assertTrue(landed == volume, String.format(FELL_WRONG, volume, landed));
            helper.succeed();
        });
    }

    /**
     * Every layer of slime standing in the test's bounds: each cell's fill and its sides' layers.
     *
     * @param helper the gametest helper
     * @return the layers
     */
    private static int layersStandingIn(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int layers = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX - 1, bounds.maxY - 1, bounds.maxZ - 1))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(GooBlocks.SLIME_COAT.get())) {
                layers += VolumeCellBlock.layersHeld(state);
            }
        }
        return layers;
    }

    private static void assertFilmFacingEast(GameTestHelper helper, BlockPos cell) {
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.SLIME_COAT.get())
                        && VolumeCellBlock.coatOn(state, Direction.EAST) == SideCoat.ATTACHED,
                String.format(NO_FILM_AT, cell, state));
    }

    private static int weirdHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.WEIRD, 0);
    }

    private static AbilityDefinition goop(GameTestHelper helper) {
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, ABILITY_REQUIRED);
        return goop;
    }
}
