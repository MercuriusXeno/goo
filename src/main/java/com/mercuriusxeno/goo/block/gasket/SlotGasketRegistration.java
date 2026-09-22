package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.data.GasketLocation;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.data.IGasketRegistryAccess;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Registry membership for the gaskets a slotted canister carries. Every block
 * entity that holds a canister in a slot registers the canister's top and
 * bottom gasket ids at its own position and slot while the canister sits
 * there, and clears them when the canister leaves, so tuner and pusher
 * lookups resolve the gasket to the block that currently holds it.
 */
public final class SlotGasketRegistration {

    private SlotGasketRegistration() {
    }

    /**
     * Registers both faces of a slot's canister at the holder's position.
     * No-op on the client or before registry access exists.
     *
     * @param access    the holder's registry access, or null on the client
     * @param level     the holder's level
     * @param pos       the holder's position
     * @param slotIndex the slot the canister sits in
     * @param meta      the canister's metadata
     */
    public static void register(@Nullable IGasketRegistryAccess access, @Nullable Level level,
                                BlockPos pos, int slotIndex, CanisterMetadata meta) {
        if (access == null || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        GasketRegistry registry = access.get();
        ResourceKey<Level> dimension = serverLevel.dimension();
        registerFace(registry, meta.topGasketId(), new GasketLocation(dimension, pos, true, slotIndex));
        registerFace(registry, meta.bottomGasketId(), new GasketLocation(dimension, pos, false, slotIndex));
    }

    /**
     * Clears the registry location of both faces of a slot's canister.
     * No-op before registry access exists.
     *
     * @param access the holder's registry access, or null on the client
     * @param meta   the canister's metadata
     */
    public static void deregister(@Nullable IGasketRegistryAccess access, CanisterMetadata meta) {
        if (access == null) {
            return;
        }
        GasketRegistry registry = access.get();
        deregisterFace(registry, meta.topGasketId());
        deregisterFace(registry, meta.bottomGasketId());
    }

    private static void registerFace(GasketRegistry registry, @Nullable UUID id, GasketLocation location) {
        if (id != null) {
            registry.updateLocation(id, location);
        }
    }

    private static void deregisterFace(GasketRegistry registry, @Nullable UUID id) {
        if (id != null) {
            registry.updateLocation(id, null);
        }
    }
}
