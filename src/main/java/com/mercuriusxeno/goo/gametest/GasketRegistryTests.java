package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
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
import org.jspecify.annotations.Nullable;
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

    private static final String LOCATION_MISMATCH = "%s: expected %s, got %s";

    private static final BlockPos HUB_POS = new BlockPos(3, 1, 1);
    private static final int HUB_SLOT = 0;
    private static final String CANISTER_LOCATION = "Canister gasket should first resolve to the canister block";
    private static final String HUB_INSERTED = "Hub should accept the canister into its slot";
    private static final String HUB_LOCATION = "Canister gasket moved into the hub should resolve to the hub's slot";
    private static final String HUB_REMOVED_CLEARS = "Removing the canister from the hub should clear its gasket location";
    private static final String HUB_BROKEN_CLEARS = "Breaking a hub holding the canister should clear its gasket location";

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
            assertLocation(helper, GasketRegistry.get(level).getLocation(topId), expected, TAP_LOCATION);
            helper.succeed();
        });
    }

    /**
     * Hub: a canister carrying a linked gasket, moved from a canister block into
     * a hub slot, resolves its registry location at the hub's slot; removing it,
     * or breaking the hub that holds it, clears that location.
     *
     * @param helper the gametest helper
     */
    public static void hubSlotGasketRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GasketRegistry registry = GasketRegistry.get(level);
        UUID topId = UUID.randomUUID();
        registry.link(UUID.randomUUID(), topId);
        helper.setBlock(BE_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity canisterBlock = helper.getBlockEntity(BE_POS, CanisterBlockEntity.class);
        canisterBlock.insertCanister(CanisterBlock.CENTER_SLOT, canisterWithGaskets(topId, UUID.randomUUID()), false);
        assertLocation(helper, registry.getLocation(topId), new GasketLocation(level.dimension(),
                helper.absolutePos(BE_POS), true, CanisterBlock.CENTER_SLOT), CANISTER_LOCATION);
        ItemStack moved = canisterBlock.removeCanister(CanisterBlock.CENTER_SLOT);

        helper.setBlock(HUB_POS, GooBlocks.HUB.get());
        HubBlockEntity hub = helper.getBlockEntity(HUB_POS, HubBlockEntity.class);
        helper.assertTrue(hub.insertCanister(HUB_SLOT, moved), HUB_INSERTED);
        GasketLocation atHub = new GasketLocation(level.dimension(), helper.absolutePos(HUB_POS), true, HUB_SLOT);
        assertLocation(helper, registry.getLocation(topId), atHub, HUB_LOCATION);

        ItemStack removed = hub.removeCanister(HUB_SLOT);
        helper.assertTrue(registry.getLocation(topId) == null, HUB_REMOVED_CLEARS);

        hub.insertCanister(HUB_SLOT, removed);
        assertLocation(helper, registry.getLocation(topId), atHub, HUB_LOCATION);
        helper.destroyBlock(HUB_POS);
        helper.assertTrue(registry.getLocation(topId) == null, HUB_BROKEN_CLEARS);
        helper.succeed();
    }

    private static void assertLocation(GameTestHelper helper, @Nullable GasketLocation actual,
                                       GasketLocation expected, String message) {
        helper.assertTrue(expected.equals(actual), String.format(LOCATION_MISMATCH, message, expected, actual));
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
