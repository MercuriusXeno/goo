package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.block.ability.LayerFalls;
import com.mercuriusxeno.goo.block.ability.MortarCellBlock;
import com.mercuriusxeno.goo.block.ability.SideCoat;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Kinetic's Grab at blocks painted with Mortar, driven through the channel's stream tick: the grab
 * lifts the blocks behind the Mortar out of the face it coats as one held hunk, as deep as the Mortar's layers.
 * decision grab-lifts-what-contiguous-rock-goo-paints-as-one-unit
 * decision grab-lift-of-mortar-pulls-out-of-the-coated-face
 */
@RegistersGameTests
public final class GrabMortarLiftTests {
    private static final String LIFTS_THE_PAINTED_GROUND = "grab_lifts_the_ground_mortar_paints";

    private static final Identifier KINETIC_GRAB = Identifier.parse("goo:kinetic_grab");
    /** The ground: two blocks of stone deep across the bay's east side, its top at y 3. */
    private static final int GROUND_LOW_Y = 1;
    private static final int GROUND_HIGH_Y = 2;
    private static final int GROUND_LOW_X = 1;
    private static final int GROUND_HIGH_X = 6;
    private static final int GROUND_LOW_Z = 0;
    private static final int GROUND_HIGH_Z = 4;
    /** The player stands on the ground at its west edge. */
    private static final BlockPos STAND_POS = new BlockPos(1, 3, 2);
    /** Two layers of Mortar lying on the ground's top, three blocks east of the player. */
    private static final BlockPos MORTAR_CELL = new BlockPos(4, 3, 2);
    private static final int MORTAR_LAYERS = 2;
    private static final List<BlockPos> PAINTED_COLUMN = List.of(MORTAR_CELL.below(), MORTAR_CELL.below(2));
    /** The top of the ground, which a lifted hunk's blocks stand above. */
    private static final double GROUND_TOP = GROUND_HIGH_Y + 1;
    /** Channel ticks enough for the held hunk to rise out of its hole to the hold. */
    private static final int HOLD_TICKS = 30;
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    private static final int HELD_GOO = 3;
    /** How far along the look a hold tick names its end; Grab reaches by its own range whatever this. */
    private static final double AIM_LENGTH = 3;
    private static final double HALF = 0.5;
    private static final String ABILITY_REQUIRED = "goo:kinetic_grab must be loaded";
    /** The bay's far corner, so a hunk count reads this test's bay alone beside a batch's other tests. */
    private static final BlockPos BAY_FAR_CORNER = new BlockPos(5, 5, 5);

    private static final String LIFTS_THE_PAINTED_WALL = "grab_lifts_the_wall_mortar_paints";
    /** The wall: two blocks of stone thick along the bay's west side, its east face at x 2. */
    private static final int WALL_LOW_X = 0;
    private static final int WALL_HIGH_X = 1;
    private static final int WALL_LOW_Y = 1;
    private static final int WALL_HIGH_Y = 3;
    private static final double WALL_FACE = WALL_HIGH_X + 1;
    /** Two layers of Mortar clinging to the wall's east face. */
    private static final BlockPos WALL_MORTAR_CELL = new BlockPos(2, 2, 2);
    private static final List<BlockPos> PAINTED_ROW = List.of(WALL_MORTAR_CELL.west(), WALL_MORTAR_CELL.west(2));
    /** The player stands on the bay's floor at its east edge, facing the wall. */
    private static final BlockPos WALL_STAND_POS = new BlockPos(5, 1, 2);
    private static final float FACING_WEST = 90f;
    /** The look raised once the grab takes, so Grab's hold point hangs clear of the wall in the open bay. */
    private static final float LOOKING_UP = -45f;
    /** Channel ticks enough for the held piece to slide out of the wall and rise to the hold. */
    private static final int WALL_HOLD_TICKS = 40;

    private static final String SPENDS_A_LAYER_PER_BLOCK = "grab_spends_a_mortar_layer_per_block_lifted";
    /** Raised ground two blocks of stone deep, its top at y 4, over a gap of air at y 1 on the bay's own floor. */
    private static final int RAISED_LOW_Y = 2;
    private static final int RAISED_HIGH_Y = 3;
    private static final int FLOOR_Y = 0;
    /** Thirteen layers of Mortar on the raised ground, more than the two blocks of it the lift can take. */
    private static final BlockPos DEEP_MORTAR_CELL = MORTAR_CELL.above();
    private static final int DEEP_LAYERS = 13;
    private static final List<BlockPos> RAISED_COLUMN = List.of(DEEP_MORTAR_CELL.below(), DEEP_MORTAR_CELL.below(2));
    private static final int DEEP_LAYERS_LEFT = DEEP_LAYERS - RAISED_COLUMN.size();
    /** Channel ticks enough for the hunk to rise and the Mortar it leaves to fall and land. */
    private static final int HOLD_TILL_LANDED_TICKS = 60;

