package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.network.HeartOverlayTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Deep's Sculker held from the glove: sneak and jump together
 * take a player into the ground, and from then on it swims through stone
 * with no key pressed into a block; a player that only walks into a wall
 * stays outside it; a diver that rises out of a top face and jumps clear
 * stands on that block, out of the dive; a diver that swims out of the
 * stone into open air is out of the dive and falls, hurt where it lands; a
 * creative flier that switches flight off falls; a diver below the world's
 * floor rises back in; nothing in the stone suffocates a player whose brew
 * stands; and a sculk sensor never hears a player under Sculker walk or jump
 * (decisions sculker-swims-solid-blocks-and-surfaces-at-will,
 * diagnose-and-correct-sculker-flying-through-air,
 * diagnose-and-correct-creative-flight-stuck-on-under-sculker, sculker-grants-suffocation-immunity
 * and sculker-mutes-your-movement-sounds). The client moves a real player, so
 * each test ticks the mock player as its connection would, reporting each
 * move for the fall it builds, and sets the swim velocity after each tick as
 * SculkerClient does. The stone floats above the barrier cap over the
 * test's own bay.
 */
@RegistersGameTests
public final class SculkerTests {
    private static final String SCULKER_SWIMS = "sculker_swims_through_stone";
    private static final String SCULKER_ENTERS = "sculker_enters_on_sneak_and_jump";
    private static final String SCULKER_WALL_HOLDS = "sculker_wall_holds_a_walker";
    private static final String SCULKER_SURFACES = "sculker_surfaces_onto_the_top_face";
    private static final String SCULKER_RISES_BACK = "sculker_rises_back_into_the_world";
    private static final String SCULKER_BREATHES = "sculker_breathes_in_stone";
    private static final String SCULKER_WALKS_SILENT = "sculker_walks_silent_past_a_sensor";
    private static final String SCULKER_HANGS_STILL = "sculker_hangs_still_in_stone";
    private static final String SCULKER_STOPS_AT_BEDROCK = "sculker_stops_at_bedrock";
    private static final String SCULKER_LEAVES_BEDROCK = "sculker_leaves_bedrock";
    private static final String SCULKER_DIVE_ENDS_IN_AIR = "sculker_dive_ends_in_open_air";
    private static final String SCULKER_FALLS_FROM_THE_DIVE = "sculker_falls_from_where_the_dive_ended";
    private static final String SCULKER_FLIGHT_SWITCHES_OFF = "sculker_creative_flight_switches_off";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SCULKER_SWIMS, SculkerTests::sculkerSwimsThroughStone);
        GooTestFunctions.registerTest(registrar, SCULKER_ENTERS, SculkerTests::sculkerEntersOnSneakAndJump);
        GooTestFunctions.registerTest(registrar, SCULKER_WALL_HOLDS, SculkerTests::sculkerWallHoldsAWalker);
        GooTestFunctions.registerTest(registrar, SCULKER_SURFACES, SculkerTests::sculkerSurfacesOntoTheTopFace);
        GooTestFunctions.registerTest(registrar, SCULKER_RISES_BACK, SculkerTests::sculkerRisesBackIntoTheWorld);
        GooTestFunctions.registerTest(registrar, SCULKER_BREATHES, SculkerTests::sculkerBreathesInStone);
        GooTestFunctions.registerTest(registrar, SCULKER_WALKS_SILENT, SculkerTests::sculkerWalksSilentPastASensor);
        GooTestFunctions.registerTest(registrar, SCULKER_HANGS_STILL, SculkerTests::sculkerHangsStillInStone);
        GooTestFunctions.registerTest(registrar, SCULKER_STOPS_AT_BEDROCK, SculkerTests::sculkerStopsAtBedrock);
        GooTestFunctions.registerTest(registrar, SCULKER_LEAVES_BEDROCK, SculkerTests::sculkerLeavesBedrock);
        GooTestFunctions.registerTest(registrar, SCULKER_DIVE_ENDS_IN_AIR, SculkerTests::sculkerDiveEndsInOpenAir);
        GooTestFunctions.registerTest(registrar, SCULKER_FALLS_FROM_THE_DIVE, SculkerTests::sculkerFallsFromWhereTheDiveEnded);
        GooTestFunctions.registerTest(registrar, SCULKER_FLIGHT_SWITCHES_OFF, SculkerTests::sculkerCreativeFlightSwitchesOff);
    }

    private static final Identifier DEEP_SCULKER = Identifier.parse("goo:deep_sculker");
    /** How far above the bay's floor the stone's top layer floats. */
    private static final int LIFT = 12;
    /** The stone reaches this far from its middle cell on each horizontal axis. */
    private static final int HALF_WIDTH = 3;
    /** The stone's layers, its top layer among them. */
    private static final int LAYERS = 5;
    /** Ticks a test holds its keys, long enough to swim several blocks. */
    private static final int STROKE_TICKS = 40;
    /** Ticks a diver sinks from the stone's top, deep enough to sink below it and short of its bottom face. */
    private static final int SINK_TICKS = 10;
    /**
     * Ticks a buried player waits: past a joining player's sixty ticks of
     * spawn invulnerability, and long enough after for suffocation to strike
     * several times.
     */
    private static final int BURIED_TICKS = 120;
    /** The yaw that faces a player east, along +x. */
    private static final float FACING_EAST = -90f;
    /** How far below the stone's top a diver that sank should stand at least. */
    private static final double SANK_AT_LEAST = 0.5;
    /** How far east a diver should swim through stone with forward alone, at the least. */
    private static final double SWUM_AT_LEAST = 1.0;
    /** How far below the world's floor the stranded diver hangs, as a fall out of the world leaves one. */
    private static final double BELOW_THE_FLOOR = 1.8;
    /** A food level short of the eighteen that heals and above the none that starves, so a hit taken stays taken. */
    private static final int NO_HEALING_FOOD = 17;
    private static final Input NONE = Input.EMPTY;
    private static final Input SNEAK_AND_JUMP = new Input(false, false, false, false, true, true, false);
    private static final Input JUMP = new Input(false, false, false, false, true, false, false);
    private static final Input SNEAK = new Input(false, false, false, false, false, true, false);
    private static final Input FORWARD = new Input(true, false, false, false, false, false, false);
    private static final Input FORWARD_AND_JUMP = new Input(true, false, false, false, true, false, false);
    /** Ticks a player walks and jumps beside the sensor. */
    private static final int WALK_TICKS = 40;
    /** Ticks a sensor takes to fall quiet again after it powers, its active span and cooldown with room to spare. */
    private static final int SENSOR_RESET_TICKS = 80;
    /** Ticks a walker heads one way before it turns back, so it paces on the stone and never walks off it. */
    private static final int PACE_TICKS = 15;
    /** The yaw that faces a player west, along -x. */
    private static final float FACING_WEST = 90f;
    /** How far south of the walk the sensor stands, well inside its hearing. */
    private static final int SENSOR_OFFSET = 2;
    private static final String SHOULD_SINK = "Sneak and jump should sink the player below the stone's top at y=%d, it stands at y=%.2f";
    private static final String SHOULD_SWIM_ON = "Once diving, forward alone should swim the player east through stone, it moved %.2f";
    private static final String SHOULD_STAY_OUT = "Walking into the wall should leave the player outside it, its east edge at x=%.2f past the wall at x=%d";
    private static final String SHOULD_STAND = "The diver should stand on the stone's top at y=%d out of the dive: y=%.2f, on ground %b, diving %b";
    private static final String SHOULD_RETURN = "A diver below the world's floor at y=%d should rise back in, it reached y=%.2f";
    private static final String SHOULD_BREATHE = "The player should take no suffocation buried in stone, health %.1f of %.1f";
    /** How far a diver holding no key may drift in two seconds and still read as hanging still. */
    private static final double STILL_WITHIN = 0.05;
    private static final String SHOULD_HANG = "A diver holding no key should hang still in stone, it drifted %.2f in y";
    private static final String SHOULD_MUTE = "A sensor beside a walking, jumping Sculker should stay unpowered, it reached power %d";
    private static final String SHOULD_HEAR = "The same sensor beside the walk with the brew ended should power, it reached power %d";
    private static final String SHOULD_MOVE = "The diver should swim a block east through the stone, moved %.2f";
    private static final String SHOULD_STOP_AT_BEDROCK = "A diver sinking onto bedrock should stop at its top face y=%d, it sank to y=%.3f";
    private static final String SHOULD_LEAVE_BEDROCK = "A diver inside bedrock holding jump should rise out of it, it moved %.2f from y=%.2f";
    /** How far a diver set inside bedrock should rise out of it with jump held, at the least. */
    private static final double ROSE_AT_LEAST = 1.0;
    /** How far into the bedrock layer the stranded diver's feet are set. */
    private static final double INTO_BEDROCK = 0.3;
    /** How far past bedrock's face a diver may stand and still read as stopped at it. */
    private static final double AT_THE_FACE = 1.0E-4;
    /**
     * Ticks a diver swims west from the stone's top layer at its west column,
     * enough to carry its whole body out of the stone's west face; it falls
     * from there onto the barrier rim around its own bay, clear of the bay
     * beside, five blocks down and past the three a fall hurts beyond.
     */
    private static final int SWIM_OUT_TICKS = 8;
    /** Ticks a diver that left the stone is watched falling. */
    private static final int FALL_TICKS = 10;
    /** Ticks between the two fall-distance reads of a diver falling from where its dive ended. */
    private static final int FALL_READ_TICKS = 3;
    /** Ticks a fallen diver is given to land on the barrier rim under the stone's west face. */
    private static final int LAND_TICKS = 60;
    private static final String SHOULD_FALL_OUT_OF_THE_DIVE =
            "A diver that swam out of the stone into open air should be out of the dive and falling: diving %b, y=%.2f then y=%.2f";
    private static final String SHOULD_BUILD_A_FALL =
            "A diver whose dive ended in open air should build fall distance as it falls: diving %b, fell %.2f then %.2f";
    private static final String SHOULD_HURT_ON_LANDING = "A diver that fell from the stone should take fall damage on landing, health %.1f of %.1f, resting at y=%.2f";

    /** How far above the stone's top face a creative flier hovers, past a jump's height so no top face lies under it. */
    private static final int FLIES_ABOVE = 4;
    /** Ticks a creative flier holds sneak and jump together, as a flier does sinking and rising at once. */
    private static final int FLIER_KEY_TICKS = 5;
    /** How far a flier with flight switched off should fall in half a second, well past what a dying swim stroke drifts. */
    private static final double FELL_AT_LEAST = 1.0;
    private static final String SHOULD_FALL_WITH_FLIGHT_OFF =
            "A creative flier under Sculker that switched flight off should fall: flying %b, diving %b, y=%.2f then y=%.2f";

    private SculkerTests() {
    }

    /**
     * A player under Sculker standing on stone holds sneak and jump and sinks
     * below the stone's top; then, holding forward alone and facing east, it
     * swims east through the stone.
     *
     * @param helper the gametest helper
     */
    public static void sculkerEntersOnSneakAndJump(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = brewed(helper);
        player.setYRot(FACING_EAST);
        player.teleportTo(top.getX() + 0.5, top.getY() + 1, top.getZ() + 0.5);
        swimFor(player, SNEAK_AND_JUMP, SINK_TICKS);
        double sunk = player.getY();
        double before = player.getX();
        swimFor(player, FORWARD, STROKE_TICKS / 2);
        double swum = player.getX() - before;
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(sunk < top.getY() + 1 - SANK_AT_LEAST, String.format(SHOULD_SINK, top.getY() + 1, sunk));
        helper.assertTrue(swum >= SWUM_AT_LEAST, String.format(SHOULD_SWIM_ON, swum));
        helper.succeed();
    }

    /**
     * A player under Sculker that never enters the dive walks east into a
     * stone wall and stays outside it.
     *
     * @param helper the gametest helper
     */
    public static void sculkerWallHoldsAWalker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = layStone(helper);
        BlockPos wall = floor.above().east(2);
        for (int up = 0; up < 3; up++) {
            level.setBlock(wall.above(up), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        ServerPlayer player = brewed(helper);
        player.setYRot(FACING_EAST);
        player.teleportTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        player.zza = 1f;
        swimFor(player, FORWARD, STROKE_TICKS);
        double eastEdge = player.getBoundingBox().maxX;
        level.getServer().getPlayerList().remove(player);
        for (int up = 0; up < 3; up++) {
            level.setBlock(wall.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        clearStone(level, floor);
        helper.assertTrue(eastEdge <= wall.getX() + 1e-3, String.format(SHOULD_STAY_OUT, eastEdge, wall.getX()));
        helper.succeed();
    }

    /**
     * A diver inside the stone under open air holds jump, rises out of the
     * top face and stands on the stone, out of the dive.
     *
     * @param helper the gametest helper
     */
    public static void sculkerSurfacesOntoTheTopFace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = diving(helper);
        player.teleportTo(top.getX() + 0.5, top.getY() - 2, top.getZ() + 0.5);
        swimFor(player, JUMP, STROKE_TICKS);
        swimFor(player, NONE, STROKE_TICKS / 2);
        double standsAt = player.getY();
        boolean onGround = player.onGround();
        boolean stillDiving = player.getData(GooAttachments.SCULKER).diving();
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        boolean onTop = Math.abs(standsAt - (top.getY() + 1)) < 0.01;
        helper.assertTrue(onTop && onGround && !stillDiving,
                String.format(SHOULD_STAND, top.getY() + 1, standsAt, onGround, stillDiving));
        helper.succeed();
    }

    /**
     * A creative diver below the world's floor holds jump and rises back into the world.
     *
     * @param helper the gametest helper
     */
    public static void sculkerRisesBackIntoTheWorld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos column = helper.absolutePos(new BlockPos(HALF_WIDTH, 0, HALF_WIDTH));
        ServerPlayer player = diving(helper);
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(column.getX() + 0.5, level.getMinY() - BELOW_THE_FLOOR, column.getZ() + 0.5);
        swimFor(player, JUMP, STROKE_TICKS);
        double reached = player.getY();
        level.getServer().getPlayerList().remove(player);
        helper.assertTrue(reached >= level.getMinY(), String.format(SHOULD_RETURN, level.getMinY(), reached));
        helper.succeed();
    }

    /**
     * A survival player under Sculker, out of the dive, set inside stone,
     * waits six seconds there and stands at full health.
     *
     * @param helper the gametest helper
     */
    public static void sculkerBreathesInStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = brewed(helper);
        player.teleportTo(top.getX() + 0.5, top.getY() - 2, top.getZ() + 0.5);
        float lowest = swimFor(player, NONE, BURIED_TICKS);
        float maxHealth = player.getMaxHealth();
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(lowest >= maxHealth, String.format(SHOULD_BREATHE, lowest, maxHealth));
        helper.succeed();
    }

    /**
     * A survival player that entered the dive, buried in stone, swims a
     * block east through it and, after six seconds buried, stands at full health.
     *
     * @param helper the gametest helper
     */
    public static void sculkerSwimsThroughStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = diving(helper);
        player.setYRot(FACING_EAST);
        player.teleportTo(top.getX() + 0.5, top.getY() - 2, top.getZ() + 0.5);
        float lowest = swimFor(player, NONE, BURIED_TICKS);
        double before = player.getX();
        swimFor(player, FORWARD, STROKE_TICKS / 2);
        double swum = player.getX() - before;
        float maxHealth = player.getMaxHealth();
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(swum >= SWUM_AT_LEAST, String.format(SHOULD_MOVE, swum));
        helper.assertTrue(lowest >= maxHealth, String.format(SHOULD_BREATHE, lowest, maxHealth));
        helper.succeed();
    }

    /**
     * A diver inside stone holding no key hangs where it is for two seconds,
     * weightless, no gravity drawing it down.
     *
     * @param helper the gametest helper
     */
    public static void sculkerHangsStillInStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = diving(helper);
        player.teleportTo(top.getX() + 0.5, top.getY() - 2, top.getZ() + 0.5);
        double before = player.getY();
        swimFor(player, NONE, STROKE_TICKS);
        double drift = player.getY() - before;
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(Math.abs(drift) <= STILL_WITHIN, String.format(SHOULD_HANG, drift));
        helper.succeed();
    }

    /**
     * A diver inside stone with bedrock as the stone's bottom layer holds
     * sneak and sinks until bedrock's top face stops it, never entering it
     * (decision sculker-swims-solid-blocks-and-surfaces-at-will).
     *
     * @param helper the gametest helper
     */
    public static void sculkerStopsAtBedrock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        int bedrockTop = layBedrockFloor(level, top);
        ServerPlayer player = diving(helper);
        player.teleportTo(top.getX() + 0.5, top.getY() - 1, top.getZ() + 0.5);
        double lowest = Double.MAX_VALUE;
        for (int stroke = 0; stroke < STROKE_TICKS; stroke++) {
            swimFor(player, SNEAK, 1);
            lowest = Math.min(lowest, player.getY());
        }
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(lowest >= bedrockTop - AT_THE_FACE, String.format(SHOULD_STOP_AT_BEDROCK, bedrockTop, lowest));
        helper.succeed();
    }

    /**
     * A diver already inside bedrock holds jump and rises out of it, so
     * bedrock never traps a diver (decision sculker-swims-solid-blocks-and-surfaces-at-will).
     *
     * @param helper the gametest helper
     */
    public static void sculkerLeavesBedrock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        int bedrockTop = layBedrockFloor(level, top);
        ServerPlayer player = diving(helper);
        double start = bedrockTop - 1 + INTO_BEDROCK;
        player.teleportTo(top.getX() + 0.5, start, top.getZ() + 0.5);
        swimFor(player, JUMP, STROKE_TICKS / 2);
        double rose = player.getY() - start;
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(rose >= ROSE_AT_LEAST, String.format(SHOULD_LEAVE_BEDROCK, rose, start));
        helper.succeed();
    }

    /**
     * A diver in the stone's top layer, facing west, swims forward out of the
     * stone's west face into open air with no top face under it, and from
     * there is out of the dive and falls
     * (decision diagnose-and-correct-sculker-flying-through-air).
     *
     * @param helper the gametest helper
     */
    public static void sculkerDiveEndsInOpenAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = diving(helper);
        player.setYRot(FACING_WEST);
        player.teleportTo(top.getX() - HALF_WIDTH + 0.5, top.getY(), top.getZ() + 0.5);
        swimFor(player, FORWARD, SWIM_OUT_TICKS);
        double leftAt = player.getY();
        swimFor(player, NONE, FALL_TICKS);
        double fellTo = player.getY();
        boolean stillDiving = player.getData(GooAttachments.SCULKER).diving();
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(!stillDiving && fellTo < leftAt,
                String.format(SHOULD_FALL_OUT_OF_THE_DIVE, stillDiving, leftAt, fellTo));
        helper.succeed();
    }

    /**
     * A survival diver, past its spawn invulnerability, swims out of the
     * stone's west face from its top layer into open air: from where its dive
     * ended it builds fall distance as it falls, and lands on the barrier rim hurt
     * (decision diagnose-and-correct-sculker-flying-through-air).
     *
     * @param helper the gametest helper
     */
    public static void sculkerFallsFromWhereTheDiveEnded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = diving(helper);
        player.setYRot(FACING_WEST);
        player.teleportTo(top.getX() - HALF_WIDTH + 0.5, top.getY(), top.getZ() + 0.5);
        swimFor(player, NONE, BURIED_TICKS);
        swimFor(player, FORWARD, SWIM_OUT_TICKS);
        boolean stillDiving = player.getData(GooAttachments.SCULKER).diving();
        double firstFall = player.fallDistance;
        swimFor(player, NONE, FALL_READ_TICKS);
        double laterFall = player.fallDistance;
        float landed = swimFor(player, NONE, LAND_TICKS);
        float maxHealth = player.getMaxHealth();
        double restsAt = player.getY();
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(!stillDiving && firstFall > 0 && laterFall > firstFall,
                String.format(SHOULD_BUILD_A_FALL, stillDiving, firstFall, laterFall));
        helper.assertTrue(landed < maxHealth, String.format(SHOULD_HURT_ON_LANDING, landed, maxHealth, restsAt));
        helper.succeed();
    }

    /**
     * Turns the stone's bottom layer to bedrock.
     *
     * @param level the level
     * @param top   the stone's top middle cell
     * @return the y of bedrock's top face
     */
    private static int layBedrockFloor(ServerLevel level, BlockPos top) {
        BlockPos bottom = top.below(LAYERS - 1);
        for (BlockPos cell : BlockPos.betweenClosed(bottom.offset(-HALF_WIDTH, 0, -HALF_WIDTH),
                bottom.offset(HALF_WIDTH, 0, HALF_WIDTH))) {
            level.setBlock(cell, Blocks.BEDROCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return bottom.getY() + 1;
    }

    /**
     * A sculk sensor beside a player under Sculker that walks and jumps
     * along the stone stays unpowered; with the brew ended, the same walk
     * beside the same sensor powers it.
     *
     * @param helper the gametest helper
     */
    public static void sculkerWalksSilentPastASensor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        BlockPos start = top.above().west(HALF_WIDTH - 1);
        BlockPos sensor = top.above().south(SENSOR_OFFSET);
        ServerPlayer player = brewed(helper);
        int[] mutedPower = {0};
        int[] heardPower = {0};
        holdTheSensorsChunks(level, sensor, true);
        helper.startSequence()
                .thenExecute(() -> standAt(player, start))
                .thenIdle(2)
                .thenExecute(() -> level.setBlock(sensor, Blocks.SCULK_SENSOR.defaultBlockState(), Block.UPDATE_ALL))
                .thenIdle(SENSOR_RESET_TICKS)
                .thenExecuteFor(WALK_TICKS, () -> mutedPower[0] = Math.max(mutedPower[0], walkTick(player, level, sensor)))
                .thenExecute(() -> {
                    HeldEffectsEvents.end(player, DEEP_SCULKER);
                    standAt(player, start);
                })
                .thenIdle(SENSOR_RESET_TICKS)
                .thenExecuteFor(WALK_TICKS, () -> heardPower[0] = Math.max(heardPower[0], walkTick(player, level, sensor)))
                .thenExecute(() -> {
                    level.getServer().getPlayerList().remove(player);
                    level.setBlock(sensor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    clearStone(level, top);
                    holdTheSensorsChunks(level, sensor, false);
                    helper.assertTrue(mutedPower[0] == 0, String.format(SHOULD_MUTE, mutedPower[0]));
                    helper.assertTrue(heardPower[0] > 0, String.format(SHOULD_HEAR, heardPower[0]));
                })
                .thenSucceed();
    }

    /**
     * Holds the chunks around a sculk sensor ticking, or lets them go: a
     * sensor delivers a vibration only while every chunk beside its own ticks
     * blocks, and holds one it cannot deliver, deaf to every step after it, so
     * a sensor at a chunk's edge past the ticking ground a test bay keeps
     * would hear nothing.
     *
     * @param level  the level
     * @param sensor the sensor's cell
     * @param held   true to hold them, false to let them go
     */
    private static void holdTheSensorsChunks(ServerLevel level, BlockPos sensor, boolean held) {
        ChunkPos middle = ChunkPos.containing(sensor);
        for (int x = middle.x() - 1; x <= middle.x() + 1; x++) {
            for (int z = middle.z() - 1; z <= middle.z() + 1; z++) {
                level.setChunkForced(x, z, held);
            }
        }
    }

    private static void standAt(ServerPlayer player, BlockPos feet) {
        player.setYRot(FACING_EAST);
        player.teleportTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        player.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * One game tick of a player pacing east and west and jumping, ticked as
     * its connection would, read as the sensor's power after it.
     *
     * @param player the player
     * @param level  the level
     * @param sensor the sensor's cell
     * @return the sensor's power now
     */
    private static int walkTick(ServerPlayer player, ServerLevel level, BlockPos sensor) {
        player.setLastClientInput(FORWARD_AND_JUMP);
        player.setJumping(true);
        player.setYRot((player.tickCount / PACE_TICKS) % 2 == 0 ? FACING_EAST : FACING_WEST);
        player.zza = 1f;
        player.doTick();
        BlockState state = level.getBlockState(sensor);
        return state.hasProperty(BlockStateProperties.POWER) ? state.getValue(BlockStateProperties.POWER) : 0;
    }

    /**
     * A survival player holding Sculker from the glove, out of the dive, fed
     * short of healing so any hit it takes shows in its health.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer brewed(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.DEEP, DEEP_SCULKER);
        player.getFoodData().setFoodLevel(NO_HEALING_FOOD);
        return player;
    }

    /**
     * A survival player holding Sculker from the glove, already in the dive.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer diving(GameTestHelper helper) {
        ServerPlayer player = brewed(helper);
        player.setData(GooAttachments.SCULKER, player.getData(GooAttachments.SCULKER).withDiving(true));
        return player;
    }

    /**
     * Ticks a player the way its client does: the tick moves it by its
     * velocity under vanilla physics, jumping where jump is held, and after
     * the tick a diver passing through stone takes the swim stroke's velocity
     * for the next, steered from the stroke before, as SculkerClient sets it.
     *
     * @param player the player
     * @param keys   the keys its client reports held
     * @param ticks  how many ticks to hold them
     * @return the lowest health the player stood at across the ticks
     */
    private static float swimFor(ServerPlayer player, Input keys, int ticks) {
        player.setLastClientInput(keys);
        player.setJumping(keys.jump());
        player.setShiftKeyDown(keys.shift());
        double forward = keys.forward() ? 1 : 0;
        float lowest = player.getHealth();
        Vec3 lastStroke = null;
        for (int tick = 0; tick < ticks; tick++) {
            Vec3 before = player.position();
            player.doTick();
            reportTheMove(player, player.position().subtract(before));
            if (SculkerSwim.passesThrough(player)) {
                Vec3 from = lastStroke == null ? SculkerSwim.enteringStroke(player) : lastStroke;
                lastStroke = SculkerSwim.swimStroke(player, from, forward, 0, SculkerSwim.riseOf(keys));
                player.setDeltaMovement(lastStroke);
            } else {
                lastStroke = null;
            }
            lowest = Math.min(lowest, player.getHealth());
        }
        return lowest;
    }

    /**
     * Hands the server a tick's move the way a client's movement packet does:
     * a server player's own tick builds no fall, so the connection checks the
     * fall from the move the client reports and clears it on any rise.
     *
     * @param player the player
     * @param moved  how far the tick moved it
     */
    private static void reportTheMove(ServerPlayer player, Vec3 moved) {
        player.doCheckFallDamage(moved.x, moved.y, moved.z, player.onGround());
        if (moved.y > 0) {
            player.resetFallDistance();
        }
    }

    /**
     * A creative player under Sculker flies in open air above the stone and
     * holds sneak and jump together, then switches creative flight off as the
     * double tap of jump does, and falls: flying reads false and nothing holds
     * it up (decision diagnose-and-correct-creative-flight-stuck-on-under-sculker).
     *
     * @param helper the gametest helper
     */
    public static void sculkerCreativeFlightSwitchesOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos top = layStone(helper);
        ServerPlayer player = brewed(helper);
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(top.getX() + 0.5, top.getY() + 1 + FLIES_ABOVE, top.getZ() + 0.5);
        swimFor(player, SNEAK_AND_JUMP, FLIER_KEY_TICKS);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        double switchedOffAt = player.getY();
        swimFor(player, NONE, FALL_TICKS);
        double fellTo = player.getY();
        boolean flying = player.getAbilities().flying;
        boolean diving = player.getData(GooAttachments.SCULKER).diving();
        level.getServer().getPlayerList().remove(player);
        clearStone(level, top);
        helper.assertTrue(!flying && switchedOffAt - fellTo >= FELL_AT_LEAST,
                String.format(SHOULD_FALL_WITH_FLIGHT_OFF, flying, diving, switchedOffAt, fellTo));
        helper.succeed();
    }

    /**
     * Fills the floating stone and returns its top layer's middle cell.
     *
     * @param helper the gametest helper
     * @return the stone's top middle cell
     */
    private static BlockPos layStone(GameTestHelper helper) {
        BlockPos top = helper.absolutePos(new BlockPos(HALF_WIDTH + 1, LIFT, HALF_WIDTH + 1));
        fill(helper.getLevel(), top, Blocks.STONE);
        return top;
    }

    private static void clearStone(ServerLevel level, BlockPos top) {
        fill(level, top, Blocks.AIR);
    }

    private static void fill(ServerLevel level, BlockPos top, Block block) {
        for (BlockPos cell : BlockPos.betweenClosed(top.offset(-HALF_WIDTH, 1 - LAYERS, -HALF_WIDTH),
                top.offset(HALF_WIDTH, 0, HALF_WIDTH))) {
            level.setBlock(cell, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
