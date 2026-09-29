package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.CanisterSlotLayout;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystalReach;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerShapes;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterPlacementResolver;
import com.mercuriusxeno.goo.item.CanisterPlacementResolver.CanisterPlacement;
import com.mercuriusxeno.goo.item.CanisterPlacementValidator;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Set;
import java.util.UUID;

/**
 * Gametests for the crystallizer (decision crystallizer-emits-chrysm): two
 * canisters in the canister block on its top, whichever holds crystal the
 * catalyst and the other's goo what grows, crystallizing at 10% crystal up to the
 * knob tier, and handing the highest tier reached to an empty-hand click. The
 * canister block on a crystallizer takes canisters only in the crystallizer's two
 * slots, laid out at the crystallizer's own centers.
 */
public final class CrystallizerTests {

    private static final BlockPos SENDER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CRYSTALLIZER_POS = new BlockPos(3, 1, 1);
    private static final BlockPos CANISTERS_POS = CRYSTALLIZER_POS.above();
    private static final int CHRYSM_VOLUME = Math.toIntExact(ChrysmTier.CHRYSM.volume());
    private static final int CRYSTAL_COST = CHRYSM_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL;
    private static final int BUDDING_VOLUME = Math.toIntExact(ChrysmTier.BUDDING_CHRYSM.volume());
    private static final int HALF_BUDDING = BUDDING_VOLUME / 2;
    private static final int MORE_ENDER = 5_000;
    private static final int FIRST = 0;
    private static final int SECOND = 1;
    private static final int PUSH_TICKS = 20;
    /** A chrysm's 500 ticks at the pace, with margin. */
    private static final int CHRYSM_TICKS = 520;
    /** A budding chrysm's 1,500 ticks at the pace, with margin. */
    private static final int BUDDING_CHRYSM_TICKS = 1_540;
    /** 500,000 mB at the pace: a chrysm's 500 ticks and 468,000 mB at 968 a tick, about 984 ticks, with margin. */
    private static final int HALF_BUDDING_TICKS = 1_020;
    /** Half a chrysm, 16,000 mB, at the chrysm pace of 64 mB a tick: 250 ticks, with margin. */
    private static final int HALF_CHRYSM_TICKS = 260;
    private static final int STILL_GROWING_TICKS = 100;
    private static final int SOME_TICKS = 20;
    /** The server takes a click's point only within this of its block's center. */
    private static final double SERVER_REACH = 1.0000001;
    /** Goo past a chrysm: 16,000 mB at the budding pace of 968 a tick, 17 ticks after the chrysm's 500. */
    private static final int EXCESS = 16_000;
    private static final int EXCESS_TICKS = 560;
    private static final double HALF = 0.5;
    private static final double SIDE_OFFSET = 2;
    private static final double PIXELS = 16.0;
    private static final double EPSILON = 1.0e-6;
    private static final double DIAL_CENTER_Y = 7.0 / 16.0;
    /** A top-face point in the front middle cell, a slot the crystallizer does not take, the dial facing north. */
    private static final double OFF_SLOT_Z = 2.0 / 16.0;
    /** The purple spot the crystal grows from, block-local, with the dial facing north. */
    private static final double[] CRYSTAL_SPOT_NORTH = {8.0 / 16.0, 5.0 / 16.0};
    private static final double CRYSTAL_HIT_LIFT = 2.0 / 16.0;
    /**
     * Pixel centers of the back-left and back-right canisters with the dial facing north:
     * the model's (4, 4) and (12, 4) turned 180 degrees.
     */
    private static final double[][] NORTH_CANISTER_CENTERS = {{12.0, 12.0}, {4.0, 12.0}};
    /** The canister block slots the back-left and back-right canisters take with the dial facing north. */
    private static final int[] NORTH_SLOTS = {8, 6};

    private CrystallizerTests() {
    }

