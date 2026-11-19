package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.SprayerFlow;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.item.SprayerNozzle;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.SprayerSelectPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the sprayer's recipe: six gold ingots and a lever, three gold across the top, two
 * down the right side and the lever at the bottom middle, craft one sprayer
 * (decision sprayer-crafts-from-six-gold-ingots-and-a-lever).
 */
@RegistersGameTests
public final class SprayerTests {
    private static final String SPRAYER_CRAFTS = "sprayer_crafts_from_six_gold_ingots_and_a_lever";
    private static final int GRID_SIDE = 3;
    private static final String NOT_CRAFTED =
            "Six gold ingots and a lever in the caulk gun shape should craft one sprayer, but crafted ";
    private static final String SPRAYER_SPRAYS = "sprayer_sprays_the_residual_of_its_canisters_goo";
    private static final String SPRAYER_DRAINS = "sprayer_drains_its_own_canister_alone";
    private static final String NO_GOOP_AIMED_AT = "The cell over the floor block aimed at, %s, should hold Goop,"
            + " holds %s";
    private static final String DRAINED_WRONG = "The fitted canister should hold less than its %d mb of weird goo"
            + " and the spare canister all of it: fitted %d, spare %d";
    private static final String SPRAYER_SPRAYS_THE_PICK = "sprayer_sprays_the_type_picked_on_its_wheel";
    private static final String NO_SLICK_AIMED_AT = "The cell over the floor block aimed at, %s, should hold Slick,"
            + " holds %s";
    private static final String PICK_DRAINED_WRONG = "The canister should hold less than its %d mb of kinetic goo"
            + " and all %d mb of its weird goo: kinetic %d, weird %d";
    private static final String MISTER_LAYS_ONE_LAYER = "mister_lays_one_layer_and_no_more";
    private static final String MISTER_SPENDS_NOTHING = "mister_spends_nothing_on_covered_spots";
    private static final String MIST_LAID_NOTHING = "The mister should lay Goop on the bare floor, laid none";
    private static final String MIST_STACKED = "Every Goop cell the mister laid should hold one layer: %s";
    private static final String MIST_SPENT = "The first pass should spend weird goo and the second none: started %d,"
            + " after the first %d, after the second %d";
    /** The held ticks of a mister's pass over the floor. */
    private static final int MIST_TICKS = 40;
    /** The second pass's held ticks, its hold's first ticks drawing the same spots as the first pass's. */
    private static final int SECOND_MIST_TICKS = 20;
    /** Ticks between the two passes, past the gap a hold continues over, so the second starts a new hold. */
    private static final int BETWEEN_PASSES = 10;
    /** The kinetic goo the two-type canister holds, less than its weird goo, mB. */
    private static final int CANISTER_KINETIC = 300;
    /** The weird goo each canister starts with, mB. */
    private static final int CANISTER_WEIRD = 500;
    /** A hold long enough that every share of the cone has flown, the center one among them. */
    private static final int HOLD_TICKS = CoatRay.FAN_SHARES + 5;
    /** Ticks given the last drips to fall onto the floor. */
    private static final int FALL_TICKS = 40;
    private static final int FLOOR_SIDE = 8;
    private static final BlockPos STAND = new BlockPos(1, 1, 3);
    private static final float FACING_EAST = -90f;
    /** Pitched down so the look meets the floor a couple of blocks ahead. */
    private static final float DOWN_AT_THE_FLOOR = 45f;
    private static final double REACH = 8;

