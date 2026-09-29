package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gametest for the vat's pour stream after a goo click
 * (decision diagnose-then-fix-vat-stream-flash).
 */
public final class VatStreamTests {

    private static final BlockPos VAT_POS = new BlockPos(1, 1, 1);
    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final int GOO_COUNT = 1;
    private static final int TICKS_AFTER_CLICK = 5;
    private static final String STREAM_TYPE = "Vat stream type ticks after the goo click";
    private static final String STREAM_RATE = "Vat stream rate ticks after the goo click";

    private VatStreamTests() {
    }

    /**
     * A goo clicked onto a placed vat leaves the vat reporting the goo's stream
     * several ticks later, at the volume the goo landed.
     *
     * @param helper the gametest helper
     */
    public static void gooClickHoldsStream(GameTestHelper helper) {
        helper.setBlock(VAT_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(VAT_POS, VatBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, GooStacks.createForOutput(ROCK, GOO_COUNT * GooStacks.THOUSAND));

        BlockPos abs = helper.absolutePos(VAT_POS);
        helper.useBlock(VAT_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

        helper.runAfterDelay(TICKS_AFTER_CLICK, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertValueEqual(vat.getVatStreamRate(now), GOO_COUNT * GooStacks.THOUSAND, STREAM_RATE);
            helper.assertTrue(vat.getVatStreamType(now) == ROCK, STREAM_TYPE);
            helper.succeed();
        });
    }
}
