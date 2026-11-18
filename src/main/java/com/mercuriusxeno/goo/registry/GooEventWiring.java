package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.ability.AbilityLoader;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityRegistrySource;
import com.mercuriusxeno.goo.ability.aging.Aging;
import com.mercuriusxeno.goo.ability.aging.AgingTable;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.ability.quantum.PortalPairs;
import com.mercuriusxeno.goo.block.ability.LayerFalls;
import com.mercuriusxeno.goo.block.ability.ServerFallLanding;
import com.mercuriusxeno.goo.command.GooCommand;
import com.mercuriusxeno.goo.command.ValuesManagers;
import com.mercuriusxeno.goo.data.GooGroups;
import com.mercuriusxeno.goo.data.GooReactionLoader;
import com.mercuriusxeno.goo.data.GooReactionSource;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.gasket.ChoralGasketItem;
import com.mercuriusxeno.goo.lab.LabFirstJoin;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.network.GooValueSync;
import com.mercuriusxeno.goo.network.PlayerKnowledge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
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
     * Common setup: hands the choral gasket item the block it places, and has water cool molten iron.
     *
     * @param event the common setup event
     */
    private static void commonSetup(FMLCommonSetupEvent event) {
        ChoralGasketItem.setGasketBlockSupplier(GooBlocks.CHORAL_GASKET_BLOCK::get);
        event.enqueueWork(GooFluids::registerMoltenIronCooling);
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
        // petrify-stone-encasement-and-calcify-map: the block maps an ability steps blocks by
        event.addListener(BlockMaps.LISTENER_ID, new BlockMaps());
        // old-blob-ages-valuables-slowly: the table an aging throw is priced and refused by
        event.addListener(AgingTable.LISTENER_ID, new AgingTable());
        // this-becomes-that-is-two-parallel-groups-in-the-goo-workbench: the groups an ability pairs by position
        event.addListener(GooGroups.LISTENER_ID, new GooGroups());
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
     * Ends the values manager's dump run and worker for the stopping server, so nothing outlives its game
     * (decision goo-workbench-opens-the-in-game-manager-screen).
     *
     * @param event the server stopping event
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ValuesManagers.close(event.getServer());
        // falling-layer-lands-when-its-drip-arrives: no fall ends unlanded, so a stop lands each at once
        GooServerState.of(event.getServer()).layerFalls().flush(new ServerFallLanding(event.getServer()));
    }

    /**
     * Lands each layer falling in a level whose drip arrives this tick, at the
     * start of the level's tick, so the layer stands in its cell through the
     * whole of its arrival tick.
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LayerFalls falls = GooServerState.of(level.getServer()).layerFalls();
            if (falls.hasPending()) {
                falls.tick(level.dimension(), level.getServer().getTickCount(), new ServerFallLanding(level.getServer()));
            }
        }
    }

    /**
     * Lands at once every layer falling from a chunk as it unloads, so no fall ends unlanded.
     * falling-layer-lands-when-its-drip-arrives
     *
     * @param event the chunk unload event
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GooServerState.of(level.getServer()).layerFalls().flushChunk(level.dimension(), event.getChunk().getPos(),
                    new ServerFallLanding(level.getServer()));
        }
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
        BlockMaps.clear();
        AgingTable.clear();
        GooGroups.clear();
        Aging.clear();
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
     * Sends goo values, ability definitions and each player's known items to players on login and datapack reload.
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
                    PlayerKnowledge.sendToPlayer(player);
                    PortalPairs.sync(player);
                });
    }

    /**
     * Fits out the Goo Lab save on its first join in a dev launch: the lab
     * built, the kit handed and every recipe taught, once per save. It runs
     * after onDatapackSync, so the known set the teaching sends lands on the client last.
     * decision goo-lab-save-builds-and-kits-on-first-join
     * decision goo-lab-save-teaches-every-recipe-on-first-join
     *
     * @param event the login event
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && LabFirstJoin.fitsOut(FMLEnvironment.isProduction(),
                player.level().getServer().getWorldData().getLevelName())) {
            LabFirstJoin.fitOutOnLogin(player);
        }
    }

    /**
     * Lands the server's pending goo effects and marker falls on arrival.
     *
     * @param event the post-tick event instance
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        GooServerState.of(event.getServer()).drainArrived(event.getServer());
    }
}