    private static final String LEAVES_A_STUCK_COLUMN_UNSPENT = "grab_leaves_a_stuck_mortar_column_unspent";
    /** Three joined Mortar cells in a row on the ground, a stone block standing over the middle one. */
    private static final BlockPos STUCK_MORTAR_CELL = MORTAR_CELL;
    private static final List<BlockPos> OUTER_MORTAR_CELLS = List.of(MORTAR_CELL.west(), MORTAR_CELL.east());
    private static final BlockPos OVER_THE_STUCK_CELL = STUCK_MORTAR_CELL.above();
    /** What the bay's Mortar totals once the outer cells pay a layer for each block lifted: the middle cell's. */
    private static final int STUCK_LAYERS_LEFT = MORTAR_LAYERS;

    /**
     * Where the player stands on the raised ground for a grab whose hold hangs the piece straight over its hole:
     * Grab holds three blocks along the look, so a look turned down 25 degrees from here ends over the hole's middle.
     */
    private static final Vec3 OVER_THE_HOLE_STAND = new Vec3(1.781, RAISED_HIGH_Y + 1, 2.5);
    private static final float LOOKING_DOWN_OVER_THE_HOLE = 25f;
    private static final String LEFTOVER_RIDES_AND_DRIPS = "grab_leftover_mortar_rides_and_drips_off";
    private static final String RELEASE_SHEDS_AT_ONCE = "grab_release_sheds_riding_mortar_at_once";
    private static final String WALL_LEFTOVER_DRIPS_DOWN = "grab_wall_leftover_mortar_drips_down";
    /** Test ticks at which the grab's age reads 1, 9 and past 50, the grab taking on test tick 1. */
    private static final int AFTER_THE_GRAB = 2;
    private static final int STILL_QUIVERING = 10;
    private static final int ALL_SHED = 52;
    /** Channel ticks enough for every shed layer's drip to land. */
    private static final int HOLD_TILL_DRIPS_LAND = 80;
    /** The last channel tick of a hold that ends before the leftover sheds. */
    private static final int RELEASE_AFTER = 5;
    /** The latest test tick a released hold has let go by: its last channel tick and its grace. */
    private static final int RELEASED_BY = RELEASE_AFTER + GrabEvents.HOLD_GRACE_TICKS + 2;
    /** Five layers of Mortar on the wall's east face, high on a wall standing over a floor three blocks below. */
    private static final int HIGH_WALL_Y = 5;
    private static final BlockPos HIGH_WALL_MORTAR_CELL = new BlockPos(2, 4, 2);
    private static final int WALL_LAYERS = 5;
    private static final int WALL_LAYERS_LEFT = WALL_LAYERS - PAINTED_ROW.size();
    /** The floor cells, the open row over the bay's stone floor at y 0. */
    private static final int ON_THE_FLOOR_Y = FLOOR_Y + 1;

