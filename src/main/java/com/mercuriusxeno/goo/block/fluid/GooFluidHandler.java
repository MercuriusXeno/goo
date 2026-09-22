package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Multi-tank fluid handler for goo containers. One tank per goo type key
 * ordinal (15 total), with a single shared capacity across all tanks.
 *
 * <p>Pipe mods see 15 typed slots and can insert/extract the correct goo type
 * at the matching index. The shared capacity ensures total volume never exceeds
 * the container's limit regardless of how many types are stored.</p>
 *
 * @see GooTypes
 * @see GooContents
 */
public class GooFluidHandler extends FluidStacksResourceHandler {

    private final Runnable onChange;
    private final LongSupplier tickSupplier;

    // --- Stream tracking (transient, for rendering incoming goo) ---

    /**
     * Goo type last inserted via gasket transfer, or null if idle.
     */
    private @Nullable ResourceKey<GooTypeDefinition> streamType;

    /**
     * Total mB inserted this tick (accumulates across multiple types).
     */
    private int streamRate;

    /**
     * Game tick of the last insertion event.
     */
    private long streamTick = -1;

    /**
     * Suppresses all side-effect callbacks (stream tracking + onChange) during bulk loads.
     */
    private boolean suppressCallbacks;

    /**
     * Creates a handler with shared capacity and a change callback.
     *
     * @param capacity total shared capacity in microblobs (mB)
     * @param onChange called when contents change (e.g. markDirtyAndSync)
     */
    public GooFluidHandler(int capacity, Runnable onChange) {
        this(capacity, onChange, () -> 0);
    }

