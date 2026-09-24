package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.UUID;

/**
 * Gametests for the gasket registry following a gasket through every host
 * (decision diagnose-then-fix-gasket-registry-holes).
 */
public final class GasketRegistryTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    private static final int RELOAD_SETTLE_TICKS = 2;
    private static final String TAP_RELOADED = "Reloaded tap block entity should stand at the tap position";
    private static final String TAP_REGISTRY_ACCESS = "Reloaded tap's gasket attachment should hold registry access";
    private static final String TAP_LOCATION = "Reloaded tap's canister gasket should resolve to the tap's slot";

    private GasketRegistryTests() {
    }

    /**
     * Tap: after the block entity reloads from its saved data, its gasket
     * attachment holds registry access and the gasket on its canister resolves
     * a registry location at the tap.
     *
     * @param helper the gametest helper
     */
    public static void tapAttachmentLoads(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.TAP.get());
        TapBlockEntity tap = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
        UUID topId = UUID.randomUUID();
        tap.insertCanister(canisterWithGaskets(topId, UUID.randomUUID()));
        reloadBlockEntity(helper, tap);

        helper.runAfterDelay(RELOAD_SETTLE_TICKS, () -> {
            ServerLevel level = helper.getLevel();
            TapBlockEntity reloaded = helper.getBlockEntity(BE_POS, TapBlockEntity.class);
            helper.assertTrue(reloaded != tap, TAP_RELOADED);
            helper.assertTrue(reloaded.gasket().registryAccess() != null, TAP_REGISTRY_ACCESS);
            GasketLocation expected = new GasketLocation(level.dimension(),
                    helper.absolutePos(BE_POS), true, TapBlockEntity.SLOT);
            helper.assertValueEqual(GasketRegistry.get(level).getLocation(topId), expected, TAP_LOCATION);
            helper.succeed();
        });
    }

    private static ItemStack canisterWithGaskets(UUID topId, UUID bottomId) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(canister,
                CanisterItem.getMetadata(canister).withTopGasketId(topId).withBottomGasketId(bottomId));
        return canister;
    }

    /**
     * Replaces a block entity with one built from its own saved data, the path a
     * chunk unload and load take: the old entity is removed first, loadStatic reads
     * the data, then the level sets the level and queues onLoad.
     *
     * @param helper the gametest helper
     * @param be     the block entity to reload
     */
    private static void reloadBlockEntity(GameTestHelper helper, BlockEntity be) {
        ServerLevel level = helper.getLevel();
        CompoundTag saved = be.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(be.getBlockPos());
        BlockEntity reloaded = BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), saved,
                level.registryAccess());
        helper.assertTrue(reloaded != null, TAP_RELOADED);
        level.setBlockEntity(reloaded);
    }
}
