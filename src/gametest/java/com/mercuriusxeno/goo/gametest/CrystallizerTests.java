package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Gametests for the crystallizer (decision crystallizer-emits-chrysm): a gasket
 * receiver taking one goo type and crystal goo and refusing a third, crystallizing
 * on arrival at 10% crystal up to the knob tier, and handing the highest tier
 * reached to an empty-hand click.
 */
public final class CrystallizerTests {

    private static final BlockPos SENDER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CRYSTALLIZER_POS = new BlockPos(3, 1, 1);
    private static final int CHRYSM_VOLUME = Math.toIntExact(ChrysmTier.CHRYSM.volume());
    private static final int CRYSTAL_COST = CHRYSM_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL;
    private static final int THIRD_TYPE_VOLUME = 500;
    private static final int KILO_VOLUME = Math.toIntExact(ChrysmTier.KILOCHRYSM.volume());
    private static final int HALF_KILO = KILO_VOLUME / 2;
    private static final int SENDER_VOLUME = 5_000;
    private static final int PUSH_TICKS = 20;
    private static final int CRYSTALLIZE_TICKS = 2;
    private static final double HALF = 0.5;

    private CrystallizerTests() {
    }

    /**
     * A crucible transmitter holding ender, crystal and rock, linked to a
     * crystallizer, feeds it ender and crystal, which crystallize, while its rock stays put.
     *
     * @param helper the gametest helper
     */
    public static void takesTwoTypesRefusesThird(GameTestHelper helper) {
        CrucibleBlockEntity sender = placeLinkedPair(helper);
        sender.insertGoo(GooTypes.ENDER, CHRYSM_VOLUME);
        sender.insertGoo(GooTypes.CRYSTAL, CRYSTAL_COST);
        sender.insertGoo(GooTypes.ROCK, THIRD_TYPE_VOLUME);
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        helper.runAfterDelay(PUSH_TICKS, () -> {
            helper.assertTrue(crystallizer.crystallized() > 0,
                    "The crystallizer should take and crystallize ender, crystallized " + crystallizer.crystallized());
            helper.assertTrue(sender.getReservoir().getVolume(GooTypes.CRYSTAL) < CRYSTAL_COST,
                    "The crystallizer should take crystal from the sender");
            helper.assertValueEqual(0, crystallizer.held().getVolume(GooTypes.ROCK), "rock in the crystallizer");
            helper.assertValueEqual(THIRD_TYPE_VOLUME, sender.getReservoir().getVolume(GooTypes.ROCK),
                    "rock left on the sender");
            helper.succeed();
        });
    }

