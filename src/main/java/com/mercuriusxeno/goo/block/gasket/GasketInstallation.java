package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Utility for gasket lifecycle operations: popping gaskets as item drops
 * and clearing their registry state. Every removal of an installed gasket,
 * by hand or by breaking its host, goes through here (decision
 * diagnose-then-fix-gasket-registry-holes).
 */
public final class GasketInstallation {

    private GasketInstallation() {
    }

    /**
     * Pops a gasket as an item drop at the given position, clears its partner's
     * reference to it, unlinks it from that partner, and removes its registry
     * location. No-op if gasketId is null.
     *
     * @param level    the current level
     * @param pos      the block position
     * @param gasketId the gasket UUID
     */
    public static void popGasket(Level level, BlockPos pos, @Nullable UUID gasketId) {
        popGasket(level, pos, gasketId != null, gasketId);
    }

    /**
     * Pops a gasket a host holds installed, as its blockstate flag says: drops
     * the item whenever installed is true, and clears the gasket's registry state
     * when it carries an id.
     *
     * @param level     the current level
     * @param pos       the block position
     * @param installed whether the host holds a gasket on this face
     * @param gasketId  the gasket UUID, or null when none was ever assigned
     */
    public static void popGasket(Level level, BlockPos pos, boolean installed, @Nullable UUID gasketId) {
        if (!installed) {
            return;
        }
        Block.popResource(level, pos, new ItemStack(GooItems.CHORAL_GASKET.get()));
        releaseFromRegistry(level, gasketId);
    }

    /**
     * The one sneak empty-hand removal every machine routes through (decision
     * sneak-empty-hand-pops-hit-gasket): when the player sneaks with an empty
     * main hand and the hit addresses an installed gasket, hands the gasket
     * item to the player, releases it from the registry and clears it from its
     * holder. Anything else leaves the world unchanged, so the machine runs its
     * standing empty-hand behavior.
     *
     * @param level  the current level
     * @param pos    the block position
     * @param player the interacting player
     * @param hit    the ray trace hit result
     * @return true if a gasket was removed
     */
    public static boolean removeAddressedGasket(Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !player.isSecondaryUseActive() || !player.getMainHandItem().isEmpty()) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof IGasketHolder holder)) {
            return false;
        }
        AddressedGasket gasket = holder.addressedGasket(hit);
        if (gasket == null) {
            return false;
        }
        UUID gasketId = holder.getGasketId(gasket.role(), gasket.slot());
        PlayerUtils.addOrDrop(player, new ItemStack(GooItems.CHORAL_GASKET.get()));
        releaseFromRegistry(level, gasketId);
        holder.uninstallGasket(gasket);
        return true;
    }

    /**
     * Clears the partner's reference to a leaving gasket, unlinks it and removes
     * its registry location. No-op for a null id or a client level.
     *
     * @param level    the current level
     * @param gasketId the gasket leaving, or null when none was ever assigned
     */
    private static void releaseFromRegistry(Level level, @Nullable UUID gasketId) {
        if (gasketId != null && level instanceof ServerLevel serverLevel) {
            GasketRegistry registry = GasketRegistry.get(serverLevel);
            clearPartnerReference(serverLevel, registry, gasketId);
            registry.unlink(gasketId);
            registry.updateLocation(gasketId, null);
        }
    }

    /**
     * Clears the partner reference the gasket's link partner holds, found through
     * the partner's registry location, so the partner stops showing and pushing
     * to a gasket that left.
     *
     * @param level    the server level
     * @param registry the gasket registry
     * @param gasketId the gasket leaving
     */
    private static void clearPartnerReference(ServerLevel level, GasketRegistry registry, UUID gasketId) {
        UUID partnerId = registry.getTarget(gasketId);
        if (partnerId == null) {
            partnerId = registry.getSource(gasketId);
        }
        GasketLocation location = partnerId == null ? null : registry.getLocation(partnerId);
        IGasketHolder holder = loadedHolderAt(level, location);
        if (holder == null) {
            return;
        }
        for (GasketRole role : GasketRole.values()) {
            if (partnerId.equals(holder.getGasketId(role, location.slot()))) {
                holder.setPartner(role, location.slot(), null);
                return;
            }
        }
    }

    /**
     * Returns the gasket holder standing at a block location in this level
     * and loaded, or null for an entity target, another dimension or an
     * unloaded chunk.
     *
     * @param level    the server level
     * @param location the registry location, or null
     * @return the holder, or null
     */
    private static @Nullable IGasketHolder loadedHolderAt(ServerLevel level, @Nullable GasketLocation location) {
        if (location == null || location.isEntityTarget() || !level.dimension().equals(location.dimension())) {
            return null;
        }
        if (!level.isLoaded(location.pos())) {
            return null;
        }
        return level.getBlockEntity(location.pos()) instanceof IGasketHolder holder ? holder : null;
    }
}
