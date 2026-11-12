package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SpireStep;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
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
 * Gametest for rock's Spire, submitted the way the second right click
 * submits it: a 3x1 footprint at rise 3 over ground of dirt, stone and
 * deepslate raises a 3x1x3 wall of stone as one rising hunk, its cells empty
 * while it rises, settles the stone into them once the rise time passes,
 * leaves every ground block as it stood, and spends rock goo for the
 * stone it grows.
 * decision spire-grows-stone-out-of-the-top
 * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 */
@RegistersGameTests
public final class SpireTests {
    private static final String SPIRE_GROWS_STONE = "spire_grows_stone_above_the_footprint";
    private static final String SPIRE_CARRIES_A_RIDER = "spire_carries_a_rider_up_with_the_stone";
    private static final String SPIRE_LETS_A_PLAYER_RIDE = "spire_accepts_a_riding_players_moves";
    private static final String SPIRE_LIFTS_AN_OVERTAKEN_RIDER = "spire_lifts_a_rider_the_stone_overtook";
    /** A few ticks into the rise, the top already half a block over the ground, as a client first sees it. */
    private static final int OVERTAKEN_TICKS = 5;

    private static final String ROCK_SPIRE = "goo:rock_spire";
    /** The footprint's ground row, three cells along X at the bay's middle depth, one above the floor. */
    private static final BlockPos CORNER = new BlockPos(1, 1, 2);
    private static final BlockPos OPPOSITE = new BlockPos(3, 1, 2);
    private static final int RISE = 3;
    /** The footprint's ground cells along X, each a different block. */
    private static final List<Block> GROUND = List.of(Blocks.DIRT, Blocks.STONE, Blocks.DEEPSLATE);
    private static final BlockPos CASTER_POS = new BlockPos(0, 1, 5);
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    private static final String ABILITY_REQUIRED = "goo:rock_spire must be loaded";
    private static final String STONE_MISSING = "The cell at %s should hold %s, holds %s";
    private static final String CHARGED_WRONG = "The growth should spend rock goo, spent %d mB";
    private static final String NO_HUNK = "One rising hunk should carry the %d stone blocks, found %s";
    /** The settle lands on the tick the rise ends; one more lets its block updates through. */
    private static final int SETTLE_SLACK_TICKS = 1;
    /** The rider stands on the footprint's middle ground cell. */
    private static final BlockPos RIDER_POS = new BlockPos(2, 2, 2);
    /** How far the rider's feet may stand from the hunk's top mid-rise and still ride it. */
    private static final double FEET_ON_THE_STONE = 0.1;
    /** How far below the server's top a player's client reports their feet, a tick or so behind it. */
    private static final double CLIENT_LAG = 0.15;
    /** How far a rider's last-tick feet may stand from last tick's top. */
    private static final double CLIMB_SLACK = 1e-3;

