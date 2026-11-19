package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.BuildStep;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametest for rock's Build, submitted the way the second right click
 * submits it: a 3x1 footprint at rise 3 over ground of dirt, stone and
 * deepslate reaches three blocks down, lifts them three higher on a 3x1x3
 * wall of stone as one rising hunk, settles both into their cells once the
 * rise time passes, and spends rock goo for the stone it grows.
 * decision spire-grows-stone-out-of-the-top
 * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
 * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 */
@RegistersGameTests
public final class BuildTests {
    private static final String BUILD_LIFTS_A_WALL = "build_lifts_a_wall_of_ground_on_stone";
    private static final String BUILD_LIFTS_THE_SURFACE = "build_lifts_the_surface_two_higher_on_stone";
    private static final String BUILD_PUSHES_OUT_OF_A_WALL = "build_pushes_a_wall_out_two_on_stone";
    private static final String BUILD_GROWS_FOR_A_FAR_CASTER = "build_grows_for_a_caster_far_from_its_footprint";
    private static final String BUILD_CARRIES_A_RIDER = "build_carries_a_rider_up_with_the_stone";
    private static final String BUILD_LETS_A_PLAYER_RIDE = "build_accepts_a_riding_players_moves";
    private static final String BUILD_LIFTS_AN_OVERTAKEN_RIDER = "build_lifts_a_rider_the_stone_overtook";
    private static final String BUILD_LEAVES_A_PILLAR_STANDING = "build_leaves_a_pillar_standing_and_grows_from_its_face";
    /** The one-wide pillar's south face cells, the footprint, two tall over a dirt foot; air lies north of it. */
    private static final BlockPos PILLAR_LOW = new BlockPos(2, 1, 2);
    private static final BlockPos PILLAR_HIGH = new BlockPos(2, 2, 2);
    private static final int PILLAR_RISE = 2;
    /** A few ticks into the rise, the top already half a block over the ground, as a client first sees it. */
    private static final int OVERTAKEN_TICKS = 5;

    private static final String ROCK_BUILD = "goo:rock_build";
    /** The footprint's ground row, three cells along X at the bay's middle depth, two above the floor. */
    private static final BlockPos CORNER = new BlockPos(1, 2, 2);
    private static final BlockPos OPPOSITE = new BlockPos(3, 2, 2);
    private static final int RISE = 3;
    /** The one-cell footprint the surface test lifts, over ground three deep. */
    private static final BlockPos SURFACE = new BlockPos(2, 3, 2);
    private static final int SURFACE_RISE = 2;
    /** The wall's north face layer, the wall standing from here south to the bay's edge. */
    private static final int WALL_Z = 3;
    /** The wall footprint's corners on that face, two by two. */
    private static final BlockPos WALL_CORNER = new BlockPos(1, 2, WALL_Z);
    private static final BlockPos WALL_OPPOSITE = new BlockPos(2, 3, WALL_Z);
    private static final int WALL_RISE = 2;
    /** The two marker blocks stacked at the surface, top first. */
    private static final List<Block> MARKERS = List.of(Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK);
    /** The footprint's ground cells along X, each a different block, none of them the stone Build grows. */
    private static final List<Block> GROUND = List.of(Blocks.DIRT, Blocks.GRANITE, Blocks.DEEPSLATE);
    /**
     * The rise a riding player's test raises: the bay holds six rows and a block stands over it, which the head of a
     * player on a rise of three would reach into.
     */
    private static final int RIDING_PLAYER_RISE = 2;
    private static final BlockPos CASTER_POS = new BlockPos(0, 1, 5);
    /** A caster standing 24 blocks back from the bay, past any reach the pin click has. */
    private static final BlockPos FAR_CASTER_POS = new BlockPos(0, 1, -24);
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    private static final String ABILITY_REQUIRED = "goo:rock_build must be loaded";
    private static final String STONE_MISSING = "The cell at %s should hold %s, holds %s";
    private static final String CHARGED_WRONG = "The growth should spend rock goo, spent %d mB";
    private static final String NO_HUNK = "One rising hunk should carry %d stone and the ground it lifts, found %s";
    /** The settle lands on the tick the rise ends; one more lets its block updates through. */
    private static final int SETTLE_SLACK_TICKS = 1;
    /** The rider stands on the footprint's middle ground cell. */
    private static final BlockPos RIDER_POS = new BlockPos(2, 3, 2);
    /** How far the rider's feet may stand from the hunk's top mid-rise and still ride it. */
    private static final double FEET_ON_THE_STONE = 0.1;
    /** How far below the server's top a player's client reports their feet, a tick or so behind it. */
    private static final double CLIENT_LAG = 0.15;
    /** How far a rider's last-tick feet may stand from last tick's top. */
    private static final double CLIMB_SLACK = 1e-3;

