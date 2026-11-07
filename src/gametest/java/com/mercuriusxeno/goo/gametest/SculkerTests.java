package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.network.HeartOverlayTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for Deep's Sculker held from the glove: a survival player standing
 * inside a block of stone swims a whole block through it and, after two
 * seconds buried, has lost no health to suffocation (decision
 * dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks).
 * The client moves a real player, so the test moves the mock player the way
 * the client would and ticks it as its connection would. The stone floats
 * above the test's own bay.
 */
@RegistersGameTests
public final class SculkerTests {
    private static final String SCULKER_SWIMS = "sculker_swims_through_stone";
    private static final String SCULKER_STOPS_AT_BEDROCK = "sculker_stops_at_bedrock";
    private static final String SCULKER_RISES_INTO_A_CEILING = "sculker_rises_into_a_ceiling";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SCULKER_SWIMS, SculkerTests::sculkerSwimsThroughStone);
        GooTestFunctions.registerTest(registrar, SCULKER_STOPS_AT_BEDROCK, SculkerTests::sculkerStopsAtBedrock);
        GooTestFunctions.registerTest(registrar, SCULKER_RISES_INTO_A_CEILING, SculkerTests::sculkerRisesIntoACeiling);
    }

    private static final Identifier DEEP_SCULKER = Identifier.parse("goo:deep_sculker");
    /** How far above the bay's floor the stone's middle floats. */
    private static final int LIFT = 12;
    /** The stone reaches this far from its middle cell on every axis. */
    private static final int STONE_HALF_WIDTH = 2;
    /** Ticks the player stays buried, long enough for suffocation to strike several times. */
    private static final int BURIED_TICKS = 40;
    /** One stroke of the swim, a whole block east, the way the client would move the player. */
    private static final Vec3 STROKE = new Vec3(1, 0, 0);
    private static final double SWUM_AT_LEAST = 0.99;
    private static final String SHOULD_SWIM = "The diver should swim a block east through the stone, moved %.2f";
    private static final String SHOULD_BREATHE = "The diver should take no suffocation buried in stone, health %.1f of %.1f";

    /** Ticks a diver holds sneak or jump, long enough to swim several blocks. */
    private static final int STROKE_TICKS = 60;
    /** How far apart on each horizontal axis the flat patch reaches from its middle. */
    private static final int PATCH_HALF_WIDTH = 2;
    private static final Input SNEAK = new Input(false, false, false, false, false, true, false);
    private static final Input JUMP = new Input(false, false, false, false, true, false, false);
    private static final String SHOULD_STOP = "Sinking should stop on the bedrock at y=%d, the diver reached y=%.2f";
    private static final String SHOULD_RISE = "Jumping into the ceiling at y=%d should take the diver into it, it reached y=%.2f";

    private SculkerTests() {
    }

    /**
     * A diver holding sneak on a flat patch of grass over dirt over bedrock
     * sinks through the grass and dirt and stops on the bedrock, never below
     * its top, so a flat world's floor holds it out of the void.
     *
     * @param helper the gametest helper
     */
    public static void sculkerStopsAtBedrock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos grass = helper.absolutePos(new BlockPos(3, LIFT, 3));
        List<Block> layers = List.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT, Blocks.BEDROCK);
        layPatch(level, grass, layers);
        int bedrockTop = grass.getY() - layers.size() + 2;
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.DEEP, DEEP_SCULKER);
        player.teleportTo(grass.getX() + 0.5, grass.getY() + 1, grass.getZ() + 0.5);
        player.setShiftKeyDown(true);
        double lowest = swimFor(player, SNEAK, -1);
        level.getServer().getPlayerList().remove(player);
        clearPatch(level, grass, layers.size());
        helper.assertTrue(lowest >= bedrockTop, String.format(SHOULD_STOP, bedrockTop, lowest));
        helper.succeed();
    }

    /**
     * A diver hanging in open air with its head against a stone ceiling, as
     * at the top of a jump, holds jump and swims
     * up into the stone, so a diver below the strata can always get back in.
     *
     * @param helper the gametest helper
     */
    public static void sculkerRisesIntoACeiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(3, LIFT, 3));
        List<Block> ceiling = List.of(Blocks.STONE, Blocks.STONE, Blocks.STONE);
        BlockPos ceilingTop = floor.above(5);
        layPatch(level, ceilingTop, ceiling);
        layPatch(level, floor, List.of(Blocks.STONE));
        int ceilingBottom = floor.getY() + 3;
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.DEEP, DEEP_SCULKER);
        player.teleportTo(floor.getX() + 0.5, ceilingBottom - player.getBbHeight(), floor.getZ() + 0.5);
        swimFor(player, JUMP, 1);
        double reached = player.getY();
        level.getServer().getPlayerList().remove(player);
        clearPatch(level, ceilingTop, ceiling.size());
        clearPatch(level, floor, 1);
        helper.assertTrue(reached > ceilingBottom, String.format(SHOULD_RISE, ceilingBottom, reached));
        helper.succeed();
    }

    /**
     * Ticks a diver the way its client does: the tick moves it by its
     * velocity under vanilla physics, jumping where jump is held, and after
     * the tick a diver passing through stone takes the swim stroke's velocity
     * for the next, as SculkerClient sets it.
     *
     * @param player the diver
     * @param keys   the keys its client reports held
     * @param rise   the rise input those keys give, jump 1, sneak -1
     * @return the lowest the diver's feet stood
     */
    private static double swimFor(ServerPlayer player, Input keys, double rise) {
        player.setLastClientInput(keys);
        player.setJumping(keys.jump());
        double lowest = player.getY();
        for (int tick = 0; tick < STROKE_TICKS; tick++) {
            player.doTick();
            if (SculkerSwim.passesThrough(player)) {
                player.setDeltaMovement(SculkerSwim.swimStroke(player, 0, 0, rise));
            }
            lowest = Math.min(lowest, player.getY());
        }
        return lowest;
    }

    /**
     * Lays a patch of layers, the first at the top cell given and each below the last.
     *
     * @param level  the level
     * @param top    the middle cell of the top layer
     * @param layers the blocks from the top down
     */
    private static void layPatch(ServerLevel level, BlockPos top, List<Block> layers) {
        for (int down = 0; down < layers.size(); down++) {
            BlockPos middle = top.below(down);
            for (BlockPos cell : BlockPos.betweenClosed(middle.offset(-PATCH_HALF_WIDTH, 0, -PATCH_HALF_WIDTH),
                    middle.offset(PATCH_HALF_WIDTH, 0, PATCH_HALF_WIDTH))) {
                level.setBlock(cell, layers.get(down).defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void clearPatch(ServerLevel level, BlockPos top, int depth) {
        layPatch(level, top, Collections.nCopies(depth, Blocks.AIR));
    }

    /**
     * A survival player holding Sculker, set inside stone, ticks two seconds
     * there, then swims a block east: it moves the whole block and stands at
     * full health.
     *
     * @param helper the gametest helper
     */
    public static void sculkerSwimsThroughStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos middle = helper.absolutePos(new BlockPos(3, LIFT, 3));
        fillStone(level, middle, Blocks.STONE);
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.DEEP, DEEP_SCULKER);
        player.teleportTo(middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5);
        for (int tick = 0; tick < BURIED_TICKS; tick++) {
            player.doTick();
        }
        double before = player.getX();
        player.move(MoverType.SELF, STROKE);
        double swum = player.getX() - before;
        float health = player.getHealth();
        float maxHealth = player.getMaxHealth();
        level.getServer().getPlayerList().remove(player);
        fillStone(level, middle, Blocks.AIR);
        helper.assertTrue(swum >= SWUM_AT_LEAST, String.format(SHOULD_SWIM, swum));
        helper.assertTrue(health >= maxHealth, String.format(SHOULD_BREATHE, health, maxHealth));
        helper.succeed();
    }

    private static void fillStone(ServerLevel level, BlockPos middle, Block block) {
        for (BlockPos cell : BlockPos.betweenClosed(middle.offset(-STONE_HALF_WIDTH, -STONE_HALF_WIDTH, -STONE_HALF_WIDTH),
                middle.offset(STONE_HALF_WIDTH, STONE_HALF_WIDTH, STONE_HALF_WIDTH))) {
            level.setBlock(cell, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
