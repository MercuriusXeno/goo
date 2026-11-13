package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SlickStep;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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
    private static final String SLICK_LAYS_FASTER = "slick_lays_faster_under_a_streamer_traveling_on_slick";
    /** The traveling streamer's speed east, in blocks a tick. */
    private static final double TRAVEL_SPEED = 0.6;
    private static final String NOT_LAID_FASTER = "Slick under a traveling streamer should have laid by held tick %d,"
            + " short of the %d held ticks it takes at rest, the stone's top holds %s";
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
    /** Ticks a hold's last drips take to fall onto a cell three blocks off, with room to spare. */
    private static final int DRIP_FLIGHT_TICKS = 10;
    /** Three blocks east of the streamer, at its height, on a block the test lays. */
    private static final BlockPos SLICKED_STONE = STREAMER_POS.east(3);
    /** The westmost and eastmost offsets from SLICKED_STONE of the strip the stream slicks. */
    private static final int STRIP_WEST = -1;
    private static final int STRIP_EAST = 2;
    /** Held ticks past the slick step's own, so the slick has surely landed. */
    private static final int LAY_SLACK_TICKS = 5;
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
    private static final String NO_SLICK = "Slick's stream should lay slick over the stone at %s, holds %s";
    private static final String NEVER_LANDED = "The items dropped onto the slick and the ice never both landed";
    private static final String NOT_SLID = "The items never both slid their ticks after landing";
    private static final String NOT_ON_SLICK = "The item should still stand on slick after its slide, stands on %s";
    private static final String NOT_ADVANCED = "The item should slide east along the slick: x %.4f at landing, %.4f after";
    private static final String SPED_UP = "An item sliding on slick should gain no speed %d ticks after landing:"
            + " %.4f then %.4f";
    private static final String NOT_AS_ICE = "An item %d ticks onto slick should slide as fast as one on ice,"
            + " the slick ramping nothing: %.6f on ice, %.6f on slick";
    /** The slick film's height over the block it lies on, a sixteenth. */
    private static final double SLICK_FILM = 1.0 / 16;
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
    private static final String SLICK_LANDS_LEVEL_AHEAD = "slick_lands_about_twelve_blocks_ahead_on_flat_ground";
    private static final String SLICK_LANDS_AFTER_RELEASE = "slick_lands_below_a_high_perch_after_the_hold_ends";
    /** Where the streamer stands in the light bay, at its west wall, on the strip's first stone. */
    private static final BlockPos FAR_STREAMER_POS = new BlockPos(0, 1, 8);
    /** The light bay is sixteen blocks across, so the strip runs from the streamer to its east wall. */
    private static final int FAR_STRIP_EAST = 15;
    /** The nearest and farthest the level shot's film may stand ahead of the streamer, in cells. */
    private static final int NEAREST_AHEAD = 11;
    private static final int FARTHEST_AHEAD = 13;
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
    private static final String NO_FILM_AHEAD = "A level Slick shot should lay a film 11 to 13 blocks ahead,"
            + " films stand %s blocks ahead";
    private static final String NO_FILM_BELOW = "Slick aimed down from %d blocks up should lay a film on the floor";
    private static final String LANDED_BEFORE_RELEASE = "The high perch's first drips should still be falling when"
            + " the hold ends, %d films stand";

    private static final String BREAKING_SLICK_DROPS = "breaking_slick_drops_its_kinetic_goo";
    private static final String PLACING_KEEPS_SLICK = "placing_a_block_keeps_the_slick";
    private static final String RECLAIM_WRONG = "A broken slick film should drop one kinetic goo of %d mb, dropped %s";
    private static final String PLACED_OVER_SLICK = "A block placed against a slick film should leave the film"
            + " standing at %s, the cell holds %s";

    private static final String SLICK_DRAINS_PER_FILM = "slick_drains_its_cost_for_each_film_it_lays";
    private static final String SLICK_LAYING_NOTHING_DRAINS_NOTHING = "slick_laying_no_film_drains_no_goo";
    private static final String SLICK_UNPAID_FILM_STAYS_OUT = "slick_lays_no_film_the_streamer_cannot_pay_for";
    private static final String NO_FILMS = "The hold should lay at least one film";
    private static final String DRAINED_WRONG = "The hold laid %d films and should drain %s mb of kinetic goo,"
            + " drained %d";
    private static final String FILMS_WRONG = "The hold should leave %d slick films, leaves %d";

    private SlickTests() {
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
        GooTestFunctions.registerTest(registrar, SLICK_LAYS_FASTER, SlickTests::slickLaysFasterUnderAStreamerTravelingOnSlick);
        GooTestFunctions.registerTest(registrar, SLICK_DRIPS_LAUNCH_AS_FROM_REST,
                SlickTests::slickDripsLaunchAsFromRestWhileTheStreamerMoves);
        GooTestFunctions.registerTest(registrar, SLICK_LANDS_LEVEL_AHEAD, SlickTests::slickLandsAboutTwelveBlocksAhead);
        GooTestFunctions.registerTest(registrar, SLICK_LANDS_AFTER_RELEASE,
                SlickTests::slickLandsBelowAHighPerchAfterRelease);
        GooTestFunctions.registerTest(registrar, SLICK_DRAINS_PER_FILM, SlickTests::slickDrainsItsCostForEachFilmItLays);
        GooTestFunctions.registerTest(registrar, SLICK_LAYING_NOTHING_DRAINS_NOTHING,
                SlickTests::slickLayingNoFilmDrainsNoGoo);
        GooTestFunctions.registerTest(registrar, SLICK_UNPAID_FILM_STAYS_OUT,
                SlickTests::slickLaysNoFilmTheStreamerCannotPayFor);
    }

    /**
     * A Slick hold over a strip of stone drains Slick's cost once for each
     * film it lays, and nothing for the ticks held.
     * slick-drains-per-film-it-lays
     *
     * @param helper the gametest helper
     */
    public static void slickDrainsItsCostForEachFilmItLays(GameTestHelper helper) {
        layStrip(helper);
        ServerPlayer streamer = slickStreamer(helper);
        int before = kineticHeld(streamer);
        int held = layTicks(helper) + LAY_SLACK_TICKS;
        holdSlickFor(helper, streamer, held);
        helper.runAfterDelay(held + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            int films = filmsIn(helper);
            helper.assertTrue(films > 0, NO_FILMS);
            GooFraction owed = ResidualPrices.filmPrice(helper).times(films);
            int drained = before - kineticHeld(streamer);
            helper.assertTrue(drained == owed.ceiling(), String.format(DRAINED_WRONG, films, owed, drained));
            helper.succeed();
        });
    }

    /**
     * A Slick hold over a strip already slick, so no cell it reaches takes a
     * film, lasting three films' worth of ticks, drains no kinetic goo.
     * slick-drains-per-film-it-lays
     *
     * @param helper the gametest helper
     */
    public static void slickLayingNoFilmDrainsNoGoo(GameTestHelper helper) {
        layStrip(helper);
        for (int east = STRIP_WEST; east <= STRIP_EAST; east++) {
            helper.setBlock(SLICKED_STONE.east(east).above(), GooBlocks.KINETIC_SLICK.get());
        }
        ServerPlayer streamer = slickStreamer(helper);
        int before = kineticHeld(streamer);
        int threeFilms = 3 * layTicks(helper);
        holdSlickFor(helper, streamer, threeFilms);
        helper.runAfterDelay(threeFilms + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            int drained = before - kineticHeld(streamer);
            helper.assertTrue(drained == 0, String.format(DRAINED_WRONG, 0, GooFraction.ZERO, drained));
            helper.succeed();
        });
    }

    /**
     * A streamer holding one film's price of kinetic goo holds Slick across
     * a strip of open stone tops: one film lands and the goo runs out.
     * slick-drains-per-film-it-lays
     *
     * @param helper the gametest helper
     */
    public static void slickLaysNoFilmTheStreamerCannotPayFor(GameTestHelper helper) {
        layStrip(helper);
        ServerPlayer streamer = slickStreamer(helper);
        GooSourceScanner.deplete(streamer, GooTypes.KINETIC,
                kineticHeld(streamer) - ResidualPrices.filmPrice(helper).ceiling());
        int threeFilms = 3 * layTicks(helper);
        holdSlickFor(helper, streamer, threeFilms);
        helper.runAfterDelay(threeFilms + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(filmsIn(helper) == 1, String.format(FILMS_WRONG, 1, filmsIn(helper)));
            helper.assertTrue(kineticHeld(streamer) == 0, "The streamer should hold no kinetic goo, holds "
                    + kineticHeld(streamer));
            helper.succeed();
        });
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
     * Slick's stream lays slick over a strip of stone; the film broken in a
     * live world leaves one kinetic goo item in its cell worth 12 mb.
     * breaking-slick-drops-kinetic-goo-worth-its-film
     *
     * @param helper the gametest helper
     */
    public static void breakingSlickDropsItsKineticGoo(GameTestHelper helper) {
        BlockPos slicked = SLICKED_STONE.above();
        laySlick(helper, () -> {
            // GameTestHelper.destroyBlock breaks with drops off; the level's own break drops what the block gives
            helper.getLevel().destroyBlock(helper.absolutePos(slicked), true);
            List<ItemStack> kinetic = helper.getEntities(EntityType.ITEM, slicked, 1.0).stream()
                    .map(ItemEntity::getItem).filter(stack -> GooTypes.KINETIC.equals(GooStacks.keyOf(stack)))
                    .toList();
            int owed = ResidualPrices.filmsRefund(helper, 1);
            helper.assertTrue(kinetic.size() == 1 && GooStacks.volumeOf(kinetic.getFirst()) == owed,
                    String.format(RECLAIM_WRONG, owed, kinetic));
            helper.succeed();
        });
    }

    /**
     * A streamer standing at the head of a long strip of stone holds Slick
     * level, then lets go: once the drips have fallen a slick film stands in
     * a cell 11 to 13 blocks ahead along the look.
     * slick-sprays-twelve-blocks-ahead
     * goop-and-slick-throw-keeps-todays-level-reach
     *
     * @param helper the gametest helper
     */
    public static void slickLandsAboutTwelveBlocksAhead(GameTestHelper helper) {
        for (int east = 0; east <= FAR_STRIP_EAST; east++) {
            helper.setBlock(FAR_STREAMER_POS.below().east(east), Blocks.STONE);
        }
        ServerPlayer streamer = slickStreamerAt(helper, FAR_STREAMER_POS);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(0);
        int hold = layTicks(helper) + LAY_SLACK_TICKS;
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
        int hold = layTicks(helper) + LAY_SLACK_TICKS;
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
            Vec3 filmTop = Vec3.atBottomCenterOf(cell).add(0, SLICK_FILM, 0);
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
     * @param helper the gametest helper
     * @return the held ticks the live slick step takes to lay under a streamer at rest
     */
    private static int layTicks(GameTestHelper helper) {
        return SlickStep.in(slick(helper).behaviors()).orElseThrow().ticks();
    }


    /**
     * A streamer standing on slick and traveling east at TRAVEL_SPEED, near
     * three times a walking player's unadjusted speed, lays slick on the
     * stone in fewer held ticks than the slick step takes at rest.
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     *
     * @param helper the gametest helper
     */
    public static void slickLaysFasterUnderAStreamerTravelingOnSlick(GameTestHelper helper) {
        helper.setBlock(STREAMER_POS.below(), Blocks.STONE);
        helper.setBlock(STREAMER_POS, GooBlocks.KINETIC_SLICK.get());
        ServerPlayer streamer = slickStreamer(helper);
        streamer.setPos(streamer.getX(), streamer.getY() + SLICK_FILM, streamer.getZ());
        streamer.setKnownMovement(new Vec3(TRAVEL_SPEED, 0, 0));
        holdUntilSlick(helper, streamer);
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
        List<Vec3> atRest = new ArrayList<>();
        helper.runAfterDelay(1, () -> atRest.addAll(launchedThisTick(helper, streamer, tick)));
        helper.runAfterDelay(2, () -> {
            streamer.setKnownMovement(MOVING_AND_RISING);
            List<Vec3> moving = launchedThisTick(helper, streamer, tick);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
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
     * Holds Slick's stream on a strip of stone for one held tick short of
     * the slick step's ticks at rest, and succeeds where the stone's top
     * holds slick by then.
     *
     * @param helper   the gametest helper
     * @param streamer the streamer
     */
    private static void holdUntilSlick(GameTestHelper helper, ServerPlayer streamer) {
        layStrip(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        BlockPos slicked = SLICKED_STONE.above();
        int atRest = layTicks(helper);
        int shortOfRest = atRest - 1;
        for (int held = 1; held <= shortOfRest; held++) {
            int heldTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(streamer, tick);
                if (heldTick == shortOfRest) {
                    helper.getLevel().getServer().getPlayerList().remove(streamer);
                    helper.assertTrue(helper.getBlockState(slicked).is(GooBlocks.KINETIC_SLICK.get()),
                            String.format(NOT_LAID_FASTER, shortOfRest, atRest, helper.getBlockState(slicked)));
                    helper.succeed();
                }
            });
        }
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
            Slide onSlick = pushAnItem(helper, Vec3.atBottomCenterOf(slicked).add(0, SLICK_FILM, 0));
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
     * Lays a strip of stone, holds Slick's stream on it until it slicks, and
     * runs what follows once the streamer is gone and the slick stands.
     *
     * @param helper  the gametest helper
     * @param onSlick what the test does on the laid slick
     */
    private static void laySlick(GameTestHelper helper, Runnable onSlick) {
        layStrip(helper);
        ServerPlayer streamer = slickStreamer(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        int hold = layTicks(helper) + LAY_SLACK_TICKS;
        for (int held = 1; held <= hold; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        BlockPos slicked = SLICKED_STONE.above();
        // goop-and-slick-coat-lands-where-each-drip-arc-strikes: the hold's last drips land after it ends
        helper.runAfterDelay(hold + DRIP_FLIGHT_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(helper.getBlockState(slicked).is(GooBlocks.KINETIC_SLICK.get()),
                    String.format(NO_SLICK, slicked, helper.getBlockState(slicked)));
            onSlick.run();
        });
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