    private BuildTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BUILD_LIFTS_A_WALL, BuildTests::buildLiftsAWallOfGroundOnStone);
        GooTestFunctions.registerTest(registrar, BUILD_LIFTS_THE_SURFACE,
                BuildTests::buildLiftsTheSurfaceTwoHigherOnStone);
        GooTestFunctions.registerTest(registrar, BUILD_PUSHES_OUT_OF_A_WALL, BuildTests::buildPushesAWallOutTwoOnStone);
        GooTestFunctions.registerTest(registrar, BUILD_GROWS_FOR_A_FAR_CASTER,
                BuildTests::buildGrowsForACasterFarFromItsFootprint);
        GooTestFunctions.registerTest(registrar, BUILD_CARRIES_A_RIDER, BuildTests::buildCarriesARiderUp);
        GooTestFunctions.registerTest(registrar, BUILD_LETS_A_PLAYER_RIDE, BuildTests::buildAcceptsARidingPlayersMoves);
        GooTestFunctions.registerTest(registrar, BUILD_LIFTS_AN_OVERTAKEN_RIDER, BuildTests::buildLiftsARiderTheStoneOvertook);
        GooTestFunctions.registerTest(registrar, BUILD_LEAVES_A_PILLAR_STANDING,
                BuildTests::buildLeavesAPillarStandingAndGrowsFromItsFace);
    }

    /**
     * Build cast on the south face of a one-wide pillar with air behind it
     * leaves every pillar block where it stood and stands its stone south of
     * the pillar; the air north of the pillar stays air.
     * decision build-extends-the-first-block-with-air-behind-it
     *
     * @param helper the gametest helper
     */
    public static void buildLeavesAPillarStandingAndGrowsFromItsFace(GameTestHelper helper) {
        helper.setBlock(PILLAR_LOW.below(), Blocks.DIRT);
        helper.setBlock(PILLAR_LOW, MARKERS.getLast());
        helper.setBlock(PILLAR_HIGH, MARKERS.getFirst());
        submit(helper, PILLAR_LOW, PILLAR_HIGH, PILLAR_RISE, Direction.SOUTH);
        helper.runAfterDelay(BuildStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            assertHolds(helper, PILLAR_LOW, MARKERS.getLast().defaultBlockState(), STONE_MISSING);
            assertHolds(helper, PILLAR_HIGH, MARKERS.getFirst().defaultBlockState(), STONE_MISSING);
            for (BlockPos face : List.of(PILLAR_LOW, PILLAR_HIGH)) {
                for (int out = 1; out <= PILLAR_RISE; out++) {
                    assertHolds(helper, face.south(out), Blocks.STONE.defaultBlockState(), STONE_MISSING);
                }
                assertHolds(helper, face.north(), Blocks.AIR.defaultBlockState(), STONE_MISSING);
            }
            helper.succeed();
        });
    }

    /**
     * A submitted 3x1 footprint at rise 3 over mixed ground reaches three
     * blocks down and lifts each column's ground block three higher on the
     * three stone grown in the cells the reach left.
     *
     * @param helper the gametest helper
     */
    public static void buildLiftsAWallOfGroundOnStone(GameTestHelper helper) {
        layTheGround(helper);
        int spent = submit(helper);
        assertRising(helper);
        helper.runAfterDelay(BuildStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            // The hunk lingers over its settled stone while the clients' chunks redraw, so nothing blinks out.
            helper.assertTrue(hunksIn(helper).size() == 1,
                    "The hunk should linger over the settled stone, found " + hunksIn(helper).size());
            for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
                assertHolds(helper, new BlockPos(x, CORNER.getY() + RISE, CORNER.getZ()),
                        GROUND.get(x - CORNER.getX()).defaultBlockState(), STONE_MISSING);
                for (int depth = 0; depth < RISE; depth++) {
                    assertHolds(helper, new BlockPos(x, CORNER.getY() - depth, CORNER.getZ()),
                            Blocks.STONE.defaultBlockState(), STONE_MISSING);
                }
            }
            // Decision no-test-pins-a-tunable-ability-number: the growth spends, whatever its price.
            helper.assertTrue(spent > 0, String.format(CHARGED_WRONG, spent));
        });
        helper.runAfterDelay(BuildStep.RISE_TICKS + BlockHunk.SETTLE_LINGER_TICKS + SETTLE_SLACK_TICKS + 1, () -> {
            helper.assertTrue(hunksIn(helper).isEmpty(),
                    "The hunk should end once it has lingered, found " + hunksIn(helper).size());
            helper.succeed();
        });
    }

    /**
     * A one-cell footprint rising two over two marker blocks stacked at the
     * surface leaves the markers two higher, in their order, on the two
     * stone grown in the cells they left, the ground under them as it stood.
     * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
     *
     * @param helper the gametest helper
     */
    public static void buildLiftsTheSurfaceTwoHigherOnStone(GameTestHelper helper) {
        for (int y = 0; y <= SURFACE.getY(); y++) {
            helper.setBlock(new BlockPos(SURFACE.getX(), y, SURFACE.getZ()), Blocks.DIRT);
        }
        for (int depth = 0; depth < MARKERS.size(); depth++) {
            helper.setBlock(SURFACE.below(depth), MARKERS.get(depth));
        }
        submit(helper, SURFACE, SURFACE, SURFACE_RISE, Direction.UP);
        helper.runAfterDelay(BuildStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            for (int depth = 0; depth < MARKERS.size(); depth++) {
                assertHolds(helper, SURFACE.below(depth).above(SURFACE_RISE), MARKERS.get(depth).defaultBlockState(),
                        STONE_MISSING);
                assertHolds(helper, SURFACE.below(depth), Blocks.STONE.defaultBlockState(), STONE_MISSING);
            }
            assertHolds(helper, SURFACE.below(MARKERS.size()), Blocks.DIRT.defaultBlockState(), STONE_MISSING);
            helper.succeed();
        });
    }

    /**
     * A two by two footprint on the north face of a wall of gold markers,
     * risen two, pushes the markers two north out of the wall on the two stone
     * grown in the cells the reach left.
     * decision build-goes-sideways
     *
     * @param helper the gametest helper
     */
    public static void buildPushesAWallOutTwoOnStone(GameTestHelper helper) {
        for (BlockPos cell : BlockPos.betweenClosed(WALL_CORNER, new BlockPos(WALL_OPPOSITE.getX(), WALL_OPPOSITE.getY(), WALL_Z + 2))) {
            helper.setBlock(cell, cell.getZ() == WALL_Z ? Blocks.GOLD_BLOCK : Blocks.DIRT);
        }
        submit(helper, WALL_CORNER, WALL_OPPOSITE, WALL_RISE, Direction.NORTH);
        helper.runAfterDelay(BuildStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            for (BlockPos cell : BlockPos.betweenClosed(WALL_CORNER, WALL_OPPOSITE)) {
                BlockPos face = cell.immutable();
                assertHolds(helper, face.north(WALL_RISE), Blocks.GOLD_BLOCK.defaultBlockState(), STONE_MISSING);
                assertHolds(helper, face.south().north(WALL_RISE), Blocks.DIRT.defaultBlockState(), STONE_MISSING);
                assertHolds(helper, face, Blocks.STONE.defaultBlockState(), STONE_MISSING);
                assertHolds(helper, face.south(), Blocks.STONE.defaultBlockState(), STONE_MISSING);
            }
            helper.succeed();
        });
    }

    /**
     * A footprint submitted by a caster standing far from it still grows: the
     * server checks the footprint against Build's bounds alone, so a strip
     * sixteen long stands whichever end the caster pinned it from.
     * decision build-dimensions-reach-sixteen-by-sixteen-by-sixteen
     *
     * @param helper the gametest helper
     */
    public static void buildGrowsForACasterFarFromItsFootprint(GameTestHelper helper) {
        layTheGround(helper);
        submit(helper, CORNER, OPPOSITE, RISE, Direction.UP, FAR_CASTER_POS);
        helper.runAfterDelay(BuildStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
                assertHolds(helper, new BlockPos(x, CORNER.getY(), CORNER.getZ()), Blocks.STONE.defaultBlockState(),
                        STONE_MISSING);
            }
            helper.succeed();
        });
    }

    /**
     * Lays the footprint's ground: each column's ground block over dirt down
     * to the bay's floor, so the reach stays inside the bay.
     *
     * @param helper the gametest helper
     */
    private static void layTheGround(GameTestHelper helper) {
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            for (int y = 0; y < CORNER.getY(); y++) {
                helper.setBlock(new BlockPos(x, y, CORNER.getZ()), Blocks.DIRT);
            }
            helper.setBlock(new BlockPos(x, CORNER.getY(), CORNER.getZ()), GROUND.get(x - CORNER.getX()));
        }
    }

    /**
     * A player's own client lifts them up the stone, a tick or so behind the
     * server's copy of it, so the feet their client reports stand a hair inside
     * the server's stone; the server's move check, noCollision, must take that
     * box, or it pulls the player back down through the stone.
     * decision spire-carries-its-riders-up-with-the-rising-stone
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void buildAcceptsARidingPlayersMoves(GameTestHelper helper) {
        layTheGround(helper);
        submit(helper, CORNER, OPPOSITE, RIDING_PLAYER_RISE, Direction.UP);
        ServerPlayer rider = helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(BuildStep.RISE_TICKS / 2, () -> {
            double top = hunksIn(helper).getFirst().getBoundingBox().maxY;
            Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(RIDER_POS));
            rider.setPos(feet.x, top - CLIENT_LAG, feet.z);
            helper.assertTrue(helper.getLevel().noCollision(rider, rider.getBoundingBox()),
                    "The server should take the move of a player whose client lifts them a hair behind its stone");
            helper.getLevel().getServer().getPlayerList().remove(rider);
            helper.succeed();
        });
    }

    /**
     * A player's client first sees the rising stone a tick or two late, its top
     * already past the player's feet; the stone still lifts whoever stands on
     * its footprint below its top onto it, rather than rising through them.
     * decision spire-carries-its-riders-up-with-the-rising-stone
     *
     * @param helper the gametest helper
     */
    public static void buildLiftsARiderTheStoneOvertook(GameTestHelper helper) {
        layTheGround(helper);
        submit(helper);
        Zombie[] rider = new Zombie[1];
        helper.runAfterDelay(OVERTAKEN_TICKS, () -> {
            rider[0] = helper.spawn(EntityType.ZOMBIE, RIDER_POS);
            rider[0].setNoAi(true);
        });
        helper.runAfterDelay(OVERTAKEN_TICKS + 2, () -> {
            double top = hunksIn(helper).getFirst().getBoundingBox().maxY;
            helper.assertTrue(Math.abs(rider[0].getY() - top) <= FEET_ON_THE_STONE, "A rider the stone overtook"
                    + " should be lifted onto its top at " + top + ", stands at " + rider[0].getY());
            helper.succeed();
        });
    }

    private static List<BlockHunk> hunksIn(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(BlockHunk.class,
                new AABB(helper.absolutePos(CORNER)).inflate(GROUND.size() + RISE));
    }

    /**
     * A zombie standing on the footprint rides the rising stone up, its feet
     * on the hunk's top mid-rise, and stands on the settled top when the rise
     * ends, alive.
     * decision spire-carries-its-riders-up-with-the-rising-stone
     *
     * @param helper the gametest helper
     */
    public static void buildCarriesARiderUp(GameTestHelper helper) {
        layTheGround(helper);
        submit(helper);
        // Spawned after the hunk, the rider ticks after it: the order that left a rider's climb out of its own tick.
        Zombie rider = helper.spawn(EntityType.ZOMBIE, RIDER_POS);
        rider.setNoAi(true);
        double[] lastTop = {Double.NaN};
        for (int tick = 1; tick < BuildStep.RISE_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                List<BlockHunk> hunks = hunksIn(helper);
                helper.assertTrue(hunks.size() == 1, "One hunk should be rising, found " + hunks.size());
                double top = hunks.getFirst().getBoundingBox().maxY;
                helper.assertTrue(Math.abs(rider.getY() - top) <= FEET_ON_THE_STONE,
                        "The rider's feet should stand on the rising stone at " + top + ", stand at " + rider.getY());
                if (!Double.isNaN(lastTop[0])) {
                    // The climb lands inside the rider's own tick, so its drawn position glides from last tick's top.
                    helper.assertTrue(Math.abs(rider.yo - lastTop[0]) <= CLIMB_SLACK, "The rider's last-tick feet "
                            + rider.yo + " should stand on last tick's top " + lastTop[0]);
                }
                lastTop[0] = top;
            });
        }
        helper.runAfterDelay(BuildStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            double settledTop = helper.absolutePos(CORNER).getY() + 1 + RISE;
            helper.assertTrue(rider.isAlive(), "The rider should ride the build up alive");
            helper.assertTrue(rider.getY() >= settledTop - FEET_ON_THE_STONE,
                    "The rider should stand on the settled top at " + settledTop + ", stands at " + rider.getY());
            helper.succeed();
        });
    }

    /**
     * Right after the submit one hunk carries the wall's stone and the ground
     * it lifts, rising toward their cells, and the reached cells stand empty.
     *
     * @param helper the gametest helper
     */
    private static void assertRising(GameTestHelper helper) {
        AABB bay = new AABB(helper.absolutePos(CORNER)).inflate(GROUND.size() + RISE);
        List<BlockHunk> hunks = helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay);
        int stone = GROUND.size() * RISE;
        helper.assertTrue(hunks.size() == 1 && hunks.getFirst().blocks().size() == 2 * stone
                && hunks.getFirst().blocks().stream().filter(block -> block.state().is(Blocks.STONE)).count() == stone,
                String.format(NO_HUNK, stone, hunks.stream().map(hunk -> hunk.blocks().size()).toList()));
        helper.assertTrue(helper.getBlockState(CORNER).isAir(), "The reached ground should stand empty mid-rise");
    }

    /**
     * Submits the footprint as a caster holding a glove, the reagents and rock goo.
     *
     * @param helper the gametest helper
     * @return the mB of rock goo the growth spent
     */
    private static int submit(GameTestHelper helper) {
        return submit(helper, CORNER, OPPOSITE, RISE, Direction.UP);
    }

    private static int submit(GameTestHelper helper, BlockPos corner, BlockPos opposite, int rise, Direction face) {
        return submit(helper, corner, opposite, rise, face, CASTER_POS);
    }

    /**
     * Submits a footprint as a caster holding a glove, the reagents and rock goo.
     *
     * @param helper   the gametest helper
     * @param corner   the footprint's pinned corner, relative to the test
     * @param opposite the footprint's far corner, relative to the test
     * @param rise     the rise
     * @param face     the face the footprint lies on
     * @param standing where the caster stands, relative to the test
     * @return the mB of rock goo the growth spent
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static int submit(GameTestHelper helper, BlockPos corner, BlockPos opposite, int rise, Direction face,
                              BlockPos standing) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(standing));
        caster.setPos(stand.x, stand.y, stand.z);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, CASTER_GOO));
        KnownRecipes.teachRequires(caster, build(helper));
        GooBuildHandler.cast(caster, new GooBuildPayload(GooTypes.id(GooTypes.ROCK), ROCK_BUILD,
                helper.absolutePos(corner), helper.absolutePos(opposite), rise, face));
        int spent = CASTER_GOO - GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.ROCK, 0);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        return spent;
    }

    private static void assertHolds(GameTestHelper helper, BlockPos pos, BlockState expected, String message) {
        BlockState actual = helper.getBlockState(pos);
        helper.assertTrue(actual.equals(expected), String.format(message, pos, expected, actual));
    }

    private static AbilityDefinition build(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ROCK_BUILD));
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }
}