    /**
     * A crystallizer given 1,000 mB of ender and 100 mB of crystal crystallizes
     * both on arrival into one chrysm, and an empty-hand click puts it in the player hand.
     *
     * @param helper the gametest helper
     */
    public static void formsOneChrysm(GameTestHelper helper) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get());
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        helper.assertValueEqual(CHRYSM_VOLUME, crystallizer.insertGoo(GooTypes.ENDER, CHRYSM_VOLUME), "ender taken");
        helper.assertValueEqual(CRYSTAL_COST, crystallizer.insertGoo(GooTypes.CRYSTAL, CRYSTAL_COST), "crystal taken");
        helper.runAfterDelay(CRYSTALLIZE_TICKS, () -> {
            helper.assertTrue(crystallizer.held().isEmpty(), "Crystallizing should spend the goo and crystal, held "
                    + crystallizer.held());
            assertClickHands(helper, crystallizer, GooItems.CHRYSM.get());
            helper.succeed();
        });
    }

    /**
     * 1,000 mB of ender with only 50 mB of crystal crystallizes half and forms no
     * chrysm; 50 mB more crystal finishes it.
     *
     * @param helper the gametest helper
     */
    public static void pausesWithoutCrystal(GameTestHelper helper) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get());
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        crystallizer.insertGoo(GooTypes.ENDER, CHRYSM_VOLUME);
        crystallizer.insertGoo(GooTypes.CRYSTAL, CRYSTAL_COST / 2);
        helper.runAfterDelay(CRYSTALLIZE_TICKS, () -> {
            helper.assertValueEqual(CHRYSM_VOLUME / 2L, crystallizer.crystallized(), "crystallized on half the crystal");
            helper.assertTrue(crystallizer.formed().isEmpty(), "No chrysm should form without the crystal for it");
            crystallizer.insertGoo(GooTypes.CRYSTAL, CRYSTAL_COST / 2);
            helper.runAfterDelay(CRYSTALLIZE_TICKS, () -> {
                assertClickHands(helper, crystallizer, GooItems.CHRYSM.get());
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
        CrystallizerBlockEntity crystallizer = placeAtMedium(helper);
        helper.assertValueEqual(KILO_VOLUME, crystallizer.insertGoo(GooTypes.ENDER, KILO_VOLUME), "ender taken");
        crystallizer.insertGoo(GooTypes.CRYSTAL, KILO_VOLUME / CrystallizerPhases.GOO_PER_CRYSTAL);
        helper.runAfterDelay(CRYSTALLIZE_TICKS, () -> {
            assertClickHands(helper, crystallizer, GooItems.KILOCHRYSM.get());
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
        CrystallizerBlockEntity crystallizer = placeAtMedium(helper);
        crystallizer.insertGoo(GooTypes.ENDER, HALF_KILO);
        crystallizer.insertGoo(GooTypes.CRYSTAL, HALF_KILO / CrystallizerPhases.GOO_PER_CRYSTAL);
        helper.runAfterDelay(CRYSTALLIZE_TICKS, () -> {
            assertClickHands(helper, crystallizer, GooItems.CHRYSM.get());
            helper.assertValueEqual((long) HALF_KILO - CHRYSM_VOLUME, crystallizer.crystallized(),
                    "crystallized goo kept after taking a chrysm");
            helper.succeed();
        });
    }

    /**
     * With the knob at small, a linked crystallizer holding a chrysm leaves the
     * sender ender where it is; once a click takes the chrysm, the sender ender
     * starts to drop.
     *
     * @param helper the gametest helper
     */
    public static void smallDialHoldsAtChrysm(GameTestHelper helper) {
        CrucibleBlockEntity sender = placeLinkedPair(helper);
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        crystallizer.insertGoo(GooTypes.ENDER, CHRYSM_VOLUME);
        crystallizer.insertGoo(GooTypes.CRYSTAL, CRYSTAL_COST);
        helper.runAfterDelay(CRYSTALLIZE_TICKS, () -> {
            helper.assertTrue(crystallizer.formedTier() == ChrysmTier.CHRYSM, "A chrysm should form");
            sender.insertGoo(GooTypes.ENDER, SENDER_VOLUME);
            sender.insertGoo(GooTypes.CRYSTAL, SENDER_VOLUME);
            helper.runAfterDelay(PUSH_TICKS, () -> {
                helper.assertValueEqual(SENDER_VOLUME, sender.getReservoir().getVolume(GooTypes.ENDER),
                        "sender ender while the chrysm is held");
                assertClickHands(helper, crystallizer, GooItems.CHRYSM.get());
                helper.runAfterDelay(PUSH_TICKS, () -> {
                    helper.assertTrue(sender.getReservoir().getVolume(GooTypes.ENDER) < SENDER_VOLUME,
                            "The sender ender should drop once the chrysm is taken");
                    helper.succeed();
                });
            });
        });
    }

    private static CrystallizerBlockEntity placeAtMedium(GameTestHelper helper) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get().defaultBlockState()
                .setValue(CrystallizerBlock.DIAL, ChrysmTier.KILOCHRYSM.ordinal() + 1));
        return helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
    }

    /**
     * A right click on the dial steps it small, medium, large and wraps back to small.
     *
     * @param helper the gametest helper
     */
    public static void dialClickWrapsFromLargeToSmall(GameTestHelper helper) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        BlockHitResult dialHit = new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + 1.0, abs.getZ() + HALF), Direction.UP, abs, false);
        for (int expected : new int[] {2, 3, 1}) {
            helper.useBlock(CRYSTALLIZER_POS, player, dialHit);
            helper.assertValueEqual(expected, helper.getBlockState(CRYSTALLIZER_POS).getValue(CrystallizerBlock.DIAL),
                    "dial after a click");
        }
        helper.succeed();
    }

    /**
     * Clicks the crystallizer side empty-handed and asserts one ender chrysm of
     * the tier lands in the player hand.
     */
    private static void assertClickHands(GameTestHelper helper, CrystallizerBlockEntity crystallizer, Item tier) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
        helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(
                new Vec3(abs.getX() + HALF, abs.getY() + HALF, abs.getZ()), Direction.NORTH, abs, false));
        ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(hand.is(tier) && hand.getCount() == 1
                        && GooTypes.ENDER.equals(hand.get(GooDataComponents.GOO_TYPE.get())),
                "The click should hand one ender " + tier + ", found " + hand);
    }

    /**
     * Sets a gasketed crucible and a gasketed crystallizer and links the
     * crucible's transmitter to the crystallizer's receiver.
     *
     * @param helper the gametest helper
     * @return the crucible, the sender
     */
    private static CrucibleBlockEntity placeLinkedPair(GameTestHelper helper) {
        helper.setBlock(SENDER_POS, GooBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.HAS_GASKET, true));
        helper.setBlock(CRYSTALLIZER_POS,
                GooBlocks.CRYSTALLIZER.get().defaultBlockState().setValue(CrystallizerBlock.HAS_GASKET, true));
        CrucibleBlockEntity sender = helper.getBlockEntity(SENDER_POS, CrucibleBlockEntity.class);
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        UUID transmitter = sender.ensureGasketId(GasketRole.TRANSMITTER);
        UUID receiver = crystallizer.ensureGasketId(GasketRole.RECEIVER);
        GasketRegistry.get(helper.getLevel()).link(transmitter, receiver);
        sender.setPartner(GasketRole.TRANSMITTER,
                new GasketPartner(helper.absolutePos(CRYSTALLIZER_POS), GooConstants.NO_SLOT));
        crystallizer.setPartner(GasketRole.RECEIVER,
                new GasketPartner(helper.absolutePos(SENDER_POS), GooConstants.NO_SLOT));
        return sender;
    }
}
