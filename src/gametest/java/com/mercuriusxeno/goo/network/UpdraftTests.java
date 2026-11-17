package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Typhoon's Updraft as a world goo through the real paths: its stream's
 * drips lay a layer of an Updraft cell each, a cell lifts what stands on it
 * a block for each of its layers and leaves what stands beside it, and a
 * cell lifting spends none of its layers. Each test walls its cells in
 * stone so no cell pours its layers away, and each lift test opens the
 * bay's sky, whose barrier roof would stop a rider six blocks up.
 * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
 * updraft-goo-lifts-one-block-per-layer
 * updraft-goo-is-semi-permanent-and-does-not-deplete
 */
@RegistersGameTests
public final class UpdraftTests {

    private static final String DRIPS_LAY_A_LAYER_EACH = "updraft_drips_lay_a_layer_each";
    private static final String LIFTS_BY_ITS_LAYERS = "updraft_lifts_by_its_layers";
    private static final String KEEPS_ITS_LAYERS_WHILE_LIFTING = "updraft_keeps_its_layers_while_lifting";
    private static final String LEAVES_A_PLAYER_TO_ITS_CLIENT = "updraft_leaves_a_players_motion_to_its_client";
    private static final String GLIDES_A_FALLING_MOB_DOWN = "updraft_glides_a_falling_mob_down";
    private static final Identifier UPDRAFT = Identifier.parse("goo:typhoon_updraft");
    /** The stone the walled tests lay, two blocks deep across the bay. */
    private static final int STONE_TOP_Y = 2;
    private static final int BAY_SPAN = 6;
    /** The floor block the drips strike, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final int DRIPS = 4;
    /** A full cell, lifting fifteen blocks. */
    private static final BlockPos TALL_CELL = new BlockPos(1, STONE_TOP_Y, 1);
    private static final int TALL_LAYERS = 15;
    /** A thin cell, lifting four blocks. */
    private static final BlockPos SHORT_CELL = new BlockPos(4, STONE_TOP_Y, 1);
    private static final int SHORT_LAYERS = 4;
    /** The stone beside the tall cell, outside its column. */
    private static final BlockPos BESIDE = TALL_CELL.east();
    /** The ticks the lift test watches: a fifteen-block rise at the shipped pace and a while hovering. */
    private static final int LIFT_WATCH_TICKS = 100;
    /** The cell the long test stands a zombie on. */
    private static final BlockPos KEPT_CELL = new BlockPos(2, STONE_TOP_Y, 2);
    private static final int KEPT_LAYERS = 6;
    /** Twice the old column's life of two hundred ticks. */
    private static final int KEPT_TICKS = 400;
    /** The cell the player test stands its player on. */
    private static final BlockPos PLAYER_CELL = new BlockPos(2, STONE_TOP_Y, 2);
    /** The motion the player test hands its player, as its client would have moved it: walking east, falling. */
    private static final Vec3 CLIENTS_MOTION = new Vec3(0.2, -0.1, 0);
    private static final int PLAYER_WATCH_TICKS = 10;
    /** A short cell, whose glide zone a zombie falls through. */
    private static final BlockPos SHORT_GLIDE_CELL = new BlockPos(2, STONE_TOP_Y, 2);
    private static final int SHORT_GLIDE_LAYERS = 2;
    /** How far over the short cell's push column the zombie starts, inside its glide zone. */
    private static final double GLIDE_START_OVER_THE_PUSH = 8;
    private static final int GLIDE_WATCH_TICKS = 40;
    /** The fastest a fall through the glide zone may run: the shipped fall cap, with a little slack. */
    private static final double GLIDE_FALL_SLACK = 0.02;
    /** typhoon_updraft.json's fall cap, the fastest a fall over an Updraft cell runs. */
    private static final double GLIDE_FALL_CAP = 0.15;
    /** How far from its cell's layer count a lifted zombie's highest feet may stand. */
    private static final double RISE_SLACK = 1.0;
    /** The most the zombie beside the column may rise and still stand on the ground. */
    private static final double GROUNDED_SLACK = 0.1;
    private static final double SPAWN_OVER_THE_TOP = 0.01;
    private static final String ABILITY_REQUIRED = "The ability registry must hold typhoon_updraft";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Updraft of %d layers, stands %s";
    private static final String RISE_WRONG = "A zombie on a %d-layer Updraft cell should rise %d blocks over its top"
            + " within %.1f, rose %.2f";
    private static final String BESIDE_ROSE = "The zombie beside the column should stay on the ground, rose %.2f";
    private static final String LAYERS_SPENT = "The Updraft cell should keep its %d layers while lifting, stands %s";
    private static final String PLAYER_MOTION_WRITTEN = "The server should leave a player's motion to its client,"
            + " held %s, stands %s";
    private static final String GLIDE_TOO_FAST = "A zombie falling through the glide zone should fall at most %.2f a tick,"
            + " fell %.2f";
    private static final String NOT_LIFTED = "After %d ticks the zombie should stand above the cell's top %.2f, stands at %.2f";

