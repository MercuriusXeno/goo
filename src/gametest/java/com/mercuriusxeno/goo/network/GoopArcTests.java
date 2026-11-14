package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for Goop's drips flying real arcs, each held through the real
 * stream tick: a level shot lands about where the old range ended, a shot
 * down from a height lands past it, and drips still falling after the hold
 * ends lay their coat when they strike, and a drip striking a mob wobbles it.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-and-slick-throw-keeps-todays-level-reach
 * goop-wobbles-a-mob-its-drip-strikes
 */
@RegistersGameTests
public final class GoopArcTests {

    private static final Identifier GOOP = Identifier.parse("goo:weird_goop");
    private static final String ABILITY_REQUIRED = "Ability registry must hold goo:weird_goop";
    private static final String GOOP_LANDS_LEVEL_AHEAD = "goop_lands_about_eight_blocks_ahead_on_flat_ground";
    private static final String GOOP_COATS_BELOW_A_PERCH = "goop_coats_the_floor_below_a_high_perch";
    private static final String GOOP_LANDS_AFTER_RELEASE = "goop_lands_its_coat_after_the_hold_ends";
    private static final String GOOP_WOBBLES_A_FAR_MOB = "goop_wobbles_a_mob_its_drip_strikes_past_eight_blocks";
    private static final String DRIP_STANDS_A_CELL = "goop_drip_stands_a_one_layer_cell_in_the_tick_it_strikes";
    private static final String DRIPS_ADD_A_LAYER_EACH = "goop_drips_add_a_layer_each_up_to_fifteen";
    /** The cell the landed drips strike, walled in stone on a stone floor so nothing pours. */
    private static final BlockPos DRIPPED_CELL = new BlockPos(4, 1, 4);
    /** The layers a whole block of slime holds. */
    private static final int WHOLE_BLOCK_LAYERS = 15;
    private static final String LAYERS_WRONG = "The dripped cell should hold %d layers of slime, holds %s";
    /** The zombie stands on the perched floor this far east of the perch, well past eight blocks from the eye. */
    private static final int ZOMBIE_EAST = 6;
    /**
     * Ticks Goop is held on the zombie: its one drip a tick scatters over the cone, so enough drips for one
     * to strike a mob thirteen blocks off.
     */
    private static final int WOBBLE_HOLD_TICKS = 40;
    private static final String NOT_WOBBLED = "A zombie %.1f blocks from the streamer, struck by Goop's drips,"
            + " should hold the wobble";
    private static final String NOT_FAR = "The zombie should stand past eight blocks from the eye, stands %.1f off";
    private static final double OLD_REACH = 8;
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    /** Pitched well down, so the cone's drips clear the perch's edge and fall to the floor east of it. */
    private static final float DOWN_OFF_THE_PERCH = 60f;
    /** Where the level shot's streamer stands in the light bay, at its west wall, on the floor's first stone. */
    private static final BlockPos LEVEL_STREAMER_POS = new BlockPos(0, 1, 8);
    /** The light bay is sixteen blocks across and holds no floor of its own, so the floor runs to its east wall. */
    private static final int FLOOR_EAST = 15;
    /** The floor runs this far north and south of the streamer's row, wider than the cone's spread. */
    private static final int FLOOR_HALF_WIDTH = 4;
    /** The nearest and farthest the level shot's coat may stand ahead of the streamer, in cells. */
    private static final int NEAREST_AHEAD = 7;
    private static final int FARTHEST_AHEAD = 9;
    /** The perched floor stands over the bay's barrier roof, in open air the bay's roof does not cut. */
    private static final int PERCHED_FLOOR_Y = 10;
    /** How far the low perch stands over the floor's top, past the eight blocks Goop's range reached. */
    private static final int LOW_PERCH = 11;
    /** How far the high perch stands over the floor's top: its drips fall longer than the hold lasts. */
    private static final int HIGH_PERCH = 30;
    /** A hold one drip a tick sprays the whole fan in, a few ticks over, so every share of the cone has flown. */
    private static final int SPRAY_HOLD_TICKS = CoatRay.FAN_SHARES + 5;
    /** Ticks given the last drips of a hold to fall onto the floor, past the longest fall these tests make. */
    private static final int FALL_TICKS = 60;
    private static final String NO_COAT_AHEAD = "A level Goop shot should coat a floor cell 7 to 9 blocks ahead,"
            + " coated cells are %s";
    private static final String NO_COAT_BELOW = "Goop aimed down from %d blocks up should coat the floor below,"
            + " coated cells are %s";
    private static final String LANDED_BEFORE_RELEASE = "The high perch's first drips should still be falling when"
            + " the hold ends, coated cells are %s";

