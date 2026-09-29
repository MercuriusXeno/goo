package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.ability.AbilityLoader;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityRegistrySource;
import com.mercuriusxeno.goo.command.GooCommand;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.data.GooReactionSource;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.GooValueSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The listeners goo hangs on the game bus and the common setup it runs, kept
 * under registry so the root package reaches no subsystem (decision
 * type-package-and-per-server-holders).
 */
public final class GooEventWiring {

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
        // Each load hands what it reads to the resources it builds, which the server swaps in whole.
        event.addListener(GooReactionLoader.LISTENER_ID,
                new GooReactionLoader((GooReactionSource) event.getServerResources()));
        event.addListener(AbilityLoader.LISTENER_ID,
                new AbilityLoader((AbilityRegistrySource) event.getServerResources()));
    }

    /**
     * Stands the starting server's goo value registry, loaded from the cache
     * or derived from recipes.
     *
     * @param event the server starting event
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        GooValues.attach(event.getServer());
    }

    /**
     * Drops the stopped server's goo value registry and everything it held in
     * flight, so nothing lands against its levels and the next server starts
     * from its own.
     *
     * @param event the server stopped event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GooValues.detach(event.getServer());
        GooServerState.of(event.getServer()).clear();
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
        AbilitySyncPayload abilityPayload =
                AbilitySyncPayload.fromRegistry(AbilityRegistry.of(event.getPlayerList().getServer()));
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        event.getRelevantPlayers()
                .filter(player -> player.connection.hasChannel(abilityPayload))
                .forEach(player -> {
                    GooValueSync.sendToPlayer(player);
                    PacketDistributor.sendToPlayer(player, abilityPayload);
                });
    }

    /**
     * Lands the server's pending goo effects, marker falls and tap drips on arrival.
     *
     * @param event the post-tick event instance
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        GooServerState.of(event.getServer()).drainArrived(event.getServer());
    }
}
