package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
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
 * Gametest for the vat's pour stream after a blob click
 * (decision diagnose-then-fix-vat-stream-flash).
 */
public final class VatStreamTests {

    private static final BlockPos VAT_POS = new BlockPos(1, 1, 1);
    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final int BLOB_COUNT = 1;
    private static final int TICKS_AFTER_CLICK = 5;
    private static final String STREAM_TYPE = "Vat stream type ticks after the blob click";
    private static final String STREAM_RATE = "Vat stream rate ticks after the blob click";

    private VatStreamTests() {
    }

    /**
     * A blob clicked onto a placed vat leaves the vat reporting the blob's stream
     * several ticks later, at the volume the blob landed.
     *
     * @param helper the gametest helper
     */
    public static void blobClickHoldsStream(GameTestHelper helper) {
        helper.setBlock(VAT_POS, GooBlocks.VAT.get());
        VatBlockEntity vat = helper.getBlockEntity(VAT_POS, VatBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, BlobStacks.createForOutput(ROCK, BLOB_COUNT * BlobStacks.MB_PER_BLOB));

        BlockPos abs = helper.absolutePos(VAT_POS);
        helper.useBlock(VAT_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));

        helper.runAfterDelay(TICKS_AFTER_CLICK, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertValueEqual(vat.getVatStreamRate(now), BLOB_COUNT * BlobStacks.MB_PER_BLOB, STREAM_RATE);
            helper.assertTrue(vat.getVatStreamType(now) == ROCK, STREAM_TYPE);
            helper.succeed();
        });
    }
}