    private SprayerTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPRAYER_CRAFTS, SprayerTests::sprayerCraftsFromSixGoldIngotsAndALever);
        GooTestFunctions.registerTest(registrar, SPRAYER_SPRAYS, SprayerTests::sprayerSpraysTheResidualOfItsCanistersGoo);
        GooTestFunctions.registerTest(registrar, SPRAYER_DRAINS, SprayerTests::sprayerDrainsItsOwnCanisterAlone);
        GooTestFunctions.registerTest(registrar, SPRAYER_SPRAYS_THE_PICK,
                SprayerTests::sprayerSpraysTheTypePickedOnItsWheel);
        GooTestFunctions.registerTest(registrar, MISTER_LAYS_ONE_LAYER, SprayerTests::misterLaysOneLayerAndNoMore);
        GooTestFunctions.registerTest(registrar, MISTER_SPENDS_NOTHING, SprayerTests::misterSpendsNothingOnCoveredSpots);
    }

    /**
     * A player holding the sprayer, its canister filled with weird goo and nothing learned, holds its use down
     * aimed at a stone floor: once the drips have fallen, Goop stands in the floor block aimed at.
     * decision spray-item-sprays-residuals-beside-the-glove
     *
     * @param helper the gametest helper
     */
    public static void sprayerSpraysTheResidualOfItsCanistersGoo(GameTestHelper helper) {
        ServerPlayer sprayer = sprayerOverAFloor(helper);
        BlockPos aimed = cellAimedAt(helper, sprayer);
        holdTheSprayer(helper, sprayer);
        helper.runAfterDelay(HOLD_TICKS + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(sprayer);
            int layers = VolumeCellBlock.layersOf(helper.getLevel().getBlockState(aimed));
            helper.assertTrue(helper.getLevel().getBlockState(aimed).is(GooBlocks.SLIME_COAT.get()) && layers >= 1,
                    String.format(NO_GOOP_AIMED_AT, aimed, helper.getLevel().getBlockState(aimed)));
            helper.succeed();
        });
    }

    /**
     * The sprayer pays for the Goop it lays from its fitted canister alone: the fitted canister holds less weird
     * goo after the spray, and a second canister of weird goo in the inventory holds what it held.
     * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
     *
     * @param helper the gametest helper
     */
    public static void sprayerDrainsItsOwnCanisterAlone(GameTestHelper helper) {
        ServerPlayer sprayer = sprayerOverAFloor(helper);
        ItemStack spare = weirdCanister();
        sprayer.getInventory().add(spare);
        BlockPos aimed = cellAimedAt(helper, sprayer);
        holdTheSprayer(helper, sprayer);
        helper.runAfterDelay(HOLD_TICKS + FALL_TICKS, () -> {
            int fitted = CanisterItem.getFluidContent(SprayerItem.fittedCanister(sprayer.getMainHandItem()))
                    .volumeOf(GooTypes.WEIRD);
            int other = spareCanisterWeird(sprayer);
            int layers = VolumeCellBlock.layersOf(helper.getLevel().getBlockState(aimed));
            helper.getLevel().getServer().getPlayerList().remove(sprayer);
            helper.assertTrue(layers >= 1, String.format(NO_GOOP_AIMED_AT, aimed, layers));
            helper.assertTrue(fitted < CANISTER_WEIRD && other == CANISTER_WEIRD,
                    String.format(DRAINED_WRONG, CANISTER_WEIRD, fitted, other));
            helper.succeed();
        });
    }

    /**
     * A sprayer whose canister holds more weird goo than kinetic goo, kinetic picked on its wheel through the
     * sprayer's select handler: it lays Slick at the floor block aimed at and drains kinetic goo, its weird goo
     * untouched.
     * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
     *
     * @param helper the gametest helper
     */
    public static void sprayerSpraysTheTypePickedOnItsWheel(GameTestHelper helper) {
        ServerPlayer sprayer = sprayerOverAFloor(helper);
        ItemStack canister = weirdCanister();
        CanisterItem.addGoo(canister, GooTypes.KINETIC, CANISTER_KINETIC);
        SprayerItem.fitCanister(sprayer.getMainHandItem(), canister);
        SprayerSelectPayload.keepOnTheHeldSprayer(sprayer,
                new SprayerSelectPayload(GooTypes.id(GooTypes.KINETIC), SprayerFlow.MEDIUM, SprayerNozzle.SPRAY));
        BlockPos aimed = cellAimedAt(helper, sprayer);
        holdTheSprayer(helper, sprayer);
        helper.runAfterDelay(HOLD_TICKS + FALL_TICKS, () -> {
            CanisterFluidContent left = CanisterItem.getFluidContent(
                    SprayerItem.fittedCanister(sprayer.getMainHandItem()));
            helper.getLevel().getServer().getPlayerList().remove(sprayer);
            BlockState cell = helper.getLevel().getBlockState(aimed);
            helper.assertTrue(cell.is(GooBlocks.KINETIC_SLICK.get()) && VolumeCellBlock.layersOf(cell) >= 1,
                    String.format(NO_SLICK_AIMED_AT, aimed, cell));
            int kinetic = left.volumeOf(GooTypes.KINETIC);
            int weird = left.volumeOf(GooTypes.WEIRD);
            helper.assertTrue(kinetic < CANISTER_KINETIC && weird == CANISTER_WEIRD,
                    String.format(PICK_DRAINED_WRONG, CANISTER_KINETIC, CANISTER_WEIRD, kinetic, weird));
            helper.succeed();
        });
    }

    /**
     * With the mister picked, a sprayer held over a bare stone floor for forty ticks lays Goop on the floor, every
     * cell of it one layer, however often the mist passes over it.
     * decision mister-nozzle-applies-one-layer-at-most
     * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
     *
     * @param helper the gametest helper
     */
    public static void misterLaysOneLayerAndNoMore(GameTestHelper helper) {
        ServerPlayer sprayer = misterOverAFloor(helper);
        holdTheSprayerFor(helper, sprayer, 1, MIST_TICKS);
        helper.runAfterDelay(MIST_TICKS + FALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(sprayer);
            List<Integer> layers = goopLayersOnTheFloor(helper);
            helper.assertTrue(!layers.isEmpty(), MIST_LAID_NOTHING);
            helper.assertTrue(layers.stream().allMatch(layer -> layer == 1), String.format(MIST_STACKED, layers));
            helper.succeed();
        });
    }

    /**
     * The mister spends only for the layers it lays: a first pass over a bare floor spends weird goo, and a second
     * pass whose hold draws the same spots, every one already holding its layer, spends none.
     * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
     *
     * @param helper the gametest helper
     */
    public static void misterSpendsNothingOnCoveredSpots(GameTestHelper helper) {
        ServerPlayer sprayer = misterOverAFloor(helper);
        int[] held = new int[3];
        held[0] = fittedWeird(sprayer);
        holdTheSprayerFor(helper, sprayer, 1, SECOND_MIST_TICKS);
        int secondStart = SECOND_MIST_TICKS + BETWEEN_PASSES;
        helper.runAfterDelay(secondStart, () -> held[1] = fittedWeird(sprayer));
        holdTheSprayerFor(helper, sprayer, secondStart + 1, SECOND_MIST_TICKS);
        helper.runAfterDelay(secondStart + SECOND_MIST_TICKS + 1, () -> {
            held[2] = fittedWeird(sprayer);
            helper.getLevel().getServer().getPlayerList().remove(sprayer);
            helper.assertTrue(held[1] < held[0] && held[2] == held[1],
                    String.format(MIST_SPENT, held[0], held[1], held[2]));
            helper.succeed();
        });
    }

    private static ServerPlayer misterOverAFloor(GameTestHelper helper) {
        ServerPlayer sprayer = sprayerOverAFloor(helper);
        SprayerSelectPayload.keepOnTheHeldSprayer(sprayer, new SprayerSelectPayload("", SprayerFlow.MEDIUM,
                SprayerNozzle.MIST));
        return sprayer;
    }

    private static int fittedWeird(ServerPlayer player) {
        return CanisterItem.getFluidContent(SprayerItem.fittedCanister(player.getMainHandItem()))
                .volumeOf(GooTypes.WEIRD);
    }

    /**
     * The layers of every Goop cell standing over the stone floor.
     *
     * @param helper the gametest helper
     * @return each cell's layers
     */
    private static List<Integer> goopLayersOnTheFloor(GameTestHelper helper) {
        List<Integer> layers = new java.util.ArrayList<>();
        for (int east = 0; east < FLOOR_SIDE; east++) {
            for (int south = 0; south < FLOOR_SIDE; south++) {
                BlockState cell = helper.getBlockState(new BlockPos(east, 1, south));
                if (cell.is(GooBlocks.SLIME_COAT.get())) {
                    layers.add(VolumeCellBlock.layersOf(cell));
                }
            }
        }
        return layers;
    }

    /**
     * Runs the sprayer's held tick every tick from a delay for a count of ticks.
     *
     * @param helper the gametest helper
     * @param player the spraying player
     * @param first  the delay of the first tick
     * @param ticks  how many ticks
     */
    private static void holdTheSprayerFor(GameTestHelper helper, ServerPlayer player, int first, int ticks) {
        for (int tick = first; tick < first + ticks; tick++) {
            helper.runAfterDelay(tick, () -> GooStreamHandler.sprayerTick(player, player.getEyePosition()));
        }
    }

    /**
     * A mock player standing on a stone floor, pitched down at it, holding a sprayer whose canister holds weird
     * goo, and holding no glove, no goo and no learned recipe.
     *
     * @param helper the gametest helper
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer sprayerOverAFloor(GameTestHelper helper) {
        for (int east = 0; east < FLOOR_SIDE; east++) {
            for (int south = 0; south < FLOOR_SIDE; south++) {
                helper.setBlock(new BlockPos(east, 0, south), Blocks.STONE);
            }
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(STAND));
        player.setPos(feet.x, feet.y, feet.z);
        player.setYRot(FACING_EAST);
        player.setXRot(DOWN_AT_THE_FLOOR);
        ItemStack sprayer = new ItemStack(GooItems.SPRAYER.get());
        SprayerItem.fitCanister(sprayer, weirdCanister());
        player.setItemInHand(InteractionHand.MAIN_HAND, sprayer);
        return player;
    }

    private static ItemStack weirdCanister() {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(canister, GooTypes.WEIRD, CANISTER_WEIRD);
        return canister;
    }

    private static int spareCanisterWeird(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(GooItems.CANISTER.get())) {
                return CanisterItem.getFluidContent(stack).volumeOf(GooTypes.WEIRD);
            }
        }
        return 0;
    }

    /**
     * The open cell over the floor block the player's look meets.
     *
     * @param helper the gametest helper
     * @param player the player
     * @return the cell, absolute
     */
    private static BlockPos cellAimedAt(GameTestHelper helper, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = helper.getLevel().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(REACH)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getBlockPos().relative(hit.getDirection());
    }

    /**
     * Runs the sprayer's held tick through GooStreamHandler every tick of a hold, the drips leaving the eye.
     *
     * @param helper the gametest helper
     * @param player the spraying player
     */
    private static void holdTheSprayer(GameTestHelper helper, ServerPlayer player) {
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.sprayerTick(player, player.getEyePosition()));
        }
    }

    /**
     * The recipe manager, handed the sprayer's grid, answers one sprayer.
     *
     * @param helper the gametest helper
     */
    public static void sprayerCraftsFromSixGoldIngotsAndALever(GameTestHelper helper) {
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack lever = new ItemStack(Items.LEVER);
        CraftingInput grid = CraftingInput.of(GRID_SIDE, GRID_SIDE, List.of(
                gold, gold, gold,
                ItemStack.EMPTY, ItemStack.EMPTY, gold,
                ItemStack.EMPTY, lever, gold));
        ServerLevel level = helper.getLevel();
        ItemStack crafted = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid))
                .orElse(ItemStack.EMPTY);
        helper.assertTrue(crafted.is(GooItems.SPRAYER.get()) && crafted.getCount() == 1, NOT_CRAFTED + crafted);
        helper.succeed();
    }
}
