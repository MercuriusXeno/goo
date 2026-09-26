package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for machine block interactions via mock player.
 * Exercises TapInteractionHandler, VatInteractionHandler,
 * HubBlockHandlers, PlexerInteractionHelper, and CrucibleInteraction.
 */
public final class MachineInteractionTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final String TAP_SHOULD_INSERT = "Tap should accept canister insertion";
    private static final String TAP_SLOT_FILLED = "Tap canister slot should be occupied";
    private static final String TAP_HAND_SHRANK = "Held canister stack should shrink by one";
    private static final int TAP_HAND_COUNT = 2;
    private static final int TAP_BLOB_COUNT = 5;
    private static final String TAP_SLOT_EMPTIED = "Tap canister slot should be empty after an empty-hand click";
    private static final String TAP_CANISTER_IN_HAND = "Player should hold the canister taken from the tap";
    private static final String TAP_CANISTER_FILLED = "Tap's slotted canister should hold every poured blob";
    private static final String TAP_BLOB_USED_UP = "Blob stack should be used up by the pour";
    /** South-facing tap: a point on the body's top face, which is the slot region's bottom face. */
    private static final Vec3 TAP_BODY_TOP_HIT_PX = new Vec3(8, 4, 3);
    /** South-facing tap: a point inside the slot region, above the body. */
    private static final Vec3 TAP_SLOT_REGION_HIT_PX = new Vec3(8, 10, 3);
    private static final String VAT_SHOULD_HAVE_CAP = "Vat should have gasket cap after gasket apply";
    private static final String HUB_SHOULD_INSERT = "Hub should have canister after insertion";
    private static final String HUB_SHOULD_PICKUP = "Hub slot should be empty after plain-click pickup";
    private static final String HUB_SHOULD_KEEP_HELD = "Player should still hold a canister after pickup";
    private static final String HUB_SHOULD_HOLD_BOTH = "Player should hold the picked-up canister beside the held one";
    private static final int HUB_SLOT_NORTH = 0;
    private static final int HUB_PICKUP_HAND_COUNT = 2;
    private static final int HUB_PICKUP_DELAY_TICKS = 12;
    private static final double PIXELS_PER_BLOCK = 16.0;
    private static final String PLEXER_SHOULD_SET = "Plexer should have target item after interaction";
    private static final String BLAZE_ROD_STAYS_WHOLE = "A blaze rod click should leave the held stack whole";
    private static final String BLAZE_ROD_LEAVES_COLD = "A blaze rod click should leave the crucible cold";
    private static final String COLD_CRUCIBLE_REFUSES = "A cold crucible with no fuel goo should leave the item entity standing";
    private static final String BLAZE_CRUCIBLE_ABSORBS = "A crucible holding blaze goo should absorb the item entity";
    private static final int CRUCIBLE_BLAZE_FUEL = 100;
    private static final String SPARK_SUCCEEDS = "A flint-and-steel click on a cold crucible should answer SUCCESS";
    private static final String SPARK_HEATS = "A spark should leave the crucible holding the spark's heat";
    private static final String SPARK_COSTS_ONE = "A spark should cost the flint and steel one durability";
    private static final String HOT_SPARK_PASSES = "A flint-and-steel click on a heated crucible should answer PASS";
    private static final String HOT_SPARK_FREE = "A pass should leave the flint and steel undamaged";
    private static final String COAL_MELTED = "The sparked crucible should melt the coal to the end";
    private static final String COAL_LEFT_BLAZE = "The coal's blaze should stand in the reservoir";
    private static final String COAL_LEFT_ROCK = "The coal's rock should stand in the reservoir";
    private static final String COAL_ENDS_HOT = "The crucible should end able to heat";
    private static final int ABSORB_DELAY = 5;
    /** X/Z center of the crucible basin in test-relative coords. */
    private static final double BASIN_CENTER_XZ = 1.5;
    /** Y just above the crucible body surface (13/16 + block y=1). */
    private static final double BASIN_SURFACE_Y = 1.85;
    private static final double BLOCK_CENTER = 0.5;
    private static final double UPPER_HIT_Y = 0.9;

    private MachineInteractionTests() {
    }

    /**
     * Creates a BlockHitResult targeting the center of the block at BE_POS.
     *
     * @param helper the gametest helper
     * @param face   the face to hit
     * @return the hit result
     */
    private static BlockHitResult hit(GameTestHelper helper, Direction face) {
        BlockPos abs = helper.absolutePos(BE_POS);
        return new BlockHitResult(Vec3.atCenterOf(abs), face, abs, false);
    }

    /**
     * Tap: inserting a canister item directly exercises the BE's slot lifecycle
     * and covers TapInteractionHandler's dispatch paths.
     *
     * @param helper the gametest helper
     */
    public static void tapCanisterInsert(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        helper.assertTrue(tap.insertCanister(new ItemStack(GooItems.CANISTER.get())),
                TAP_SHOULD_INSERT);
        helper.assertFalse(tap.getCanister().isEmpty(), TAP_SLOT_FILLED);
        helper.succeed();
    }

    /**
     * Tap: a plain canister click on the top face of the body puts the canister in the slot
     * (decision tap-top-click-inserts-canister).
     *
     * @param helper the gametest helper
     */
    public static void tapTopClickInsertsCanister(GameTestHelper helper) {
        clickEmptyTapWithCanister(helper, TAP_BODY_TOP_HIT_PX, Direction.UP);
    }

    /**
     * Tap: a plain canister click anywhere in the slot region the outline draws puts the
     * canister in the slot (decision tap-top-click-inserts-canister).
     *
     * @param helper the gametest helper
     */
    public static void tapSlotRegionClickInsertsCanister(GameTestHelper helper) {
        clickEmptyTapWithCanister(helper, TAP_SLOT_REGION_HIT_PX, Direction.NORTH);
    }

    /**
     * Tap: an empty-hand click inside the slot region of a filled tap hands the canister back.
     *
     * @param helper the gametest helper
     */
    public static void tapEmptyHandClickTakesCanister(GameTestHelper helper) {
        TapBlockEntity tap = placeFilledTap(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        helper.useBlock(BE_POS, player, tapHit(helper, TAP_SLOT_REGION_HIT_PX, Direction.NORTH));

        helper.assertTrue(tap.getCanister().isEmpty(), TAP_SLOT_EMPTIED);
        helper.assertTrue(player.getMainHandItem().is(GooItems.CANISTER.get()), TAP_CANISTER_IN_HAND);
        helper.succeed();
    }

    /**
     * Tap: a blob click inside the slot region of a filled tap pours the blobs into the
     * slotted canister and uses the blob stack up.
     *
     * @param helper the gametest helper
     */
    public static void tapBlobClickPoursIntoSlottedCanister(GameTestHelper helper) {
        TapBlockEntity tap = placeFilledTap(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, BlobStacks.createForOutput(GooTypes.ROCK, TAP_BLOB_COUNT * BlobStacks.MB_PER_BLOB));

        helper.useBlock(BE_POS, player, tapHit(helper, TAP_SLOT_REGION_HIT_PX, Direction.NORTH));

        helper.assertTrue(tap.getFluidContent().amount() == TAP_BLOB_COUNT * BlobStacks.MB_PER_BLOB,
                TAP_CANISTER_FILLED);
        helper.assertTrue(player.getMainHandItem().isEmpty(), TAP_BLOB_USED_UP);
        helper.succeed();
    }

    /**
     * Places a south-facing tap holding an empty canister in its slot.
     *
     * @param helper the gametest helper
     * @return the tap's block entity
     */
    private static TapBlockEntity placeFilledTap(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        helper.assertTrue(tap.insertCanister(new ItemStack(GooItems.CANISTER.get())), TAP_SHOULD_INSERT);
        return tap;
    }

    /**
     * Builds a hit on the tap at BE_POS from a point given in pixels inside its block.
     *
     * @param helper  the gametest helper
     * @param localPx the hit point inside the tap's block, in pixels
     * @param face    the face the hit lands on
     * @return the hit result
     */
    private static BlockHitResult tapHit(GameTestHelper helper, Vec3 localPx, Direction face) {
        BlockPos abs = helper.absolutePos(BE_POS);
        return new BlockHitResult(
                Vec3.atLowerCornerOf(abs).add(localPx.scale(1.0 / PIXELS_PER_BLOCK)), face, abs, false);
    }

    /**
     * Places a south-facing empty tap, right-clicks it through the block's use path with a
     * two-canister stack in a non-sneaking player's hand, and asserts one canister moved into the slot.
     *
     * @param helper  the gametest helper
     * @param localPx the hit point inside the tap's block, in pixels
     * @param face    the face the hit lands on
     */
    private static void clickEmptyTapWithCanister(GameTestHelper helper, Vec3 localPx, Direction face) {
        helper.setBlock(BE_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get(), TAP_HAND_COUNT));

        helper.useBlock(BE_POS, player, tapHit(helper, localPx, face));

        helper.assertFalse(tap.getCanister().isEmpty(), TAP_SLOT_FILLED);
        helper.assertTrue(player.getMainHandItem().getCount() == TAP_HAND_COUNT - 1, TAP_HAND_SHRANK);
        helper.succeed();
    }

    /**
     * Vat: right-click with choral gasket on upper half applies GASKET_CAP.
     *
     * @param helper the gametest helper
     */
    public static void vatGasketApply(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.VAT.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CHORAL_GASKET.get()));

        BlockPos abs = helper.absolutePos(BE_POS);
        BlockHitResult topHit = new BlockHitResult(
                new Vec3(abs.getX() + BLOCK_CENTER, abs.getY() + UPPER_HIT_Y, abs.getZ() + BLOCK_CENTER),
                Direction.UP, abs, false);
        helper.useBlock(BE_POS, player, topHit);

        BlockState state = helper.getBlockState(BE_POS);
        helper.assertTrue(state.getValue(VatBlock.GASKET_CAP), VAT_SHOULD_HAVE_CAP);
        helper.succeed();
    }

    /**
     * Hub: right-click with canister item inserts into a slot.
     *
     * @param helper the gametest helper
     */
    public static void hubCanisterInsert(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get()));

        helper.useBlock(BE_POS, player, hit(helper, Direction.UP));

        boolean anyInserted = false;
        for (int i = 0; i < hub.containerState().maxSlots(); i++) {
            if (!hub.getCanister(i).isEmpty()) {
                anyInserted = true;
                break;
            }
        }
        helper.assertTrue(anyInserted, HUB_SHOULD_INSERT);
        helper.succeed();
    }

    /**
     * Hub: a plain click on a filled slot with a canister in hand picks that canister up
     * and leaves the held one in hand. The slot is filled through the same plain click
     * first, so both legs of the rule run through useItemOn (decision hub-plain-click-rule).
     *
     * @param helper the gametest helper
     */
    public static void hubCanisterPickup(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(BE_POS, HubBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(GooItems.CANISTER.get(), HUB_PICKUP_HAND_COUNT));

        helper.useBlock(BE_POS, player, slotHit(helper, HUB_SLOT_NORTH));
        helper.assertFalse(hub.getCanister(HUB_SLOT_NORTH).isEmpty(), HUB_SHOULD_INSERT);

        // InteractionCooldown refuses a second click for ten ticks after the insert.
        helper.runAfterDelay(HUB_PICKUP_DELAY_TICKS, () -> {
            helper.useBlock(BE_POS, player, slotHit(helper, HUB_SLOT_NORTH));

            helper.assertTrue(hub.getCanister(HUB_SLOT_NORTH).isEmpty(), HUB_SHOULD_PICKUP);
            helper.assertFalse(player.getMainHandItem().isEmpty(), HUB_SHOULD_KEEP_HELD);
            helper.assertTrue(
                    player.getInventory().countItem(GooItems.CANISTER.get()) == HUB_PICKUP_HAND_COUNT,
                    HUB_SHOULD_HOLD_BOTH);
            helper.succeed();
        });
    }

    /**
     * Creates a BlockHitResult on the top face at the center of the given hub slot.
     *
     * @param helper the gametest helper
     * @param slot   the hub slot index
     * @return the hit result
     */
    private static BlockHitResult slotHit(GameTestHelper helper, int slot) {
        BlockPos abs = helper.absolutePos(BE_POS);
        double[] center = HubBlock.SLOT_CENTERS[slot];
        return new BlockHitResult(
                new Vec3(abs.getX() + center[0] / PIXELS_PER_BLOCK, abs.getY() + 1.0,
                        abs.getZ() + center[1] / PIXELS_PER_BLOCK),
                Direction.UP, abs, false);
    }

    /**
     * Plexer: setting a target item directly exercises the BE's target lifecycle.
     * The cutaway hit detection is geometric and hard to simulate via useBlock,
     * so we test the BE method that the interaction handler delegates to.
     *
     * @param helper the gametest helper
     */
    public static void plexerSetTarget(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.PLEXER.get());
        PlexerBlockEntity plexer = helper.getBlockEntity(BE_POS, PlexerBlockEntity.class);
        plexer.setTargetItem(new ItemStack(Items.STONE));
        helper.assertFalse(plexer.getTargetItem().isEmpty(), PLEXER_SHOULD_SET);
        helper.succeed();
    }

    /**
     * Crucible: a blaze rod click inserts no fuel, leaving the stack whole and the
     * crucible cold (decision fuel-goo-heats-per-mb).
     *
     * @param helper the gametest helper
     */
    public static void crucibleBlazeRodClickLeavesItCold(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BLAZE_ROD));

        helper.useBlock(BE_POS, player, hit(helper, Direction.UP));

        helper.assertValueEqual(player.getItemInHand(InteractionHand.MAIN_HAND).getCount(), 1, BLAZE_ROD_STAYS_WHOLE);
        helper.assertFalse(crucible.canHeat(), BLAZE_ROD_LEAVES_COLD);
        helper.succeed();
    }

    /**
     * Crucible: a cold crucible with no fuel goo leaves an item dropped into it standing.
     *
     * @param helper the gametest helper
     */
    public static void coldCrucibleAbsorbsNothing(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        ItemEntity dropped = spawnInBasin(helper);

        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertFalse(dropped.isRemoved(), COLD_CRUCIBLE_REFUSES);
            helper.succeed();
        });
    }

    /**
     * Crucible: the same crucible holding blaze goo in its reservoir absorbs the item.
     *
     * @param helper the gametest helper
     */
    public static void blazeCrucibleAbsorbsItem(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class).insertGoo(GooTypes.BLAZE, CRUCIBLE_BLAZE_FUEL);
        ItemEntity dropped = spawnInBasin(helper);

        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertTrue(dropped.isRemoved(), BLAZE_CRUCIBLE_ABSORBS);
            helper.succeed();
        });
    }

    /**
     * Crucible: a flint-and-steel click sparks a cold crucible for the spark's heat at one
     * durability; the same click on the heated crucible passes and costs nothing
     * (decision flint-and-steel-sparks-the-crucible).
     *
     * @param helper the gametest helper
     */
    public static void flintAndSteelSparksColdCrucible(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, flint);

        helper.assertValueEqual(sparkClick(helper, player), InteractionResult.SUCCESS, SPARK_SUCCEEDS);
        helper.assertValueEqual(crucible.heatTicks(), GooConfig.SPARK_HEAT_TICKS.get(), SPARK_HEATS);
        helper.assertValueEqual(flint.getDamageValue(), 1, SPARK_COSTS_ONE);

        helper.assertValueEqual(sparkClick(helper, player), InteractionResult.PASS, HOT_SPARK_PASSES);
        helper.assertValueEqual(flint.getDamageValue(), 1, HOT_SPARK_FREE);
        helper.succeed();
    }

    /**
     * Crucible: a sparked empty crucible absorbs a coal and melts it to the end on the
     * blaze goo the coal yields.
     *
     * @param helper the gametest helper
     */
    public static void sparkedCrucibleMeltsCoalOnItsBlaze(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
        sparkClick(helper, player);
        ItemEntity coal = spawnInBasin(helper, Items.COAL);

        helper.succeedWhen(() -> {
            helper.assertTrue(coal.isRemoved(), COAL_MELTED);
            helper.assertTrue(crucible.getMeltingItem().isEmpty(), COAL_MELTED);
            helper.assertTrue(crucible.getReservoir().getVolume(GooTypes.BLAZE) > 0, COAL_LEFT_BLAZE);
            helper.assertTrue(crucible.getReservoir().getVolume(GooTypes.ROCK) > 0, COAL_LEFT_ROCK);
            helper.assertTrue(crucible.canHeat(), COAL_ENDS_HOT);
        });
    }

    /**
     * Right-clicks the crucible's top with the player's main-hand stack.
     *
     * @param helper the gametest helper
     * @param player the clicking player
     * @return the crucible's answer
     */
    private static InteractionResult sparkClick(GameTestHelper helper, Player player) {
        return helper.getBlockState(BE_POS).useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND),
                helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, Direction.UP));
    }

    /**
     * Spawns a still cobblestone item entity inside the crucible basin.
     *
     * @param helper the gametest helper
     * @return the spawned entity
     */
    private static ItemEntity spawnInBasin(GameTestHelper helper) {
        return spawnInBasin(helper, Items.COBBLESTONE);
    }

    /**
     * Spawns a still item entity of one item inside the crucible basin.
     *
     * @param helper the gametest helper
     * @param item   the item the entity carries
     * @return the spawned entity
     */
    private static ItemEntity spawnInBasin(GameTestHelper helper, Item item) {
        Vec3 at = helper.absoluteVec(new Vec3(BASIN_CENTER_XZ, BASIN_SURFACE_Y, BASIN_CENTER_XZ));
        ItemEntity entity = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(item));
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
