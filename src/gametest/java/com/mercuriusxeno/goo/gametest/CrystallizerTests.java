package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Gametests for the crystallizer (decision crystallizer-emits-chrysm): two
 * canisters on its top, whichever holds crystal the catalyst and the other's goo
 * what grows, crystallizing at 10% crystal up to the knob tier, and handing the
 * highest tier reached to an empty-hand click.
 */
public final class CrystallizerTests {

    private static final BlockPos SENDER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CRYSTALLIZER_POS = new BlockPos(3, 1, 1);
    private static final int CHRYSM_VOLUME = Math.toIntExact(ChrysmTier.CHRYSM.volume());
    private static final int CRYSTAL_COST = CHRYSM_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL;
    private static final int KILO_VOLUME = Math.toIntExact(ChrysmTier.KILOCHRYSM.volume());
    private static final int HALF_KILO = KILO_VOLUME / 2;
    private static final int MORE_ENDER = 5_000;
    private static final int FIRST = 0;
    private static final int SECOND = 1;
    private static final int PUSH_TICKS = 20;
    /** A chrysm's 200 ticks at the pace, with margin. */
    private static final int CHRYSM_TICKS = 220;
    /** A kilochrysm's 400 ticks at the pace, with margin. */
    private static final int KILOCHRYSM_TICKS = 440;
    /** 500,000 mB at the pace: about 380 ticks, with margin. */
    private static final int HALF_KILO_TICKS = 420;
    /** Half a chrysm, 500 mB, at the flat pace of 5 mB a tick: 100 ticks, with margin. */
    private static final int HALF_CHRYSM_TICKS = 110;
    private static final int STILL_GROWING_TICKS = 100;
    private static final int SOME_TICKS = 20;
    private static final double HALF = 0.5;
    private static final double DIAL_CENTER_Y = 7.0 / 16.0;
    /** The purple spot the crystal grows from, block-local, with the dial facing north. */
    private static final double[] CRYSTAL_SPOT_NORTH = {8.0 / 16.0, 5.0 / 16.0};
    private static final double CRYSTAL_HIT_LIFT = 2.0 / 16.0;
    /** Block-local centers of the back-left and back-right slots with the dial facing north. */
    private static final double[][] NORTH_SLOT_CENTERS = {{11.0 / 16.0, 11.0 / 16.0}, {5.0 / 16.0, 11.0 / 16.0}};

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
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST), false);
        helper.runAfterDelay(SOME_TICKS, () -> {
            helper.assertValueEqual(0L, crystallizer.crystallized(), "crystallized from crystal alone");
            crystallizer.insertCanister(SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME), false);
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
        crystallizer.insertCanister(FIRST, canister(GooTypes.ENDER, CHRYSM_VOLUME), false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.CRYSTAL, CRYSTAL_COST), false);
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
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST), false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.CRYSTAL, CHRYSM_VOLUME), false);
        helper.runAfterDelay(CHRYSM_TICKS, () -> {
            assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.CRYSTAL);
            helper.succeed();
        });
    }

    /**
     * A canister clicked onto a filled slot stays in the hand and the slot keeps its canister;
     * clicked onto the empty slot it goes in.
     *
     * @param helper the gametest helper
     */
    public static void filledSlotRefusesACanister(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST), false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, canister(GooTypes.ENDER, CHRYSM_VOLUME));
        helper.useBlock(CRYSTALLIZER_POS, player, topHit(helper, FIRST));
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(GooItems.CANISTER.get()),
                "A canister clicked onto a filled slot should stay in the hand");
        helper.assertValueEqual(GooTypes.CRYSTAL, crystallizer.getSlotGooType(FIRST), "the filled slot's goo");
        helper.useBlock(CRYSTALLIZER_POS, player, topHit(helper, SECOND));
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "A canister clicked onto the empty slot should go in");
        helper.assertValueEqual(GooTypes.ENDER, crystallizer.getSlotGooType(SECOND), "the empty slot's new goo");
        helper.succeed();
    }

    /**
     * 1,000 mB of ender beside only 50 mB of crystal crystallizes half and forms no
     * chrysm; 50 mB more crystal finishes it. The model reads active while it
     * crystallizes and idle after.
     *
     * @param helper the gametest helper
     */
    public static void pausesWithoutCrystal(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST / 2), false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME), false);
        helper.runAfterDelay(STILL_GROWING_TICKS / 2, () -> helper.assertTrue(
                helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.ACTIVE),
                "The crystallizer should read active while crystallizing"));
        helper.runAfterDelay(HALF_CHRYSM_TICKS + SOME_TICKS + SOME_TICKS, () -> {
            helper.assertValueEqual(CHRYSM_VOLUME / 2L, crystallizer.crystallized(), "crystallized on half the crystal");
            helper.assertTrue(crystallizer.formed().isEmpty(), "No chrysm should form without the crystal for it");
            helper.assertFalse(helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.ACTIVE),
                    "The crystallizer should read idle once nothing crystallizes");
            crystallizer.insertGoo(FIRST, GooTypes.CRYSTAL, CRYSTAL_COST / 2);
            helper.runAfterDelay(HALF_CHRYSM_TICKS, () -> {
                assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.ENDER);
                helper.succeed();
            });
        });
    }

    /**
     * With the knob at medium, 1,000,000 mB of ender and 100,000 mB of crystal
     * crystallize into a kilochrysm that a click hands over.
     *
     * @param helper the gametest helper
     */
    public static void advancesToKilochrysm(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 2);
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, KILO_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL),
                false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.ENDER, KILO_VOLUME), false);
        helper.runAfterDelay(KILOCHRYSM_TICKS, () -> {
            assertClickHands(helper, GooItems.KILOCHRYSM.get(), GooTypes.ENDER);
            helper.succeed();
        });
    }

    /**
     * With the knob at medium, 500,000 mB of ender crystallized: a click hands one
     * chrysm and the crystallizer keeps the other 499,000 mB crystallized.
     *
     * @param helper the gametest helper
     */
    public static void takingKeepsTheRemainder(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 2);
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, HALF_KILO / CrystallizerPhases.GOO_PER_CRYSTAL),
                false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.ENDER, HALF_KILO), false);
        helper.runAfterDelay(HALF_KILO_TICKS, () -> {
            assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.ENDER);
            helper.assertValueEqual((long) HALF_KILO - CHRYSM_VOLUME, crystallizer.crystallized(),
                    "crystallized goo kept after taking a chrysm");
            helper.succeed();
        });
    }

    /**
     * With the knob at small, a crystallizer holding a chrysm leaves the ender in its
     * canister; once a click takes the chrysm, that ender starts to crystallize.
     *
     * @param helper the gametest helper
     */
    public static void smallDialHoldsAtChrysm(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST * 2), false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME), false);
        helper.runAfterDelay(CHRYSM_TICKS, () -> {
            helper.assertTrue(crystallizer.formedTier() == ChrysmTier.CHRYSM, "A chrysm should form");
            crystallizer.insertGoo(SECOND, GooTypes.ENDER, MORE_ENDER);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                helper.assertValueEqual(MORE_ENDER, enderIn(crystallizer), "ender held while the chrysm is inside");
                assertClickHands(helper, GooItems.CHRYSM.get(), GooTypes.ENDER);
                helper.runAfterDelay(SOME_TICKS, () -> {
                    helper.assertTrue(enderIn(crystallizer) < MORE_ENDER,
                            "The ender should crystallize once the chrysm is taken");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Full canisters of ender and crystal crystallize at the pace: no chrysm 100 ticks
     * in, one by 220 (operator ruling: a chrysm at about 10 s).
     *
     * @param helper the gametest helper
     */
    public static void crystallizesAtAnEvenPace(GameTestHelper helper) {
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, KILO_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL),
                false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.ENDER, KILO_VOLUME), false);
        helper.runAfterDelay(STILL_GROWING_TICKS, () -> {
            helper.assertTrue(crystallizer.crystallized() > 0 && crystallizer.formed().isEmpty(),
                    "Half way to a chrysm there should be crystallized goo and no chrysm, crystallized "
                            + crystallizer.crystallized());
            helper.runAfterDelay(CHRYSM_TICKS - STILL_GROWING_TICKS, () -> {
                helper.assertTrue(crystallizer.formedTier() == ChrysmTier.CHRYSM,
                        "A chrysm should have formed by 220 ticks, crystallized " + crystallizer.crystallized());
                helper.succeed();
            });
        });
    }

    /**
     * A crucible transmitter linked to the gasket on the ingredient canister's top
     * fills that canister, and the ender it brings crystallizes.
     *
     * @param helper the gametest helper
     */
    public static void gasketFillsTheIngredientCanister(GameTestHelper helper) {
        helper.setBlock(SENDER_POS, GooBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.HAS_GASKET, true));
        CrucibleBlockEntity sender = helper.getBlockEntity(SENDER_POS, CrucibleBlockEntity.class);
        CrystallizerBlockEntity crystallizer = placeCrystallizer(helper, 1);
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST), false);
        UUID receiver = UUID.randomUUID();
        ItemStack ingredient = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(ingredient, CanisterItem.getMetadata(ingredient).withTopGasketId(receiver));
        crystallizer.insertCanister(SECOND, ingredient, false);
        UUID transmitter = sender.ensureGasketId(GasketRole.TRANSMITTER);
        GasketRegistry.get(helper.getLevel()).link(transmitter, receiver);
        sender.setPartner(GasketRole.TRANSMITTER, new GasketPartner(helper.absolutePos(CRYSTALLIZER_POS), SECOND));
        crystallizer.setPartner(GasketRole.RECEIVER, SECOND,
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
        crystallizer.insertCanister(FIRST, canister(GooTypes.CRYSTAL, CRYSTAL_COST), false);
        crystallizer.insertCanister(SECOND, canister(GooTypes.ENDER, CHRYSM_VOLUME), false);
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
     * A right click on the dial, on the face toward the player, steps the knob small,
     * medium, large and wraps back to small; a click on the top face leaves it.
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
        for (int expected : new int[] {2, 3, 1}) {
            helper.useBlock(CRYSTALLIZER_POS, player, knobHit);
            helper.assertValueEqual(expected, helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.KNOB),
                    "knob after a click");
        }
        helper.succeed();
    }

    /**
     * Sets a crystallizer, its dial facing north, with the knob at the given size.
     */
    private static CrystallizerBlockEntity placeCrystallizer(GameTestHelper helper, int knob) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get().defaultBlockState()
                .setValue(CrystallizerBlock.FACING, Direction.NORTH).setValue(CrystallizerBlock.KNOB, knob));
        return helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
    }

    private static ItemStack canister(ResourceKey<GooTypeDefinition> type, int volume) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setFluidContent(canister, new CanisterFluidContent(GooFluids.resource(type), volume));
        return canister;
    }

    private static int enderIn(CrystallizerBlockEntity crystallizer) {
        CanisterFluidContent content = crystallizer.getSlotFluidContent(SECOND);
        return content.isEmpty() ? 0 : content.amount();
    }

    /** A click on the top face over a slot's footprint, the dial facing north. */
    private static BlockHitResult topHit(GameTestHelper helper, int slot) {
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        return new BlockHitResult(new Vec3(abs.getX() + NORTH_SLOT_CENTERS[slot][0], abs.getY() + 1.0,
                abs.getZ() + NORTH_SLOT_CENTERS[slot][1]), Direction.UP, abs, false);
    }

    /**
     * Clicks the crystallizer's back face empty-handed and asserts one chrysm of the
     * tier and type lands in the player hand.
     */
    private static void assertClickHands(GameTestHelper helper, Item tier, ResourceKey<GooTypeDefinition> type) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + HALF, abs.getZ() + 1.0), Direction.SOUTH, abs, false));
        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(hand.is(tier) && hand.getCount() == 1 && type.equals(hand.get(GooDataComponents.GOO_TYPE.get())),
                "The click should hand one " + type.identifier().getPath() + " " + tier + ", found " + hand);
    }
}
