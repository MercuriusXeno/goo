package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * Stands the {@code goo:goo_type} datapack registry. NeoForge loads every
 * {@code data/<namespace>/goo/goo_type/<id>.json} it finds into it when a
 * world's datapacks load, and sends the loaded entries to each client that
 * joins, so a type a datapack adds needs no code on either side.
 */
public final class GooTypeRegistry {

    private GooTypeRegistry() {
    }

    /**
     * Subscribes the registry declaration to the mod event bus.
     *
     * @param modEventBus the mod event bus
     */
    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(GooTypeRegistry::onNewRegistry);
    }

    /**
     * Declares the goo type registry, synced to clients with the same codec
     * the datapack decodes through.
     *
     * @param event the datapack registry declaration event
     */
    private static void onNewRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(GooTypes.REGISTRY, GooTypeDefinition.CODEC, GooTypeDefinition.CODEC);
    }
}
