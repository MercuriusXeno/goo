package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Kinetic's Slick, cast through the real stream tick.
 * slick-coats-the-ground-with-kinetic-speed
 * slick-ramp-grows-while-a-thing-keeps-moving
 * breaking-slick-drops-kinetic-goo-worth-its-film
 */
@RegistersGameTests
public final class SlickTests {

    private static final Identifier SLICK = Identifier.parse("goo:kinetic_slick");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:kinetic_slick";
    private static final String SLICK_SLIDES_AN_ITEM = "slick_slides_an_item_without_speeding_it";
    private static final String SLICK_SPEEDS_A_MOB = "slick_speeds_up_a_mob_walking_on_it";
    private static final String SLICK_RAISES_A_WALKER = "slick_raises_a_slow_walker_to_its_start_speed";
    private static final String SLICK_DRIPS_LAUNCH_AS_FROM_REST =
            "slick_drips_launch_as_from_rest_while_the_streamer_moves";
    /** A streamer's known movement, sprinting east while rising off a bounce, in blocks a tick. */
    private static final Vec3 MOVING_AND_RISING = new Vec3(0.28, 0.42, -0.1);
    /** How far two launches may differ and stand equal: double rounding. */
    private static final double SAME_LAUNCH = 1e-9;
    private static final String LAUNCHED_UNEVENLY = "Each tick should throw the same drips: %d at rest, %d moving";
    private static final String LAUNCHED_WITH_MOVEMENT = "A moving streamer's drip should launch as at rest: %s at"
            + " rest, %s moving";
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Three blocks east of the streamer, at its height, on a block the test lays. */
    private static final BlockPos SLICKED_STONE = STREAMER_POS.east(3);
    /** The westmost and eastmost offsets from SLICKED_STONE of the strip the stream slicks. */
    private static final int STRIP_WEST = -1;
    private static final int STRIP_EAST = 2;
    /** A hold long enough that every share of the cone's fan has thrown its drips, a few ticks over. */
    private static final int SPRAY_HOLD_TICKS = CoatRay.FAN_SHARES + 5;
    /** The item's drop over the slick, low enough that it lands within a few ticks. */
    private static final double DROP_HEIGHT = 0.5;
    /** The push the item lands with, toward the slicked strip's far end. */
    private static final double PUSH = 0.05;
    /** The ticks the item slides on the slick between the two readings. */
    private static final int SLIDE_TICKS = 10;
    /** The control lane's row, packed ice as slippery as the slick, three blocks south of the slicked strip. */
    private static final int ICE_LANE_SOUTH = 3;
    /** The ticks a walker spends on its slippery lane before its speed is read. */
    private static final int SLIPPERY_TICKS = 6;
    private static final String NEVER_LANDED = "The items dropped onto the slick and the ice never both landed";
    private static final String NOT_SLID = "The items never both slid their ticks after landing";
    private static final String NOT_ON_SLICK = "The item should still stand on slick after its slide, stands on %s";
    private static final String NOT_ADVANCED = "The item should slide east along the slick: x %.4f at landing, %.4f after";
    private static final String SPED_UP = "An item sliding on slick should gain no speed %d ticks after landing:"
            + " %.4f then %.4f";
    private static final String NOT_AS_ICE = "An item %d ticks onto slick should slide as fast as one on ice,"
            + " the slick ramping nothing: %.6f on ice, %.6f on slick";
    /** A one-layer slick cell's height over the block it lies on, a fifteenth. */
    private static final double ONE_LAYER = 1.0 / 15;
    /** The layers a whole block of slick holds. */
    private static final int WHOLE_BLOCK_LAYERS = 15;
    /** Two items sliding alike differ by no more than float rounding. */
    private static final double SAME_SPEED = 1e-6;
    private static final String NEVER_WALKED = "The walkers never crossed from stone onto their slippery lanes:"
            + " slick %s, ice %s";
    private static final String NOT_FASTER_THAN_STONE = "A husk %d ticks onto slick should outpace its walk on stone:"
            + " %.4f on stone, %.4f on slick";
    private static final String NOT_RAISED = "A husk stepping onto slick should leave its first slick tick"
            + " faster than it walked the stone: %.4f against %.4f on stone";
    private static final String NOT_FASTER_THAN_ICE = "A husk %d ticks onto slick should outpace a husk %d ticks"
            + " onto ice: %.4f on ice, %.4f on slick";
    private static final String SLICK_LANDS_LEVEL_AHEAD = "slick_lands_about_eight_blocks_ahead_on_flat_ground";
    private static final String SLICK_LANDS_AFTER_RELEASE = "slick_lands_below_a_high_perch_after_the_hold_ends";
    /** Where the streamer stands in the light bay, at its west wall, on the strip's first stone. */
    private static final BlockPos FAR_STREAMER_POS = new BlockPos(0, 1, 8);
    /** The light bay is sixteen blocks across, so the strip runs from the streamer to its east wall. */
    private static final int FAR_STRIP_EAST = 15;
    /** The nearest and farthest the level shot's film may stand ahead of the streamer, in cells. */
    private static final int NEAREST_AHEAD = 7;
    private static final int FARTHEST_AHEAD = 9;
    /** Ticks given the last drips of a hold to fall onto the floor, past the longest fall these tests make. */
    private static final int FALL_TICKS = 60;
    /** The perched floor stands over the light bay's barrier roof, in open air the roof does not cut. */
    private static final int PERCHED_FLOOR_Y = 10;
    /** How far the perch's top stands over the floor's top: its drips fall longer than the hold lasts. */
    private static final int HIGH_PERCH = 40;
    /** The perched floor runs this far north and south of the streamer's row, wider than the cone's spread. */
    private static final int FLOOR_HALF_WIDTH = 4;
    /** How far east of the perch's middle the streamer stands, near its edge. */
    private static final double PERCH_EDGE = 0.4;
    /** Pitched steeply down, so the drips fall onto the floor inside the bay's span. */
    private static final float DOWN_OFF_THE_PERCH = 75f;
    private static final String NO_FILM_AHEAD = "A level Slick shot should lay a film 7 to 9 blocks ahead,"
            + " films stand %s blocks ahead";
    private static final String NO_FILM_BELOW = "Slick aimed down from %d blocks up should lay a film on the floor";
    private static final String LANDED_BEFORE_RELEASE = "The high perch's first drips should still be falling when"
            + " the hold ends, %d films stand";