    private GoopArcTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GOOP_LANDS_LEVEL_AHEAD, GoopArcTests::goopLandsAboutEightBlocksAhead);
        GooTestFunctions.registerTest(registrar, GOOP_COATS_BELOW_A_PERCH, GoopArcTests::goopCoatsTheFloorBelowAPerch);
        GooTestFunctions.registerTest(registrar, GOOP_LANDS_AFTER_RELEASE, GoopArcTests::goopLandsItsCoatAfterRelease);
        GooTestFunctions.registerTest(registrar, GOOP_WOBBLES_A_FAR_MOB, GoopArcTests::goopWobblesAFarMobItsDripStrikes);
        GooTestFunctions.registerTest(registrar, DRIP_STANDS_A_CELL, GoopArcTests::goopDripStandsAOneLayerCell);
        GooTestFunctions.registerTest(registrar, DRIPS_ADD_A_LAYER_EACH, GoopArcTests::goopDripsAddALayerEach);
    }

    /**
     * One Goop drip striking a bare stone floor stands a slime cell of one
     * layer over it in the tick it strikes.
     * drip-creates-a-volume-cell-immediately
     *
     * @param helper the gametest helper
     */
    public static void goopDripStandsAOneLayerCell(GameTestHelper helper) {
        wallInOnStone(helper, DRIPPED_CELL);
        ServerPlayer caster = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        LandedDrips.landOn(helper, caster, goop(helper), DRIPPED_CELL, 1);
        assertLayers(helper, DRIPPED_CELL, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /**
     * Goop drips striking a standing slime cell add a layer each: two in one
     * tick raise a one-layer cell to three, and a cell of fifteen layers, a
     * whole block, takes no more.
     * drip-on-a-standing-cell-increases-it
     * world-goo-fills-a-block-space-in-one-to-fifteen-layers
     *
     * @param helper the gametest helper
     */
    public static void goopDripsAddALayerEach(GameTestHelper helper) {
        wallInOnStone(helper, DRIPPED_CELL);
        ServerPlayer caster = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        AbilityDefinition goop = goop(helper);
        LandedDrips.landOn(helper, caster, goop, DRIPPED_CELL, 1);
        LandedDrips.landOn(helper, caster, goop, DRIPPED_CELL, 2);
        assertLayers(helper, DRIPPED_CELL, 3);
        helper.setBlock(DRIPPED_CELL, GooBlocks.SLIME_COAT.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, WHOLE_BLOCK_LAYERS));
        LandedDrips.landOn(helper, caster, goop, DRIPPED_CELL, 1);
        assertLayers(helper, DRIPPED_CELL, WHOLE_BLOCK_LAYERS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.succeed();
    }

    /**
     * Lays a stone floor under a cell and stone on its four sides, so slime in it cannot pour.
     *
     * @param helper the gametest helper
     * @param cell   the cell
     */
    private static void wallInOnStone(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
    }

    private static void assertLayers(GameTestHelper helper, BlockPos cell, int layers) {
        BlockState state = helper.getBlockState(cell);
        Object held = state.is(GooBlocks.SLIME_COAT.get()) ? state.getValue(VolumeCellBlock.LAYERS) : state;
        helper.assertTrue(Integer.valueOf(layers).equals(held), String.format(LAYERS_WRONG, layers, held));
    }

    /**
     * A streamer standing on a flat stone floor holds Goop level, then lets
     * go: once the drips have fallen a slime coat stands in a floor cell 7 to
     * 9 blocks ahead along the look.
     * goop-and-slick-throw-keeps-todays-level-reach
     *
     * @param helper the gametest helper
     */
    public static void goopLandsAboutEightBlocksAhead(GameTestHelper helper) {
        BlockPos floor = LEVEL_STREAMER_POS.below();
        layFloor(helper, floor.getY(), floor.getZ());
        ServerPlayer streamer = goopStreamerAt(helper, LEVEL_STREAMER_POS, LEVEL);
        int hold = SPRAY_HOLD_TICKS;
        holdGoopFor(helper, streamer, hold);
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<BlockPos> coated = coatedOver(helper, floor.getY(), floor.getZ());
            boolean ahead = coated.stream().anyMatch(cell -> cell.getZ() == floor.getZ()
                    && cell.getX() - LEVEL_STREAMER_POS.getX() >= NEAREST_AHEAD
                    && cell.getX() - LEVEL_STREAMER_POS.getX() <= FARTHEST_AHEAD);
            helper.assertTrue(ahead, String.format(NO_COAT_AHEAD, coated));
            helper.succeed();
        });
    }

    /**
     * A streamer on a perch eleven blocks over a stone floor holds Goop aimed
     * down off the perch: once the drips have fallen slime stands on the floor.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper the gametest helper
     */
    public static void goopCoatsTheFloorBelowAPerch(GameTestHelper helper) {
        layFloor(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
        ServerPlayer streamer = perchedStreamer(helper, LOW_PERCH);
        int hold = SPRAY_HOLD_TICKS;
        holdGoopFor(helper, streamer, hold);
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<BlockPos> coated = coatedOver(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
            helper.assertTrue(!coated.isEmpty(), String.format(NO_COAT_BELOW, LOW_PERCH, coated));
            helper.succeed();
        });
    }

    /**
     * A streamer on a perch thirty blocks over a stone floor holds Goop
     * aimed down for a coat's worth and lets go before the first drip lands:
     * the floor stands bare when the hold ends, and slime stands on it once
     * the drips have fallen.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param helper the gametest helper
     */
    public static void goopLandsItsCoatAfterRelease(GameTestHelper helper) {
        layFloor(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
        ServerPlayer streamer = perchedStreamer(helper, HIGH_PERCH);
        int hold = SPRAY_HOLD_TICKS;
        holdGoopFor(helper, streamer, hold);
        helper.runAfterDelay(hold + 1, () -> {
            List<BlockPos> coated = coatedOver(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
            helper.assertTrue(coated.isEmpty(), String.format(LANDED_BEFORE_RELEASE, coated));
        });
        helper.runAfterDelay(hold + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            List<BlockPos> coated = coatedOver(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
            helper.assertTrue(!coated.isEmpty(), String.format(NO_COAT_BELOW, HIGH_PERCH, coated));
            helper.succeed();
        });
    }

    /**
     * A streamer on a perch eleven blocks over a stone floor holds Goop aimed
     * at a zombie standing on the floor below, farther than eight blocks
     * from the eye: once the drips have fallen the zombie holds the wobble.
     * goop-wobbles-a-mob-its-drip-strikes
     *
     * @param helper the gametest helper
     */
    public static void goopWobblesAFarMobItsDripStrikes(GameTestHelper helper) {
        layFloor(helper, PERCHED_FLOOR_Y, LEVEL_STREAMER_POS.getZ());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                new BlockPos(LEVEL_STREAMER_POS.getX() + ZOMBIE_EAST, PERCHED_FLOOR_Y + 1, LEVEL_STREAMER_POS.getZ()));
        ServerPlayer streamer = perchedStreamer(helper, LOW_PERCH);
        DripAim.at(streamer, zombie.getBoundingBox().getCenter(), goop(helper).delivery().throwSpeed());
        double off = streamer.getEyePosition().distanceTo(zombie.getBoundingBox().getCenter());
        helper.assertTrue(off > OLD_REACH, String.format(NOT_FAR, off));
        holdGoopFor(helper, streamer, WOBBLE_HOLD_TICKS);
        helper.runAfterDelay(WOBBLE_HOLD_TICKS + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(streamer);
            helper.assertTrue(zombie.hasData(GooAttachments.WOBBLED), String.format(NOT_WOBBLED, off));
            helper.succeed();
        });
    }

    /**
     * Lays a stone floor across the light bay at a height, east of its west
     * wall and either side of a row.
     *
     * @param helper the gametest helper
     * @param y      the floor's height
     * @param row    the row the floor centers on
     */
    private static void layFloor(GameTestHelper helper, int y, int row) {
        for (int east = 0; east <= FLOOR_EAST; east++) {
            for (int z = row - FLOOR_HALF_WIDTH; z <= row + FLOOR_HALF_WIDTH; z++) {
                helper.setBlock(new BlockPos(east, y, z), Blocks.STONE);
            }
        }
    }

    /**
     * The cells over a floor that hold slime.
     *
     * @param helper the gametest helper
     * @param y      the floor's height
     * @param row    the row the floor centers on
     * @return each slime cell over the floor
     */
    private static List<BlockPos> coatedOver(GameTestHelper helper, int y, int row) {
        List<BlockPos> coated = new ArrayList<>();
        for (int east = 0; east <= FLOOR_EAST; east++) {
            for (int z = row - FLOOR_HALF_WIDTH; z <= row + FLOOR_HALF_WIDTH; z++) {
                BlockPos cell = new BlockPos(east, y + 1, z);
                if (helper.getBlockState(cell).is(GooBlocks.SLIME_COAT.get())) {
                    coated.add(cell);
                }
            }
        }
        return coated;
    }

    /**
     * Stands a Goop streamer on a stone perch at the floor's west edge, a
     * height over the perched floor's top, aimed down and east off the perch.
     *
     * @param helper the gametest helper
     * @param height how far the perch's top stands over the floor's top
     * @return the streamer
     */
    private static ServerPlayer perchedStreamer(GameTestHelper helper, int height) {
        BlockPos stand = new BlockPos(LEVEL_STREAMER_POS.getX(), PERCHED_FLOOR_Y + 1 + height,
                LEVEL_STREAMER_POS.getZ());
        helper.setBlock(stand.below(), Blocks.STONE);
        return goopStreamerAt(helper, stand, DOWN_OFF_THE_PERCH);
    }

    /**
     * Stands a Goop streamer facing east, pitched, taught Goop.
     *
     * @param helper the gametest helper
     * @param stand  the cell the streamer stands in
     * @param pitch  the look's pitch, down from level
     * @return the streamer
     */
    private static ServerPlayer goopStreamerAt(GameTestHelper helper, BlockPos stand, float pitch) {
        ServerPlayer streamer = SelfDeliveryTests.invoker(helper, GooTypes.WEIRD);
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(stand));
        streamer.setPos(feet.x, feet.y, feet.z);
        streamer.setYRot(FACING_EAST);
        streamer.setXRot(pitch);
        KnownRecipes.teachRequires(streamer, goop(helper));
        return streamer;
    }

    /**
     * Streams Goop every tick of a hold from the first tick, aimed wherever the streamer looks.
     *
     * @param helper   the gametest helper
     * @param streamer the streaming player
     * @param ticks    how many ticks the hold lasts
     */
    private static void holdGoopFor(GameTestHelper helper, ServerPlayer streamer, int ticks) {
        Vec3 eye = streamer.getEyePosition();
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.WEIRD), GOOP.toString(), eye, eye);
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(streamer, tick));
        }
    }


    private static AbilityDefinition goop(GameTestHelper helper) {
        AbilityDefinition goop = AbilityRegistry.of(helper.getLevel()).getAbility(GOOP);
        helper.assertTrue(goop != null, ABILITY_REQUIRED);
        return goop;
    }
}
