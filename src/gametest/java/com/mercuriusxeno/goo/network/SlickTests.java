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
            Walker onSlick = walkTheLane(helper, slickStart);
            Walker onIce = walkTheLane(helper, iceStart);
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
            Walker walker = walkTheLane(helper, slickStart);
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
        for (int east = STRIP_WEST; east <= STRIP_EAST; east++) {
            helper.setBlock(SLICKED_STONE.east(east).below(), Blocks.BEDROCK);
            helper.setBlock(SLICKED_STONE.east(east), Blocks.STONE);
        }
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
     * @param helper the gametest helper
     * @param start  the lane's first slippery block
     * @return the walker
     */
    private static Walker walkTheLane(GameTestHelper helper, BlockPos start) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, start.west(2).above());
        helper.walkTo(husk, start.east(STRIP_EAST - STRIP_WEST).above(), 1f);
        return new Walker(husk);
    }

    /**
     * A husk walking its lane, with its speed on its last stone tick and on
     * its slippery stretch.
     */
    private static final class Walker {
        private final Mob mob;
        private double onStone = -1;
        private int slipperyTicks;
        private double firstOnSlippery = -1;
        private double onSlippery = -1;

        Walker(Mob mob) {
            this.mob = mob;
        }

        void watch(GameTestHelper helper) {
            if (onSlippery >= 0) {
                return;
            }
            double speed = mob.getDeltaMovement().horizontalDistance();
            if (helper.getLevel().getBlockState(mob.getOnPos()).is(Blocks.STONE)) {
                onStone = speed;
                return;
            }
            if (onStone >= 0 && ++slipperyTicks == 1) {
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
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.KINETIC);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STREAMER_POS));
        streamer.setPos(stand.x, stand.y, stand.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(AT_THE_STONE);
        AbilityDefinition slick = AbilityRegistry.of(helper.getLevel()).getAbility(SLICK);
        helper.assertTrue(slick != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(streamer, slick);
        return streamer;
    }
}
