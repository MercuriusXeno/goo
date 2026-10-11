package com.mercuriusxeno.goo.ability.aging;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Loads the aging table under {@code data/<ns>/goo_aging/} on every datapack
 * load and holds the server's current rows by the block each ages, so an
 * aging throw costs its row's price and a block no row names refuses it.
 * The table is the running server's alone, swapped whole on each load and
 * dropped when the server stops.
 * old-blob-ages-valuables-slowly
 */
public final class AgingTable extends SimplePreparableReloadListener<Map<Identifier, AgingEntry>> {

    /** Datapack directory: data/<ns>/goo_aging/. */
    public static final String DIRECTORY = "goo_aging";

    /** Registration id for the reload listener. */
    public static final Identifier LISTENER_ID = Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY);

    private static final String LOG_LOADED = "Loaded {} aging entries";
    private static final FileToIdConverter LISTER = FileToIdConverter.json(DIRECTORY);

    private static volatile Map<Identifier, AgingEntry> bySource = Map.of();

    /**
     * The row the current load holds for a block.
     *
     * @param state the block
     * @return the row, empty for a block no row names
     */
    public static Optional<AgingEntry> entryFor(BlockState state) {
        return entryFor(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /**
     * The row the current load holds for a block id.
     *
     * @param source the block's id
     * @return the row, empty for a block no row names
     */
    public static Optional<AgingEntry> entryFor(Identifier source) {
        return Optional.ofNullable(bySource.get(source));
    }

    /**
     * The yore an aging throw on a block costs: its row's price, or none,
     * a refusal, for a block no row names.
     *
     * @param table the rows by the block each ages
     * @param block the struck block's id
     * @return the price, empty where the throw is refused
     */
    public static OptionalInt priceIn(Map<Identifier, AgingEntry> table, Identifier block) {
        AgingEntry entry = table.get(block);
        return entry == null ? OptionalInt.empty() : OptionalInt.of(entry.price());
    }

    /**
     * The price the current load asks for an aging throw on a block.
     *
     * @param state the struck block
     * @return the price, empty where the throw is refused
     */
    public static OptionalInt priceOf(BlockState state) {
        return priceIn(bySource, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /**
     * Indexes rows by the block each ages.
     *
     * @param entries the rows
     * @return the rows by source block
     */
    public static Map<Identifier, AgingEntry> bySource(Collection<AgingEntry> entries) {
        Map<Identifier, AgingEntry> indexed = new HashMap<>();
        entries.forEach(entry -> indexed.put(entry.source(), entry));
        return Map.copyOf(indexed);
    }

    /** Drops the held rows, as a server stop does. */
    public static void clear() {
        bySource = Map.of();
    }

    @Override
    protected Map<Identifier, AgingEntry> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return IdentifiedJsonScan.scan(manager, LISTER, JsonOps.INSTANCE, id -> AgingEntry.CODEC);
    }

    @Override
    protected void apply(Map<Identifier, AgingEntry> prepared, ResourceManager manager, ProfilerFiller profiler) {
        bySource = bySource(prepared.values());
        Goo.LOGGER.info(LOG_LOADED, prepared.size());
    }
}
