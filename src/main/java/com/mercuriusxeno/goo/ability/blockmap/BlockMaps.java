package com.mercuriusxeno.goo.ability.blockmap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the block maps under {@code data/<ns>/block_maps/} on every datapack
 * load and holds the server's current set, which a step names by id
 * (decision petrify-stone-encasement-and-calcify-map). The set is the
 * running server's alone, swapped whole on each load and dropped when the
 * server stops.
 */
public final class BlockMaps extends SimplePreparableReloadListener<Map<Identifier, BlockMap>> {

    /** Datapack directory: data/<ns>/block_maps/. */
    private static final String DIRECTORY = "block_maps";

    /** Registration id for the reload listener. */
    public static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY);

    private static final String LOG_LOADED = "Loaded {} block maps";
    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    private static volatile Map<Identifier, BlockMap> current = Map.of();

    /**
     * The block map the current load holds under an id.
     *
     * @param id the map's id, its file under block_maps
     * @return the map, empty where the load holds none under the id
     */
    public static Optional<BlockMap> get(Identifier id) {
        return Optional.ofNullable(current.get(id));
    }

    /** Drops the held maps, as a server stop does. */
    public static void clear() {
        current = Map.of();
    }

    @Override
    protected Map<Identifier, BlockMap> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return IdentifiedJsonScan.scan(manager, LISTER, JsonOps.INSTANCE, id -> BlockMap.CODEC);
    }

    @Override
    protected void apply(Map<Identifier, BlockMap> prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = Map.copyOf(prepared);
        Goo.LOGGER.info(LOG_LOADED, prepared.size());
    }
}
