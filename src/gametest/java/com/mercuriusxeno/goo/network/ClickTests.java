package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.held.HeldEffectsEvents;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Gametest for Deep's Click heard by the sculk: a player clicks at a stone
 * wall ten blocks east, and once the wave arrives a warden twelve blocks from
 * the landing hears it, gains no anger at the player and takes the landing as
 * its disturbance, a sculk sensor four blocks from the landing turns active,
 * and a shrieker beside that sensor stays silent
 * (decision click-vibration-carries-no-source-entity). The click goes through
 * the real throw path, GooThrowHandler.execute. A warden hears every
 * vibration near it, so the scene stands far from every other test, its
 * chunks held loaded while it runs.
 */
@RegistersGameTests
public final class ClickTests {
    private static final String CLICK_TRICKS_THE_SCULK = "click_tricks_the_warden_and_the_sensor";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, CLICK_TRICKS_THE_SCULK, ClickTests::clickTricksTheWardenAndTheSensor);
        GooTestFunctions.registerTest(registrar, CLICK_FREE_UNDER_SCULKER, ClickTests::clickIsFreeUnderSculker);
        GooTestFunctions.registerTest(registrar, CLICK_RIPPLES_FROM_STONE, ClickTests::clickRipplesFromInsideStone);
    }

    private static final String CLICK_RIPPLES_FROM_STONE = "click_ripples_from_inside_stone";
    /** How far east of the diver the buried sensor sits on the eye line. */
    private static final int BURIED_SENSOR_EAST = 9;
    /** How far below the diver's eye the client's camera sits, so its aim from inside stone starts a hair low. */
    private static final double CAMERA_BELOW_EYE = 0.05;
    private static final String SHOULD_TRIP_BURIED = "A click from inside stone should ripple east and trip the sensor buried %d blocks along the look";

    /**
     * A diver under Sculker, eyes inside stone and facing east, clicks with
     * the point the glove aims from inside a block: the camera's own spot, a
     * hair below the eye, where the aim ray meets the block it starts in. The
     * ripple runs east along the look through the stone and trips a sculk
     * sensor buried nine blocks along it
     * (decision submerged-click-ripples-through-the-ground-to-its-range).
     *
     * @param helper the gametest helper
     */
    public static void clickRipplesFromInsideStone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stand = helper.absolutePos(BlockPos.ZERO.above()).offset(FAR_OFF, 0, -FAR_OFF);
        BlockPos sensor = stand.above().east(BURIED_SENSOR_EAST);
        boolean[] tripped = new boolean[1];
        ServerPlayer[] player = new ServerPlayer[1];
        helper.startSequence()
                .thenExecute(() -> buryScene(level, stand, sensor, true))
                .thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(stand), SHOULD_TICK))
                .thenExecute(() -> {
                    player[0] = HeartOverlayTests.selfInvoked(helper, GooTypes.DEEP, DEEP_SCULKER);
                    player[0].setData(GooAttachments.SCULKER,
                            player[0].getData(GooAttachments.SCULKER).withDiving(true));
                    player[0].teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                    player[0].setYRot(FACING_EAST);
                    player[0].setXRot(0);
                    clickAt(player[0], player[0].getEyePosition().add(0, -CAMERA_BELOW_EYE, 0));
                })
                .thenExecuteFor(WATCH_TICKS, () -> {
                    BlockState state = level.getBlockState(sensor);
                    tripped[0] |= state.is(Blocks.SCULK_SENSOR)
                            && SculkSensorBlock.getPhase(state) == SculkSensorPhase.ACTIVE;
                })
                .thenExecute(() -> {
                    level.getServer().getPlayerList().remove(player[0]);
                    buryScene(level, stand, sensor, false);
                    helper.assertTrue(tripped[0], String.format(SHOULD_TRIP_BURIED, BURIED_SENSOR_EAST));
                })
                .thenSucceed();
    }

    /**
     * Lays the stone the diver clicks from, with the sensor buried in it, or
     * clears it, holding its chunks loaded while it stands.
     *
     * @param level  the level
     * @param stand  the cell the diver's feet stand in
     * @param sensor the buried sensor's cell
     * @param laying true to lay the scene, false to clear it
     */
    private static void buryScene(ServerLevel level, BlockPos stand, BlockPos sensor, boolean laying) {
        if (laying) {
            forceChunks(level, stand, true);
        }
        BlockState fill = laying ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        for (BlockPos cell : BlockPos.betweenClosed(stand.offset(-2, -2, -2),
                stand.offset(BURIED_SENSOR_EAST + 3, 3, 2))) {
            level.setBlock(cell, fill, Block.UPDATE_CLIENTS);
        }
        level.setBlock(sensor, laying ? Blocks.SCULK_SENSOR.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        if (!laying) {
            forceChunks(level, stand, false);
        }
    }

    private static final String CLICK_FREE_UNDER_SCULKER = "click_is_free_under_sculker";
    private static final Identifier DEEP_SCULKER = Identifier.parse("goo:deep_sculker");
    private static final String SHOULD_BE_FREE = "A click under Sculker should drain no deep, it drained %d mB";
    private static final String SHOULD_COST_ONE = "A click without Sculker should drain 1 mB of deep, it drained %d mB";

    /**
     * A survival player holding Sculker from the glove clicks and its deep
     * stands where it stood; with Sculker ended, the same click drains 1 mB.
     * Each click goes through GooThrowHandler.execute in one tick, so no
     * upkeep falls between the reads (decision click-costs-one-mb-and-nothing-under-sculker).
     *
     * @param helper the gametest helper
     */
    public static void clickIsFreeUnderSculker(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.DEEP, DEEP_SCULKER);
        int underSculker = drainOfAClick(player);
        HeldEffectsEvents.end(player, DEEP_SCULKER);
        int withoutSculker = drainOfAClick(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(underSculker == 0, String.format(SHOULD_BE_FREE, underSculker));
        helper.assertTrue(withoutSculker == 1, String.format(SHOULD_COST_ONE, withoutSculker));
        helper.succeed();
    }

    /**
     * Clicks once through the real throw path.
     *
     * @param player the clicking player
     * @return the deep the click drained, in mB
     */
    private static int drainOfAClick(ServerPlayer player) {
        int before = deepHeld(player);
        HeartOverlayTests.invoke(player, GooTypes.DEEP, DEEP_CLICK);
        return before - deepHeld(player);
    }

    /**
     * Clicks at a point as the glove does for a free-aim ability: the throw
     * names the point its reticule marks, through the real throw path.
     *
     * @param player the clicking player, holding the glove
     * @param aimed  the point the reticule marks
     */
    private static void clickAt(ServerPlayer player, Vec3 aimed) {
        KnownRecipes.teachRequires(player, AbilityRegistry.of(player.level()).getAbility(DEEP_CLICK));
        GooGloveItem.setSelection(player.getMainHandItem(), GloveSelection.ofAbility(GooTypes.DEEP, DEEP_CLICK));
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.DEEP), NO_ENTITY,
                BlockPos.containing(aimed), Direction.WEST.ordinal(), false, DEEP_CLICK.toString(),
                player.getEyePosition(), aimed));
    }

    private static int deepHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.DEEP, 0);
    }

    private static final Identifier DEEP_CLICK = Identifier.parse("goo:deep_click");
    private static final int NO_ENTITY = -1;
    /** How far off its own bay the scene lies on each horizontal axis, past every other test and ShriekTests' scene. */
    private static final int FAR_OFF = 4096;
    /** How far east of the player the wall stands. */
    private static final int WALL_EAST = 10;
    /** The wall's height, past the player's eye. */
    private static final int WALL_HEIGHT = 4;
    /** The sensor stands two blocks short of the wall and three south of the look, inside its hearing of 8. */
    private static final int SENSOR_SHORT = 2;
    private static final int SENSOR_SOUTH = 3;
    /** The warden stands two blocks short of the wall and twelve north, inside its hearing of 16 and outside the sensor's. */
    private static final int WARDEN_NORTH = 12;
    /** The floor reaches this far from the player each way across the look, past the warden. */
    private static final int FLOOR_HALF_WIDTH = 13;
    /** Ticks the scene is watched after the click: the wave's delay, the vibration's travel and the sensor's active span. */
    private static final int WATCH_TICKS = 40;
    /** The dig cooldown a naturally spawned warden starts with, holding it above ground through the test. */
    private static final long DIG_COOLDOWN_TICKS = 1200L;
    /** The yaw that faces a player east, along +x. */
    private static final float FACING_EAST = -90f;
    private static final String SHOULD_TICK = "The far chunks should tick their entities";
    private static final String SHOULD_SPAWN = "A warden should spawn";
    private static final String SHOULD_DISTURB = "The warden should take the landing %s as its disturbance, it took %s";
    private static final String SHOULD_STAY_CALM = "The warden should gain no anger at the clicking player, it reached %d";
    private static final String SHOULD_TRIP = "The sculk sensor near the landing should turn active";
    private static final String SHOULD_STAY_SILENT = "The shrieker beside the sensor should not shriek";

    private ClickTests() {
    }

    /**
     * The player clicks at the wall, and over the two seconds after it the
     * warden's disturbance, its anger at the player, the sensor's phase and
     * the shrieker's shrieking are read each tick.
     *
     * @param helper the gametest helper
     */
    public static void clickTricksTheWardenAndTheSensor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stand = helper.absolutePos(BlockPos.ZERO.above()).offset(-FAR_OFF, 0, FAR_OFF);
        BlockPos wall = stand.east(WALL_EAST);
        BlockPos sensor = wall.west(SENSOR_SHORT).south(SENSOR_SOUTH);
        BlockPos shrieker = sensor.south();
        BlockPos wardenAt = wall.west(SENSOR_SHORT).north(WARDEN_NORTH);
        Watched watched = new Watched();
        Warden[] warden = new Warden[1];
        ServerPlayer[] player = new ServerPlayer[1];
        helper.startSequence()
                .thenExecute(() -> buildScene(level, stand, wall, sensor, shrieker, true))
                .thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(stand)
                        && level.isPositionEntityTicking(wardenAt), SHOULD_TICK))
                .thenExecute(() -> {
                    warden[0] = spawnWarden(helper, wardenAt);
                    player[0] = clicker(helper, stand);
                    clickAt(player[0], new Vec3(wall.getX(), player[0].getEyeY(), wall.getZ() + 0.5));
                })
                .thenExecuteFor(WATCH_TICKS, () -> watched.read(level, warden[0], player[0], sensor, shrieker))
                .thenExecute(() -> {
                    // the eye stands 1.62 above the floor cell, so the look meets the wall one cell up
                    BlockPos landingCell = wall.west().above(1);
                    warden[0].discard();
                    level.getServer().getPlayerList().remove(player[0]);
                    buildScene(level, stand, wall, sensor, shrieker, false);
                    helper.assertTrue(landingCell.equals(watched.disturbance),
                            String.format(SHOULD_DISTURB, landingCell, watched.disturbance));
                    helper.assertTrue(watched.anger == 0, String.format(SHOULD_STAY_CALM, watched.anger));
                    helper.assertTrue(watched.sensorActive, SHOULD_TRIP);
                    helper.assertFalse(watched.shrieked, SHOULD_STAY_SILENT);
                })
                .thenSucceed();
    }

    /** What the scene showed over the ticks after the click. */
    private static final class Watched {
        private @Nullable BlockPos disturbance;
        private int anger;
        private boolean sensorActive;
        private boolean shrieked;

        void read(ServerLevel level, Warden warden, ServerPlayer player, BlockPos sensor, BlockPos shrieker) {
            if (disturbance == null) {
                disturbance = warden.getBrain().getMemory(MemoryModuleType.DISTURBANCE_LOCATION).orElse(null);
            }
            anger = Math.max(anger, warden.getAngerManagement().getActiveAnger(player));
            BlockState sensorState = level.getBlockState(sensor);
            sensorActive |= sensorState.is(Blocks.SCULK_SENSOR)
                    && SculkSensorBlock.getPhase(sensorState) == SculkSensorPhase.ACTIVE;
            BlockState shriekerState = level.getBlockState(shrieker);
            shrieked |= shriekerState.is(Blocks.SCULK_SHRIEKER) && shriekerState.getValue(SculkShriekerBlock.SHRIEKING);
        }
    }

    /**
     * Lays the scene or clears it: a stone floor under the player, the wall,
     * the sensor and the shrieker, its chunks held loaded while it stands.
     *
     * @param level    the level
     * @param stand    the cell the player stands in
     * @param wall     the wall's bottom cell
     * @param sensor   the sensor's cell
     * @param shrieker the shrieker's cell
     * @param laying   true to lay the scene, false to clear it
     */
    private static void buildScene(ServerLevel level, BlockPos stand, BlockPos wall, BlockPos sensor,
                                   BlockPos shrieker, boolean laying) {
        if (laying) {
            forceChunks(level, stand, true);
        }
        BlockState floor = laying ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        for (BlockPos cell : BlockPos.betweenClosed(stand.below().offset(-1, 0, -FLOOR_HALF_WIDTH),
                wall.below().offset(1, 0, FLOOR_HALF_WIDTH))) {
            level.setBlock(cell, floor, Block.UPDATE_CLIENTS);
        }
        for (int up = 0; up < WALL_HEIGHT; up++) {
            level.setBlock(wall.above(up), floor, Block.UPDATE_CLIENTS);
        }
        level.setBlock(sensor, laying ? Blocks.SCULK_SENSOR.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        level.setBlock(shrieker, laying ? Blocks.SCULK_SHRIEKER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        if (!laying) {
            forceChunks(level, stand, false);
        }
    }

    private static void forceChunks(ServerLevel level, BlockPos stand, boolean forced) {
        ChunkPos low = ChunkPos.containing(stand.offset(-1, 0, -FLOOR_HALF_WIDTH));
        ChunkPos high = ChunkPos.containing(stand.offset(WALL_EAST + 1, 0, FLOOR_HALF_WIDTH));
        for (int x = low.x(); x <= high.x(); x++) {
            for (int z = low.z(); z <= high.z(); z++) {
                level.setChunkForced(x, z, forced);
            }
        }
    }

    private static Warden spawnWarden(GameTestHelper helper, BlockPos at) {
        Warden warden = EntityType.WARDEN.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(warden != null, SHOULD_SPAWN);
        warden.snapTo(Vec3.atBottomCenterOf(at));
        warden.setPersistenceRequired();
        // a warden spawned without finalizeSpawn digs back down at once, and a digging warden hears nothing
        warden.getBrain().setMemoryWithExpiry(MemoryModuleType.DIG_COOLDOWN, Unit.INSTANCE, DIG_COOLDOWN_TICKS);
        helper.getLevel().addFreshEntity(warden);
        return warden;
    }

    /**
     * A mock player holding the glove and deep goo, standing on the floor and
     * facing the wall level.
     *
     * @param helper the gametest helper
     * @param stand  the cell the player stands in
     * @return the player
     */
    private static ServerPlayer clicker(GameTestHelper helper, BlockPos stand) {
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.DEEP);
        player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        return player;
    }
}