    private SpireTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPIRE_GROWS_STONE, SpireTests::spireGrowsStoneAboveTheFootprint);
        GooTestFunctions.registerTest(registrar, SPIRE_CARRIES_A_RIDER, SpireTests::spireCarriesARiderUp);
        GooTestFunctions.registerTest(registrar, SPIRE_LETS_A_PLAYER_RIDE, SpireTests::spireAcceptsARidingPlayersMoves);
        GooTestFunctions.registerTest(registrar, SPIRE_LIFTS_AN_OVERTAKEN_RIDER, SpireTests::spireLiftsARiderTheStoneOvertook);
    }

    /**
     * A submitted 3x1 footprint at rise 3 over mixed ground grows stone in the
     * three cells above each footprint cell.
     *
     * @param helper the gametest helper
     */
    public static void spireGrowsStoneAboveTheFootprint(GameTestHelper helper) {
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            helper.setBlock(new BlockPos(x, CORNER.getY(), CORNER.getZ()), GROUND.get(x - CORNER.getX()));
        }
        int spent = submit(helper);
        assertRising(helper);
        helper.runAfterDelay(SpireStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            // The hunk lingers over its settled stone while the clients' chunks redraw, so nothing blinks out.
            helper.assertTrue(hunksIn(helper).size() == 1,
                    "The hunk should linger over the settled stone, found " + hunksIn(helper).size());
            for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
                for (int height = 1; height <= RISE; height++) {
                    assertHolds(helper, new BlockPos(x, CORNER.getY() + height, CORNER.getZ()),
                            Blocks.STONE.defaultBlockState(), STONE_MISSING);
                }
            }
            // Decision no-test-pins-a-tunable-ability-number: the growth spends, whatever its price.
            helper.assertTrue(spent > 0, String.format(CHARGED_WRONG, spent));
        });
        helper.runAfterDelay(SpireStep.RISE_TICKS + BlockHunk.SETTLE_LINGER_TICKS + SETTLE_SLACK_TICKS + 1, () -> {
            helper.assertTrue(hunksIn(helper).isEmpty(),
                    "The hunk should end once it has lingered, found " + hunksIn(helper).size());
            helper.succeed();
        });
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
    public static void spireAcceptsARidingPlayersMoves(GameTestHelper helper) {
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            helper.setBlock(new BlockPos(x, CORNER.getY(), CORNER.getZ()), GROUND.get(x - CORNER.getX()));
        }
        submit(helper);
        ServerPlayer rider = helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(SpireStep.RISE_TICKS / 2, () -> {
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
    public static void spireLiftsARiderTheStoneOvertook(GameTestHelper helper) {
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            helper.setBlock(new BlockPos(x, CORNER.getY(), CORNER.getZ()), GROUND.get(x - CORNER.getX()));
        }
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
    public static void spireCarriesARiderUp(GameTestHelper helper) {
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            helper.setBlock(new BlockPos(x, CORNER.getY(), CORNER.getZ()), GROUND.get(x - CORNER.getX()));
        }
        submit(helper);
        // Spawned after the hunk, the rider ticks after it: the order that left a rider's climb out of its own tick.
        Zombie rider = helper.spawn(EntityType.ZOMBIE, RIDER_POS);
        rider.setNoAi(true);
        double[] lastTop = {Double.NaN};
        for (int tick = 1; tick < SpireStep.RISE_TICKS; tick++) {
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
        helper.runAfterDelay(SpireStep.RISE_TICKS + SETTLE_SLACK_TICKS, () -> {
            double settledTop = helper.absolutePos(CORNER).getY() + 1 + RISE;
            helper.assertTrue(rider.isAlive(), "The rider should ride the spire up alive");
            helper.assertTrue(rider.getY() >= settledTop - FEET_ON_THE_STONE,
                    "The rider should stand on the settled top at " + settledTop + ", stands at " + rider.getY());
            helper.succeed();
        });
    }

    /**
     * Right after the submit one hunk carries the wall's stone, rising toward
     * its cells.
     *
     * @param helper the gametest helper
     */
    private static void assertRising(GameTestHelper helper) {
        AABB bay = new AABB(helper.absolutePos(CORNER)).inflate(GROUND.size() + RISE);
        List<BlockHunk> hunks = helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay);
        int stone = GROUND.size() * RISE;
        helper.assertTrue(hunks.size() == 1 && hunks.getFirst().blocks().size() == stone
                && hunks.getFirst().blocks().stream().allMatch(block -> block.state().is(Blocks.STONE)),
                String.format(NO_HUNK, stone, hunks.stream().map(hunk -> hunk.blocks().size()).toList()));
    }

    /**
     * Submits the footprint as a caster holding a glove, the reagents and rock goo.
     *
     * @param helper the gametest helper
     * @return the mB of rock goo the growth spent
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static int submit(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.setPos(stand.x, stand.y, stand.z);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, CASTER_GOO));
        KnownRecipes.teachRequires(caster, spire(helper));
        GooSpireHandler.cast(caster, new GooSpirePayload(GooTypes.id(GooTypes.ROCK), ROCK_SPIRE,
                helper.absolutePos(CORNER), helper.absolutePos(OPPOSITE), RISE));
        int spent = CASTER_GOO - GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.ROCK, 0);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        return spent;
    }

    private static void assertHolds(GameTestHelper helper, BlockPos pos, BlockState expected, String message) {
        BlockState actual = helper.getBlockState(pos);
        helper.assertTrue(actual.equals(expected), String.format(message, pos, expected, actual));
    }

    private static AbilityDefinition spire(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ROCK_SPIRE));
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }
}
