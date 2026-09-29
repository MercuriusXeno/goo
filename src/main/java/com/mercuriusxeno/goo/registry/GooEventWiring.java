package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityLoader;
import com.mercuriusxeno.goo.block.ability.ChainMarkerFallScheduler;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.command.GooCommand;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.BlobThrowHandler;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mercuriusxeno.goo.type.GooTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The listeners goo hangs on the game bus and the common setup it runs, kept
 * under registry so the root package reaches no subsystem (decision
 * type-package-and-per-server-holders).
 */
public final class GooEventWiring {

    /**
     * Log message for startup value loading.
     */
    private static final String LOG_VALUES_LOADED = "Goo values loaded: {} effective values from cache";
    /**
     * Log message when no cache exists and derivation runs on first boot.
     */
    private static final String LOG_NO_CACHE = "No cached goo values found, deriving from recipes";

    private GooEventWiring() {
    }

    /**
     * Subscribes the capability registration and the common setup to the mod
     * bus, and every game-bus listener below to the NeoForge bus.
     *
     * @param modEventBus the mod event bus
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GooCapabilityRegistration::registerCapabilities);
        modEventBus.addListener(GooEventWiring::commonSetup);
        NeoForge.EVENT_BUS.register(GooEventWiring.class);
    }

    /**
     * Common setup: hands the choral gasket item the block it places.
     *
     * @param event the common setup event
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        ChoralGasketItem.setGasketBlockSupplier(GooBlocks.CHORAL_GASKET_BLOCK::get);
    }

    /**
     * Registers the reaction and ability datapack reload listeners.
     *
     * @param event the reload listener registration event
     */
    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        // The type registry is loaded by now and the value and ability loaders below read it by id.
        GooTypes.capture(event.getRegistryAccess());
        event.addListener(GooReactionLoader.LISTENER_ID, new GooReactionLoader());
        event.addListener(AbilityLoader.LISTENER_ID, new AbilityLoader());
    }

    /**
     * Loads goo values from the effective cache when the server starts.
     * If no cache exists (first run or fresh world), derives values from
     * recipes and saves the cache for subsequent starts.
     *
     * @param event the server starting event
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        GooValueRegistry values = Goo.GOO_VALUES;
        values.loadEffectiveCache();
        if (values.size() == 0) {
            Goo.LOGGER.info(LOG_NO_CACHE);
            values.loadBaseValuesFromPacks(event.getServer());
            values.deriveFromRecipes(event.getServer());
            values.saveEffectiveValues();
        }
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_VALUES_LOADED, values.size());
        }
    }

    /**
     * Registers /goo subcommands with the server command dispatcher.
     *
     * @param event the command registration event
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        GooCommand.register(event.getDispatcher());
    }

    /**
     * Sends goo values and ability definitions to players on login and datapack reload.
     *
     * @param event the datapack sync event (player-specific on login, all players on /reload)
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        AbilitySyncPayload abilityPayload = AbilitySyncPayload.fromRegistry();
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        event.getRelevantPlayers()
                .filter(player -> player.connection.hasChannel(abilityPayload))
                .forEach(player -> {
                    GooValueSync.sendToPlayer(player);
                    PacketDistributor.sendToPlayer(player, abilityPayload);
                });
    }

    /**
     * Ticks pending blob effects and tap drips so they apply on arrival.
     *
     * @param event the post-tick event instance
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        BlobThrowHandler.onServerTick(event);
        if (ChainMarkerFallScheduler.hasPending()) {
            ChainMarkerFallScheduler
                    .drainArrivedFalls(event.getServer().getTickCount());
        }
        TapDripScheduler.drainArrived(event.getServer());
    }
}
