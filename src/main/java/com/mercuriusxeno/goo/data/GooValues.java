package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.loading.FMLPaths;
import org.jspecify.annotations.Nullable;

/**
 * Where a reader finds goo values: by the level it stands in. A server level
 * answers its server's registry, which lives from server start to server stop;
 * a client level answers the values its connection received, which live as
 * long as the connection (decision type-package-and-per-server-holders).
 */
public final class GooValues {

    /**
     * File under the config directory the server caches its effective values in.
     */
    static final String CACHE_FILE = "goo_derived_values.json";
    private static final String LOG_VALUES_LOADED = "Goo values loaded: {} effective values from cache";
    private static final String LOG_NO_CACHE = "No cached goo values found, deriving from recipes";
    private static final String NOT_STARTED = "The server holds no goo value registry: it has not started or has stopped";

    private GooValues() {
    }

    /**
     * Answers the goo values the level's side holds.
     *
     * @param level the level the reader stands in
     * @return the values, empty when the side holds none
     */
    public static IGooValueLookup of(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            return of(server);
        }
        if (level instanceof GooValueSource source) {
            return source.gooValueLookup();
        }
        return GooValueTable.EMPTY;
    }

    /**
     * Answers the goo values the server holds.
     *
     * @param server the server
     * @return the values, empty before the server starts and after it stops
     */
    public static IGooValueLookup of(MinecraftServer server) {
        GooValueRegistry registry = heldBy(server);
        return registry == null ? GooValueTable.EMPTY : registry.table();
    }

    /**
     * Answers the registry a running server holds, for the commands that
     * reload, derive and audit it.
     *
     * @param server the running server
     * @return the registry
     * @throws IllegalStateException when the server has not started or has stopped
     */
    public static GooValueRegistry registryOf(MinecraftServer server) {
        GooValueRegistry registry = heldBy(server);
        if (registry == null) {
            throw new IllegalStateException(NOT_STARTED);
        }
        return registry;
    }

    /**
     * Stands a fresh registry on a starting server: loads the cached values,
     * or derives them from the server's recipes and caches them when no cache
     * exists.
     *
     * @param server the starting server
     * @return the registry the server now holds
     */
    public static GooValueRegistry attach(MinecraftServer server) {
        GooValueRegistry registry = new GooValueRegistry();
        registry.setEffectiveCachePath(FMLPaths.CONFIGDIR.get().resolve(CACHE_FILE));
        registry.loadEffectiveCache();
        if (registry.table().size() == 0) {
            Goo.LOGGER.info(LOG_NO_CACHE);
            registry.loadBaseValuesFromPacks(server);
            registry.deriveFromRecipes(server);
            registry.saveEffectiveValues();
        }
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_VALUES_LOADED, registry.table().size());
        }
        ((GooServerValueHolder) server).holdGooValueRegistry(registry);
        return registry;
    }

    /**
     * Drops the registry a stopped server holds.
     *
     * @param server the stopped server
     */
    public static void detach(MinecraftServer server) {
        ((GooServerValueHolder) server).holdGooValueRegistry(null);
    }

    private static @Nullable GooValueRegistry heldBy(MinecraftServer server) {
        return ((GooServerValueHolder) server).gooValueRegistry();
    }
}
