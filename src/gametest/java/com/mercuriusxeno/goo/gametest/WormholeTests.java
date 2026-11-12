package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.OpenHoles;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Gametests for Quantum's tunnel, a wormhole: thrown at a wall, it opens a
 * mouth on the struck face and one at the first opening ahead a player fits
 * in, a 3x3 or a 2x3 within it; stepping into either comes out of the other
 * until it closes after ten seconds; thrown where no opening lies within its
 * reach it is refused at the face and costs nothing; no block is made
 * see-through (decision hole-closes-the-space-to-an-opening-ahead).
 */
@RegistersGameTests
public final class WormholeTests {
    private static final String THROUGH_TO_A_FULL_OPENING = "tunnel_carries_through_to_a_full_opening";
    private static final String THROUGH_TO_A_NARROW_OPENING = "tunnel_carries_through_to_a_narrow_opening";
    private static final String REFUSED_WITH_NO_OPENING = "tunnel_refuses_with_no_opening";
    private static final String USES_A_ONE_BY_TWO_WALL = "tunnel_uses_a_one_by_two_wall";
    private static final String DROPS_THROUGH_A_ONE_BY_ONE_FLOOR = "tunnel_drops_through_a_one_by_one_floor";
    private static final String REACHES_THROUGH_A_THICK_WALL = "tunnel_reaches_through_a_thick_wall";
    private static final String HOLDS_OPEN_WHILE_OCCUPIED = "tunnel_holds_open_while_occupied";
    private static final String CRAWLS_THROUGH_A_ONE_BY_ONE_WALL = "tunnel_crawls_through_a_one_by_one_wall";
    private static final String STANDS_BEFORE_A_FULL_MOUTH = "tunnel_stands_before_a_full_mouth";
    private static final String DISMISSED_BY_A_HIT = "tunnel_dismissed_by_a_hit";
    private static final String REFUSED_ON_GLASS = "tunnel_refused_on_glass";
    private static final String CENTRES_ON_THE_STRUCK_BLOCK = "tunnel_centres_on_the_struck_block";
    private static final String KEEPS_OFF_A_PORTAL_ON_THE_STRUCK_FACE = "tunnel_keeps_off_a_portal_on_the_struck_face";
    private static final String KEEPS_OFF_A_PORTAL_ON_THE_FAR_FACE = "tunnel_keeps_off_a_portal_on_the_far_face";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, KEEPS_OFF_A_PORTAL_ON_THE_STRUCK_FACE,
                WormholeTests::keepsOffAPortalOnTheStruckFace);
        GooTestFunctions.registerTest(registrar, KEEPS_OFF_A_PORTAL_ON_THE_FAR_FACE,
                WormholeTests::keepsOffAPortalOnTheFarFace);
        GooTestFunctions.registerTest(registrar, THROUGH_TO_A_FULL_OPENING, WormholeTests::throughToAFullOpening);
        GooTestFunctions.registerTest(registrar, THROUGH_TO_A_NARROW_OPENING, WormholeTests::throughToANarrowOpening);
        GooTestFunctions.registerTest(registrar, REFUSED_WITH_NO_OPENING, WormholeTests::refusedWithNoOpening);
        GooTestFunctions.registerTest(registrar, CLOSES_AFTER_ITS_LIFETIME, WormholeTests::closesAfterItsLifetime);
        GooTestFunctions.registerTest(registrar, USES_A_ONE_BY_TWO_WALL, WormholeTests::usesAOneByTwoWall);
        GooTestFunctions.registerTest(registrar, DROPS_THROUGH_A_ONE_BY_ONE_FLOOR,
                WormholeTests::dropsThroughAOneByOneFloor);
        GooTestFunctions.registerTest(registrar, REACHES_THROUGH_A_THICK_WALL, WormholeTests::reachesThroughAThickWall);
        GooTestFunctions.registerTest(registrar, HOLDS_OPEN_WHILE_OCCUPIED, WormholeTests::holdsOpenWhileOccupied);
        GooTestFunctions.registerTest(registrar, CRAWLS_THROUGH_A_ONE_BY_ONE_WALL,
                WormholeTests::crawlsThroughAOneByOneWall);
        GooTestFunctions.registerTest(registrar, STANDS_BEFORE_A_FULL_MOUTH, WormholeTests::standsBeforeAFullMouth);
        GooTestFunctions.registerTest(registrar, DISMISSED_BY_A_HIT, WormholeTests::dismissedByAHit);
        GooTestFunctions.registerTest(registrar, REFUSED_ON_GLASS, WormholeTests::refusedOnGlass);
        GooTestFunctions.registerTest(registrar, CENTRES_ON_THE_STRUCK_BLOCK,
                WormholeTests::centresOnTheStruckBlock);
        GooTestFunctions.registerTest(registrar, CROSSING_WHOOSHES_AT_BOTH_MOUTHS,
                WormholeTests::crossingWhooshesAtBothMouths);
    }

    private static final String CROSSING_WHOOSHES_AT_BOTH_MOUTHS = "hole_crossing_whooshes_at_both_mouths";

    /**
     * Going through the struck mouth plays the portals' whoosh once at the
     * struck mouth's centre and once at the far mouth's, heard by the one
     * crossing (decision wormhole-mouths-take-the-whoosh-and-the-hum).
     *
     * @param helper the gametest helper
     */
    public static void crossingWhooshesAtBothMouths(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        PacketRecorder heard = PacketRecorder.attachTo(caster);
        heard.markClientLoaded();
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            java.util.Optional<OpenHoles.Hole> hole = holeStruckAt(helper, STRUCK);
            helper.assertTrue(hole.isPresent(), "The hole should stand open on the struck face");
            crossAt(helper, caster, THROUGH_THE_FACE);
            Vec3 in = hole.get().entry().center();
            Vec3 out = hole.get().exit().center();
            helper.assertTrue(HeardSounds.at(heard, GooSoundIds.PORTAL_CROSS, in) == 1
                    && HeardSounds.at(heard, GooSoundIds.PORTAL_CROSS, out) == 1, "Going through the struck mouth "
                    + "should play the whoosh once at its centre " + in + " and once at the far mouth's " + out
                    + ", plays " + HeardSounds.describe(HeardSounds.of(heard, GooSoundIds.PORTAL_CROSS)));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static final String CLOSES_AFTER_ITS_LIFETIME = "tunnel_closes_after_its_lifetime";

    private static final Identifier QUANTUM_TUNNEL = Identifier.parse("goo:quantum_tunnel");
    /** quantum_tunnel.json's cost, which the caster holds exactly. */
    private static final int HOLE_COST = 2000;
    /** The wall block the blob strikes on its north face, the centre of the scan. */
    private static final BlockPos STRUCK = new BlockPos(2, 2, 2);
    /** Where the caster stands, north of the wall. */
    private static final Vec3 START = new Vec3(2.5, 1, 0.5);
    /** The wall's near and far z, relative: two blocks thick. */
    private static final int WALL_NEAR_Z = 2;
    private static final int WALL_FAR_Z = 3;
    /** The z, relative, of the opening past the wall. */
    private static final int OPENING_Z = 4;
    /** Feet just through the struck face, the body's middle a tenth behind it. */
    private static final Vec3 THROUGH_THE_FACE = new Vec3(2.5, 1, 2.1);
    /** Where feet come out of the far mouth, matching the step through the struck face: a tenth past the wall. */
    private static final Vec3 OUT_PAST_THE_WALL = new Vec3(2.5, 1, 4.1);
    /** Feet just back through the far mouth from the opening, the middle a tenth behind it. */
    private static final Vec3 BACK_THROUGH_THE_FAR_FACE = new Vec3(2.5, 1, 3.9);
    /** Where feet come back out before the struck face: a tenth before it. */
    private static final Vec3 BACK_BEFORE_THE_FACE = new Vec3(2.5, 1, 1.9);
    /** The column, relative, the downward throw strikes the top of. */
    private static final BlockPos COLUMN_TOP = new BlockPos(2, 0, 2);
    /** How near a spot a traveller stands to count as there. */
    private static final double THERE = 0.3;
    /** Ticks the throw is given to land and open its mouths. */
    private static final int LANDED = 30;
    /** Ticks a step into a mouth is given to carry the traveller. */
    private static final int STEP = 3;
    /** quantum_tunnel.json's lifetime: the mouths close after thirty seconds. */
    private static final int LIFETIME = 600;

    private WormholeTests() {
    }

    /**
     * Thrown at a two-thick wall with open air past it, the hole leaves the
     * caster where they stood; going through the struck face carries them on
     * out of the far mouth into the opening, and going back through the far
     * mouth carries them out before the struck face.
     *
     * @param helper the gametest helper
     */
    public static void throughToAFullOpening(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            assertAt(helper, caster, START, "The hole should carry no one the moment it lands");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(STRUCK)).is(Blocks.STONE),
                    "The struck wall should stand as it was");
            crossAt(helper, caster, THROUGH_THE_FACE);
        });
        helper.runAfterDelay(LANDED + STEP, () -> {
            assertAt(helper, caster, OUT_PAST_THE_WALL, "Going through the struck face should come out past the wall");
            crossAt(helper, caster, BACK_THROUGH_THE_FAR_FACE);
        });
        helper.runAfterDelay(LANDED + STEP + STEP, () -> {
            assertAt(helper, caster, BACK_BEFORE_THE_FACE, "Going back through the far mouth should come out before "
                    + "the struck face");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * With a stone column narrowing the opening past the wall to 2x3, going
     * through the struck face still comes out past the wall.
     *
     * @param helper the gametest helper
     */
    public static void throughToANarrowOpening(GameTestHelper helper) {
        buildWall(helper);
        for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(3, y, OPENING_Z), Blocks.STONE);
        }
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> crossAt(helper, caster, THROUGH_THE_FACE));
        helper.runAfterDelay(LANDED + STEP, () -> {
            assertAt(helper, caster, OUT_PAST_THE_WALL, "Going through the struck face should come out into the 2x3 "
                    + "opening");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Once the hole's lifetime has run, its mouths are closed: stepping
     * against the struck face carries no one.
     *
     * @param helper the gametest helper
     */
    public static void closesAfterItsLifetime(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED + LIFETIME, () -> crossAt(helper, caster, THROUGH_THE_FACE));
        helper.runAfterDelay(LANDED + LIFETIME + STEP, () -> {
            assertAt(helper, caster, THROUGH_THE_FACE, "A closed hole should carry no one");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Stands the player's feet at a spot and has the server hear what a
     * client says on the frame its eye crosses a gate there: it crossed the
     * open hole's mouth, whichever one the server finds the eye behind.
     */
    private static void crossAt(GameTestHelper helper, ServerPlayer player, Vec3 feet) {
        standAt(helper, player, feet);
        Vec3 stood = player.position();
        for (OpenHoles.Hole hole : OpenHoles.open()) {
            for (Gate mouth : List.of(hole.entry(), hole.exit())) {
                if (player.position().equals(stood)) {
                    GatePassage.announced(player, mouth, stood, Vec3.ZERO, player.getYRot(), player.getXRot());
                }
            }
        }
    }

    private static void standAt(GameTestHelper helper, ServerPlayer player, Vec3 feet) {
        Vec3 at = helper.absoluteVec(feet);
        player.teleportTo(at.x, at.y, at.z);
    }

    private static void assertAt(GameTestHelper helper, ServerPlayer player, Vec3 feet, String message) {
        Vec3 at = helper.absoluteVec(feet);
        helper.assertTrue(player.position().distanceTo(at) < THERE,
                message + ": expected " + at + ", stands at " + player.position());
    }

    /**
     * Thrown down at a stone column running to the bottom of the world, the
     * hole finds no opening within its reach: it is refused, the caster
     * stays and keeps the goo it held.
     *
     * @param helper the gametest helper
     */
    public static void refusedWithNoOpening(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = helper.absolutePos(COLUMN_TOP);
        for (int y = top.getY(); y >= level.getMinY(); y--) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlockAndUpdate(new BlockPos(top.getX() + x, y, top.getZ() + z),
                            Blocks.STONE.defaultBlockState());
                }
            }
        }
        ServerPlayer caster = caster(helper);
        Vec3 start = caster.position();
        throwAt(caster, top, Direction.UP);
        helper.assertTrue(GooSourceScanner.hasEnough(caster, GooTypes.QUANTUM, HOLE_COST),
                "A refused hole should cost nothing");
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(caster.position().distanceTo(start) < THERE,
                    "A refused hole should leave the caster where they stood, stands at " + caster.position());
            level.getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Thrown at a wall one block wide and two tall, the hole uses the space
     * it is thrown on: a 1x2 mouth on the struck face, and going through it
     * comes out past the wall.
     *
     * @param helper the gametest helper
     */
    public static void usesAOneByTwoWall(GameTestHelper helper) {
        for (int y = 1; y <= 2; y++) {
            for (int z = WALL_NEAR_Z; z <= WALL_FAR_Z; z++) {
                helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
            }
        }
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(LOW_IN_THE_SLIM_WALL), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            assertMouthSpans(helper, LOW_IN_THE_SLIM_WALL, 1, 2, 1);
            crossAt(helper, caster, THROUGH_THE_FACE);
        });
        helper.runAfterDelay(LANDED + STEP, () -> {
            assertAt(helper, caster, OUT_PAST_THE_WALL, "Going through a 1x2 hole should come out past the wall");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Thrown down at a floor over a cell only one block wide, the hole makes
     * the 1x1 the space below leaves room for, and a body dropping into it
     * comes out in the cell beneath.
     *
     * @param helper the gametest helper
     */
    public static void dropsThroughAOneByOneFloor(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, FLOOR_Y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, FLOOR_Y - 1, z), x == 2 && z == 2 ? Blocks.AIR : Blocks.STONE);
                // Stone deeper than the hole's reach under the floor, so a smaller mouth beside the open cell, which
                // the 3x3 centred on the struck block also tries, finds no opening below.
                for (int depth = FLOOR_Y - 2; depth >= FLOOR_Y - 2 - HOLE_REACH; depth--) {
                    helper.setBlock(new BlockPos(x, depth, z), Blocks.STONE);
                }
            }
        }
        ServerPlayer caster = caster(helper);
        caster.snapTo(helper.absoluteVec(ON_THE_FLOOR));
        throwAt(caster, helper.absolutePos(new BlockPos(2, FLOOR_Y, 2)), Direction.UP);
        helper.runAfterDelay(LANDED, () -> {
            assertMouthSpans(helper, new BlockPos(2, FLOOR_Y, 2), 1, 1, 1);
            crossAt(helper, caster, SUNK_IN_THE_FLOOR);
        });
        helper.runAfterDelay(LANDED + STEP, () -> {
            assertAt(helper, caster, IN_THE_CELL_BELOW, "Dropping into a 1x1 floor hole should come out in the "
                    + "cell beneath");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Thrown up at a ceiling twenty blocks thick, past sixteen blocks but
     * within the hole's thirty-two, the hole opens through to the room above.
     * The wall runs up over the bay rather than across it, since the bay is
     * six blocks deep and a wall twenty long across it stands in the next
     * test's bay, and the world's floor lies too near beneath it.
     *
     * @param helper the gametest helper
     */
    public static void reachesThroughAThickWall(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                for (int height = 0; height < THICK_WALL; height++) {
                    helper.setBlock(new BlockPos(x, THICK_CEILING.getY() + height, z), Blocks.STONE);
                }
                for (int room = THICK_WALL; room < THICK_WALL + ROOM_ABOVE; room++) {
                    helper.setBlock(new BlockPos(x, THICK_CEILING.getY() + room, z), Blocks.AIR);
                }
            }
        }
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(THICK_CEILING), Direction.DOWN);
        helper.runAfterDelay(LANDED, () -> {
            helper.assertTrue(holeStruckAt(helper, THICK_CEILING).isPresent(), "A hole thrown at a ceiling twenty "
                    + "blocks thick should open through it");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A hole whose time runs out while a body stands in one of its mouths
     * stays open until the body steps clear, then closes.
     *
     * @param helper the gametest helper
     */
    public static void holdsOpenWhileOccupied(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> standAt(helper, caster, IN_THE_MOUTH));
        helper.runAfterDelay(LANDED + LIFETIME + STEP, () -> {
            helper.assertTrue(holeStruckAt(helper, STRUCK).isPresent(), "A hole with a body in its mouth should stay "
                    + "open past its time");
            standAt(helper, caster, START);
        });
        helper.runAfterDelay(LANDED + LIFETIME + STEP + STEP, () -> {
            helper.assertTrue(holeStruckAt(helper, STRUCK).isEmpty(), "A hole past its time should close once the "
                    + "body steps clear");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A 1x1 hole on a wall is crawled through: standing before its mouth puts
     * a player in a crawl, while before a 3x3 mouth it stays standing.
     *
     * @param helper the gametest helper
     */
    public static void crawlsThroughAOneByOneWall(GameTestHelper helper) {
        for (int z = WALL_NEAR_Z; z <= WALL_FAR_Z; z++) {
            helper.setBlock(new BlockPos(2, 1, z), Blocks.STONE);
        }
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(LOW_IN_THE_SLIM_WALL), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            assertMouthSpans(helper, LOW_IN_THE_SLIM_WALL, 1, 1, 1);
            standAt(helper, caster, BEFORE_THE_FACE);
            helper.assertTrue(GatePassage.crawlsThrough(caster), "A player before a 1x1 wall mouth should crawl");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Before a 3x3 mouth a player stays standing.
     *
     * @param helper the gametest helper
     */
    public static void standsBeforeAFullMouth(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            standAt(helper, caster, BEFORE_THE_FACE);
            helper.assertFalse(GatePassage.crawlsThrough(caster), "A player before a 3x3 mouth should stand");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A hit on a hole's mouth dismisses the hole, as a hit on a portal breaks
     * the portal, and the wall behind it stands.
     *
     * @param helper the gametest helper
     */
    public static void dismissedByAHit(GameTestHelper helper) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            helper.assertTrue(holeStruckAt(helper, STRUCK).isPresent(), "The hole should stand open before the hit");
            helper.assertTrue(OpenHoles.dismissAt(helper.getLevel(), helper.absolutePos(STRUCK), Direction.NORTH),
                    "A hit on the hole's mouth should dismiss it");
            helper.assertTrue(holeStruckAt(helper, STRUCK).isEmpty(), "The hole should stand dismissed");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(STRUCK)).is(Blocks.STONE),
                    "The wall behind a dismissed hole should stand");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Thrown at a wall one block wide and four tall, the hole centres on the
     * struck block, its mouth the block below it, the struck block and the
     * one above, rather than opening down to the wall's foot.
     *
     * @param helper the gametest helper
     */
    public static void centresOnTheStruckBlock(GameTestHelper helper) {
        for (int y = 1; y <= TALL_WALL_TOP; y++) {
            for (int z = WALL_NEAR_Z; z <= WALL_FAR_Z; z++) {
                helper.setBlock(new BlockPos(2, y, z), Blocks.STONE);
            }
        }
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(HIGH_IN_THE_TALL_WALL), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            assertMouthSpans(helper, HIGH_IN_THE_TALL_WALL, 1, 3, 1);
            double bottom = holeStruckAt(helper, HIGH_IN_THE_TALL_WALL).orElseThrow().entry().blocks().minY;
            helper.assertTrue(bottom == helper.absolutePos(HIGH_IN_THE_TALL_WALL.below()).getY(), "The hole should "
                    + "centre on the struck block, its bottom the block below it, stands at " + bottom);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A hole opens only on opaque solid blocks: thrown at a wall of glass it
     * is refused, no mouth opening.
     *
     * @param helper the gametest helper
     */
    public static void refusedOnGlass(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = WALL_NEAR_Z; z <= WALL_FAR_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                }
            }
        }
        ServerPlayer caster = caster(helper);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            helper.assertTrue(holeStruckAt(helper, STRUCK).isEmpty(), "A hole thrown at glass should be refused");
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * Thrown at a wall whose struck face carries a portal, the tunnel counts
     * the portal in the way: no mouth it opens on that face covers the
     * portal's blocks, so the two never lie one over the other.
     *
     * @param helper the gametest helper
     */
    public static void keepsOffAPortalOnTheStruckFace(GameTestHelper helper) {
        assertKeepsOffAPortal(helper, new BlockPos(2, 1, WALL_NEAR_Z), Direction.NORTH);
    }

    /**
     * Thrown at a wall whose far face carries a portal, the tunnel counts the
     * portal in the way: no mouth it opens on the far face covers the
     * portal's blocks.
     *
     * @param helper the gametest helper
     */
    public static void keepsOffAPortalOnTheFarFace(GameTestHelper helper) {
        assertKeepsOffAPortal(helper, new BlockPos(2, 1, WALL_FAR_Z), Direction.SOUTH);
    }

    private static void assertKeepsOffAPortal(GameTestHelper helper, BlockPos portalFoot, Direction portalFace) {
        buildWall(helper);
        ServerPlayer caster = caster(helper);
        Portal portal = new Portal(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(portalFoot)),
                portalFace, Direction.UP);
        QuantumPortals portals = QuantumPortals.get(helper.getLevel());
        UUID portalOwner = UUID.randomUUID();
        portals.place(portalOwner, portal);
        throwAt(caster, helper.absolutePos(STRUCK), Direction.NORTH);
        helper.runAfterDelay(LANDED, () -> {
            List<Gate> over = OpenHoles.open().stream().flatMap(hole -> Stream.of(hole.entry(), hole.exit()))
                    .filter(mouth -> mouth.face() == portalFace && mouth.blocks().intersects(portal.blocks()))
                    .toList();
            portals.fall(portal);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(over.isEmpty(), "No tunnel mouth should cover the portal on " + portalFace + " of "
                    + portal.blocks() + ", covering it: " + over.stream().map(Gate::blocks).toList());
            helper.succeed();
        });
    }

    /** The top of a wall one block wide and four tall. */
    private static final int TALL_WALL_TOP = 4;
    /** The block the throw marks in the tall wall, two above its foot. */
    private static final BlockPos HIGH_IN_THE_TALL_WALL = new BlockPos(2, 3, 2);

    /** Feet a step before the struck face. */
    private static final Vec3 BEFORE_THE_FACE = new Vec3(2.5, 1, 1.5);

    /** Feet in the struck mouth, the body halfway into the wall's first block. */
    private static final Vec3 IN_THE_MOUTH = new Vec3(2.5, 1, 2.2);

    /** Asserts the hole struck at a block opened, its struck-face mouth covering a box of blocks this size. */
    private static void assertMouthSpans(GameTestHelper helper, BlockPos struck, int x, int y, int z) {
        java.util.Optional<OpenHoles.Hole> hole = holeStruckAt(helper, struck);
        helper.assertTrue(hole.isPresent(), "A hole should stand open on the struck face at " + struck);
        net.minecraft.world.phys.AABB mouth = hole.get().entry().blocks();
        helper.assertTrue(mouth.getXsize() == x && mouth.getYsize() == y && mouth.getZsize() == z,
                "The hole's mouth should span " + x + "x" + y + "x" + z + " blocks, spans " + mouth);
    }

    /** The open hole whose struck-face mouth covers a block, among the holes every test in the run opened. */
    private static java.util.Optional<OpenHoles.Hole> holeStruckAt(GameTestHelper helper, BlockPos struck) {
        BlockPos at = helper.absolutePos(struck);
        return OpenHoles.open().stream().filter(hole -> hole.entry().covers(at)).findFirst();
    }

    /** The lower block of the slim wall, the throw's mark. */
    private static final BlockPos LOW_IN_THE_SLIM_WALL = new BlockPos(2, 1, 2);
    /** The floor's height, relative: two layers of stone below it, a cell open in the middle of the upper one. */
    private static final int FLOOR_Y = 3;
    /** Where the caster stands on the floor. */
    private static final Vec3 ON_THE_FLOOR = new Vec3(2.5, FLOOR_Y + 1, 0.5);
    /** Feet sunk into the 1x1 floor hole, the middle a tenth below the floor's face. */
    private static final Vec3 SUNK_IN_THE_FLOOR = new Vec3(2.5, FLOOR_Y, 2.5);
    /** Where feet come out, in the one-block cell beneath the floor. */
    private static final Vec3 IN_THE_CELL_BELOW = new Vec3(2.5, FLOOR_Y - 1, 2.5);
    /** How many cells past the struck block a hole's scan runs, as quantum_tunnel.json reads. */
    private static final int HOLE_REACH = 32;
    /** A wall thicker than sixteen blocks and thinner than thirty-two. */
    private static final int THICK_WALL = 20;
    /** How tall the room over the thick ceiling stands, room for a body to come out. */
    private static final int ROOM_ABOVE = 3;
    /** The thick ceiling's lowest block over the bay's middle, the throw's mark. */
    private static final BlockPos THICK_CEILING = new BlockPos(2, 3, 2);

    /** Ticks a refused throw is watched for, longer than any flight in the bay. */
    private static final int SETTLE_TICKS = 30;

    private static void buildWall(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = WALL_NEAR_Z; z <= WALL_FAR_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /** A survival Quantum caster holding the hole's cost exactly and knowing the hole. */
    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        caster.setGameMode(GameType.SURVIVAL);
        caster.connection.markClientLoaded();
        caster.getInventory().clearContent();
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.QUANTUM, HOLE_COST));
        KnownRecipes.teachRequires(caster, AbilityRegistry.of(caster.level()).getAbility(QUANTUM_TUNNEL));
        caster.snapTo(helper.absoluteVec(START));
        return caster;
    }

    private static void throwAt(ServerPlayer caster, BlockPos struck, Direction face) {
        GooGloveItem.setSelection(caster.getMainHandItem(), GloveSelection.ofAbility(GooTypes.QUANTUM, QUANTUM_TUNNEL));
        GooThrowHandler.execute(caster, new GooThrowPayload(GooTypes.id(GooTypes.QUANTUM), -1, struck,
                face.ordinal(), false, QUANTUM_TUNNEL.toString(), caster.getEyePosition()));
    }
}
