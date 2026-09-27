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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Gametests for the crystallizer (decision crystallizer-emits-chrysm): a gasket
 * receiver taking one goo type and crystal goo and refusing a third, and forming
 * one chrysm after {@link CrystallizerPhases#CHRYSM_TICKS} that an empty-hand
 * click hands to the player.
 */
public final class CrystallizerTests {

    private static final BlockPos SENDER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CRYSTALLIZER_POS = new BlockPos(3, 1, 1);
    private static final int CHRYSM_VOLUME = Math.toIntExact(ChrysmTier.CHRYSM.volume());
    private static final int CRYSTAL_COST = CrystallizerPhases.crystalCost(ChrysmTier.CHRYSM);
    private static final int THIRD_TYPE_VOLUME = 500;
    private static final int PUSH_TICKS = 20;
    private static final double HALF = 0.5;

    private CrystallizerTests() {
    }

    /**
     * A crucible transmitter holding ender, crystal and rock, linked to a
     * crystallizer, fills it with ender and crystal while its rock stays put.
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
            helper.assertTrue(crystallizer.held().getVolume(GooTypes.ENDER) > 0,
                    "The crystallizer should take ender, held " + crystallizer.held());
            helper.assertTrue(crystallizer.held().getVolume(GooTypes.CRYSTAL) > 0,
                    "The crystallizer should take crystal, held " + crystallizer.held());
            helper.assertValueEqual(0, crystallizer.held().getVolume(GooTypes.ROCK), "rock in the crystallizer");
            helper.assertValueEqual(THIRD_TYPE_VOLUME, sender.getReservoir().getVolume(GooTypes.ROCK),
                    "rock left on the sender");
            helper.succeed();
        });
    }

    /**
     * A crystallizer holding 1,000 mB of ender and the phase's crystal forms no
     * chrysm before n ticks, forms one after, spending both, and an empty-hand
     * click puts it in the player's hand.
     *
     * @param helper the gametest helper
     */
    public static void formsOneChrysm(GameTestHelper helper) {
        helper.setBlock(CRYSTALLIZER_POS, GooBlocks.CRYSTALLIZER.get());
        CrystallizerBlockEntity crystallizer = helper.getBlockEntity(CRYSTALLIZER_POS, CrystallizerBlockEntity.class);
        helper.assertValueEqual(CHRYSM_VOLUME, crystallizer.insertGoo(GooTypes.ENDER, CHRYSM_VOLUME), "ender taken");
        helper.assertValueEqual(CRYSTAL_COST, crystallizer.insertGoo(GooTypes.CRYSTAL, CRYSTAL_COST), "crystal taken");
        helper.runAfterDelay(CrystallizerPhases.CHRYSM_TICKS - 2, () ->
                helper.assertTrue(crystallizer.formed().isEmpty(), "No chrysm should form before n ticks"));
        helper.runAfterDelay(CrystallizerPhases.CHRYSM_TICKS + 1, () -> {
            helper.assertTrue(crystallizer.held().isEmpty(), "Forming should spend the goo and crystal, held "
                    + crystallizer.held());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            BlockPos abs = helper.absolutePos(CRYSTALLIZER_POS);
            helper.useBlock(CRYSTALLIZER_POS, player, new BlockHitResult(
                    new Vec3(abs.getX() + HALF, abs.getY() + 1.0, abs.getZ() + HALF), Direction.UP, abs, false));
            ItemStack hand = player.getItemInHand(InteractionHand.MAIN_HAND);
            helper.assertTrue(hand.is(GooItems.CHRYSM.get()) && hand.getCount() == 1
                            && GooTypes.ENDER.equals(hand.get(GooDataComponents.GOO_TYPE.get())),
                    "The click should hand one ender chrysm, found " + hand);
            helper.assertTrue(crystallizer.formed().isEmpty(), "The chrysm should leave the crystallizer");
            helper.succeed();
        });
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
