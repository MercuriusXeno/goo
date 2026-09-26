package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooConstants;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    private static final BlockPos PARTNER_POS = new BlockPos(1, 1, 3);
    private static final int PARTNER_SLOT = CanisterBlock.CENTER_SLOT;
    private static final String REMOVAL = "removal";
    private static final String MACHINE_BROKEN = "The machine should have left the level";
    private static final String MACHINE_GASKET_ID = "Machine should hold a gasket id before it breaks";
    private static final String BREAK_LOCATION = "Broken machine's gasket should resolve no registry location";
    private static final String BREAK_PAIRING = "Broken machine's gasket should hold no registry pairing";
    private static final String PARTNER_PAIRING = "Partner's gasket should hold no registry pairing";
    /** The gasket item lands within a block of the broken machine. */
    private static final double DROP_RANGE = 1.5;
    private static final Supplier<BlockState> CRUCIBLE_GASKETED = () ->
            GooBlocks.CRUCIBLE.get().defaultBlockState().setValue(CrucibleBlock.HAS_GASKET, true);
    private static final Supplier<BlockState> VAT_GASKETED = () ->
            GooBlocks.VAT.get().defaultBlockState().setValue(VatBlock.GASKET_BASE, true);
    private static final Supplier<BlockState> TAP_GASKETED = () ->
            GooBlocks.TAP.get().defaultBlockState().setValue(TapBlock.HAS_GASKET, true);
    private static final Supplier<BlockState> HUB_GASKETED = () ->
            GooBlocks.HUB.get().defaultBlockState().setValue(HubBlock.HAS_GASKET, true);
    private static final String PARTNER_REF = "Partner's gasket state should read no partner";

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

    /**
     * Crucible: breaking a crucible whose gasket is linked to a partner drops the
     * gasket once, clears the gasket's location and pairing, and the partner reads unlinked.
     *
     * @param helper the gametest helper
     */
    public static void breakPopsGasketCrucible(GameTestHelper helper) {
        breakPopsGasket(helper, CRUCIBLE_GASKETED.get(), GasketRole.TRANSMITTER, GasketRegistryTests::playerBreaks);
    }

    /**
     * Vat: breaking a vat whose base gasket is linked to a partner drops the
     * gasket once, clears the gasket's location and pairing, and the partner reads unlinked.
     *
     * @param helper the gametest helper
     */
    public static void breakPopsGasketVat(GameTestHelper helper) {
        breakPopsGasket(helper, VAT_GASKETED.get(), GasketRole.TRANSMITTER, GasketRegistryTests::playerBreaks);
    }

    /**
     * Tap: breaking a tap whose gasket is linked to a partner drops the gasket
     * once, clears the gasket's location and pairing, and the partner reads unlinked.
     *
     * @param helper the gametest helper
     */
    public static void breakPopsGasketTap(GameTestHelper helper) {
        breakPopsGasket(helper, TAP_GASKETED.get(), GasketRole.RECEIVER, GasketRegistryTests::playerBreaks);
    }

    /**
     * Hub: breaking a hub whose intake gasket is linked to a partner drops the
     * gasket once, clears the gasket's location and pairing, and the partner reads unlinked.
     *
     * @param helper the gametest helper
     */
    public static void breakPopsGasketHub(GameTestHelper helper) {
        breakPopsGasket(helper, HUB_GASKETED.get(), GasketRole.RECEIVER, GasketRegistryTests::playerBreaks);
    }

    /**
     * Every gasketed machine removed without a player, as an explosion or a
     * command removes it, drops its gasket once and leaves the registry clean
     * (decision machine-base-owns-the-lifecycle): the block entity's removal
     * drops what no player break reached.
     *
     * @param helper the gametest helper
     */
    public static void removalPopsGasketEveryMachine(GameTestHelper helper) {
        List<Supplier<BlockState>> machines = List.of(CRUCIBLE_GASKETED, VAT_GASKETED, TAP_GASKETED, HUB_GASKETED);
        List<GasketRole> roles = List.of(GasketRole.TRANSMITTER, GasketRole.TRANSMITTER,
                GasketRole.RECEIVER, GasketRole.RECEIVER);
        for (int i = 0; i < machines.size(); i++) {
            assertBreakPops(helper, machines.get(i).get(), roles.get(i), h -> h.destroyBlock(BE_POS));
            helper.killAllEntitiesOfClass(ItemEntity.class);
        }
        helper.succeed();
    }

    /**
     * Has a mock server player break the machine, which runs the block's
     * {@code playerWillDestroy} before the block entity's removal.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    private static void playerBreaks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.gameMode.destroyBlock(helper.absolutePos(BE_POS));
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void breakPopsGasket(GameTestHelper helper, BlockState machineState, GasketRole machineRole,
                                        Consumer<GameTestHelper> breaker) {
        assertBreakPops(helper, machineState, machineRole, breaker);
        helper.succeed();
    }

    /**
     * Links the gasket of a machine placed in the given state to a canister
     * block's gasket the way the tuner does (registry pairing, locations and
     * both partner refs), breaks the machine, and asserts one gasket item
     * dropped and nothing in the registry or on the partner still names the
     * machine's gasket.
     *
     * @param helper       the gametest helper
     * @param machineState the machine's block state, gasket installed
     * @param machineRole  the role of the machine's gasket
     * @param breaker      how the machine leaves the level
     */
    private static void assertBreakPops(GameTestHelper helper, BlockState machineState, GasketRole machineRole,
                                        Consumer<GameTestHelper> breaker) {
        GasketRegistry registry = GasketRegistry.get(helper.getLevel());
        helper.setBlock(PARTNER_POS, GooBlocks.CANISTER.get());
        CanisterBlockEntity partner = helper.getBlockEntity(PARTNER_POS, CanisterBlockEntity.class);
        partner.insertCanister(PARTNER_SLOT, canisterWithGaskets(UUID.randomUUID(), UUID.randomUUID()), false);
        GasketRole partnerRole = machineRole == GasketRole.RECEIVER ? GasketRole.TRANSMITTER : GasketRole.RECEIVER;
        UUID partnerId = partner.getGasketId(partnerRole, PARTNER_SLOT);
        UUID machineId = linkMachineToPartner(helper, machineState, machineRole, partner, partnerRole);

        breaker.accept(helper);
        helper.assertTrue(helper.getBlockState(BE_POS).isAir(), MACHINE_BROKEN);

        helper.assertItemEntityCountIs(GooItems.CHORAL_GASKET.get(), BE_POS, DROP_RANGE, 1);
        helper.assertTrue(registry.getLocation(machineId) == null, BREAK_LOCATION);
        helper.assertTrue(registry.getTarget(machineId) == null && registry.getSource(machineId) == null,
                BREAK_PAIRING);
        helper.assertTrue(registry.getTarget(partnerId) == null && registry.getSource(partnerId) == null,
                PARTNER_PAIRING);
        helper.assertTrue(partner.getPartner(partnerRole, PARTNER_SLOT) == null, PARTNER_REF);
    }

    /**
     * Places the machine and links its gasket to the partner canister's gasket
     * the way the tuner leaves them: registry location and pairing, and each
     * side's partner reference.
     *
     * @param helper       the gametest helper
     * @param machineState the machine's block state, gasket installed
     * @param machineRole  the role of the machine's gasket
     * @param partner      the partner canister block
     * @param partnerRole  the role of the partner's gasket
     * @return the machine's gasket id
     */
    private static UUID linkMachineToPartner(GameTestHelper helper, BlockState machineState, GasketRole machineRole,
                                             CanisterBlockEntity partner, GasketRole partnerRole) {
        ServerLevel level = helper.getLevel();
        GasketRegistry registry = GasketRegistry.get(level);
        BlockPos partnerAbs = helper.absolutePos(PARTNER_POS);
        UUID partnerId = partner.getGasketId(partnerRole, PARTNER_SLOT);
        helper.setBlock(BE_POS, machineState);
        BlockPos machineAbs = helper.absolutePos(BE_POS);
        IGasketHolder machine = (IGasketHolder) helper.getBlockEntity(BE_POS, BlockEntity.class);
        UUID machineId = machine.ensureGasketId(machineRole);
        helper.assertTrue(machineId != null, MACHINE_GASKET_ID);
        registry.updateLocation(machineId, new GasketLocation(level.dimension(), machineAbs,
                machineRole == GasketRole.RECEIVER, GooConstants.NO_SLOT));
        if (machineRole == GasketRole.TRANSMITTER) {
            registry.link(machineId, partnerId);
        } else {
            registry.link(partnerId, machineId);
        }
        machine.setPartner(machineRole, GooConstants.NO_SLOT, new GasketPartner(partnerAbs, PARTNER_SLOT));
        partner.setPartner(partnerRole, PARTNER_SLOT, new GasketPartner(machineAbs, GooConstants.NO_SLOT));
        return machineId;
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