    private GrabMortarLiftTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIFTS_THE_PAINTED_GROUND, GrabMortarLiftTests::liftsTheGroundMortarPaints);
        GooTestFunctions.registerTest(registrar, LIFTS_THE_PAINTED_WALL, GrabMortarLiftTests::liftsTheWallMortarPaints);
        GooTestFunctions.registerTest(registrar, SPENDS_A_LAYER_PER_BLOCK,
                GrabMortarLiftTests::spendsAMortarLayerPerBlockLifted);
        GooTestFunctions.registerTest(registrar, LEAVES_A_STUCK_COLUMN_UNSPENT,
                GrabMortarLiftTests::leavesAStuckMortarColumnUnspent);
        GooTestFunctions.registerTest(registrar, LEFTOVER_RIDES_AND_DRIPS,
                GrabMortarLiftTests::leftoverMortarRidesAndDripsOff);
        GooTestFunctions.registerTest(registrar, RELEASE_SHEDS_AT_ONCE,
                GrabMortarLiftTests::releaseShedsRidingMortarAtOnce);
        GooTestFunctions.registerTest(registrar, WALL_LEFTOVER_DRIPS_DOWN,
                GrabMortarLiftTests::wallLeftoverMortarDripsDown);
    }

    /**
     * A mock player Grabbing at 13 layers of Mortar over two blocks of stone, holding the piece over its hole,
     * carries the 11 leftover layers on the piece, none dripping before the grab's tick 10 and all dripped off by its
     * tick 50, and they land as Mortar on the hole's floor.
     * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
     *
     * @param helper the gametest helper
     */
    public static void leftoverMortarRidesAndDripsOff(GameTestHelper helper) {
        ServerPlayer player = grabOverTheHole(helper, HOLD_TILL_DRIPS_LAND);
        helper.runAfterDelay(AFTER_THE_GRAB, () -> {
            helper.assertTrue(MortarCellBlock.layersAt(helper.getLevel(), helper.absolutePos(DEEP_MORTAR_CELL)) == 0,
                    "The grabbed Mortar's cell should hold no Mortar once the leftover rides the piece");
            assertRiding(helper, player, DEEP_LAYERS_LEFT);
        });
        helper.runAfterDelay(STILL_QUIVERING, () -> {
            assertRiding(helper, player, DEEP_LAYERS_LEFT);
            int falling = fallsInTheBay(helper);
            helper.assertTrue(falling == 0, "No layer should drip off before tick 10, " + falling + " fall");
        });
        helper.runAfterDelay(ALL_SHED, () -> assertRiding(helper, player, 0));
        helper.runAfterDelay(HOLD_TILL_DRIPS_LAND + 1, () -> {
            int pooled = mortarLayersAtY(helper, ON_THE_FLOOR_Y);
            helper.assertTrue(pooled == DEEP_LAYERS_LEFT,
                    "The hole's floor should hold the " + DEEP_LAYERS_LEFT + " dripped layers, holds " + pooled);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * A mock player Grabbing at 13 layers of Mortar over two blocks of stone and letting go at tick 5 sheds the 11
     * riding layers at once, each falling from a cell within or beside the piece.
     * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
     *
     * @param helper the gametest helper
     */
    public static void releaseShedsRidingMortarAtOnce(GameTestHelper helper) {
        ServerPlayer player = grabOverTheHole(helper, RELEASE_AFTER);
        // The leftover rides the piece while the hold stands, so every fall after the release is a shed layer.
        helper.runAfterDelay(RELEASE_AFTER, () -> assertRiding(helper, player, DEEP_LAYERS_LEFT));
        for (int tick = RELEASE_AFTER + 1; tick <= RELEASED_BY; tick++) {
            int checked = tick;
            helper.runAfterDelay(tick, () -> checkShedOnRelease(helper, player, checked));
        }
    }

    private static void checkShedOnRelease(GameTestHelper helper, ServerPlayer player, int tick) {
        BlockHunk hunk = onlyHunk(helper);
        if (hunk.ridingLayers() > 0 && tick < RELEASED_BY) {
            return;
        }
        helper.assertTrue(hunk.ridingLayers() == 0, "The released piece should shed every riding layer by tick "
                + RELEASED_BY + ", " + hunk.ridingLayers() + " still ride");
        AABB beside = hunk.getBoundingBox().inflate(1);
        AABB bay = bay(helper);
        List<LayerFalls.Fall> falls = GooServerState.of(helper.getLevel().getServer()).layerFalls().held().stream()
                .filter(fall -> bay.contains(Vec3.atCenterOf(fall.start()))).toList();
        long near = falls.stream().filter(fall -> beside.contains(Vec3.atCenterOf(fall.start()))).count();
        helper.assertTrue(near == DEEP_LAYERS_LEFT && falls.size() == DEEP_LAYERS_LEFT, "The " + DEEP_LAYERS_LEFT
                + " shed layers should fall from beside the piece, " + near + " of " + falls.size() + " do");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A mock player Grabbing at five layers of Mortar on a stone wall's east face over two blocks of wall, a floor
     * three blocks below, sees the 3 leftover layers drip straight down off the held piece onto the floor.
     * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
     *
     * @param helper the gametest helper
     */
    public static void wallLeftoverMortarDripsDown(GameTestHelper helper) {
        layStone(helper, FLOOR_Y, FLOOR_Y);
        for (int x = WALL_LOW_X; x <= WALL_HIGH_X; x++) {
            for (int y = WALL_LOW_Y; y <= HIGH_WALL_Y; y++) {
                for (int z = GROUND_LOW_Z; z <= GROUND_HIGH_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        helper.setBlock(HIGH_WALL_MORTAR_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.coatProperty(Direction.WEST), SideCoat.ATTACHED)
                .setValue(VolumeCellBlock.LAYERS, WALL_LAYERS));
        ServerPlayer player = grabber(helper, Vec3.atBottomCenterOf(WALL_STAND_POS), FACING_WEST);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(HIGH_WALL_MORTAR_CELL)));
        channel(helper, player, HOLD_TILL_DRIPS_LAND, LOOKING_UP);
        helper.runAfterDelay(AFTER_THE_GRAB, () -> assertRiding(helper, player, WALL_LAYERS_LEFT));
        helper.runAfterDelay(ALL_SHED, () -> assertRiding(helper, player, 0));
        helper.runAfterDelay(HOLD_TILL_DRIPS_LAND + 1, () -> {
            int pooled = mortarLayersAtY(helper, ON_THE_FLOOR_Y);
            helper.assertTrue(pooled == WALL_LAYERS_LEFT,
                    "The floor below the piece should hold the " + WALL_LAYERS_LEFT + " dripped layers, holds "
                            + pooled);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * Lays 13 layers of Mortar on raised ground over air and has a mock player grab them, the look turned down so
     * Grab holds the piece straight over its hole.
     */
    private static ServerPlayer grabOverTheHole(GameTestHelper helper, int channelTicks) {
        layStone(helper, FLOOR_Y, FLOOR_Y);
        layStone(helper, RAISED_LOW_Y, RAISED_HIGH_Y);
        helper.setBlock(DEEP_MORTAR_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, DEEP_LAYERS));
        ServerPlayer player = grabber(helper, OVER_THE_HOLE_STAND, FACING_EAST);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atBottomCenterOf(helper.absolutePos(DEEP_MORTAR_CELL)));
        channel(helper, player, channelTicks, LOOKING_DOWN_OVER_THE_HOLE);
        return player;
    }

    private static void assertRiding(GameTestHelper helper, ServerPlayer player, int layers) {
        BlockHunk hunk = theOneHunk(helper, player);
        helper.assertTrue(hunk.ridingLayers() == layers,
                "The piece should carry " + layers + " riding layers, carries " + hunk.ridingLayers());
    }

    private static BlockHunk onlyHunk(GameTestHelper helper) {
        List<BlockHunk> hunks = helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay(helper));
        helper.assertTrue(hunks.size() == 1, "One hunk should hold the painted blocks, found " + hunks.size());
        return hunks.getFirst();
    }

    private static int fallsInTheBay(GameTestHelper helper) {
        AABB bay = bay(helper);
        return (int) GooServerState.of(helper.getLevel().getServer()).layerFalls().held().stream()
                .filter(fall -> bay.contains(Vec3.atCenterOf(fall.start()))).count();
    }

    private static int mortarLayersAtY(GameTestHelper helper, int y) {
        int layers = 0;
        for (BlockPos cell : BlockPos.betweenClosed(new BlockPos(0, y, 0), new BlockPos(BAY_FAR_CORNER.getX(), y,
                BAY_FAR_CORNER.getZ()))) {
            layers += MortarCellBlock.layersAt(helper.getLevel(), helper.absolutePos(cell));
        }
        return layers;
    }

    /**
     * A mock player Grabbing at 13 layers of Mortar over two blocks of stone with air below holds the two blocks,
     * and the Mortar left in the bay totals 11, one layer spent for each block lifted.
     * decision mortar-lift-spends-one-layer-per-block-lifted
     *
     * @param helper the gametest helper
     */
    public static void spendsAMortarLayerPerBlockLifted(GameTestHelper helper) {
        ServerPlayer player = grabOverTheHole(helper, HOLD_TILL_DRIPS_LAND);
        helper.runAfterDelay(HOLD_TILL_DRIPS_LAND + 1, () -> {
            BlockHunk hunk = theOneHunk(helper, player);
            helper.assertTrue(hunk.blocks().size() == RAISED_COLUMN.size()
                            && hunk.blocks().stream().allMatch(block -> block.state().is(Blocks.STONE)),
                    "The hunk should hold the two stone blocks under the Mortar, held " + hunk.blocks());
            int left = bayMortarLayers(helper) + hunk.ridingLayers();
            helper.assertTrue(left == DEEP_LAYERS_LEFT,
                    "The bay's Mortar should total " + DEEP_LAYERS_LEFT + " layers after the lift, totals " + left);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * A mock player Grabbing at three joined two-layer Mortar cells, a stone block over the middle one, leaves the
     * middle cell's 2 layers on its unmoved stone and lifts the outer columns, which pay their 2 layers each.
     * decision grab-lift-leaves-out-a-column-that-would-get-stuck
     * decision mortar-lift-spends-one-layer-per-block-lifted
     *
     * @param helper the gametest helper
     */
    public static void leavesAStuckMortarColumnUnspent(GameTestHelper helper) {
        layGround(helper);
        helper.setBlock(OVER_THE_STUCK_CELL, Blocks.STONE);
        BlockState twoLayers = GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, MORTAR_LAYERS);
        helper.setBlock(STUCK_MORTAR_CELL, twoLayers);
        OUTER_MORTAR_CELLS.forEach(cell -> helper.setBlock(cell, twoLayers));
        ServerPlayer player = grabber(helper, Vec3.atBottomCenterOf(STAND_POS), FACING_EAST);
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                Vec3.atBottomCenterOf(helper.absolutePos(OUTER_MORTAR_CELLS.getFirst())));
        channel(helper, player, HOLD_TILL_LANDED_TICKS, LEVEL);
        helper.runAfterDelay(HOLD_TILL_LANDED_TICKS + 1, () -> {
            helper.assertTrue(MortarCellBlock.layersAt(helper.getLevel(), helper.absolutePos(STUCK_MORTAR_CELL))
                    == MORTAR_LAYERS, "The stuck middle cell should keep its " + MORTAR_LAYERS + " layers");
            helper.assertBlockPresent(Blocks.STONE, STUCK_MORTAR_CELL.below());
            helper.assertBlockPresent(Blocks.STONE, STUCK_MORTAR_CELL.below(2));
            for (BlockPos outer : OUTER_MORTAR_CELLS) {
                helper.assertBlockPresent(Blocks.AIR, outer.below());
                helper.assertBlockPresent(Blocks.AIR, outer.below(2));
            }
            BlockHunk hunk = theOneHunk(helper, player);
            helper.assertTrue(hunk.blocks().size() == OUTER_MORTAR_CELLS.size() * MORTAR_LAYERS,
                    "The hunk should hold the outer columns' four stone blocks, held " + hunk.blocks());
            int left = bayMortarLayers(helper);
            helper.assertTrue(left == STUCK_LAYERS_LEFT,
                    "The bay's Mortar should total " + STUCK_LAYERS_LEFT + " layers after the lift, totals " + left);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /** Channels Grab for a run of stream ticks, the look turned to a pitch on the second, once the grab takes. */
    private static void channel(GameTestHelper helper, ServerPlayer player, int ticks, float pitchOnceTaken) {
        for (int held = 1; held <= ticks; held++) {
            int tick = held;
            helper.runAfterDelay(tick, () -> {
                if (tick == 2) {
                    player.setXRot(pitchOnceTaken);
                }
                GooStreamHandler.streamTick(player, aimTick(player));
            });
        }
    }

    private static int bayMortarLayers(GameTestHelper helper) {
        int layers = 0;
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.ZERO, BAY_FAR_CORNER)) {
            layers += MortarCellBlock.layersAt(helper.getLevel(), helper.absolutePos(cell));
        }
        return layers;
    }

    private static void layStone(GameTestHelper helper, int lowY, int highY) {
        for (int x = GROUND_LOW_X; x <= GROUND_HIGH_X; x++) {
            for (int y = lowY; y <= highY; y++) {
                for (int z = GROUND_LOW_Z; z <= GROUND_HIGH_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    /**
     * A mock player Grabbing at two layers of Mortar on a stone wall's east face leaves air where the two blocks
     * behind that face stood, and holds one hunk carrying those two blocks out east of the wall.
     *
     * @param helper the gametest helper
     */
    public static void liftsTheWallMortarPaints(GameTestHelper helper) {
        layWall(helper);
        helper.setBlock(WALL_MORTAR_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.coatProperty(Direction.WEST), SideCoat.ATTACHED)
                .setValue(VolumeCellBlock.LAYERS, MORTAR_LAYERS));
        ServerPlayer player = grabber(helper, Vec3.atBottomCenterOf(WALL_STAND_POS), FACING_WEST);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(WALL_MORTAR_CELL)));
        for (int held = 1; held <= WALL_HOLD_TICKS; held++) {
            int tick = held;
            helper.runAfterDelay(tick, () -> {
                if (tick == 2) {
                    player.setXRot(LOOKING_UP);
                }
                GooStreamHandler.streamTick(player, aimTick(player));
            });
        }
        helper.runAfterDelay(WALL_HOLD_TICKS + 1, () -> {
            for (BlockPos cell : PAINTED_ROW) {
                helper.assertBlockPresent(Blocks.AIR, cell);
            }
            BlockHunk hunk = theOneHunk(helper, player);
            helper.assertTrue(hunk.blocks().size() == PAINTED_ROW.size()
                            && hunk.blocks().stream().allMatch(block -> block.state().is(Blocks.STONE)),
                    "The hunk should hold the two stone blocks behind the Mortar, held " + hunk.blocks());
            double west = hunk.getBoundingBox().minX - helper.absolutePos(BlockPos.ZERO).getX();
            helper.assertTrue(west >= WALL_FACE - HALF * HALF,
                    "The hunk should stand east of the wall face at x " + WALL_FACE + ", its west side at " + west);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void layWall(GameTestHelper helper) {
        for (int x = WALL_LOW_X; x <= WALL_HIGH_X; x++) {
            for (int y = WALL_LOW_Y; y <= WALL_HIGH_Y; y++) {
                for (int z = GROUND_LOW_Z; z <= GROUND_HIGH_Z; z++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
    }

    private static BlockHunk theOneHunk(GameTestHelper helper, ServerPlayer player) {
        List<BlockHunk> hunks = helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay(helper));
        helper.assertTrue(hunks.size() == 1, "One hunk should hold the painted blocks, found " + hunks.size());
        BlockHunk hunk = hunks.getFirst();
        helper.assertTrue(GrabEvents.heldBy(player) == hunk, "The player should hold the hunk");
        return hunk;
    }

    /**
     * A mock player Grabbing at two layers of Mortar on stone ground leaves air where the two blocks under it
     * stood, and holds one hunk carrying those two blocks above the hole once the look comes level.
     *
     * @param helper the gametest helper
     */
    public static void liftsTheGroundMortarPaints(GameTestHelper helper) {
        layGround(helper);
        helper.setBlock(MORTAR_CELL, GooBlocks.ROCK_MORTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, MORTAR_LAYERS));
        ServerPlayer player = grabber(helper, Vec3.atBottomCenterOf(STAND_POS), FACING_EAST);
        Vec3 painted = Vec3.atBottomCenterOf(helper.absolutePos(MORTAR_CELL));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, painted);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            int tick = held;
            helper.runAfterDelay(tick, () -> {
                if (tick == 2) {
                    player.setXRot(LEVEL);
                }
                GooStreamHandler.streamTick(player, aimTick(player));
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            for (BlockPos cell : PAINTED_COLUMN) {
                helper.assertBlockPresent(Blocks.AIR, cell);
            }
            List<BlockHunk> hunks = helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay(helper));
            helper.assertTrue(hunks.size() == 1, "One hunk should hold the painted ground, found " + hunks.size());
            BlockHunk hunk = hunks.getFirst();
            helper.assertTrue(hunk.blocks().size() == PAINTED_COLUMN.size()
                            && hunk.blocks().stream().allMatch(block -> block.state().is(Blocks.STONE)),
                    "The hunk should hold the two stone blocks under the Mortar, held " + hunk.blocks());
            double bottom = hunk.getBoundingBox().minY - helper.absolutePos(BlockPos.ZERO).getY();
            helper.assertTrue(bottom >= GROUND_TOP - HALF * HALF,
                    "The hunk should stand above its hole, out of the ground at y " + GROUND_TOP + ", its bottom at "
                            + bottom);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    private static void layGround(GameTestHelper helper) {
        layStone(helper, GROUND_LOW_Y, GROUND_HIGH_Y);
    }

    private static AABB bay(GameTestHelper helper) {
        return AABB.encapsulatingFullBlocks(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(BAY_FAR_CORNER));
    }

    private static GooStreamPayload aimTick(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), KINETIC_GRAB.toString(),
                eye, eye.add(player.getLookAngle().scale(AIM_LENGTH)));
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer grabber(GameTestHelper helper, Vec3 standAt, float facing) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = helper.absoluteVec(standAt);
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(facing);
        player.setYHeadRot(facing);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.KINETIC, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, grab);
        return player;
    }
}