    /**
     * Creates a handler with shared capacity, change callback, and tick supplier
     * for stream tracking.
     *
     * @param capacity     total shared capacity in microblobs (mB)
     * @param onChange     called when contents change (e.g. markDirtyAndSync)
     * @param tickSupplier supplies the current game tick for stream timing
     */
    public GooFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier) {
        super(GooTypes.order().size(), capacity);
        this.onChange = onChange;
        this.tickSupplier = tickSupplier;
    }

    /**
     * Only the goo fluid matching this tank index is valid.
     * Index maps to the type's position in GooTypes.order().
     *
     * @param index    tank index (0-14)
     * @param resource the fluid resource to validate
     * @return true if the resource's stamped goo type matches the tank's
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        ResourceKey<GooTypeDefinition> type = GooFluids.keyOf(resource);
        return type != null && GooTypes.indexOf(type) == index;
    }

    /**
     * Returns the effective capacity for a slot, accounting for shared capacity.
     * Each slot's effective capacity is the total capacity minus volume in all
     * other slots. The default insert() clamps at {@code getCapacity - currentAmount},
     * yielding the correct shared remaining space.
     *
     * @param index    tank index
     * @param resource the fluid resource
     * @return effective capacity for this slot in mB
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        int otherVolume = 0;
        for (int i = 0; i < size(); i++) {
            if (i != index) {
                otherVolume += getAmountAsInt(i);
            }
        }
        return capacity - otherVolume;
    }

    /**
     * Detects insertions for stream tracking, then notifies the owner. Suppressed during bulk loads.
     *
     * @param index            the tank index
     * @param previousContents the previous fluid stack contents
     */
    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        if (suppressCallbacks) {
            return;
        }
        int delta = (int) getAmountAsLong(index) - previousContents.getAmount();
        if (delta > 0) {
            trackInsertion(index, delta);
        }
        onChange.run();
    }

    /**
     * Records an insertion event for stream rendering.
     *
     * @param index the tank index that received goo
     * @param delta the volume inserted in mB
     */
    private void trackInsertion(int index, int delta) {
        long now = tickSupplier.getAsLong();
        if (now != streamTick) {
            streamRate = 0;
            streamTick = now;
        }
        streamType = GooTypes.order().get(index);
        streamRate += delta;
    }

    // --- Stream getters (queried by BER extractRenderState) ---

    /**
     * Returns the goo type currently streaming in, or null if no active stream.
     * A stream is considered active if goo was inserted within the last tick.
     *
     * @param currentTick the current game tick
     * @return the streaming goo type, or null
     */
    public @Nullable ResourceKey<GooTypeDefinition> getStreamType(long currentTick) {
        return (currentTick - streamTick <= 1) ? streamType : null;
    }

    /**
     * Returns the transfer rate of the active stream in mB/tick.
     *
     * @param currentTick the current game tick
     * @return mB/tick if stream is active, 0 otherwise
     */
    public int getStreamRate(long currentTick) {
        return (currentTick - streamTick <= 1) ? streamRate : 0;
    }

    // --- Bridge methods ---

    /**
     * Creates a {@link GooContents} snapshot from the current tank state.
     * Used for rendering, serialization, and backward-compatible APIs.
     *
     * @return immutable GooContents reflecting current volumes
     */
    public GooContents toGooContents() {
        Map<ResourceKey<GooTypeDefinition>, Integer> map = collectNonEmptyTanks();
        return map.isEmpty() ? GooContents.EMPTY : new GooContents(map);
    }

    /**
     * Builds a map of all goo types with non-zero volume.
     *
     * @return the non-empty tank volumes keyed by goo type
     */
    private Map<ResourceKey<GooTypeDefinition>, Integer> collectNonEmptyTanks() {
        List<ResourceKey<GooTypeDefinition>> types = GooTypes.order();
        Map<ResourceKey<GooTypeDefinition>, Integer> map = new HashMap<>();
        for (int i = 0; i < types.size(); i++) {
            int amount = (int) getAmountAsLong(i);
            if (amount > 0) {
                map.put(types.get(i), amount);
            }
        }
        return map;
    }

    /**
     * Loads volumes from a {@link GooContents} snapshot into the tanks.
     * Clears all tanks first, then sets each type's volume. Suppresses
     * all callbacks (stream tracking and onChange) - caller should sync after loading.
     *
     * @param contents the contents to load from
     */
    public void loadFrom(GooContents contents) {
        suppressCallbacks = true;
        try {
            applyAllTanks(contents);
        } finally {
            suppressCallbacks = false;
        }
    }

    /**
     * Sets each tank's contents from the snapshot, clearing tanks with zero volume.
     *
     * @param contents the goo contents to load
     */
    private void applyAllTanks(GooContents contents) {
        List<ResourceKey<GooTypeDefinition>> types = GooTypes.order();
        for (int i = 0; i < types.size(); i++) {
            applyTank(i, types.get(i), contents.getVolume(types.get(i)));
        }
    }

    /**
     * Sets a single tank from a volume, clearing it if zero.
     *
     * @param index  the tank index
     * @param type   the goo type
     * @param volume the volume to set
     */
    private void applyTank(int index, ResourceKey<GooTypeDefinition> type, int volume) {
        if (volume > 0) {
            set(index, GooFluids.resource(type), Math.min(volume, Integer.MAX_VALUE));
        } else {
            set(index, FluidResource.EMPTY, 0);
        }
    }

    /**
     * Returns the total volume across all 15 tanks.
     *
     * @return the long value
     */
    public int totalVolume() {
        int total = 0;
        for (int i = 0; i < size(); i++) {
            total += (int) getAmountAsLong(i);
        }
        return total;
    }

    /**
     * Returns true if all tanks are empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return totalVolume() == 0;
    }

    /**
     * Updates the shared capacity. Used when matrix upgrades change.
     *
     * @param newCapacity new total capacity in mB
     */
    public void setCapacity(int newCapacity) {
        this.capacity = newCapacity;
    }

    // --- Convenience methods for internal use ---

    /**
     * Inserts goo by type, handling transaction lifecycle internally.
     *
     * @param type     the goo type to insert
     * @param amount   volume in mB to insert
     * @param simulate if true, returns how much would be accepted without mutating
     * @return the amount actually inserted (or that would be)
     */
    public int insertGoo(ResourceKey<GooTypeDefinition> type, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int index = GooTypes.indexOf(type);
        FluidResource resource = GooFluids.resource(type);
        try (var tx = Transaction.openRoot()) {
            int inserted = insert(index, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return inserted;
        }
    }

    /**
     * Extracts goo by type, handling transaction lifecycle internally.
     *
     * @param type     the goo type to extract
     * @param amount   volume in mB to extract
     * @param simulate if true, returns how much would be extracted without mutating
     * @return the amount actually extracted (or that would be)
     */
    public int extractGoo(ResourceKey<GooTypeDefinition> type, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        int index = GooTypes.indexOf(type);
        FluidResource resource = GooFluids.resource(type);
        try (var tx = Transaction.openRoot()) {
            int extracted = extract(index, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return extracted;
        }
    }

    /**
     * Returns the volume of a specific goo type.
     *
     * @param type the goo type to query
     * @return volume in mB, or 0 if absent
     */
    public int getVolume(ResourceKey<GooTypeDefinition> type) {
        return (int) getAmountAsLong(GooTypes.indexOf(type));
    }

    /**
     * Returns the largest goo type by volume, or null if empty.
     *
     * @return the dominant goo type, or null
     */
    @Nullable
    public ResourceKey<GooTypeDefinition> largestType() {
        return toGooContents().largestType();
    }
}
