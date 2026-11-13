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
    private static final String SLICK_LAYS_AT_ITS_TICKS = "slick_lays_at_its_ticks_under_a_streamer_at_rest";
    /** kinetic_slick.json's slick ticks. */
    private static final int JSON_LAY_TICKS = 20;
    /** The traveling streamer's speed east, in blocks a tick. */
    private static final double TRAVEL_SPEED = 0.6;
    /**
     * The held ticks slick takes under the traveling streamer: a walking
     * player's unadjusted speed is about 0.2159 blocks a tick, so the factor
     * is about 2.78 and a held tick builds about 0.139 of a cell, short of a
     * whole cell after 7 ticks and past it after 8.
     */
    private static final int TRAVELING_LAY_TICKS = 8;
    private static final String LAID_EARLY = "Slick should not have laid by held tick %d, expected at %d";
    private static final String NOT_LAID = "Slick should have laid by held tick %d, the stone's top holds %s";
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the stone's center three blocks off. */
    private static final float AT_THE_STONE = 20.5f;
    /** Three blocks east of the streamer, at its height, on a block the test lays. */
    private static final BlockPos SLICKED_STONE = STREAMER_POS.east(3);
    /** The westmost and eastmost offsets from SLICKED_STONE of the strip the stream slicks. */
    private static final int STRIP_WEST = -1;
    private static final int STRIP_EAST = 2;
    /** kinetic_slick.json's slick ticks, and some slack past them so the slick has surely landed. */
    private static final int LAY_TICKS = 25;
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
    private static final String NOT_SLOW_ON_STONE = "The husk should walk the stone slower than the start speed:"
            + " %.4f against %.4f";
    private static final String NOT_RAISED = "A husk stepping onto slick slowly should leave its first slick tick"
            + " at the start speed or faster: %.4f against %.4f";
    private static final String NOT_FASTER_THAN_ICE = "A husk %d ticks onto slick should outpace a husk %d ticks"
            + " onto ice: %.4f on ice, %.4f on slick";
    private static final String SLICK_REACHES_TWELVE = "slick_lays_twelve_blocks_ahead_and_no_further";
    /** Where the streamer stands in the light bay, at its west wall, on the strip's first stone. */
    private static final BlockPos FAR_STREAMER_POS = new BlockPos(0, 1, 8);
    /** The light bay is sixteen blocks across, so the strip runs from the streamer to its east wall. */
    private static final int FAR_STRIP_EAST = 15;
    /** The stone ten blocks east of the streamer, its top about 10.1 blocks from their eye, past 8 and inside 12. */
    private static final BlockPos BEYOND_EIGHT = FAR_STREAMER_POS.below().east(10);
    /** The first stone east whose top lies wholly more than 12 blocks from the streamer's eye, about 12.6 at its nearest. */
    private static final int PAST_TWELVE_EAST = 13;
    private static final String NOT_LAID_BEYOND_EIGHT = "Slick should lay %d held ticks on over the stone at %s,"
            + " past 8 blocks from the eye and inside 12, holds %s";
    private static final String LAID_PAST_TWELVE = "Slick should not lay over the stone at %s, past 12 blocks from"
            + " the eye, holds %s";

    private static final String BREAKING_SLICK_DROPS = "breaking_slick_drops_its_kinetic_goo";
    private static final String PLACING_KEEPS_SLICK = "placing_a_block_keeps_the_slick";
    private static final String RECLAIM_WRONG = "A broken slick film should drop one kinetic goo of %d mb, dropped %s";
    private static final String PLACED_OVER_SLICK = "A block placed against a slick film should leave the film"
            + " standing at %s, the cell holds %s";

    private static final String SLICK_DRAINS_PER_FILM = "slick_drains_its_cost_for_each_film_it_lays";
    private static final String SLICK_LAYING_NOTHING_DRAINS_NOTHING = "slick_laying_no_film_drains_no_goo";
    private static final String SLICK_UNPAID_FILM_STAYS_OUT = "slick_lays_no_film_the_streamer_cannot_pay_for";
    /** A hold three films' worth of ticks long. */
    private static final int THREE_FILMS_TICKS = 3 * JSON_LAY_TICKS;
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
        GooTestFunctions.registerTest(registrar, SLICK_LAYS_AT_ITS_TICKS, SlickTests::slickLaysAtItsTicksUnderAStreamerAtRest);
        GooTestFunctions.registerTest(registrar, SLICK_REACHES_TWELVE, SlickTests::slickLaysTwelveBlocksAheadAndNoFurther);
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
        holdSlickFor(helper, streamer, LAY_TICKS);
        helper.runAfterDelay(LAY_TICKS + 1, () -> {
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
        holdSlickFor(helper, streamer, THREE_FILMS_TICKS);
        helper.runAfterDelay(THREE_FILMS_TICKS + 1, () -> {
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
        holdSlickFor(helper, streamer, THREE_FILMS_TICKS);
        helper.runAfterDelay(THREE_FILMS_TICKS + 1, () -> {
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
     * A streamer at rest at the head of a long strip of stone, looking at the
     * stone ten blocks east, lays slick over it at the 20 held ticks
     * kinetic_slick.json names, and every stone on the strip whose top lies
     * more than 12 blocks from their eye stays bare.
     * slick-sprays-twelve-blocks-ahead
     *
     * @param helper the gametest helper
     */
    public static void slickLaysTwelveBlocksAheadAndNoFurther(GameTestHelper helper) {
        for (int east = 0; east <= FAR_STRIP_EAST; east++) {
            helper.setBlock(FAR_STREAMER_POS.below().east(east), Blocks.STONE);
        }
        ServerPlayer streamer = slickStreamerAt(helper, FAR_STREAMER_POS);
        lookAt(streamer, Vec3.upFromBottomCenterOf(helper.absolutePos(BEYOND_EIGHT), 1));
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        for (int held = 1; held < JSON_LAY_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(JSON_LAY_TICKS, () -> {
            GooStreamHandler.streamTick(streamer, tick);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLaidToTwelve(helper);
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
     * Asserts slick over the stone past 8 blocks and air over every stone
     * past 12 on the strip.
     *
     * @param helper the gametest helper
     */
    private static void assertLaidToTwelve(GameTestHelper helper) {
        BlockPos laid = BEYOND_EIGHT.above();
        helper.assertTrue(helper.getBlockState(laid).is(GooBlocks.KINETIC_SLICK.get()),
                String.format(NOT_LAID_BEYOND_EIGHT, JSON_LAY_TICKS, laid, helper.getBlockState(laid)));
        for (int east = PAST_TWELVE_EAST; east <= FAR_STRIP_EAST; east++) {
            BlockPos bare = FAR_STREAMER_POS.east(east);
            helper.assertTrue(helper.getBlockState(bare).isAir(),
                    String.format(LAID_PAST_TWELVE, bare, helper.getBlockState(bare)));
        }
    }

    /**
     * Turns a streamer's look onto a point.
     *
     * @param streamer the streamer
     * @param target   the point to look at
     */
    private static void lookAt(ServerPlayer streamer, Vec3 target) {
        Vec3 line = target.subtract(streamer.getEyePosition());
        streamer.setYRot((float) Math.toDegrees(Math.atan2(-line.x, line.z)));
        streamer.setXRot((float) Math.toDegrees(-Math.atan2(line.y, line.horizontalDistance())));
    }

    /**
     * A streamer standing on slick and traveling east at TRAVEL_SPEED, near
     * three times a walking player's unadjusted speed, lays slick on the
     * stone in TRAVELING_LAY_TICKS held ticks, and the stone's top is still
     * air a held tick earlier.
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
        holdUntilSlick(helper, streamer, TRAVELING_LAY_TICKS);
    }

    /**
     * A streamer standing on stone at rest lays slick on the stone at the 20
     * held ticks kinetic_slick.json names, the stone's top still air a held
     * tick earlier.
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     *
     * @param helper the gametest helper
     */
    public static void slickLaysAtItsTicksUnderAStreamerAtRest(GameTestHelper helper) {
        holdUntilSlick(helper, slickStreamer(helper), JSON_LAY_TICKS);
    }

    /**
     * Holds Slick's stream on a strip of stone and succeeds where the
     * stone's top is air after the held tick before the expected one and
     * slick after the expected one.
     *
     * @param helper   the gametest helper
     * @param streamer the streamer
     * @param layTicks the held ticks the slick is expected to take
     */
    private static void holdUntilSlick(GameTestHelper helper, ServerPlayer streamer, int layTicks) {
        layStrip(helper);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(),
                streamer.getEyePosition(), streamer.getEyePosition());
        BlockPos slicked = SLICKED_STONE.above();
        for (int held = 1; held <= layTicks; held++) {
            int heldTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(streamer, tick);
                boolean slick = helper.getBlockState(slicked).is(GooBlocks.KINETIC_SLICK.get());
                if (heldTick == layTicks - 1) {
                    helper.assertTrue(!slick, String.format(LAID_EARLY, heldTick, layTicks));
                } else if (heldTick == layTicks) {
                    helper.assertTrue(slick, String.format(NOT_LAID, layTicks, helper.getBlockState(slicked)));
                    helper.getLevel().getServer().getPlayerList().remove(streamer);
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
     * stone onto it slower than the slick step's start speed and moves at
     * the start speed or faster the first tick it stands on the slick.
     * slick-raises-a-slow-entry-to-a-start-speed
     *
     * @param helper the gametest helper
     */
    public static void slickRaisesASlowWalkerToItsStartSpeed(GameTestHelper helper) {
        laySlick(helper, () -> {
            BlockPos slickStart = SLICKED_STONE.east(STRIP_WEST);
            layLane(helper, slickStart, null);
            Walker walker = walkTheLane(helper, slickStart, GooBlocks.KINETIC_SLICK.get());
            double startSpeed = SlickStep.in(AbilityRegistry.of(helper.getLevel()).getAbility(SLICK).behaviors())
                    .orElseThrow().startSpeed();
            // a sequence ticks apart from the delays this callback runs among, which take no new delay mid-tick
            helper.startSequence().thenWaitUntil(() -> {
                walker.watch(helper);
                helper.assertTrue(walker.stepped(), String.format(NEVER_WALKED, walker, "unwalked"));
            }).thenExecute(() -> {
                helper.assertTrue(walker.onStone < startSpeed,
                        String.format(NOT_SLOW_ON_STONE, walker.onStone, startSpeed));
                helper.assertTrue(walker.firstOnSlippery >= startSpeed,
                        String.format(NOT_RAISED, walker.firstOnSlippery, startSpeed));
            }).thenSucceed();
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
        for (int held = 1; held <= LAY_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        BlockPos slicked = SLICKED_STONE.above();
        helper.runAfterDelay(LAY_TICKS + 1, () -> {
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
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(AT_THE_STONE);
        return streamer;
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
        AbilityDefinition slick = AbilityRegistry.of(helper.getLevel()).getAbility(SLICK);
        helper.assertTrue(slick != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, slick);
        return streamer;
    }
}