    /**
     * A crystal canister slotted first and ticked, then an ender canister slotted,
     * crystallizes ender: a click hands one ender chrysm.
     *
     * @param helper the gametest helper
     */
    public static void crystalFirstThenEnder(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        helper.runAfterDelay(SOME_TICKS, () -> {
            helper.assertValueEqual(0L, crystallizer.crystallized(), "crystallized from crystal alone");
            insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
            helper.runAfterDelay(CHRYSM_TICKS, () -> {
                assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.ENDER);
                helper.succeed();
            });
        });
    }

    /**
     * Ender in the first slot and crystal in the second crystallize the same as the other way round.
     *
     * @param helper the gametest helper
     */
    public static void eitherSlotHoldsTheCrystal(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        insert(helper, SECOND, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        helper.runAfterDelay(CHRYSM_TICKS, () -> {
            assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.ENDER);
            helper.succeed();
        });
    }

    /**
     * Two crystal canisters grow a crystal chrysm, the first spent as the catalyst.
     *
     * @param helper the gametest helper
     */
    public static void twoCrystalCanistersGrowCrystal(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        insert(helper, SECOND, canister(GooTypes.CRYSTAL, CHRYSM_VOLUME));
        helper.runAfterDelay(CHRYSM_TICKS, () -> {
            assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.CRYSTAL);
            helper.succeed();
        });
    }

    /**
     * The canister block on a crystallizer takes a canister only in the crystallizer's
     * two slots: a direct insert elsewhere is refused, the placement validator allows
     * those two alone, and a canister used on the crystallizer's top places a canister
     * block above into the slot the click's quarter names, or, off both, into one of
     * the two.
     *
     * @param helper the gametest helper
     */
    public static void canisterBlockAboveTakesOnlyTheTwoSlots(GameTestHelper helper) {
        placeCrystallizerAlone(helper, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.CANISTER.get()));
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        Set<Integer> allowed = Set.of(NORTH_SLOTS[FIRST], NORTH_SLOTS[SECOND]);
        BlockHitResult frontOfTop = new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + 1.0, abs.getZ() + OFF_SLOT_Z), Direction.UP, abs, false);
        CanisterPlacement offBoth = CanisterPlacementResolver.resolve(new BlockPlaceContext(player,
                InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND), frontOfTop), false);
        helper.assertTrue(offBoth != null && allowed.contains(offBoth.slot()) && !offBoth.intoExisting()
                        && offBoth.pos().equals(abs.above()),
                "A canister off both slots should place a canister block above into one of them, found " + offBoth);
        helper.useBlock(CRYSTALLIZER_POS, player, topHit(helper, SECOND));
        helper.assertBlockPresent(GooBlocks.CANISTER.get(), CANISTERS_POS);
        CanisterBlockEntity canisters = canisters(helper);
        for (int slot = 0; slot < CanisterSlotLayout.SLOT_COUNT; slot++) {
            helper.assertValueEqual(allowed.contains(slot),
                    CanisterPlacementValidator.isSlotAllowed(helper.getLevel(), abs.above(), slot),
                    "slot " + slot + " allowed on the crystallizer");
            helper.assertValueEqual(slot == NORTH_SLOTS[SECOND], canisters.isSlotFilled(slot),
                    "slot " + slot + " filled by the click on the back-right quarter");
        }
        helper.assertFalse(canisters.insertCanister(CanisterBlock.CENTER_SLOT,
                new ItemStack(GooItems.CANISTER.get()), false), "The center slot should refuse a canister");
        helper.assertTrue(canisters.insertCanister(NORTH_SLOTS[FIRST],
                new ItemStack(GooItems.CANISTER.get()), false), "The back-left slot should take a canister");
        helper.assertFalse(helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class).canAttachOnTop(),
                "Two canisters fill the crystallizer's top");
        helper.succeed();
    }

    /**
     * A canister block standing on a crystallizer lays its canisters out at the
     * crystallizer's centers, the model's (4, 4) and (12, 4) turned to its facing: each
     * slot's shape centers there, a hit there addresses that slot, and the block's shape
     * holds both canisters.
     *
     * @param helper the gametest helper
     */
    public static void canisterBlockOnCrystallizerStandsAtItsCenters(GameTestHelper helper) {
        placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        BlockPos above = helper.absolutePos(CANISTERS_POS);
        float[][] centers = CanisterSlotLayout.centersAt(helper.getLevel(), above);
        CanisterBlockEntity canisters = canisters(helper);
        for (int role : new int[] {FIRST, SECOND}) {
            int slot = NORTH_SLOTS[role];
            AABB bounds = CanisterBlock.slotShape(centers, slot).bounds();
            helper.assertTrue(Math.abs(bounds.getCenter().x * PIXELS - NORTH_CANISTER_CENTERS[role][0]) < EPSILON
                            && Math.abs(bounds.getCenter().z * PIXELS - NORTH_CANISTER_CENTERS[role][1]) < EPSILON,
                    "Slot " + slot + " should center at " + NORTH_CANISTER_CENTERS[role][0] + ", "
                            + NORTH_CANISTER_CENTERS[role][1] + ", found " + bounds);
            BlockHitResult hit = canisterHit(helper, role);
            helper.assertValueEqual(slot, canisters.resolveSlot(hit), "hit slot for role " + role);
            BlockHitResult side = canisterSideHit(helper, role);
            helper.assertValueEqual(slot, canisters.resolveSlot(side), "side hit slot for role " + role);
            AABB outline = canisters.outlineShape(side).bounds();
            helper.assertTrue(Math.abs(outline.getCenter().x * PIXELS - NORTH_CANISTER_CENTERS[role][0]) < EPSILON
                            && Math.abs(outline.getCenter().z * PIXELS - NORTH_CANISTER_CENTERS[role][1]) < EPSILON,
                    "A side hit should outline its own canister, not an unused grid slot, found " + outline);
            AABB shape = helper.getBlockState(CANISTERS_POS).getShape(helper.getLevel(), above).bounds();
            helper.assertTrue(shape.contains(bounds.getCenter()), "The block's shape should hold slot " + slot);
        }
        helper.succeed();
    }

    /**
     * A hit on a canister's outer side, 2 px out from its center on x and 2 px toward
     * the grid's middle row on z, nearer an unused grid slot's center than its own.
     */
    private static BlockHitResult canisterSideHit(GameTestHelper helper, int role) {
        BlockPos above = helper.absolutePos(CANISTERS_POS);
        double[] center = NORTH_CANISTER_CENTERS[role];
        double outward = center[0] > HALF * PIXELS ? SIDE_OFFSET : -SIDE_OFFSET;
        return new BlockHitResult(new Vec3(above.getX() + (center[0] + outward) / PIXELS, above.getY() + HALF,
                above.getZ() + (center[1] - SIDE_OFFSET) / PIXELS),
                outward > 0 ? Direction.EAST : Direction.WEST, above, false);
    }

    /**
     * The crystallizer's own clicks pass anything but the knob and a mature crystal:
     * a canister click on its top and an empty-hand click there both answer PASS, so a
     * canister's own placement runs, on the client too.
     *
     * @param helper the gametest helper
     */
    public static void clicksItDoesNotOwnPass(GameTestHelper helper) {
        placeCrystallizerAlone(helper, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, canister);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        BlockHitResult top = new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + 1.0, abs.getZ() + HALF), Direction.UP, abs, false);
        var state = helper.getBlockState(CRYSTALLIZER_POS);
        helper.assertValueEqual(net.minecraft.world.InteractionResult.PASS,
                state.useItemOn(canister, helper.getLevel(), player, InteractionHand.MAIN_HAND, top),
                "a canister click on the top");
        helper.assertValueEqual(net.minecraft.world.InteractionResult.PASS,
                state.useWithoutItem(helper.getLevel(), player, top), "an empty-hand click on the top");
        helper.succeed();
    }

    /**
     * Each canister used on the crystallizer's top plays a sound where it lands: the
     * first stands a new canister block, the second enters the block already standing.
     * Listens for the level sounds the server plays at the canister block.
     *
     * @param helper the gametest helper
     */
    public static void eachCanisterPlacedPlaysASound(GameTestHelper helper) {
        placeCrystallizerAlone(helper, 1);
        BlockPos above = helper.absolutePos(CANISTERS_POS);
        List<String> heard = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
            if (BlockPos.containing(event.getPosition()).equals(above)) {
                heard.add(event.getSound().getRegisteredName());
            }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            for (int role = FIRST; role <= SECOND; role++) {
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                ItemStack canister = new ItemStack(GooItems.CANISTER.get());
                player.setItemInHand(InteractionHand.MAIN_HAND, canister);
                int before = heard.size();
                canister.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, topHit(helper, role)));
                helper.assertTrue(canisters(helper).slotBounds(NORTH_SLOTS[role]) != null,
                        "The canister for role " + role + " should stand in its slot");
                helper.assertTrue(heard.size() > before,
                        "Placing the canister for role " + role + " should play a sound, heard " + heard);
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    /**
     * A goo poured on a full canister stays in the hand: the pour goes only into the
     * canister aimed at, never into the empty one beside it.
     *
     * @param helper the gametest helper
     */
    public static void aPourFillsOnlyTheAimedCanister(GameTestHelper helper) {
        placeCrystallizer(helper, 1);
        int full = ContainerCapacity.canisterCapacity(0);
        insert(helper, FIRST, canister(GooTypes.BLAZE, full));
        insert(helper, SECOND, new ItemStack(GooItems.CANISTER.get()));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, GooStacks.createForOutput(GooTypes.BLAZE, CHRYSM_VOLUME));
        helper.useBlock(CANISTERS_POS, player, canisterHit(helper, FIRST));
        helper.assertTrue(canisters(helper).getSlotFluidContent(NORTH_SLOTS[SECOND]).isEmpty(),
                "A pour on the full canister should leave the empty one empty, found "
                        + canisters(helper).getSlotFluidContent(NORTH_SLOTS[SECOND]));
        helper.assertValueEqual(full, canisters(helper).getSlotFluidContent(NORTH_SLOTS[FIRST]).amount(),
                "the aimed canister stays full");
        helper.succeed();
    }

    /**
     * Only one canister holds the goo that grows: beside ender, the other canister
     * refuses ender and blaze, by pour, by network insert and as a filled canister,
     * and takes crystal; beside crystal it takes ender.
     *
     * @param helper the gametest helper
     */
    public static void oneCanisterHoldsTheGrowingGoo(GameTestHelper helper) {
        placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        insert(helper, SECOND, new ItemStack(GooItems.CANISTER.get()));
        CanisterBlockEntity canisters = canisters(helper);
        int second = NORTH_SLOTS[SECOND];
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, GooStacks.createForOutput(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.useBlock(CANISTERS_POS, player, canisterHit(helper, SECOND));
        helper.assertTrue(canisters.getSlotFluidContent(second).isEmpty(), "a poured ender beside ender is refused");
        helper.assertValueEqual(0, canisters.insertGoo(second, GooTypes.BLAZE, CHRYSM_VOLUME),
                "a network blaze beside ender is refused");
        helper.assertValueEqual(CRYSTAL_COST, canisters.insertGoo(second, GooTypes.CRYSTAL, CRYSTAL_COST),
                "crystal beside ender is taken");
        canisters.removeCanister(second);
        helper.assertFalse(canisters.insertCanister(second, canister(GooTypes.BLAZE, CHRYSM_VOLUME), false),
                "a filled blaze canister beside ender is refused");
        canisters.removeCanister(NORTH_SLOTS[FIRST]);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        helper.assertTrue(canisters.insertCanister(second, canister(GooTypes.ENDER, CHRYSM_VOLUME), false),
                "an ender canister beside crystal is taken");
        helper.succeed();
    }

    /**
     * A chrysm's ender beside half its crystal crystallizes half and forms no
     * chrysm; the other half of the crystal finishes it. The model reads active while it
     * crystallizes and idle after.
     *
     * @param helper the gametest helper
     */
    public static void pausesWithoutCrystal(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST / 2));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.runAfterDelay(STILL_GROWING_TICKS / 2, () -> helper.assertTrue(
                helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.ACTIVE),
                "The crystallizer should read active while crystallizing"));
        helper.runAfterDelay(HALF_CHRYSM_TICKS + SOME_TICKS + SOME_TICKS, () -> {
            helper.assertValueEqual(CHRYSM_VOLUME / 2L, crystallizer.crystallized(), "crystallized on half the crystal");
            helper.assertTrue(crystallizer.formed().isEmpty(), "No chrysm should form without the crystal for it");
            helper.assertFalse(helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.ACTIVE),
                    "The crystallizer should read idle once nothing crystallizes");
            canisters(helper).insertGoo(NORTH_SLOTS[FIRST], GooTypes.CRYSTAL, CRYSTAL_COST / 2);
            helper.runAfterDelay(HALF_CHRYSM_TICKS, () -> {
                assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.ENDER);
                helper.succeed();
            });
        });
    }

    /**
     * With the knob at 2, 1,000,000 mB of ender and 100,000 mB of crystal
     * crystallize into a budding chrysm that a click hands over.
     *
     * @param helper the gametest helper
     */
    public static void advancesToBuddingChrysm(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 2);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, BUDDING_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL));
        insert(helper, SECOND, canister(GooTypes.ENDER, BUDDING_VOLUME));
        helper.runAfterDelay(BUDDING_CHRYSM_TICKS, () -> {
            assertClickHands(helper, GooItems.BUDDING_CHRYSM.get(), GooTypes.ENDER);
            helper.succeed();
        });
    }

    /**
     * With the knob at 2, 500,000 mB of ender crystallized is part grown: a click
     * hands nothing and the crystal stays.
     *
     * @param helper the gametest helper
     */
    public static void partGrownCrystalIsNotClickable(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 2);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, HALF_BUDDING / CrystallizerPhases.GOO_PER_CRYSTAL));
        insert(helper, SECOND, canister(GooTypes.ENDER, HALF_BUDDING));
        helper.runAfterDelay(HALF_BUDDING_TICKS, () -> {
            helper.assertValueEqual((long) HALF_BUDDING, crystallizer.crystallized(), "crystallized before the click");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
            helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(
                    new Vec3(abs.getX() + HALF, abs.getY() + HALF, abs.getZ() + 1.0), Direction.SOUTH, abs, false));
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                    "A part-grown crystal should hand nothing");
            helper.assertValueEqual((long) HALF_BUDDING, crystallizer.crystallized(), "crystallized after the click");
            helper.succeed();
        });
    }

    /**
     * Stepping the dial up while half a chrysm of ender is still crystallizing keeps the
     * crystal whole and drops nothing, and it grows on once crystal arrives (operator
     * ruling: a dial click never resets the crystal).
     *
     * @param helper the gametest helper
     */
    public static void dialStepKeepsAGrowingCrystal(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST / 2));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.runAfterDelay(HALF_CHRYSM_TICKS + SOME_TICKS, () -> {
            helper.assertValueEqual(CHRYSM_VOLUME / 2L, crystallizer.crystallized(), "crystallized before the dial");
            clickDial(helper);
            helper.assertValueEqual(2, helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.KNOB),
                    "knob after the click");
            helper.assertValueEqual(CHRYSM_VOLUME / 2L, crystallizer.crystallized(), "crystallized after the dial");
            helper.assertTrue(droppedNear(helper).isEmpty(), "A dial click should drop nothing");
            canisters(helper).insertGoo(NORTH_SLOTS[FIRST], GooTypes.CRYSTAL, CRYSTAL_COST / 2);
            helper.runAfterDelay(SOME_TICKS, () -> {
                helper.assertTrue(crystallizer.crystallized() > CHRYSM_VOLUME / 2L,
                        "The kept crystal should grow on, crystallized " + crystallizer.crystallized());
                helper.succeed();
            });
        });
    }

    /**
     * Wrapping the dial from materia to off keeps a growing crystal whole and pauses it.
     *
     * @param helper the gametest helper
     */
    public static void offPausesAGrowingCrystal(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, CrystallizerPhases.KNOB_MAX);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.runAfterDelay(STILL_GROWING_TICKS, () -> {
            long before = crystallizer.crystallized();
            helper.assertTrue(before > 0, "The crystal should be growing before the dial");
            clickDial(helper);
            helper.assertValueEqual(0, helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.KNOB),
                    "knob after the wrap");
            helper.runAfterDelay(SOME_TICKS, () -> {
                helper.assertValueEqual(before, crystallizer.crystallized(), "crystallized while off");
                helper.assertTrue(droppedNear(helper).isEmpty(), "The wrap to off should drop nothing");
                helper.succeed();
            });
        });
    }

    /**
     * A crystal grown past a chrysm, the dial then stepped round to chrysm, hands one
     * chrysm and the excess as an ender goo and a crystal goo of its tenth,
     * emptying the crystallizer (operator ruling).
     *
     * @param helper the gametest helper
     */
    public static void clickHandsTheDialsTierAndTheExcess(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 2);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, (CHRYSM_VOLUME + EXCESS) / CrystallizerPhases.GOO_PER_CRYSTAL));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME + EXCESS));
        helper.runAfterDelay(EXCESS_TICKS, () -> {
            helper.assertValueEqual((long) CHRYSM_VOLUME + EXCESS, crystallizer.crystallized(), "crystallized");
            for (int click = 0; click < CrystallizerPhases.KNOB_POSITIONS - 1; click++) {
                clickDial(helper);
            }
            helper.assertValueEqual(1, helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.KNOB),
                    "knob stepped round to chrysm");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
            helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(
                    new Vec3(abs.getX() + CRYSTAL_SPOT_NORTH[0], abs.getY() + 1.0 + CRYSTAL_HIT_LIFT,
                            abs.getZ() + CRYSTAL_SPOT_NORTH[1]), Direction.UP, abs, false));
            helper.assertTrue(player.getInventory().contains(stack -> stack.is(GooItems.CHRYSM.get())),
                    "The click should hand one chrysm");
            helper.assertTrue(player.getInventory().contains(stack -> GooTypes.ENDER.equals(GooStacks.keyOf(stack))
                    && GooStacks.volumeOf(stack) == EXCESS), "The click should hand the excess ender");
            helper.assertTrue(player.getInventory().contains(stack -> GooTypes.CRYSTAL.equals(GooStacks.keyOf(stack))
                    && GooStacks.volumeOf(stack) == EXCESS / CrystallizerPhases.GOO_PER_CRYSTAL),
                    "The click should hand the excess's crystal");
            helper.assertValueEqual(0L, crystallizer.crystallized(), "crystallized after the click");
            helper.succeed();
        });
    }

    private static void clickDial(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + DIAL_CENTER_Y, abs.getZ()), Direction.NORTH, abs, false));
    }

    private static List<ItemEntity> droppedNear(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(CRYSTALLIZER_POS)).inflate(2));
    }

    /**
     * With the knob at small, a crystallizer holding a chrysm leaves the ender in its
     * canister; once a click takes the chrysm, that ender starts to crystallize.
     *
     * @param helper the gametest helper
     */
    public static void smallDialHoldsAtChrysm(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST * 2));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.runAfterDelay(CHRYSM_TICKS, () -> {
            helper.assertTrue(crystallizer.formedTier() == ChrysmTier.CHRYSM, "A chrysm should form");
            canisters(helper).insertGoo(NORTH_SLOTS[SECOND], GooTypes.ENDER, MORE_ENDER);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                helper.assertValueEqual(MORE_ENDER, enderIn(helper), "ender held while the chrysm is inside");
                assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.ENDER);
                helper.runAfterDelay(SOME_TICKS, () -> {
                    helper.assertTrue(enderIn(helper) < MORE_ENDER,
                            "The ender should crystallize once the chrysm is taken");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Full canisters of ender and crystal crystallize at the pace: no chrysm 100 ticks
     * in, one by 520 (decision crystal-pace-doubles-per-tier: a chrysm at 25 s).
     *
     * @param helper the gametest helper
     */
    public static void crystallizesAtAnEvenPace(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, BUDDING_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL));
        insert(helper, SECOND, canister(GooTypes.ENDER, BUDDING_VOLUME));
        helper.runAfterDelay(STILL_GROWING_TICKS, () -> {
            helper.assertTrue(crystallizer.crystallized() > 0 && crystallizer.formed().isEmpty(),
                    "Half way to a chrysm there should be crystallized goo and no chrysm, crystallized "
                            + crystallizer.crystallized());
            helper.runAfterDelay(CHRYSM_TICKS - STILL_GROWING_TICKS, () -> {
                helper.assertTrue(crystallizer.formedTier() == ChrysmTier.CHRYSM,
                        "A chrysm should have formed by 520 ticks, crystallized " + crystallizer.crystallized());
                helper.succeed();
            });
        });
    }

    /**
     * A crucible transmitter linked to the gasket on the ingredient canister's top, a
     * slot of the canister block on the crystallizer, fills that canister, and the
     * ender it brings crystallizes.
     *
     * @param helper the gametest helper
     */
    public static void gasketFillsTheIngredientCanister(GameTestHelper helper) {
        helper.setBlock(SENDER_POS, GooBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity sender = helper.getBlockEntity(SENDER_POS, CrucibleBlockEntity.class);
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        UUID receiver = UUID.randomUUID();
        ItemStack ingredient = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(ingredient, CanisterItem.getMetadata(ingredient).withTopGasketId(receiver));
        insert(helper, SECOND, ingredient);
        UUID transmitter = sender.ensureGasketId(GasketRole.TRANSMITTER);
        GasketRegistry.get(helper.getLevel()).link(transmitter, receiver);
        sender.setPartner(GasketRole.TRANSMITTER,
                new GasketPartner(helper.absolutePos(CANISTERS_POS), NORTH_SLOTS[SECOND]));
        canisters(helper).setPartner(GasketRole.RECEIVER, NORTH_SLOTS[SECOND],
                new GasketPartner(helper.absolutePos(SENDER_POS), GooConstants.NO_SLOT));
        sender.insertGoo(GooTypes.ENDER, CHRYSM_VOLUME);
        helper.runAfterDelay(PUSH_TICKS, () -> {
            helper.assertTrue(crystallizer.crystallized() > 0 && GooTypes.ENDER.equals(crystallizer.formingType()),
                    "Ender through the canister's gasket should crystallize, crystallized "
                            + crystallizer.crystallized());
            helper.succeed();
        });
    }

    /**
     * A crystallized chrysm grows a crystal above the spot: the crystallizer's shape
     * reaches up around it, and an empty-hand click on the crystal hands the chrysm.
     *
     * @param helper the gametest helper
     */
    public static void clickingTheCrystalTakesTheChrysm(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.runAfterDelay(CHRYSM_TICKS, () -> {
            BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
            double top = helper.getBlockState(CRYSTALLIZER_POS).getShape(helper.getLevel(), abs).bounds().maxY;
            helper.assertTrue(top > 1.0, "The crystallizer's shape should reach up around the crystal, top " + top);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(
                    new Vec3(abs.getX() + CRYSTAL_SPOT_NORTH[0], abs.getY() + 1.0 + CRYSTAL_HIT_LIFT,
                            abs.getZ() + CRYSTAL_SPOT_NORTH[1]), Direction.UP, abs, false));
            ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(hand.is(GooItems.CHRYSM.get()), "A click on the crystal should hand the chrysm, found " + hand);
            helper.succeed();
        });
    }

    /**
     * A grown crystal of the tier is taken from beside the crystallizer at a standing
     * player's eye height, aimed anywhere across its body, level looks included, through
     * the client's target decision: the game's own ray, or the crystal on the sight line
     * when it is nearer (operator ruling: the crystal's shape is the thing to interact
     * with, from any side, at the player's normal reach).
     *
     * @param helper the gametest helper
     * @param tier   the tier the crystal has grown to, the dial set to it
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void standingAimTakesTheCrystal(GameTestHelper helper, ChrysmTier tier) {
        placeCrystallizer(helper, tier.ordinal() + 1);
        seedCrystal(helper, tier);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        AABB box = CrystallizerShapes.crystalShape(Direction.NORTH, crystallizer.crystallized()).bounds().move(abs);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        StringBuilder failed = new StringBuilder();
        int tried = 0;
        Vec3[] eyes = {new Vec3(abs.getX() - 1.3, abs.getY() + 1.62, abs.getZ() + 0.35),
            new Vec3(abs.getX() - 1.3, abs.getY() + 1.62, abs.getZ() + 0.9),
            new Vec3(abs.getX() - 1.3, box.minY + (box.maxY - box.minY) * HALF, box.minZ + (box.maxZ - box.minZ) * HALF)};
        for (Vec3 eye : eyes) {
            for (double u = 0.2; u <= 0.8; u += 0.3) {
                for (double v = 0.1; v <= 0.9; v += 0.4) {
                    Vec3 aim = new Vec3(box.minX + (box.maxX - box.minX) * u, box.minY + (box.maxY - box.minY) * v,
                            box.minZ + (box.maxZ - box.minZ) * HALF);
                    tried++;
                    String missed = clickAlongSight(helper, player, eye, aim, tier);
                    if (missed != null) {
                        failed.append(" | eye ").append(eye.subtract(Vec3.atLowerCornerOf(abs))).append(" aim ")
                                .append(aim.subtract(Vec3.atLowerCornerOf(abs))).append(": ").append(missed);
                    }
                }
            }
        }
        helper.getLevel().removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        helper.assertTrue(failed.isEmpty(), "Standing aims that missed the " + tier + " crystal (" + tried
                + " tried, box " + box.move(Vec3.atLowerCornerOf(abs).reverse()) + "):" + failed);
        helper.succeed();
    }

    /**
     * Clicks along one sight line as the client targets it, and seeds the crystal again after a take.
     *
     * @return null when the tier's chrysm was taken or another block stands in front, else what happened
     */
    private static String clickAlongSight(GameTestHelper helper, ServerPlayer player, Vec3 eye, Vec3 aim,
                                          ChrysmTier tier) {
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.getInventory().clearContent();
        Vec3 end = eye.add(aim.subtract(eye).normalize().scale(player.blockInteractionRange()));
        BlockHitResult gameHit = helper.getLevel().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        BlockHitResult sighted = CrystalReach.nearestCrystalHit(pos -> crystalShapeAt(helper, pos), eye, end);
        BlockHitResult click = (BlockHitResult) CrystalReach.retarget(gameHit, sighted, eye);
        if (!click.getBlockPos().equals(helper.absolutePos(CRYSTALLIZER_POS))) {
            return null; // Another block stands in front of the crystal on this sight line, so the click is rightly its.
        }
        Vec3 off = click.getLocation().subtract(Vec3.atCenterOf(click.getBlockPos()));
        boolean serverAccepts = Math.abs(off.x) < SERVER_REACH && Math.abs(off.y) < SERVER_REACH
                && Math.abs(off.z) < SERVER_REACH;
        InteractionResult result = serverAccepts ? player.gameMode.useItemOn(player, helper.getLevel(),
                player.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, click) : null;
        Item handed = GooItems.CHRYSM_TIERS.get(tier.ordinal()).get();
        if (player.getInventory().contains(stack -> stack.is(handed))) {
            seedCrystal(helper, tier);
            return null;
        }
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        return "gameHit " + helper.getLevel().getBlockState(gameHit.getBlockPos()).getBlock() + " " + gameHit.getType()
                + ", sighted " + (sighted == null ? null : sighted.getLocation().subtract(Vec3.atLowerCornerOf(abs)))
                + ", clicked " + click.getLocation().subtract(Vec3.atLowerCornerOf(abs)) + ", serverAccepts "
                + serverAccepts + ", result " + result;
    }

    /** Reloads the crystallizer from its saved data holding a grown crystal of ender at the tier. */
    private static void seedCrystal(GameTestHelper helper, ChrysmTier tier) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        CrystallizerBlockEntity placed = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        net.minecraft.nbt.CompoundTag saved = placed.saveWithFullMetadata(level.registryAccess());
        saved.putLong("Crystallized", tier.volume());
        saved.putString("FormingType", GooTypes.ENDER.identifier().toString());
        level.removeBlockEntity(placed.getBlockPos());
        level.setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(placed.getBlockPos(),
                placed.getBlockState(), saved, level.registryAccess()));
    }

    private static VoxelShape crystalShapeAt(GameTestHelper helper, BlockPos pos) {
        BlockState state = helper.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof CrystallizerBlock)
                || !(helper.getLevel().getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer)) {
            return Shapes.empty();
        }
        return CrystallizerShapes.crystalShape(state.getValue(CrystallizerBlock.FACING), crystallizer.crystallized());
    }

    /**
     * A mature crystal is taken whatever the player holds: a stone block, a canister
     * and a goo each take a chrysm in turn, and each held item stays.
     *
     * @param helper the gametest helper
     */
    public static void anyHeldItemTakesTheCrystal(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST * 3));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME * 3));
        ItemStack[] held = {new ItemStack(Items.STONE), new ItemStack(GooItems.CANISTER.get()),
            GooStacks.createForOutput(GooTypes.ENDER, CHRYSM_VOLUME)};
        takeWithEach(helper, held, 0);
    }

    private static void takeWithEach(GameTestHelper helper, ItemStack[] held, int index) {
        if (index == held.length) {
            helper.succeed();
            return;
        }
        helper.runAfterDelay(CHRYSM_TICKS, () -> {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack holding = held[index].copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, holding);
            BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
            BlockHitResult crystalHit = new BlockHitResult(new Vec3(abs.getX() + CRYSTAL_SPOT_NORTH[0],
                    abs.getY() + 1.0 + CRYSTAL_HIT_LIFT, abs.getZ() + CRYSTAL_SPOT_NORTH[1]), Direction.UP, abs, false);
            helper.useBlock(CRYSTALLIZER_POS, player, crystalHit);
            helper.assertTrue(player.getInventory().contains(stack -> stack.is(GooItems.CHRYSM.get())),
                    "Holding " + held[index] + " should still take the crystal");
            helper.assertTrue(ItemStack.matches(held[index], player.getItemInHand(InteractionHand.MAIN_HAND)),
                    "The held " + held[index] + " should stay in the hand");
            takeWithEach(helper, held, index + 1);
        });
    }

    /**
     * A right click on the dial, on the face toward the player, steps the knob through
     * budding chrysm, flowering chrysm, materia and off, and back to chrysm; a click on
     * the top face leaves it.
     *
     * @param helper the gametest helper
     */
    public static void dialClickWrapsFromLargeToSmall(GameTestHelper helper) {
        placeCrystallizer(helper, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        BlockHitResult knobHit = new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + DIAL_CENTER_Y, abs.getZ()), Direction.NORTH, abs, false);
        BlockHitResult topHit = new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + 1.0, abs.getZ() + HALF), Direction.UP, abs, false);
        helper.useBlock(CRYSTALLIZER_POS, player, topHit);
        helper.assertValueEqual(1, helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.KNOB),
                "knob after a top-face click");
        for (int expected : new int[] {2, 3, 4, 0, 1}) {
            helper.useBlock(CRYSTALLIZER_POS, player, knobHit);
            helper.assertValueEqual(expected, helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.KNOB),
                    "knob after a click");
        }
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(CRYSTALLIZER_POS)).inflate(2)).isEmpty(),
                "Dial clicks on an empty crystallizer should drop nothing");
        helper.succeed();
    }

    /**
     * With the knob off, a crystallizer fed ender and crystal crystallizes nothing and
     * reads idle (decision dial-five-positions-off-to-materia).
     *
     * @param helper the gametest helper
     */
    public static void offCrystallizesNothing(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 0);
        insert(helper, FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST));
        insert(helper, SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.runAfterDelay(STILL_GROWING_TICKS, () -> {
            helper.assertValueEqual(0L, crystallizer.crystallized(), "crystallized with the knob off");
            helper.assertValueEqual(CHRYSM_VOLUME, enderIn(helper), "ender left in its canister");
            helper.assertFalse(helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.ACTIVE),
                    "The crystallizer should read idle with the knob off");
            helper.succeed();
        });
    }

    /**
     * Sets a crystallizer, its dial facing north, with the knob at the given size, and
     * an empty canister block on its top.
     */
    private static CrystallizerBlockEntity placeCrystallizer(GameTestHelper helper, int knob) {
        CrystallizerBlockEntity crystallizer = placeCrystallizerAlone(helper, knob);
        helper.setBlock(CANISTERS_POS, GooBlocks.CANISTER.get());
        return crystallizer;
    }

    /**
     * Sets a crystallizer, its dial facing north, with the knob at the given size, and nothing on its top.
     */
    private static CrystallizerBlockEntity placeCrystallizerAlone(GameTestHelper helper, int knob) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get().defaultBlockState()
                .setValue(CrystallizerBlock.FACING, Direction.NORTH).setValue(CrystallizerBlock.KNOB, knob));
        return helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
    }

    private static CanisterBlockEntity canisters(GameTestHelper helper) {
        return helper.getBlockEntity(CANISTERS_POS, CanisterBlockEntity.class);
    }

    /**
     * Puts a canister into the canister block above, in the slot the role takes, and
     * asserts the slot the crystallizer reads for the role is that slot.
     */
    private static void insert(GameTestHelper helper, int role, ItemStack canister) {
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        helper.assertValueEqual(NORTH_SLOTS[role], crystallizer.canisterSlot(role), "slot for role " + role);
        helper.assertTrue(canisters(helper).insertCanister(NORTH_SLOTS[role], canister, false),
                "The canister block should take the canister for role " + role);
    }

    private static ItemStack canister(ResourceKey<GooTypeDefinition> type, int volume) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(canister, new CanisterFluidContent(GooFluids.resource(type), volume));
        return canister;
    }

    private static int enderIn(GameTestHelper helper) {
        CanisterFluidContent content = canisters(helper).getSlotFluidContent(NORTH_SLOTS[SECOND]);
        return content.isEmpty() ? 0 : content.amount();
    }

    /** A click on the crystallizer's top face over a canister's quarter, the dial facing north. */
    private static BlockHitResult topHit(GameTestHelper helper, int role) {
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        return new BlockHitResult(new Vec3(abs.getX() + NORTH_CANISTER_CENTERS[role][0] / PIXELS, abs.getY() + 1.0,
                abs.getZ() + NORTH_CANISTER_CENTERS[role][1] / PIXELS), Direction.UP, abs, false);
    }

    /** A hit on the side of a canister standing in the canister block above, the dial facing north. */
    private static BlockHitResult canisterHit(GameTestHelper helper, int role) {
        BlockPos abs = helper.absolutePos(CANISTERS_POS);
        return new BlockHitResult(new Vec3(abs.getX() + NORTH_CANISTER_CENTERS[role][0] / PIXELS, abs.getY() + HALF,
                abs.getZ() + NORTH_CANISTER_CENTERS[role][1] / PIXELS), Direction.UP, abs, false);
    }

    /**
     * Clicks the mature crystal empty-handed and asserts one chrysm of the tier and
     * type lands in the player hand.
     */
    private static void assertClickHands(GameTestHelper helper, Item tier, ResourceKey<GooTypeDefinition> type) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(new Vec3(abs.getX() + CRYSTAL_SPOT_NORTH[0],
                abs.getY() + 1.0 + CRYSTAL_HIT_LIFT, abs.getZ() + CRYSTAL_SPOT_NORTH[1]), Direction.UP, abs, false));
        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(hand.is(tier) && hand.getCount() == 1 && type.equals(hand.get(GooDataComponents.GOO_TYPE.get())),
                "The click should hand one " + type.identifier().getPath() + " " + tier + ", found " + hand);
    }
}