    private static final String BREAKING_SLICK_DROPS = "breaking_slick_drops_its_kinetic_goo";
    private static final String PLACING_KEEPS_SLICK = "placing_a_block_keeps_the_slick";
    private static final String RECLAIM_WRONG = "A broken slick cell should drop %d mb of kinetic goo, dropped %s";
    private static final String PLACED_OVER_SLICK = "A block placed against a slick cell should leave the cell"
            + " standing at %s, the cell holds %s";
    /** Past the eight layers a cell held before a cell held fifteen. */
    private static final int BROKEN_LAYERS = 11;

    private static final String SLICK_LAYING_NOTHING_DRAINS_NOTHING = "slick_laying_no_film_drains_no_goo";
    /** Pitched straight up, at the slab over the streamer's head. */
    private static final float STRAIGHT_UP = -90f;
    /** Blocks over the streamer's stand to the slab over its head, close enough that the whole cone meets it. */
    private static final int OVERHEAD = 3;
    private static final String DRAINED_WRONG = "The drips should drain %s mb of kinetic goo, drained %d";

    private static final String DRIP_STANDS_A_CELL_AT_ANY_SPEED = "slick_drip_stands_a_one_layer_cell_at_any_speed";
    /** A sprinting streamer's known movement east, in blocks a tick. */
    private static final Vec3 SPRINTING = new Vec3(0.28, 0, 0);
    /** The cells the at-rest and the sprinting caster's drips strike, walled in stone on a stone floor. */
    private static final BlockPos AT_REST_CELL = new BlockPos(1, 1, 1);
    private static final BlockPos SPRINTING_CELL = new BlockPos(3, 1, 1);
    private static final String LAYERS_WRONG = "The slick cell at %s should hold %d layers, holds %s";
    private static final String SLICK_FILLS_AND_RAMPS_ALIKE = "slick_fills_to_fifteen_and_ramps_the_same_at_any_height";
    private static final String HEIGHT_WRONG = "A slick cell of %d layers should stand %.4f high, stands %.4f";
    private static final String RAMPED_UNEVENLY = "A husk %d ticks onto thin slick and one %d ticks onto full slick"
            + " should move alike: %.6f thin, %.6f full";
    /** The full lane's row, three blocks south of the thin lane, walled from it. */
    private static final int FULL_LANE_SOUTH = 3;
    private static final String DRIPS_DRAIN_A_LAYER_EACH =
            "slick_drips_drain_a_layers_price_each_and_unpaid_drips_lay_nothing";
    /** The drips the drain test lands, over two ticks. */
    private static final int COUNTED_DRIPS = 4;
    private static final String SLICK_SETTLES_SOONER = "a_full_slick_cell_settles_sooner_than_a_full_slime_cell";
    /** The middles of the slick's and the slime's crosses of open floor in the light bay. */
    private static final BlockPos SLICK_CROSS = new BlockPos(4, 1, 4);
    private static final BlockPos SLIME_CROSS = new BlockPos(10, 1, 4);
    /** Ticks the two crosses are watched, past either settling. */
    private static final int SETTLE_WATCH_TICKS = 90;
    /** Ticks a cross must stand unchanged at the watch's end to read as settled. */
    private static final int STILL_TICKS = 20;
    private static final String NOT_SOONER = "Full slick should settle sooner than full slime: slick last changed"
            + " on tick %d, slime on tick %d";
    private static final String NEVER_SETTLED = "Both crosses should stand still the last %d watched ticks: slick"
            + " last changed on tick %d, slime on tick %d";
    private static final String SPRINT_RIDES_THE_TRACK = "slick_lays_a_track_a_sprinter_rides";
    /** A sprinting player's speed east, in blocks a tick, and the small fall each tick that keeps it grounded. */
    private static final Vec3 SPRINT_STEP = new Vec3(0.28, -0.08, 0);
    /** How far ahead of the sprinter's feet the stream is aimed, in blocks, at the floor's top. */
    private static final double AIM_AHEAD = 3.5;
    /** Ticks the sprint lasts: from the bay's west wall to short of its east wall. */
    private static final int SPRINT_TICKS = 46;
    /** The most layers a slick cell on the path may hold: a player steps up 0.6 of a block, so nine fifteenths. */
    private static final int STEP_LAYERS = 9;
    private static final String NEVER_ON_SLICK = "The sprinter should reach the slick track, stood on %s";
    private static final String OFF_THE_TRACK = "The sprinter should stand on slick every tick after first reaching it,"
            + " stood on %s at x %.2f on tick %d";
    private static final String TRACK_TOO_TALL = "No slick cell on the path should hold more than %d layers, the path"
            + " holds %s";

    private SlickTests() {
    }