    private UpdraftTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIPS_LAY_A_LAYER_EACH, UpdraftTests::dripsLayALayerEach);
        GooTestFunctions.registerTest(registrar, LIFTS_BY_ITS_LAYERS, UpdraftTests::liftsByItsLayers);
        GooTestFunctions.registerTest(registrar, KEEPS_ITS_LAYERS_WHILE_LIFTING, UpdraftTests::keepsItsLayersWhileLifting);
        GooTestFunctions.registerTest(registrar, LEAVES_A_PLAYER_TO_ITS_CLIENT, UpdraftTests::leavesAPlayersMotionToItsClient);
        GooTestFunctions.registerTest(registrar, GLIDES_A_FALLING_MOB_DOWN, UpdraftTests::glidesAFallingMobDown);
    }

    /**
     * Four Updraft drips striking a walled stone floor, from a typhoon-holding
     * caster who pays for their layers, lay an Updraft cell of four layers.
     *
     * @param helper the gametest helper
     */
    public static void dripsLayALayerEach(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition updraft = AbilityRegistry.of(helper.getLevel()).getAbility(UPDRAFT);
        helper.assertTrue(updraft != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON);
        KnownRecipes.teachRequires(caster, updraft);
        LandedDrips.landOn(helper, caster, updraft, DRIP_FLOOR, DRIPS);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(standsUpdraft(state, DRIPS), String.format(DRIP_LAYERS_WRONG, DRIPS, state));
        helper.succeed();
    }

    /**
     * A zombie on a fifteen-layer cell rises fifteen blocks over its top and
     * one on a four-layer cell four, each within a block, while a zombie on
     * the stone beside the tall cell never leaves the ground.
     *
     * @param helper the gametest helper
     */
    public static void liftsByItsLayers(GameTestHelper helper) {
        layStone(helper);
        Rider tall = new Rider(helper, TALL_CELL, TALL_LAYERS);
        Rider low = new Rider(helper, SHORT_CELL, SHORT_LAYERS);
        Mob beside = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atBottomCenterOf(BESIDE.above()));
        double besideStart = beside.getY();
        double[] besideHighest = {besideStart};
        helper.onEachTick(() -> {
            tall.watch();
            low.watch();
            besideHighest[0] = Math.max(besideHighest[0], beside.getY());
        });
        helper.runAfterDelay(LIFT_WATCH_TICKS, () -> {
            tall.assertRose();
            low.assertRose();
            double rose = besideHighest[0] - besideStart;
            helper.assertTrue(rose < GROUNDED_SLACK, String.format(BESIDE_ROSE, rose));
            helper.succeed();
        });
    }

    /**
     * A six-layer cell lifting a zombie for four hundred ticks still holds
     * its six layers, and the zombie still stands above its top.
     *
     * @param helper the gametest helper
     */
    public static void keepsItsLayersWhileLifting(GameTestHelper helper) {
        layStone(helper);
        Rider rider = new Rider(helper, KEPT_CELL, KEPT_LAYERS);
        int laid = VolumeCellBlock.layersOf(helper.getBlockState(KEPT_CELL));
        helper.runAfterDelay(KEPT_TICKS, () -> {
            BlockState state = helper.getBlockState(KEPT_CELL);
            helper.assertTrue(laid == KEPT_LAYERS && standsUpdraft(state, laid),
                    String.format(LAYERS_SPENT, KEPT_LAYERS, state));
            double feet = rider.zombie.getY();
            helper.assertTrue(feet > rider.top, String.format(NOT_LIFTED, KEPT_TICKS, rider.top, feet));
            helper.succeed();
        });
    }

    /**
     * A player standing on an Updraft cell keeps the motion its client gave
     * it: the server writes no lift onto a player, whose own client moves it,
     * so walking over the goo is never stalled or hitched.
     * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
     *
     * @param helper the gametest helper
     */
    public static void leavesAPlayersMotionToItsClient(GameTestHelper helper) {
        layStone(helper);
        Block updraft = updraftCell();
        helper.setBlock(PLAYER_CELL, updraft == Blocks.AIR ? Blocks.AIR.defaultBlockState()
                : updraft.defaultBlockState().setValue(VolumeCellBlock.LAYERS, KEPT_LAYERS));
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON);
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(PLAYER_CELL))
                .add(0, (double) KEPT_LAYERS / VolumeCellBlock.MAX_LAYERS, 0);
        player.setPos(feet.x, feet.y, feet.z);
        player.setDeltaMovement(CLIENTS_MOTION);
        double[] highestRise = {CLIENTS_MOTION.y};
        // read what the server's tick left before handing the client's motion back for the next
        helper.onEachTick(() -> {
            highestRise[0] = Math.max(highestRise[0], player.getDeltaMovement().y);
            player.setDeltaMovement(CLIENTS_MOTION);
        });
        helper.runAfterDelay(PLAYER_WATCH_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(highestRise[0] == CLIENTS_MOTION.y,
                    String.format(PLAYER_MOTION_WRITTEN, CLIENTS_MOTION, highestRise[0]));
            helper.succeed();
        });
    }

    /**
     * A zombie starting high over a two-layer cell, inside the glide zone
     * above its push column, falls no faster than the shipped fall cap on its
     * way down to the push.
     * updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it
     *
     * @param helper the gametest helper
     */
    public static void glidesAFallingMobDown(GameTestHelper helper) {
        layStone(helper);
        Block updraft = updraftCell();
        helper.setBlock(SHORT_GLIDE_CELL, updraft == Blocks.AIR ? Blocks.AIR.defaultBlockState()
                : updraft.defaultBlockState().setValue(VolumeCellBlock.LAYERS, SHORT_GLIDE_LAYERS));
        double over = (double) SHORT_GLIDE_LAYERS / VolumeCellBlock.MAX_LAYERS + SHORT_GLIDE_LAYERS
                + GLIDE_START_OVER_THE_PUSH;
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                Vec3.atBottomCenterOf(SHORT_GLIDE_CELL).add(0, over, 0));
        double[] last = {zombie.getY()};
        double[] fastest = {0};
        helper.onEachTick(() -> {
            fastest[0] = Math.max(fastest[0], last[0] - zombie.getY());
            last[0] = zombie.getY();
        });
        helper.runAfterDelay(GLIDE_WATCH_TICKS, () -> {
            helper.assertTrue(fastest[0] <= GLIDE_FALL_CAP + GLIDE_FALL_SLACK,
                    String.format(GLIDE_TOO_FAST, GLIDE_FALL_CAP, fastest[0]));
            helper.succeed();
        });
    }

    /**
     * Lays stone two blocks deep across the bay, so a cell set into its top
     * course stands walled on every side.
     *
     * @param helper the gametest helper
     */
    private static void layStone(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 1, 0, BAY_SPAN - 1, STONE_TOP_Y, BAY_SPAN - 1)
                .forEach(pos -> helper.setBlock(pos.immutable(), Blocks.STONE));
    }

    private static boolean standsUpdraft(BlockState state, int layers) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(UPDRAFT)
                && state.getValue(VolumeCellBlock.LAYERS) == layers;
    }

    private static Block updraftCell() {
        return BuiltInRegistries.BLOCK.getValue(UPDRAFT);
    }

    /** A zombie stood on an Updraft cell of a count of layers, its highest feet watched. */
    private static final class Rider {

        private final GameTestHelper helper;
        private final int layers;
        private final Mob zombie;
        /** The cell's top, absolute, where the zombie's feet start. */
        private final double top;
        private double highest;

        Rider(GameTestHelper helper, BlockPos cell, int layers) {
            this.helper = helper;
            this.layers = layers;
            Block updraft = updraftCell();
            helper.setBlock(cell, updraft == Blocks.AIR ? Blocks.AIR.defaultBlockState()
                    : updraft.defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers));
            double over = (double) layers / VolumeCellBlock.MAX_LAYERS + SPAWN_OVER_THE_TOP;
            this.zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atBottomCenterOf(cell).add(0, over, 0));
            this.top = helper.absolutePos(cell).getY() + (double) layers / VolumeCellBlock.MAX_LAYERS;
            this.highest = zombie.getY();
        }

        void watch() {
            highest = Math.max(highest, zombie.getY());
        }

        void assertRose() {
            double rose = highest - top;
            helper.assertTrue(Math.abs(rose - layers) <= RISE_SLACK,
                    String.format(RISE_WRONG, layers, layers, RISE_SLACK, rose));
        }
    }
}
