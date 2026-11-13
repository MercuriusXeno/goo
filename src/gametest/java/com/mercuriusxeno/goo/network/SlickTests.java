package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SlickStep;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Kinetic's Slick, cast through the real stream tick.
 * slick-coats-the-ground-with-kinetic-speed
 * slick-ramp-grows-while-a-thing-keeps-moving
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
    private static final BlockPos STREAMER_POS = new BlockPos(0, 1, 2);
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the stone's center three blocks off. */
    private static final float AT_THE_STONE = 20.5f;
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
    private static final String SLICK_REACHES_ITS_RANGE = "slick_lays_ahead_to_its_range_and_no_further";
    /** Where the streamer stands in the light bay, at its west wall, on the strip's first stone. */
    private static final BlockPos FAR_STREAMER_POS = new BlockPos(0, 1, 8);
    /** The light bay is sixteen blocks across, so the strip runs from the streamer to its east wall. */
    private static final int FAR_STRIP_EAST = 15;
    /** The stone ten blocks east of the streamer, its top about 10.1 blocks from their eye, well inside the stream's range. */
    private static final BlockPos FAR_INSIDE = FAR_STREAMER_POS.below().east(10);
    private static final String NOT_LAID_FAR_INSIDE = "Slick should lay %d held ticks on over the stone at %s,"
            + " inside the stream's %.1f block range, holds %s";
    private static final String LAID_PAST_RANGE = "Slick should not lay over the stone at %s, %.2f blocks from"
            + " the eye and past the stream's %.1f block range, holds %s";

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
        GooTestFunctions.registerTest(registrar, SLICK_LAYS_FASTER, SlickTests::slickLaysFasterUnderAStreamerTravelingOnSlick);
        GooTestFunctions.registerTest(registrar, SLICK_REACHES_ITS_RANGE, SlickTests::slickLaysAheadToItsRangeAndNoFurther);
    }

    /**
     * A streamer at rest at the head of a long strip of stone, looking at the
     * stone ten blocks east, lays slick over it once held past the slick
     * step's ticks, and every stone on the strip whose top lies wholly past
     * the stream's range from their eye stays bare. The hold and the range
     * are read from the ability, so a retune moves them both.
     * slick-sprays-twelve-blocks-ahead
     *
     * @param helper the gametest helper
     */
    public static void slickLaysAheadToItsRangeAndNoFurther(GameTestHelper helper) {
        for (int east = 0; east <= FAR_STRIP_EAST; east++) {
            helper.setBlock(FAR_STREAMER_POS.below().east(east), Blocks.STONE);
        }
        ServerPlayer streamer = slickStreamerAt(helper, FAR_STREAMER_POS);
        lookAt(streamer, Vec3.upFromBottomCenterOf(helper.absolutePos(FAR_INSIDE), 1));
        Vec3 eye = streamer.getEyePosition();
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), SLICK.toString(), eye, eye);
        int hold = layTicks(helper) + LAY_SLACK_TICKS;
        for (int held = 1; held < hold; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
        helper.runAfterDelay(hold, () -> {
            GooStreamHandler.streamTick(streamer, tick);
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            assertLaidToItsRange(helper, eye, hold);
            helper.succeed();
        });
    }

    /**
     * Asserts slick over the stone well inside the stream's range and air
     * over every stone on the strip whose top lies wholly past it.
     *
     * @param helper the gametest helper
     * @param eye    the streamer's eye
     * @param hold   the held ticks the stream ran
     */
    private static void assertLaidToItsRange(GameTestHelper helper, Vec3 eye, int hold) {
        double range = slick(helper).delivery().range();
        BlockPos laid = FAR_INSIDE.above();
        helper.assertTrue(helper.getBlockState(laid).is(GooBlocks.KINETIC_SLICK.get()),
                String.format(NOT_LAID_FAR_INSIDE, hold, laid, range, helper.getBlockState(laid)));
        for (int east = 0; east <= FAR_STRIP_EAST; east++) {
            BlockPos stone = helper.absolutePos(FAR_STREAMER_POS.below().east(east));
            AABB top = new AABB(stone.getX(), stone.getY() + 1, stone.getZ(),
                    stone.getX() + 1, stone.getY() + 1, stone.getZ() + 1);
            double nearest = Math.sqrt(top.distanceToSqr(eye));
            BlockPos bare = FAR_STREAMER_POS.east(east);
            if (nearest > range) {
                helper.assertTrue(helper.getBlockState(bare).isAir(),
                        String.format(LAID_PAST_RANGE, bare, nearest, range, helper.getBlockState(bare)));
            }
        }
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
        helper.runAfterDelay(hold + 1, () -> {
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
        KnownRecipes.teachRequires(streamer, slick(helper));
        return streamer;
    }
}
