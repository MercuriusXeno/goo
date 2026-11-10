package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Kinetic's Grab, driven through the channel's stream tick
 * and the throw a left click sends: a held zombie loses its target and
 * hangs off the ground in front of the player, looking down draws it
 * closer, a throw sends it along the look (decision
 * grab-holds-and-throws-a-physics-body), and an item entity flies to the
 * player and into the inventory instead (decision
 * grab-pulls-an-item-to-you-and-picks-it-up). Every tick names the entity
 * the glove outlines, and Grab takes that one, off the look or not, and
 * refuses a sent player or boss (decision outlined-target-is-the-one-pick).
 */
@RegistersGameTests
public final class GrabChannelTests {
    private static final String GRAB_SUSPENDS_THE_ZOMBIE = "grab_suspends_the_zombie";
    private static final String GRAB_PITCH_DRAWS_CLOSER = "grab_pitch_draws_closer";
    private static final String GRAB_THROWS_ALONG_THE_LOOK = "grab_throws_along_the_look";
    private static final String GRAB_PULLS_AN_ITEM_INTO_THE_INVENTORY = "grab_pulls_an_item_into_the_inventory";
    private static final String GRAB_THROW_FLIES_WHILE_THE_CHANNEL_HOLDS = "grab_throw_flies_while_the_channel_holds";
    private static final String GRAB_SECOND_CLICK_KEEPS_THE_THROW_FLYING = "grab_second_click_keeps_the_throw_flying";
    private static final String GRAB_HOLDS_THE_SENT_PIG_OFF_THE_LOOK = "grab_holds_the_sent_pig_off_the_look";
    private static final String GRAB_REFUSES_A_SENT_PLAYER = "grab_refuses_a_sent_player";
    private static final String GRAB_REFUSES_A_SENT_BOSS = "grab_refuses_a_sent_boss";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GRAB_SUSPENDS_THE_ZOMBIE, GrabChannelTests::grabSuspendsTheZombie);
        GooTestFunctions.registerTest(registrar, GRAB_PITCH_DRAWS_CLOSER, GrabChannelTests::grabPitchDrawsCloser);
        GooTestFunctions.registerTest(registrar, GRAB_THROWS_ALONG_THE_LOOK, GrabChannelTests::grabThrowsAlongTheLook);
        GooTestFunctions.registerTest(registrar, GRAB_PULLS_AN_ITEM_INTO_THE_INVENTORY,
                GrabChannelTests::grabPullsAnItemIntoTheInventory);
        GooTestFunctions.registerTest(registrar, GRAB_THROW_FLIES_WHILE_THE_CHANNEL_HOLDS,
                GrabChannelTests::grabThrowFliesWhileTheChannelHolds);
        GooTestFunctions.registerTest(registrar, GRAB_SECOND_CLICK_KEEPS_THE_THROW_FLYING,
                GrabChannelTests::grabSecondClickKeepsTheThrowFlying);
        GooTestFunctions.registerTest(registrar, GRAB_HOLDS_THE_SENT_PIG_OFF_THE_LOOK,
                GrabChannelTests::grabHoldsTheSentPigOffTheLook);
        GooTestFunctions.registerTest(registrar, GRAB_REFUSES_A_SENT_PLAYER, GrabChannelTests::grabRefusesASentPlayer);
        GooTestFunctions.registerTest(registrar, GRAB_REFUSES_A_SENT_BOSS, GrabChannelTests::grabRefusesASentBoss);
        GooTestFunctions.registerTest(registrar, GRAB_DRAWS_A_PIG_BACK_THROUGH_A_PORTAL,
                GrabChannelTests::grabDrawsAPigBackThroughAPortal);
        GooTestFunctions.registerTest(registrar, GRAB_DRAWS_THROUGH_A_PORTAL_IN_STEADY_STEPS,
                GrabChannelTests::grabDrawsThroughAPortalInSteadySteps);
        GooTestFunctions.registerTest(registrar, GRAB_DRAWS_THROUGH_A_PORTAL_IT_STANDS_BESIDE,
                GrabChannelTests::grabDrawsThroughAPortalItStandsBeside);
        GooTestFunctions.registerTest(registrar, GRAB_DRAWS_UP_OUT_OF_A_FLOOR_PORTAL_WITHOUT_A_LEAP,
                GrabChannelTests::grabDrawsUpOutOfAFloorPortalWithoutALeap);
        GooTestFunctions.registerTest(registrar, GRAB_NEVER_CYCLES_A_MOB_THROUGH_FACING_PORTALS,
                GrabChannelTests::grabNeverCyclesAMobThroughFacingPortals);
        GooTestFunctions.registerTest(registrar, GRAB_HOLDS_A_MOB_PAST_THE_PORTAL_IT_IS_AIMED_INTO,
                GrabChannelTests::grabHoldsAMobPastThePortalItIsAimedInto);
        GooTestFunctions.registerTest(registrar,
                GRAB_AIMED_THROUGH_A_PORTAL_DRAWS_THROUGH_IT_THOUGH_A_STRAIGHT_WAY_STANDS_OPEN,
                GrabChannelTests::grabAimedThroughAPortalDrawsThroughItThoughAStraightWayStandsOpen);
    }


    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 2);
    /** Three blocks beside the look line, wider than any look pick's slack. */
    private static final BlockPos OFF_THE_LOOK_POS = new BlockPos(3, 1, 5);
    /** Several blocks east of the player, on the floor, so the pull takes ticks to arrive. */
    private static final Vec3 ITEM_POS = new Vec3(5.5, 1.2, 2.5);
    /** Channel ticks enough for a pull from ITEM_POS to arrive at the pull speed. */
    private static final int PULL_TICKS = 12;
    /** Yaw looking east, along +x. */
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    /** A pitch halfway to the full draw, so the hold comes in from far toward near. */
    private static final float LOOKING_DOWN = 30f;
    private static final int HOLD_TICKS = 5;
    private static final int FLIGHT_TICKS = 2;
    private static final int HELD_GOO = 3;
    /** The ability's far hold distance, eye to the held entity's middle. */
    private static final double FAR = 4;
    private static final double SETTLED = 0.3;
    /** How far a held entity must hang above where it stood to read as lifted. */
    private static final double LIFTED = 0.4;
    /** How much closer the hold must draw at LOOKING_DOWN than level. */
    private static final double DRAWN_IN = 0.5;
    /** How far along the look a thrown entity must fly in FLIGHT_TICKS; one left alone moves none. */
    private static final double THROWN = 1.0;
    private static final double HALF = 0.5;
    private static final Identifier KINETIC_GRAB = Identifier.parse("goo:kinetic_grab");
    private static final String ABILITY_REQUIRED = "Ability registry must hold kinetic_grab";

    private GrabChannelTests() {
    }

    /**
     * A mob holding the player as its target loses it on the grab and hangs
     * in front of the player at the far distance, off the ground, its AI suspended.
     *
     * @param helper the gametest helper
     */
    public static void grabSuspendsTheZombie(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setTarget(player);
        double stoodAt = zombie.getY();
        holdFor(helper, player, zombie, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.assertTrue(zombie.getTarget() == null, "A held zombie should have no target");
            helper.assertTrue(zombie.isNoAi(), "A held zombie's AI should be suspended");
            helper.assertTrue(zombie.getY() > stoodAt + LIFTED,
                    "A held zombie should hang off the ground, stood at " + stoodAt + ", hangs at " + zombie.getY());
            double distance = player.getEyePosition().distanceTo(middleOf(zombie));
            helper.assertTrue(Math.abs(distance - FAR) < SETTLED,
                    "A held zombie should hang " + FAR + " from the eye, hangs " + distance);
            leave(helper, player);
        });
    }

    /**
     * Pitching the look down while holding draws the held zombie closer to the eye.
     *
     * @param helper the gametest helper
     */
    public static void grabPitchDrawsCloser(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        AtomicReference<Double> levelDistance = new AtomicReference<>();
        holdFor(helper, player, zombie, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS, () -> {
            levelDistance.set(player.getEyePosition().distanceTo(middleOf(zombie)));
            player.setXRot(LOOKING_DOWN);
        });
        for (int held = HOLD_TICKS + 1; held <= 2 * HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player, zombie)));
        }
        helper.runAfterDelay(2 * HOLD_TICKS + 1, () -> {
            double drawnDistance = player.getEyePosition().distanceTo(middleOf(zombie));
            helper.assertTrue(drawnDistance < levelDistance.get() - DRAWN_IN,
                    "Looking down should draw the zombie in from " + levelDistance.get() + ", hangs " + drawnDistance);
            leave(helper, player);
        });
    }

    /**
     * A throw while holding sends the zombie along the look and hands its AI back.
     *
     * @param helper the gametest helper
     */
    public static void grabThrowsAlongTheLook(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        throwAfterHold(helper, player, zombie);
        helper.runAfterDelay(HOLD_TICKS + 2, () -> helper.assertTrue(!zombie.isNoAi(),
                "A thrown zombie's AI should be handed back"));
    }

    /**
     * An item entity the channel strikes flies to the player each channel
     * tick, held by no hold, and the player's own tick picks it up on arrival.
     * The player ticks through doTick, the way the connection would.
     * decision grab-pulls-an-item-to-you-and-picks-it-up
     *
     * @param helper the gametest helper
     */
    public static void grabPullsAnItemIntoTheInventory(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Vec3 itemAt = helper.absoluteVec(ITEM_POS);
        ItemEntity item = new ItemEntity(helper.getLevel(), itemAt.x, itemAt.y, itemAt.z,
                new ItemStack(Items.SLIME_BALL));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, item.position());
        AtomicReference<Double> lastDistance = new AtomicReference<>(player.getEyePosition().distanceTo(item.position()));
        for (int pulled = 1; pulled <= PULL_TICKS; pulled++) {
            int tick = pulled;
            helper.runAfterDelay(tick, () -> {
                // The item moves on its own entity tick, after this one, so the last channel tick's pull reads here.
                if (tick > 1 && item.isAlive()) {
                    double distance = player.getEyePosition().distanceTo(item.position());
                    helper.assertTrue(distance < lastDistance.get(), "A pulled item should close on the player each tick, was "
                            + lastDistance.get() + ", is " + distance);
                    lastDistance.set(distance);
                }
                GooStreamHandler.streamTick(player, holdTick(player, item));
                player.doTick();
                helper.assertTrue(GrabEvents.heldBy(player) == null, "A pulled item should stand unheld");
                if (tick == 1) {
                    helper.assertTrue(player.getInventory().countItem(Items.SLIME_BALL) == 0,
                            "The item should still be flying after the first tick");
                }
            });
        }
        helper.runAfterDelay(PULL_TICKS + 1, () -> {
            helper.assertTrue(player.getInventory().countItem(Items.SLIME_BALL) == 1,
                    "The pulled item should land in the inventory, item " + (item.isAlive() ? "still stands "
                            + player.getEyePosition().distanceTo(item.position()) + " from the eye" : "gone"));
            leave(helper, player);
        });
    }

    /**
     * A left click thrown while right click still holds the channel sends the
     * zombie along the look, and the channel ticks after the throw lift it no
     * more, so the throw is not undone by a re-grab
     * (decision diagnose-and-correct-grabs-left-click-throw).
     *
     * @param helper the gametest helper
     */
    public static void grabThrowFliesWhileTheChannelHolds(GameTestHelper helper) {
        throwWhileTheChannelHolds(helper, false);
    }

    /**
     * A second left click after the throw, still within the same right-click
     * press, throws nothing and leaves the press spent, so the channel ticks
     * after it lift the flying zombie no more
     * (decision diagnose-and-correct-grabs-left-click-throw).
     *
     * @param helper the gametest helper
     */
    public static void grabSecondClickKeepsTheThrowFlying(GameTestHelper helper) {
        throwWhileTheChannelHolds(helper, true);
    }

    /**
     * Holds a zombie, throws it on the tick after the hold, optionally clicks
     * again the tick after that, keeps the channel ticking throughout, and
     * checks the zombie flew along the look and stands unheld.
     */
    private static void throwWhileTheChannelHolds(GameTestHelper helper, boolean clicksAgain) {
        ServerPlayer player = grabber(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        AtomicReference<Vec3> heldAt = new AtomicReference<>();
        holdFor(helper, player, zombie, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            heldAt.set(zombie.position());
            helper.assertTrue(GrabEvents.throwHeld(player), "The player should hold the zombie to throw it");
        });
        if (clicksAgain) {
            helper.runAfterDelay(HOLD_TICKS + 2, () -> helper.assertTrue(!GrabEvents.throwHeld(player),
                    "A second click after the throw should hold nothing to throw"));
        }
        for (int held = HOLD_TICKS + 1; held <= HOLD_TICKS + 1 + FLIGHT_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player, zombie)));
        }
        helper.runAfterDelay(HOLD_TICKS + 2 + FLIGHT_TICKS, () -> {
            double along = zombie.position().subtract(heldAt.get()).dot(player.getLookAngle());
            helper.assertTrue(along > THROWN, "A zombie thrown while the channel holds should fly along the look, flew "
                    + along + (GrabEvents.heldBy(player) == zombie ? ", held again" : ", unheld"));
            helper.assertTrue(GrabEvents.heldBy(player) == null, "The channel should not lift the thrown zombie again");
            leave(helper, player);
        });
    }

    /**
     * Holds for HOLD_TICKS from the second tick, throws on the tick after,
     * and checks the entity flew along the look FLIGHT_TICKS later.
     */
    private static void throwAfterHold(GameTestHelper helper, ServerPlayer player, Entity thrown) {
        AtomicReference<Vec3> heldAt = new AtomicReference<>();
        for (int held = 2; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player, thrown)));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            heldAt.set(thrown.position());
            helper.assertTrue(GrabEvents.throwHeld(player), "The player should hold the entity to throw it");
        });
        helper.runAfterDelay(HOLD_TICKS + 1 + FLIGHT_TICKS, () -> {
            double along = thrown.position().subtract(heldAt.get()).dot(player.getLookAngle());
            helper.assertTrue(along > THROWN, "The thrown entity should fly along the look, flew " + along + " to y " + thrown.getY()
                    + (thrown.onGround() ? " on the ground" : " in the air"));
            leave(helper, player);
        });
    }

    /**
     * A pig standing off the look but sent as the outlined target is held.
     *
     * @param helper the gametest helper
     */
    public static void grabHoldsTheSentPigOffTheLook(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Pig pig = helper.spawn(EntityType.PIG, OFF_THE_LOOK_POS);
        holdFor(helper, player, pig, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.assertTrue(GrabEvents.heldBy(player) == pig,
                    "The pig sent as the outlined target should be held, held " + GrabEvents.heldBy(player));
            leave(helper, player);
        });
    }

    /**
     * A sent player is refused and nothing is held, though a zombie stands on the look.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void grabRefusesASentPlayer(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        ServerPlayer bystander = helper.makeMockServerPlayerInLevel();
        Vec3 beside = Vec3.atBottomCenterOf(helper.absolutePos(OFF_THE_LOOK_POS));
        bystander.setPos(beside.x, beside.y, beside.z);
        refusesTheSent(helper, player, bystander,
                () -> helper.getLevel().getServer().getPlayerList().remove(bystander));
    }

    /**
     * A sent boss is refused and nothing is held, though a zombie stands on the look.
     *
     * @param helper the gametest helper
     */
    public static void grabRefusesASentBoss(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        WitherBoss wither = helper.spawn(EntityType.WITHER, OFF_THE_LOOK_POS);
        wither.setNoAi(true);
        refusesTheSent(helper, player, wither, wither::discard);
    }

    private static final String GRAB_DRAWS_A_PIG_BACK_THROUGH_A_PORTAL = "grab_draws_a_pig_back_through_a_portal";
    /** The wall between the player and the pig, its west face the plane x 6. */
    private static final int WALL_X = 6;
    /** The near portal's lower block, on the wall's west face before the player. */
    private static final BlockPos NEAR_PORTAL = new BlockPos(WALL_X, 1, 2);
    /** The far portal's lower block, on the wall's east face two blocks north. */
    private static final BlockPos FAR_PORTAL = new BlockPos(WALL_X, 1, 0);
    /** The pig, east of the wall before the far portal, walled off from the player's eye. */
    private static final BlockPos PAST_THE_WALL = new BlockPos(WALL_X + 2, 1, 0);
    /** Channel ticks enough to draw the pig to the far portal, through it, and in. */
    private static final int THROUGH_TICKS = 10;

    /**
     * A pig behind a wall, sent as the target through a linked pair whose
     * near portal the player faces, is drawn back through the pair to hang on
     * the player's side, rather than dragged at the wall between
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void grabDrawsAPigBackThroughAPortal(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        for (int z = -1; z <= 3; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        Portal near = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(NEAR_PORTAL)), Direction.WEST,
                Direction.UP);
        Portal far = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FAR_PORTAL)), Direction.EAST,
                Direction.UP);
        PortalChoice.place(level, player.getUUID(), near);
        PortalChoice.place(level, player.getUUID(), far);
        Pig pig = helper.spawn(EntityType.PIG, PAST_THE_WALL);
        holdFor(helper, player, pig, THROUGH_TICKS);
        helper.runAfterDelay(THROUGH_TICKS + 1, () -> {
            helper.assertTrue(GrabEvents.heldBy(player) == pig, "The pig behind the wall should be held through the "
                    + "pair, held " + GrabEvents.heldBy(player));
            helper.assertTrue(pig.getX() < helper.absolutePos(NEAR_PORTAL).getX(), "The pig should be drawn back "
                    + "through the pair to the player's side of the wall, stands at " + helper.relativeVec(pig.position())
                    );
            PortalChoice.breakAt(level, near.block().pos(), near.face());
            PortalChoice.breakAt(level, far.block().pos(), far.face());
            leave(helper, player);
        });
    }

    private static final String GRAB_DRAWS_THROUGH_A_PORTAL_IN_STEADY_STEPS =
            "grab_draws_through_a_portal_in_steady_steps";
    private static final String GRAB_DRAWS_THROUGH_A_PORTAL_IT_STANDS_BESIDE =
            "grab_draws_through_a_portal_it_stands_beside";
    /** The farthest a drawn body moves in one tick on its way to a portal: Grab's pull speed, with slack. */
    private static final double STEADY_STEP = 1.05;

    /**
     * A pig drawn back through a pair moves to the far portal in steps no
     * longer than Grab's pull speed, carrying that motion, rather than one
     * jump the client never shows arriving, so it is seen to slide into the
     * portal and on out of its partner
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void grabDrawsThroughAPortalInSteadySteps(GameTestHelper helper) {
        drawsThroughInSteadySteps(helper, PAST_THE_WALL);
    }

    /** How far from a portal's middle a body carried out of it first stands: its own half size and the margin. */
    private static final double CAME_OUT_REACH = 1.2;
    /** Before the far portal, near enough that the portal's blocks stand open to the pig. */
    private static final BlockPos BESIDE_THE_FAR_PORTAL = new BlockPos(WALL_X + 1, 1, 0);

    /**
     * A pig standing right before the far portal, where the portal's blocks
     * stand open to it, is drawn through the pair in steady steps all the
     * same, never jumped straight at the hold point through the opened block
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void grabDrawsThroughAPortalItStandsBeside(GameTestHelper helper) {
        drawsThroughInSteadySteps(helper, BESIDE_THE_FAR_PORTAL);
    }

    private static final String GRAB_DRAWS_UP_OUT_OF_A_FLOOR_PORTAL_WITHOUT_A_LEAP =
            "grab_draws_up_out_of_a_floor_portal_without_a_leap";
    /** The floor portal's first block, flat on the floor before the player, running east. */
    private static final BlockPos FLOOR_PORTAL = new BlockPos(3, 0, 2);

    /**
     * A pig drawn back through a pair whose near portal lies on the floor
     * comes up out of it moving no way of its own, rather than with the leap
     * a floor portal gives a walker to step off it, so Grab draws it to the
     * hold point without flinging it up first
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void grabDrawsUpOutOfAFloorPortalWithoutALeap(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        for (int z = -1; z <= 3; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
        helper.setBlock(FLOOR_PORTAL, Blocks.STONE);
        helper.setBlock(FLOOR_PORTAL.east(), Blocks.STONE);
        ServerLevel level = helper.getLevel();
        Portal floor = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FLOOR_PORTAL)), Direction.UP,
                Direction.EAST);
        Portal far = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FAR_PORTAL)), Direction.EAST,
                Direction.UP);
        PortalChoice.place(level, player.getUUID(), floor);
        PortalChoice.place(level, player.getUUID(), far);
        Pig pig = helper.spawn(EntityType.PIG, PAST_THE_WALL);
        double wall = helper.absolutePos(NEAR_PORTAL).getX();
        java.util.List<Vec3> motions = new java.util.ArrayList<>();
        java.util.List<Vec3> cameOut = new java.util.ArrayList<>();
        for (int held = 1; held <= THROUGH_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                if (pig.getX() < wall) {
                    motions.add(pig.getDeltaMovement());
                    cameOut.add(pig.getBoundingBox().getCenter());
                }
                GooStreamHandler.streamTick(player, holdTick(player, pig));
            });
        }
        helper.runAfterDelay(THROUGH_TICKS + 1, () -> {
            helper.assertTrue(!motions.isEmpty(), "The pig should come up out of the floor portal, stands at "
                    + helper.relativeVec(pig.position()));
            Vec3 mouth = floor.gate().center();
            helper.assertTrue(cameOut.get(0).distanceTo(mouth) < CAME_OUT_REACH, "The pig should first stand on the "
                    + "player's side at the floor portal, carried through the pair, not jumped through the wall; stood "
                    + "at " + helper.relativeVec(cameOut.get(0)));
            helper.assertTrue(motions.get(0).y() <= 0, "The pig should come up out of the floor portal with no leap "
                    + "of its own, moving " + motions.get(0));
            PortalChoice.breakAt(level, floor.block().pos(), floor.face());
            PortalChoice.breakAt(level, far.block().pos(), far.face());
            leave(helper, player);
        });
    }

    private static final String GRAB_AIMED_THROUGH_A_PORTAL_DRAWS_THROUGH_IT_THOUGH_A_STRAIGHT_WAY_STANDS_OPEN =
            "grab_aimed_through_a_portal_draws_through_it_though_a_straight_way_stands_open";
    /** The near pillar's column, the bay's last inside the barrier ring, past the hold point. */
    private static final int PILLAR_X = 5;
    /** The near pillar's lower block, the near portal on its west face before the player. */
    private static final BlockPos NEAR_PILLAR = new BlockPos(PILLAR_X, 1, 2);
    /** The far pillar's lower block, three blocks south and one nearer, the far portal on its east face. */
    private static final BlockPos FAR_PILLAR = new BlockPos(PILLAR_X - 1, 1, 5);
    /** Where Grab holds a body's middle for the player at STAND_POS looking level east: the far distance on. */
    private static final Vec3 HOLD_MIDDLE = new Vec3(4.5, 2.62, 2.5);
    /** The zombie, east of the far pillar on the line out of the far portal, in plain sight of the hold point. */
    private static final BlockPos IN_PLAIN_SIGHT = new BlockPos(PILLAR_X, 1, 5);

    /**
     * A zombie Grab is aimed at through a portal is drawn through that
     * portal, though a straight way to the hold point stands open beside the
     * pillars, rather than pulled sideways across to it
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void grabAimedThroughAPortalDrawsThroughItThoughAStraightWayStandsOpen(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        for (BlockPos pillar : java.util.List.of(NEAR_PILLAR, FAR_PILLAR)) {
            helper.setBlock(pillar, Blocks.STONE);
            helper.setBlock(pillar.above(), Blocks.STONE);
        }
        ServerLevel level = helper.getLevel();
        Portal near = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(NEAR_PILLAR)), Direction.WEST,
                Direction.UP);
        Portal far = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FAR_PILLAR)), Direction.EAST,
                Direction.UP);
        PortalChoice.place(level, player.getUUID(), near);
        PortalChoice.place(level, player.getUUID(), far);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, IN_PLAIN_SIGHT);
        Vec3 holdMiddle = helper.absoluteVec(HOLD_MIDDLE);
        net.minecraft.world.phys.BlockHitResult straight = level.clip(new net.minecraft.world.level.ClipContext(
                zombie.getBoundingBox().getCenter(), holdMiddle, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        helper.assertTrue(straight.getType() == net.minecraft.world.phys.HitResult.Type.MISS, "The layout should "
                + "leave a straight way open from the zombie to the hold point, blocked at "
                + helper.relativeVec(straight.getLocation()) + ", " + level.getBlockState(straight.getBlockPos()));
        java.util.List<Vec3> cameOut = new java.util.ArrayList<>();
        for (int held = 1; held <= THROUGH_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                cameOut.add(zombie.getBoundingBox().getCenter());
                GooStreamHandler.streamTick(player, holdTick(player, zombie));
            });
        }
        helper.runAfterDelay(THROUGH_TICKS + 1, () -> {
            // Off the far portal's column, the zombie first stands either out of the near portal, within its width,
            // or somewhere across the open ground between, pulled sideways.
            double farColumn = far.gate().blocks().minZ - HALF;
            Vec3 left = cameOut.stream().filter(middle -> middle.z() < farColumn).findFirst().orElseThrow(
                    () -> new AssertionError("The zombie should leave the far portal"));
            AABB nearMouth = near.gate().blocks();
            helper.assertTrue(left.z() >= nearMouth.minZ && left.z() <= nearMouth.maxZ, "The zombie aimed at "
                    + "through the portal should come out of the near portal, carried through the pair, not pulled "
                    + "sideways across; went " + cameOut.stream().map(helper::relativeVec).toList());
            PortalChoice.breakAt(level, near.block().pos(), near.face());
            PortalChoice.breakAt(level, far.block().pos(), far.face());
            leave(helper, player);
        });
    }

    private static final String GRAB_NEVER_CYCLES_A_MOB_THROUGH_FACING_PORTALS =
            "grab_never_cycles_a_mob_through_facing_portals";
    /** The block the west portal of a facing pair stands on, its face the plane x 2. */
    private static final BlockPos FACING_WEST_WALL = new BlockPos(1, 1, 2);
    /** The block the east portal of the pair stands on, its face the plane x 4. */
    private static final BlockPos FACING_EAST_WALL = new BlockPos(4, 1, 2);
    /** Between the pair: where the player stands, facing the west portal, its hold point behind that wall. */
    private static final BlockPos BETWEEN = new BlockPos(3, 1, 2);
    /** Yaw looking west, along -x. */
    private static final float FACING_WEST = 90f;
    /** Ticks enough for a cycling pig to go round several times. */
    private static final int CYCLE_TICKS = 20;

    /**
     * A pig held between two portals facing each other, with the hold point
     * behind one portal's wall, is carried through the pair at most once,
     * never cycled through it tick after tick: the far leg out of the pair to
     * the hold point is walled, so no way through the pair reaches it
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void grabNeverCyclesAMobThroughFacingPortals(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(BETWEEN));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_WEST);
        player.setYHeadRot(FACING_WEST);
        for (BlockPos wall : java.util.List.of(FACING_WEST_WALL, FACING_EAST_WALL)) {
            for (int z = 1; z <= 3; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(wall.getX(), y, z), Blocks.STONE);
                }
            }
        }
        ServerLevel level = helper.getLevel();
        Portal west = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FACING_WEST_WALL)), Direction.EAST,
                Direction.UP);
        Portal east = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FACING_EAST_WALL)), Direction.WEST,
                Direction.UP);
        PortalChoice.place(level, player.getUUID(), west);
        PortalChoice.place(level, player.getUUID(), east);
        Pig pig = helper.spawn(EntityType.PIG, BETWEEN);
        java.util.List<Vec3> path = new java.util.ArrayList<>();
        path.add(pig.position());
        for (int held = 1; held <= CYCLE_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, holdTick(player, pig));
                path.add(pig.position());
            });
        }
        helper.runAfterDelay(CYCLE_TICKS + 1, () -> {
            // Each time the pig stands past the west portal's face it has gone into the pair once more.
            double westFace = helper.absolutePos(FACING_WEST_WALL).getX() + 1;
            long intoThePair = path.stream().filter(feet -> feet.x() < westFace).count();
            helper.assertTrue(intoThePair <= 1, "The pig should go into the facing pair at most once, went in "
                    + intoThePair + " times along " + path.stream().map(helper::relativeVec).toList());
            PortalChoice.breakAt(level, west.block().pos(), west.face());
            PortalChoice.breakAt(level, east.block().pos(), east.face());
            leave(helper, player);
        });
    }

    private static final String GRAB_HOLDS_A_MOB_PAST_THE_PORTAL_IT_IS_AIMED_INTO =
            "grab_holds_a_mob_past_the_portal_it_is_aimed_into";
    /** The west portal's block, its face the plane x 1, facing east. */
    private static final BlockPos AIMED_INTO = new BlockPos(0, 1, 2);
    /** The east portal's block, its face the plane x 4, facing west, three blocks across from the west one. */
    private static final BlockPos BEHIND_THE_HOLDER = new BlockPos(4, 1, 2);
    /** Where the player stands, half a block before the east portal, looking west into the west one. */
    private static final Vec3 BEFORE_THE_EAST_PORTAL = new Vec3(3.5, 1, 2.5);
    /** Where the pig starts, between the pair. */
    private static final BlockPos BETWEEN_THE_PAIR = new BlockPos(2, 1, 2);
    /**
     * Where the look, four blocks long, ends carried through the pair: two and a half to the west face, the
     * last block and a half on west out of the east face.
     */
    private static final double HELD_PAST_THE_PORTAL_X = 2.5;
    /** Ticks the pig is given to come to its hold, then ticks it is watched held. */
    private static final int SETTLE_TICKS = 10;
    private static final int WATCHED_TICKS = 10;
    /** How near the hold the pig's middle stands while held still. */
    private static final double AT_THE_HOLD = 0.3;

    /**
     * A pig held by a look aimed into a portal facing its partner is held
     * where the look ends carried through the pair, as seen through the
     * portal, and stays there, rather than dragged at the wall behind the
     * portal it is aimed into and carried round the pair every tick, as the
     * operator found a held slime looping
     * (decision aim-and-shots-carry-through-linked-gates).
     *
     * @param helper the gametest helper
     */
    public static void grabHoldsAMobPastThePortalItIsAimedInto(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Vec3 stand = helper.absoluteVec(BEFORE_THE_EAST_PORTAL);
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_WEST);
        player.setYHeadRot(FACING_WEST);
        for (BlockPos wall : java.util.List.of(AIMED_INTO, BEHIND_THE_HOLDER)) {
            for (int z = 1; z <= 3; z++) {
                for (int y = 1; y <= 3; y++) {
                    helper.setBlock(new BlockPos(wall.getX(), y, z), Blocks.STONE);
                }
            }
        }
        ServerLevel level = helper.getLevel();
        Portal west = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(AIMED_INTO)), Direction.EAST,
                Direction.UP);
        Portal east = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(BEHIND_THE_HOLDER)),
                Direction.WEST, Direction.UP);
        PortalChoice.place(level, player.getUUID(), west);
        PortalChoice.place(level, player.getUUID(), east);
        Pig pig = helper.spawn(EntityType.PIG, BETWEEN_THE_PAIR);
        java.util.List<Vec3> held = new java.util.ArrayList<>();
        for (int tick = 1; tick <= SETTLE_TICKS + WATCHED_TICKS; tick++) {
            boolean watched = tick > SETTLE_TICKS;
            helper.runAfterDelay(tick, () -> {
                GooStreamHandler.streamTick(player, holdTick(player, pig));
                if (watched) {
                    held.add(helper.relativeVec(middleOf(pig)));
                }
            });
        }
        helper.runAfterDelay(SETTLE_TICKS + WATCHED_TICKS + 1, () -> {
            boolean still = held.stream().allMatch(middle -> Math.abs(middle.x() - HELD_PAST_THE_PORTAL_X) < AT_THE_HOLD);
            PortalChoice.breakAt(level, west.block().pos(), west.face());
            PortalChoice.breakAt(level, east.block().pos(), east.face());
            helper.assertTrue(still, "The pig should be held still at x " + HELD_PAST_THE_PORTAL_X
                    + ", past the portal it is aimed into, its middle stood at " + held);
            leave(helper, player);
        });
    }

    private static void drawsThroughInSteadySteps(GameTestHelper helper, BlockPos pigAt) {
        ServerPlayer player = grabber(helper);
        for (int z = -1; z <= 3; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        Portal near = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(NEAR_PORTAL)), Direction.WEST,
                Direction.UP);
        Portal far = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FAR_PORTAL)), Direction.EAST,
                Direction.UP);
        PortalChoice.place(level, player.getUUID(), near);
        PortalChoice.place(level, player.getUUID(), far);
        Pig pig = helper.spawn(EntityType.PIG, pigAt);
        double wall = helper.absolutePos(NEAR_PORTAL).getX();
        java.util.List<Vec3> path = new java.util.ArrayList<>();
        java.util.List<Vec3> cameOut = new java.util.ArrayList<>();
        path.add(pig.position());
        for (int held = 1; held <= THROUGH_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                if (pig.getX() < wall) {
                    cameOut.add(pig.getBoundingBox().getCenter());
                }
                GooStreamHandler.streamTick(player, holdTick(player, pig));
                path.add(pig.position());
            });
        }
        helper.runAfterDelay(THROUGH_TICKS + 1, () -> {
            helper.assertTrue(!cameOut.isEmpty() && cameOut.get(0).distanceTo(near.gate().center()) < CAME_OUT_REACH,
                    "The pig should first stand on the player's side at the near portal, carried through the pair, "
                            + "not jumped through the wall; stood at " + cameOut.stream().map(helper::relativeVec)
                            .toList() + " along " + path.stream().map(helper::relativeVec).toList());
            // The far portal's face is the wall's east face; a body east of it has yet to go through.
            double farFace = wall + 1;
            long beforeThePortal = path.stream().filter(feet -> feet.x() > farFace).count();
            helper.assertTrue(beforeThePortal > 1 || pigAt.equals(BESIDE_THE_FAR_PORTAL), "The pig should be seen "
                    + "drawn toward the portal over more than one tick, not jumped into it, along "
                    + path.stream().map(helper::relativeVec).toList());
            for (int at = 1; at < path.size(); at++) {
                double step = path.get(at).distanceTo(path.get(at - 1));
                helper.assertTrue(path.get(at).x() <= farFace || step <= STEADY_STEP,
                        "On its way to the portal the pig should move in steady steps, moved " + step + " on tick "
                                + at + " along " + path.stream().map(helper::relativeVec).toList());
            }
            helper.assertTrue(pig.getX() < wall, "The pig should come through to the player's side, stands at "
                    + helper.relativeVec(pig.position()));
            PortalChoice.breakAt(level, near.block().pos(), near.face());
            PortalChoice.breakAt(level, far.block().pos(), far.face());
            leave(helper, player);
        });
    }

    private static void refusesTheSent(GameTestHelper helper, ServerPlayer player, Entity sent, Runnable cleanUp) {
        holdFor(helper, player, sent, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.assertTrue(GrabEvents.heldBy(player) == null, "A sent " + sent.getType()
                    + " should be refused and nothing held, held " + GrabEvents.heldBy(player));
            cleanUp.run();
            leave(helper, player);
        });
    }

    private static void holdFor(GameTestHelper helper, ServerPlayer player, Entity outlined, int ticks) {
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player, outlined)));
        }
    }

    private static GooStreamPayload holdTick(ServerPlayer player, Entity outlined) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), KINETIC_GRAB.toString(),
                eye, eye.add(player.getLookAngle().scale(FAR))).targeting(outlined.getId());
    }

    private static Vec3 middleOf(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() * HALF, 0);
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer grabber(GameTestHelper helper) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setXRot(LEVEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.KINETIC, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, grab);
        return player;
    }
}