    /**
     * A player sprinting east over open stone floor, streaming Slick aimed a
     * few blocks ahead of their feet each tick, stands on slick on every tick
     * after first reaching the track, and no slick cell on the path holds
     * more layers than the player steps up.
     * slick-is-a-layered-cell-that-flows-thinner-and-faster-than-slime
     * slick-coats-the-ground-with-kinetic-speed
     *
     * @param helper the gametest helper
     */
    public static void slickLaysATrackASprinterRides(GameTestHelper helper) {
        int row = FAR_STREAMER_POS.getZ();
        for (int east = 0; east <= FAR_STRIP_EAST; east++) {
            for (int z = row - FLOOR_HALF_WIDTH; z <= row + FLOOR_HALF_WIDTH; z++) {
                helper.setBlock(new BlockPos(east, FAR_STREAMER_POS.getY() - 1, z), Blocks.STONE);
            }
        }
        ServerPlayer sprinter = slickStreamerAt(helper, FAR_STREAMER_POS);
        sprinter.setYRot(FACING_EAST);
        sprinter.setSprinting(true);
        sprinter.setKnownMovement(new Vec3(SPRINT_STEP.x, 0, 0));
        double floorTop = helper.absolutePos(FAR_STREAMER_POS).getY();
        boolean[] onTrack = {false};
        List<String> offTrack = new ArrayList<>();
        for (int tick = 1; tick <= SPRINT_TICKS; tick++) {
            int now = tick;
            helper.runAfterDelay(tick, () -> {
                sprinter.move(MoverType.SELF, SPRINT_STEP);
                BlockState under = helper.getLevel().getBlockState(sprinter.getOnPos());
                boolean onSlick = under.is(GooBlocks.KINETIC_SLICK.get());
                if (onTrack[0] && !onSlick && offTrack.isEmpty()) {
                    offTrack.add(String.format(OFF_THE_TRACK, under, sprinter.getX(), now));
                }
                onTrack[0] |= onSlick;
                DripAim.at(sprinter, new Vec3(sprinter.getX() + AIM_AHEAD, floorTop, sprinter.getZ()),
                        slick(helper).delivery().throwSpeed());
                GooStreamHandler.streamTick(sprinter, GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC),
                        SLICK.toString(), sprinter.getEyePosition(), sprinter.getEyePosition()));
            });
        }
        helper.runAfterDelay(SPRINT_TICKS + 1, () -> {
            BlockState lastUnder = helper.getLevel().getBlockState(sprinter.getOnPos());
            helper.getLevel().getServer().getPlayerList().remove(sprinter);
            helper.assertTrue(onTrack[0], String.format(NEVER_ON_SLICK, lastUnder));
            helper.assertTrue(offTrack.isEmpty(), String.join("; ", offTrack));
            List<Integer> path = new ArrayList<>();
            for (int east = 0; east <= FAR_STRIP_EAST; east++) {
                path.add(VolumeCellBlock.layersOf(helper.getBlockState(FAR_STREAMER_POS.east(east))));
            }
            helper.assertTrue(path.stream().allMatch(layers -> layers <= STEP_LAYERS),
                    String.format(TRACK_TOO_TALL, STEP_LAYERS, path));
            helper.succeed();
        });
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SLICK_SLIDES_AN_ITEM, SlickTests::slickSlidesAnItemWithoutSpeedingIt);
        GooTestFunctions.registerTest(registrar, SLICK_SPEEDS_A_MOB, SlickTests::slickSpeedsUpAMobWalkingOnIt);
        GooTestFunctions.registerTest(registrar, SLICK_RAISES_A_WALKER, SlickTests::slickRaisesASlowWalkerToItsStartSpeed);
        GooTestFunctions.registerTest(registrar, BREAKING_SLICK_DROPS, SlickTests::breakingSlickDropsItsKineticGoo);
        GooTestFunctions.registerTest(registrar, PLACING_KEEPS_SLICK, SlickTests::placingABlockKeepsTheSlick);
        GooTestFunctions.registerTest(registrar, SLICK_DRIPS_LAUNCH_AS_FROM_REST,
                SlickTests::slickDripsLaunchAsFromRestWhileTheStreamerMoves);
        GooTestFunctions.registerTest(registrar, SLICK_LANDS_LEVEL_AHEAD, SlickTests::slickLandsAboutEightBlocksAhead);
        GooTestFunctions.registerTest(registrar, SLICK_LANDS_AFTER_RELEASE,
                SlickTests::slickLandsBelowAHighPerchAfterRelease);
        GooTestFunctions.registerTest(registrar, SLICK_LAYING_NOTHING_DRAINS_NOTHING,
                SlickTests::slickLayingNoFilmDrainsNoGoo);
        GooTestFunctions.registerTest(registrar, DRIP_STANDS_A_CELL_AT_ANY_SPEED,
                SlickTests::slickDripStandsAOneLayerCellAtAnySpeed);
        GooTestFunctions.registerTest(registrar, SLICK_FILLS_AND_RAMPS_ALIKE,
                SlickTests::slickFillsToFifteenAndRampsTheSameAtAnyHeight);
        GooTestFunctions.registerTest(registrar, DRIPS_DRAIN_A_LAYER_EACH, SlickTests::slickDripsDrainALayersPriceEach);
        GooTestFunctions.registerTest(registrar, SLICK_SETTLES_SOONER, SlickTests::aFullSlickCellSettlesSooner);
        GooTestFunctions.registerTest(registrar, SPRINT_RIDES_THE_TRACK, SlickTests::slickLaysATrackASprinterRides);
    }

    /**
     * A Slick hold aimed straight up at a bottom slab over the streamer's
     * head, whose top holds no slick, so no cell the stream reaches can take
     * a layer, lasting three sprays of the whole fan, drains no kinetic goo.
     * slick-drains-per-film-it-lays
     *
     * @param helper the gametest helper
     */
    public static void slickLayingNoFilmDrainsNoGoo(GameTestHelper helper) {
        helper.setBlock(STREAMER_POS.above(OVERHEAD), Blocks.STONE_SLAB);
        ServerPlayer streamer = slickStreamerAt(helper, STREAMER_POS);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(STRAIGHT_UP);
        int before = kineticHeld(streamer);
        int held = SPRAY_HOLD_TICKS * 3;
        holdSlickFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            int drained = before - kineticHeld(streamer);
            helper.assertTrue(drained == 0, String.format(DRAINED_WRONG, GooFraction.ZERO, drained));
            helper.succeed();
        });
    }

    /**
     * One Slick drip from a caster at rest and one from a caster sprinting
     * each stand a slick cell of one layer over a bare stone floor in the
     * tick they strike.
     * drip-creates-a-volume-cell-immediately
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     *
     * @param helper the gametest helper
     */
    public static void slickDripStandsAOneLayerCellAtAnySpeed(GameTestHelper helper) {
        wallInOnStone(helper, AT_REST_CELL);
        wallInOnStone(helper, SPRINTING_CELL);
        ServerPlayer atRest = slickStreamerAt(helper, STREAMER_POS);
        LandedDrips.landOn(helper, atRest, slick(helper), AT_REST_CELL, 1);
        assertLayers(helper, AT_REST_CELL, 1);
        ServerPlayer sprinting = slickStreamerAt(helper, STREAMER_POS);
        sprinting.setSprinting(true);
        sprinting.setKnownMovement(SPRINTING);
        LandedDrips.landOn(helper, sprinting, slick(helper), SPRINTING_CELL, 1);
        assertLayers(helper, SPRINTING_CELL, 1);
        helper.getLevel().getServer().getPlayerList().remove(atRest);
        helper.getLevel().getServer().getPlayerList().remove(sprinting);
        helper.succeed();
    }

    /**
     * Slick drips landed on a walled cell fill it to fifteen layers, its
     * collision standing a fifteenth high at one layer and a whole block at
     * fifteen; a husk walking a lane of one-layer slick and a husk walking a
     * lane of full slick move alike after the same ticks on it.
     * slick-is-a-layered-cell-that-flows-thinner-and-faster-than-slime
     *
     * @param helper the gametest helper
     */
    public static void slickFillsToFifteenAndRampsTheSameAtAnyHeight(GameTestHelper helper) {
        BlockPos thinStart = SLICKED_STONE.east(STRIP_WEST);
        BlockPos fullStart = thinStart.south(FULL_LANE_SOUTH);
        layStrip(helper);
        layFullLane(helper, fullStart);
        ServerPlayer caster = slickStreamerAt(helper, STREAMER_POS);
        BlockPos filled = fullStart.above();
        LandedDrips.landOn(helper, caster, slick(helper), filled, 1);
        assertHeight(helper, filled, 1);
        LandedDrips.landOn(helper, caster, slick(helper), filled, WHOLE_BLOCK_LAYERS - 1);
        assertLayers(helper, filled, WHOLE_BLOCK_LAYERS);
        assertHeight(helper, filled, WHOLE_BLOCK_LAYERS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        for (int east = 1; east <= STRIP_EAST - STRIP_WEST; east++) {
            helper.setBlock(filled.east(east), slickOf(WHOLE_BLOCK_LAYERS));
        }
        laySlickStrip(helper);
        layLane(helper, thinStart, null);
        Walker onThin = walkTheLane(helper, thinStart, GooBlocks.KINETIC_SLICK.get());
        Walker onFull = walkTheLane(helper, filled, GooBlocks.KINETIC_SLICK.get());
        // a sequence ticks apart from the delays this callback runs among, which take no new delay mid-tick
        helper.startSequence().thenWaitUntil(() -> {
            onThin.watch(helper);
            onFull.watch(helper);
            helper.assertTrue(onThin.crossed() && onFull.crossed(), String.format(NEVER_WALKED, onThin, onFull));
        }).thenExecute(() -> helper.assertTrue(Math.abs(onThin.onSlippery - onFull.onSlippery) < SAME_SPEED,
                String.format(RAMPED_UNEVENLY, SLIPPERY_TICKS, SLIPPERY_TICKS, onThin.onSlippery,
                        onFull.onSlippery))).thenSucceed();
    }

    /**
     * Lays the full lane: stone a block up west of its start, where the husk
     * steps on level with full slick, the lane's row walled north, south and
     * east in stone so full slick cannot pour out of it, on a stone floor.
     *
     * @param helper the gametest helper
     * @param start  the lane's floor stone under its first slick cell
     */
    private static void layFullLane(GameTestHelper helper, BlockPos start) {
        int laneEnd = STRIP_EAST - STRIP_WEST;
        for (int east = -2; east <= laneEnd + 1; east++) {
            BlockPos floor = start.east(east);
            helper.setBlock(floor.below(), Blocks.BEDROCK);
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above().north(), Blocks.STONE);
            helper.setBlock(floor.above().south(), Blocks.STONE);
            helper.setBlock(floor.above(), east < 0 || east > laneEnd ? Blocks.STONE : Blocks.AIR);
            helper.setBlock(floor.above(2), Blocks.AIR);
            helper.setBlock(floor.above(3), Blocks.AIR);
        }
    }

    /**
     * A counted run of Slick drips landed on a walled cell drains the caster
     * one layer's price for each layer placed, a fifteenth of Slick's cost;
     * once the caster holds no kinetic goo, a landed drip leaves the cell as
     * it stood.
     * slick-drains-per-film-it-lays
     *
     * @param helper the gametest helper
     */
    public static void slickDripsDrainALayersPriceEach(GameTestHelper helper) {
        wallInOnStone(helper, AT_REST_CELL);
        ServerPlayer caster = slickStreamerAt(helper, STREAMER_POS);
        AbilityDefinition slick = slick(helper);
        int before = kineticHeld(caster);
        LandedDrips.landOn(helper, caster, slick, AT_REST_CELL, 1);
        LandedDrips.landOn(helper, caster, slick, AT_REST_CELL, COUNTED_DRIPS - 1);
        assertLayers(helper, AT_REST_CELL, COUNTED_DRIPS);
        GooFraction owed = ResidualPrices.slickLayerPrice(helper).times(COUNTED_DRIPS);
        int drained = before - kineticHeld(caster);
        helper.assertTrue(drained == owed.ceiling(), String.format(DRAINED_WRONG, owed, drained));
        GooSourceScanner.deplete(caster, GooTypes.KINETIC, kineticHeld(caster));
        LandedDrips.landOn(helper, caster, slick, AT_REST_CELL, 1);
        assertLayers(helper, AT_REST_CELL, COUNTED_DRIPS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /**
     * A full slick cell and a full slime cell, each at the middle of a cross
     * of open stone floor walled in, pour level: the slick, thinner, stands
     * still on an earlier tick than the slime.
     * slick-is-a-layered-cell-that-flows-thinner-and-faster-than-slime
     *
     * @param helper the gametest helper
     */
    public static void aFullSlickCellSettlesSooner(GameTestHelper helper) {
        List<BlockPos> slickCross = crossOfOpenFloor(helper, SLICK_CROSS);
        List<BlockPos> slimeCross = crossOfOpenFloor(helper, SLIME_CROSS);
        helper.setBlock(SLICK_CROSS, slickOf(WHOLE_BLOCK_LAYERS));
        helper.setBlock(SLIME_CROSS, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, WHOLE_BLOCK_LAYERS));
        int[] lastChanged = {0, 0};
        List<List<Integer>> last = new ArrayList<>(List.of(layersIn(helper, slickCross), layersIn(helper, slimeCross)));
        for (int tick = 1; tick <= SETTLE_WATCH_TICKS; tick++) {
            int now = tick;
            helper.runAfterDelay(tick, () -> {
                List<List<Integer>> crosses = List.of(layersIn(helper, slickCross), layersIn(helper, slimeCross));
                for (int cross = 0; cross < crosses.size(); cross++) {
                    if (!crosses.get(cross).equals(last.get(cross))) {
                        lastChanged[cross] = now;
                        last.set(cross, crosses.get(cross));
                    }
                }
            });
        }
        helper.runAfterDelay(SETTLE_WATCH_TICKS + 1, () -> {
            helper.assertTrue(Math.max(lastChanged[0], lastChanged[1]) <= SETTLE_WATCH_TICKS - STILL_TICKS,
                    String.format(NEVER_SETTLED, STILL_TICKS, lastChanged[0], lastChanged[1]));
            helper.assertTrue(lastChanged[0] < lastChanged[1],
                    String.format(NOT_SOONER, lastChanged[0], lastChanged[1]));
            helper.succeed();
        });
    }

    /**
     * Lays a cross of open cells on a stone floor, a middle and its four
     * sides, walled in stone around.
     *
     * @param helper the gametest helper
     * @param middle the cross's middle cell
     * @return the cross's cells, the middle first
     */
    private static List<BlockPos> crossOfOpenFloor(GameTestHelper helper, BlockPos middle) {
        List<BlockPos> cross = new ArrayList<>(List.of(middle));
        Direction.Plane.HORIZONTAL.forEach(side -> cross.add(middle.relative(side)));
        for (BlockPos cell : cross) {
            helper.setBlock(cell.below(), Blocks.STONE);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (!cross.contains(cell.relative(side))) {
                    helper.setBlock(cell.relative(side), Blocks.STONE);
                }
            }
            helper.setBlock(cell, Blocks.AIR);
        }
        return cross;
    }

    private static List<Integer> layersIn(GameTestHelper helper, List<BlockPos> cells) {
        return cells.stream().map(cell -> VolumeCellBlock.layersOf(helper.getBlockState(cell))).toList();
    }

    /**
     * Lays a stone floor under a cell and stone on its four sides, so slick in it cannot pour.
     *
     * @param helper the gametest helper
     * @param cell   the cell
     */
    private static void wallInOnStone(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        helper.setBlock(cell, Blocks.AIR);
    }

    private static BlockState slickOf(int layers) {
        return GooBlocks.KINETIC_SLICK.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers);
    }

    private static void assertLayers(GameTestHelper helper, BlockPos cell, int layers) {
        BlockState state = helper.getBlockState(cell);
        Object held = state.is(GooBlocks.KINETIC_SLICK.get()) && state.hasProperty(VolumeCellBlock.LAYERS)
                ? state.getValue(VolumeCellBlock.LAYERS) : state;
        helper.assertTrue(Integer.valueOf(layers).equals(held), String.format(LAYERS_WRONG, cell, layers, held));
    }

    private static void assertHeight(GameTestHelper helper, BlockPos cell, int layers) {
        BlockPos absolute = helper.absolutePos(cell);
        double height = helper.getLevel().getBlockState(absolute).getCollisionShape(helper.getLevel(), absolute)
                .max(Direction.Axis.Y);
        double owed = (double) layers / WHOLE_BLOCK_LAYERS;
        helper.assertTrue(Math.abs(height - owed) < SAME_SPEED, String.format(HEIGHT_WRONG, layers, owed, height));
    }

    /**
     * Streams Slick every tick of a hold from the first tick, aimed wherever the streamer looks.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param ticks    how many ticks the hold lasts
     */
    private static void holdSlickFor(GameTestHelper helper, ServerPlayer streamer, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
    }

    private static int kineticHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.KINETIC, 0);
    }

    /**
     * The slick films standing anywhere in the test's bounds.
     *
     * @param helper the gametest helper
     * @return the count
     */
    private static int filmsIn(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int films = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (helper.getLevel().getBlockState(pos).is(GooBlocks.KINETIC_SLICK.get())) {
                films++;
            }
        }
        return films;
    }

    /**
     * A slick cell of eleven layers, walled in so nothing pours, broken in a
     * live world leaves kinetic goo in its cell worth eleven layers' price,
     * each a fifteenth of the loaded Slick definition's cost.
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param helper the gametest helper
     */
    public static void breakingSlickDropsItsKineticGoo(GameTestHelper helper) {
        wallInOnStone(helper, AT_REST_CELL);
        helper.setBlock(AT_REST_CELL, slickOf(BROKEN_LAYERS));
        // GameTestHelper.destroyBlock breaks with drops off; the level's own break drops what the block gives
        helper.getLevel().destroyBlock(helper.absolutePos(AT_REST_CELL), true);
        List<ItemStack> kinetic = helper.getEntities(EntityType.ITEM, AT_REST_CELL, 1.0).stream()
                .map(ItemEntity::getItem).filter(stack -> GooTypes.KINETIC.equals(GooStacks.keyOf(stack))).toList();
        int dropped = kinetic.stream().mapToInt(GooStacks::volumeOf).sum();
        int owed = ResidualPrices.slickLayersRefund(helper, BROKEN_LAYERS);
        helper.assertTrue(dropped == owed, String.format(RECLAIM_WRONG, owed, kinetic));
        helper.succeed();
    }

    /**
     * A streamer standing at the head of a long strip of stone holds Slick
     * level, then lets go: once the drips have fallen a slick film stands in
     * a cell 7 to 9 blocks ahead along the look.
     * slick-sprays-twelve-blocks-ahead
     * goop-and-slick-throw-keeps-todays-level-reach
     *
     * @param helper the gametest helper
     */
    public static void slickLandsAboutEightBlocksAhead(GameTestHelper helper) {
        for (int east = 0; east <= FAR_STRIP_EAST; east++) {
            helper.setBlock(FAR_STREAMER_POS.below().east(east), Blocks.STONE);
        }
        ServerPlayer streamer = slickStreamerAt(helper, FAR_STREAMER_POS);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(0);
        int hold = SPRAY_HOLD_TICKS;
        holdSlickFor(helper, streamer, hold);
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<Integer> filmed = new ArrayList<>();
            for (int east = 0; east <= FAR_STRIP_EAST; east++) {
                if (helper.getBlockState(FAR_STREAMER_POS.east(east)).is(GooBlocks.KINETIC_SLICK.get())) {
                    filmed.add(east);
                }
            }
            helper.assertTrue(filmed.stream().anyMatch(east -> east >= NEAREST_AHEAD && east <= FARTHEST_AHEAD),
                    String.format(NO_FILM_AHEAD, filmed));
            helper.succeed();
        });
    }

    /**
     * A streamer on a perch forty blocks over a stone floor holds Slick aimed
     * down off the perch for a film's worth and lets go before the first drip
     * lands: the floor stands bare when the hold ends, and a slick film
     * stands on it once the drips have fallen.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper the gametest helper
     */
    public static void slickLandsBelowAHighPerchAfterRelease(GameTestHelper helper) {
        for (int east = 0; east <= FAR_STRIP_EAST; east++) {
            for (int z = FAR_STREAMER_POS.getZ() - FLOOR_HALF_WIDTH; z <= FAR_STREAMER_POS.getZ() + FLOOR_HALF_WIDTH;
                 z++) {
                helper.setBlock(new BlockPos(east, PERCHED_FLOOR_Y, z), Blocks.STONE);
            }
        }
        BlockPos stand = new BlockPos(FAR_STREAMER_POS.getX(), PERCHED_FLOOR_Y + 1 + HIGH_PERCH,
                FAR_STREAMER_POS.getZ());
        helper.setBlock(stand.below(), Blocks.STONE);
        ServerPlayer streamer = slickStreamerAt(helper, stand);
        // stood at the perch's east edge, so the steep cone clears the perch
        streamer.setPos(streamer.getX() + PERCH_EDGE, streamer.getY(), streamer.getZ());
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(DOWN_OFF_THE_PERCH);
        int hold = SPRAY_HOLD_TICKS;
        holdSlickFor(helper, streamer, hold);
        helper.runAfterDelay(hold + 1, () -> helper.assertTrue(filmsIn(helper) == 0,
                String.format(LANDED_BEFORE_RELEASE, filmsIn(helper))));
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            int films = 0;
            for (int east = 0; east <= FAR_STRIP_EAST; east++) {
                for (int z = FAR_STREAMER_POS.getZ() - FLOOR_HALF_WIDTH;
                     z <= FAR_STREAMER_POS.getZ() + FLOOR_HALF_WIDTH; z++) {
                    if (helper.getBlockState(new BlockPos(east, PERCHED_FLOOR_Y + 1, z))
                            .is(GooBlocks.KINETIC_SLICK.get())) {
                        films++;
                    }
                }
            }
            helper.assertTrue(films > 0, String.format(NO_FILM_BELOW, HIGH_PERCH));
            helper.succeed();
        });
    }
    /**
     * Slick's stream lays slick over a strip of stone; a survival player
     * uses a stone block item against the film's top, and the film still
     * stands, the stone set over it rather than in its place.
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param helper the gametest helper
     */
    public static void placingABlockKeepsTheSlick(GameTestHelper helper) {
        BlockPos slicked = SLICKED_STONE.above();
        laySlick(helper, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack stone = new ItemStack(Items.STONE);
            player.setItemInHand(InteractionHand.MAIN_HAND, stone);
            BlockPos cell = helper.absolutePos(slicked);
            Vec3 filmTop = Vec3.atBottomCenterOf(cell).add(0, ONE_LAYER, 0);
            stone.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(filmTop, Direction.UP, cell, false)));
            helper.assertTrue(helper.getBlockState(slicked).is(GooBlocks.KINETIC_SLICK.get()),
                    String.format(PLACED_OVER_SLICK, slicked, helper.getBlockState(slicked)));
            helper.succeed();
        });
    }

    /**
     * @param helper the gametest helper
     * @return the live Slick ability
     */
    private static AbilityDefinition slick(GameTestHelper helper) {
        AbilityDefinition slick = AbilityRegistry.of(helper.getLevel()).getAbility(SLICK);
        helper.assertTrue(slick != null, ABILITY_REQUIRED);
        return slick;
    }

    /**
     * A streamer at rest throws a held tick of Slick's drips, then throws the
     * next tick with the same aim while its known movement runs east and up:
     * the drips DripFlights holds launch at the same velocities both ticks.
     * goop-and-slick-drips-add-none-of-the-players-movement
     *
     * @param helper the gametest helper
     */
    public static void slickDripsLaunchAsFromRestWhileTheStreamerMoves(GameTestHelper helper) {
        ServerPlayer streamer = slickStreamer(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        helper.runAfterDelay(1, () -> launchedThisTick(helper, streamer, tick));
        helper.runAfterDelay(2, () -> {
            streamer.setKnownMovement(MOVING_AND_RISING);
            List<Vec3> moving = launchedThisTick(helper, streamer, tick);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            // operator ruling 2026-11-14: each tick scatters its own places, so the moving tick's drips are read
            // against that tick's places thrown from rest
            Delivery delivery = slick(helper).delivery();
            List<Vec3> atRest = CoatRay.scatteredShares(2, delivery.dripsPerTick()).stream()
                    .map(share -> DripArc.launch(streamer.getLookAngle(), delivery.coneDegrees(), share,
                            delivery.throwSpeed())).toList();
            helper.assertTrue(!atRest.isEmpty() && atRest.size() == moving.size(),
                    String.format(LAUNCHED_UNEVENLY, atRest.size(), moving.size()));
            for (int i = 0; i < atRest.size(); i++) {
                helper.assertTrue(atRest.get(i).distanceTo(moving.get(i)) <= SAME_LAUNCH,
                        String.format(LAUNCHED_WITH_MOVEMENT, atRest.get(i), moving.get(i)));
            }
            helper.succeed();
        });
    }

    /**
     * Streams one tick and answers the velocities of the drips it threw.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param tick     the stream's tick
     * @return the launch velocities of the drips the tick threw, in the order thrown
     */
    private static List<Vec3> launchedThisTick(GameTestHelper helper, ServerPlayer streamer, GooStreamPayload tick) {
        DripFlights flights = GooServerState.of(helper.getLevel().getServer()).dripFlights();
        List<DripFlights.Flight> before = flights.flyingFrom(streamer.getUUID());
        GooStreamHandler.streamTick(streamer, tick);
        return flights.flyingFrom(streamer.getUUID()).stream()
                .filter(flight -> before.stream().noneMatch(earlier -> earlier == flight))
                .map(DripFlights.Flight::velocity).toList();
    }

    /**
     * Slick's stream held on a strip of stone lays slick over it; an item
     * dropped onto the slick with a push east slides east along it and is no
     * faster ten ticks after it lands than it was landing, since a thing not
     * trying to move only slides.
     * slick-coats-the-ground-with-kinetic-speed
     *
     * @param helper the gametest helper
     */
    public static void slickSlidesAnItemWithoutSpeedingIt(GameTestHelper helper) {
        BlockPos slicked = SLICKED_STONE.above();
        laySlick(helper, () -> {
            BlockPos iceStart = SLICKED_STONE.east(STRIP_WEST).south(ICE_LANE_SOUTH);
            layLane(helper, iceStart, Blocks.PACKED_ICE);
            Slide onSlick = pushAnItem(helper, Vec3.atBottomCenterOf(slicked).add(0, ONE_LAYER, 0));
            Slide onIce = pushAnItem(helper, Vec3.atBottomCenterOf(slicked.south(ICE_LANE_SOUTH)));
            watchTheSlides(helper, onSlick, onIce);
        });
    }

    /**
     * Drops an item over a slippery block with a push east.
     *
     * @param helper  the gametest helper
     * @param surface the top of the block it drops onto
     * @return the item's slide
     */
    private static Slide pushAnItem(GameTestHelper helper, Vec3 surface) {
        ItemEntity item = helper.spawnItem(Items.SLIME_BALL, surface.add(0, DROP_HEIGHT, 0));
        item.setDeltaMovement(PUSH, 0, 0);
        return new Slide(item);
    }

    /**
     * Slick's stream lays slick over a strip of stone; a husk walks from
     * stone onto it while a second husk walks from stone onto packed ice,
     * which is as slippery as the slick. Some ticks onto its lane the husk on
     * slick moves faster than it walked the stone and faster than the husk on
     * ice, so the speed is Slick's ramp and not the slipperiness alone.
     * slick-ramp-grows-while-a-thing-keeps-moving
     *
     * @param helper the gametest helper
     */
    public static void slickSpeedsUpAMobWalkingOnIt(GameTestHelper helper) {
        laySlick(helper, () -> {
            BlockPos slickStart = SLICKED_STONE.east(STRIP_WEST);
            BlockPos iceStart = slickStart.south(ICE_LANE_SOUTH);
            layLane(helper, slickStart, null);
            layLane(helper, iceStart, Blocks.PACKED_ICE);
            Walker onSlick = walkTheLane(helper, slickStart, GooBlocks.KINETIC_SLICK.get());
            Walker onIce = walkTheLane(helper, iceStart, Blocks.PACKED_ICE);
            watchTheWalk(helper, onSlick, onIce);
        });
    }

    /**
     * Slick's stream lays slick over a strip of stone; a husk walks from
     * stone onto it and the first tick it stands on the slick raises it past
     * the speed it walked the stone.
     * slick-raises-a-slow-entry-to-a-start-speed
     *
     * @param helper the gametest helper
     */
    public static void slickRaisesASlowWalkerToItsStartSpeed(GameTestHelper helper) {
        laySlick(helper, () -> {
            BlockPos slickStart = SLICKED_STONE.east(STRIP_WEST);
            layLane(helper, slickStart, null);
            Walker walker = walkTheLane(helper, slickStart, GooBlocks.KINETIC_SLICK.get());
            // a sequence ticks apart from the delays this callback runs among, which take no new delay mid-tick
            helper.startSequence().thenWaitUntil(() -> {
                walker.watch(helper);
                helper.assertTrue(walker.stepped(), String.format(NEVER_WALKED, walker, "unwalked"));
            }).thenExecute(() -> helper.assertTrue(walker.firstOnSlippery > walker.onStone,
                    String.format(NOT_RAISED, walker.firstOnSlippery, walker.onStone))).thenSucceed();
        });
    }

    /**
     * Lays a strip of stone with a one-layer slick cell over each of its
     * stones, and runs what follows on it; the stream's own landing is
     * proven where the tests hold the stream, and a hold would pile its
     * drips into cells too tall to walk onto.
     *
     * @param helper  the gametest helper
     * @param onSlick what the test does on the laid slick
     */
    private static void laySlick(GameTestHelper helper, Runnable onSlick) {
        layStrip(helper);
        laySlickStrip(helper);
        onSlick.run();
    }

    /**
     * Lays a one-layer slick cell over each stone of the strip.
     *
     * @param helper the gametest helper
     */
    private static void laySlickStrip(GameTestHelper helper) {
        for (int east = STRIP_WEST; east <= STRIP_EAST; east++) {
            helper.setBlock(SLICKED_STONE.east(east).above(), GooBlocks.KINETIC_SLICK.get());
        }
    }

    /**
     * Lays the strip of stone on bedrock the stream slicks.
     *
     * @param helper the gametest helper
     */
    private static void layStrip(GameTestHelper helper) {
        for (int east = STRIP_WEST; east <= STRIP_EAST; east++) {
            helper.setBlock(SLICKED_STONE.east(east).below(), Blocks.BEDROCK);
            helper.setBlock(SLICKED_STONE.east(east), Blocks.STONE);
        }
    }

    /**
     * Lays a walker's lane: two blocks of stone west of the lane's start,
     * where the streamer stood, and the slippery stretch from the start east,
     * left as the stream laid it where no block is named.
     *
     * @param helper    the gametest helper
     * @param start     the lane's first slippery block
     * @param slippery  the block laid on the slippery stretch, null to keep the slick
     */
    private static void layLane(GameTestHelper helper, BlockPos start, Block slippery) {
        for (int west = 1; west <= 2; west++) {
            BlockPos stone = start.west(west);
            helper.setBlock(stone.below(), Blocks.BEDROCK);
            helper.setBlock(stone, Blocks.STONE);
            helper.setBlock(stone.above(), Blocks.AIR);
        }
        if (slippery != null) {
            for (int east = 0; east <= STRIP_EAST - STRIP_WEST; east++) {
                helper.setBlock(start.east(east).below(), Blocks.BEDROCK);
                helper.setBlock(start.east(east), slippery);
                helper.setBlock(start.east(east).above(), Blocks.AIR);
            }
        }
    }

    /**
     * Spawns a husk on a lane's stone, where daylight does not burn it, and
     * walks it east along the lane.
     *
     * @param helper   the gametest helper
     * @param start    the lane's first slippery block
     * @param slippery the block the lane's slippery stretch stands on
     * @return the walker
     */
    private static Walker walkTheLane(GameTestHelper helper, BlockPos start, Block slippery) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, start.west(2).above());
        helper.walkTo(husk, start.east(STRIP_EAST - STRIP_WEST).above(), 1f);
        return new Walker(husk, slippery);
    }

    /**
     * A husk walking its lane, with its speed on its last stone tick and on
     * the ticks its getOnPos block is its lane's slippery block.
     */
    private static final class Walker {
        private final Mob mob;
        private final Block slippery;
        private double onStone = -1;
        private int slipperyTicks;
        private double firstOnSlippery = -1;
        private double onSlippery = -1;

        Walker(Mob mob, Block slippery) {
            this.mob = mob;
            this.slippery = slippery;
        }

        void watch(GameTestHelper helper) {
            if (onSlippery >= 0) {
                return;
            }
            double speed = mob.getDeltaMovement().horizontalDistance();
            BlockState under = helper.getLevel().getBlockState(mob.getOnPos());
            if (under.is(Blocks.STONE)) {
                onStone = speed;
                return;
            }
            if (onStone < 0 || !under.is(slippery)) {
                return;
            }
            if (++slipperyTicks == 1) {
                firstOnSlippery = speed;
            }
            if (slipperyTicks == SLIPPERY_TICKS) {
                onSlippery = speed;
            }
        }

        boolean stepped() {
            return firstOnSlippery >= 0;
        }

        boolean crossed() {
            return onSlippery >= 0;
        }

        @Override
        public String toString() {
            return String.format("stone %.4f, slippery %.4f", onStone, onSlippery);
        }
    }

    /**
     * Reads both walkers each tick and, once both have spent their ticks on
     * their slippery lanes, succeeds where the husk on slick is the faster.
     *
     * @param helper  the gametest helper
     * @param onSlick the husk on the slicked lane
     * @param onIce   the husk on the ice lane
     */
    private static void watchTheWalk(GameTestHelper helper, Walker onSlick, Walker onIce) {
        // a sequence ticks apart from the delays this callback runs among, which take no new delay mid-tick
        helper.startSequence().thenWaitUntil(() -> {
            onSlick.watch(helper);
            onIce.watch(helper);
            helper.assertTrue(onSlick.crossed() && onIce.crossed(), String.format(NEVER_WALKED, onSlick, onIce));
        }).thenExecute(() -> {
            helper.assertTrue(onSlick.onSlippery > onSlick.onStone, String.format(NOT_FASTER_THAN_STONE,
                    SLIPPERY_TICKS, onSlick.onStone, onSlick.onSlippery));
            helper.assertTrue(onSlick.onSlippery > onIce.onSlippery, String.format(NOT_FASTER_THAN_ICE,
                    SLIPPERY_TICKS, SLIPPERY_TICKS, onIce.onSlippery, onSlick.onSlippery));
        }).thenSucceed();
    }

    /**
     * An item sliding on its slippery block: where and how fast it landed,
     * and how fast it moved the slide's ticks after.
     */
    private static final class Slide {
        private final ItemEntity item;
        private double xAtLanding;
        private double atLanding = -1;
        private int slidTicks;
        private double later = -1;

        Slide(ItemEntity item) {
            this.item = item;
        }

        void watch() {
            if (later >= 0) {
                return;
            }
            if (atLanding < 0 && item.onGround()) {
                xAtLanding = item.getX();
                atLanding = item.getDeltaMovement().horizontalDistance();
            } else if (atLanding >= 0 && ++slidTicks == SLIDE_TICKS) {
                later = item.getDeltaMovement().horizontalDistance();
            }
        }

        boolean landed() {
            return atLanding >= 0;
        }

        boolean slid() {
            return later >= 0;
        }
    }

    /**
     * Reads both items each tick and, once both have slid their ticks,
     * judges the slick's.
     *
     * @param helper  the gametest helper
     * @param onSlick the item on the slick
     * @param onIce   the item on the ice
     */
    private static void watchTheSlides(GameTestHelper helper, Slide onSlick, Slide onIce) {
        // a sequence ticks apart from the delays this callback runs among, which take no new delay mid-tick
        helper.startSequence().thenWaitUntil(() -> {
            onSlick.watch();
            onIce.watch();
            helper.assertTrue(onSlick.landed() && onIce.landed(), NEVER_LANDED);
            helper.assertTrue(onSlick.slid() && onIce.slid(), NOT_SLID);
        }).thenExecute(() -> assertSlid(helper, onSlick, onIce)).thenSucceed();
    }

    /**
     * Succeeds where the item on slick slid east, still stands on slick, is
     * no faster than it landed, and moves as fast as the item on ice.
     *
     * @param helper  the gametest helper
     * @param onSlick the item on the slick
     * @param onIce   the item on the ice
     */
    private static void assertSlid(GameTestHelper helper, Slide onSlick, Slide onIce) {
        ItemEntity item = onSlick.item;
        BlockPos under = item.getOnPos();
        helper.assertTrue(item.getX() > onSlick.xAtLanding,
                String.format(NOT_ADVANCED, onSlick.xAtLanding, item.getX()));
        helper.assertTrue(helper.getLevel().getBlockState(under).is(GooBlocks.KINETIC_SLICK.get()),
                String.format(NOT_ON_SLICK, helper.getLevel().getBlockState(under)));
        helper.assertTrue(onSlick.later <= onSlick.atLanding,
                String.format(SPED_UP, SLIDE_TICKS, onSlick.atLanding, onSlick.later));
        helper.assertTrue(Math.abs(onSlick.later - onIce.later) < SAME_SPEED,
                String.format(NOT_AS_ICE, SLIDE_TICKS, onIce.later, onSlick.later));
    }

    /**
     * A Kinetic invoker standing at the bay's west side facing east, pitched
     * down at the stone, taught what Slick requires.
     *
     * @param helper the gametest helper
     * @return the streamer
     */
    private static ServerPlayer slickStreamer(GameTestHelper helper) {
        ServerPlayer streamer = slickStreamerAt(helper, STREAMER_POS);
        aimAtTheStone(helper, streamer);
        return streamer;
    }

    /**
     * Aims a streamer's axis drip down onto the middle of the slicked stone's
     * top, since a drip striking a side lays nothing.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     */
    private static void aimAtTheStone(GameTestHelper helper, ServerPlayer streamer) {
        DripAim.at(streamer, Vec3.upFromBottomCenterOf(helper.absolutePos(SLICKED_STONE), 1),
                slick(helper).delivery().throwSpeed());
    }

    /**
     * A Kinetic invoker standing on a cell, taught what Slick requires.
     *
     * @param helper the gametest helper
     * @param stand  the cell they stand in
     * @return the streamer
     */
    private static ServerPlayer slickStreamerAt(GameTestHelper helper, BlockPos stand) {
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.KINETIC);
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(stand));
        streamer.setPos(feet.x, feet.y, feet.z);
        KnownRecipes.teachRequires(streamer, slick(helper));
        return streamer;
    }
}
