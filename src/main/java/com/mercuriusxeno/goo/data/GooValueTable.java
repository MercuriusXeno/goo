package com.mercuriusxeno.goo.data;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * One immutable snapshot of goo values: what a reader looks up. The server's
 * {@link GooValueRegistry} publishes a fresh table each time it loads or
 * derives, and a client's connection holds the table the server last synced,
 * so a reader holds one consistent snapshot however the values change
 * (decision type-package-and-per-server-holders).
 */
public final class GooValueTable implements IGooValueLookup {

    /**
     * The table a side holds before any value arrives, and after its server stops.
     */
    public static final GooValueTable EMPTY = new GooValueTable(Map.of(), Set.of(), Set.of(), Set.of());

    private final Map<Identifier, GooValue> effectiveValues;
    private final Set<Identifier> baseValueIds;
    private final Set<Identifier> deniedItems;
    private final Set<Identifier> restrictedItems;

    /**
     * Copies the given values into a table no later change reaches.
     *
     * @param effectiveValues the effective value of each valued item
     * @param baseValueIds    the items holding a hand-keyed base value
     * @param deniedItems     the items denied a value
     * @param restrictedItems the items the plexer may not reconstitute
     */
    public GooValueTable(Map<Identifier, GooValue> effectiveValues, Collection<Identifier> baseValueIds,
                         Collection<Identifier> deniedItems, Collection<Identifier> restrictedItems) {
        this.effectiveValues = Map.copyOf(effectiveValues);
        this.baseValueIds = Set.copyOf(baseValueIds);
        this.deniedItems = Set.copyOf(deniedItems);
        this.restrictedItems = Set.copyOf(restrictedItems);
    }

    /**
     * A table of effective values alone, the form a client receives in a sync.
     *
     * @param effectiveValues the effective value of each valued item
     * @return the table
     */
    public static GooValueTable ofEffectiveValues(Map<Identifier, GooValue> effectiveValues) {
        return new GooValueTable(effectiveValues, Set.of(), Set.of(), Set.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable GooValue lookup(Identifier itemId) {
        return effectiveValues.get(itemId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasBaseValue(Identifier itemId) {
        return baseValueIds.contains(itemId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDenied(Identifier itemId) {
        return deniedItems.contains(itemId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRestricted(Identifier itemId) {
        return restrictedItems.contains(itemId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return effectiveValues.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Identifier, GooValue> getEffectiveValues() {
        return effectiveValues;
    }
}
