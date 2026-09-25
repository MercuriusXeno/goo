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

    /**
     * True when each tank holds the full capacity on its own rather than
     * sharing it with the other tanks (decision crucible-refuses-past-two-billion).
     */
    private final boolean capacityPerType;

    /**
     * Incoming goo, transient, for rendering the pour.
     */
    private final GooStream stream = new GooStream();

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
        this(capacity, onChange, tickSupplier, false);
    }

    /**
     * Creates a handler whose capacity is shared across tanks or held by each.
     *
     * @param capacity        the capacity in microblobs (mB)
     * @param onChange        called when contents change
     * @param tickSupplier    supplies the current game tick for stream timing
     * @param capacityPerType true when each tank holds the whole capacity
     */
    private GooFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier, boolean capacityPerType) {
        super(GooTypes.order().size(), capacity);
        this.onChange = onChange;
        this.tickSupplier = tickSupplier;
        this.capacityPerType = capacityPerType;
    }

    /**
     * Creates a handler where each goo type holds up to the capacity on its
     * own, the crucible's reservoir (decision crucible-refuses-past-two-billion).
     *
     * @param capacityPerType the most each goo type holds, in mB
     * @param onChange        called when contents change
     * @return the handler
     */
    public static GooFluidHandler withCapacityPerType(int capacityPerType, Runnable onChange) {
        return new GooFluidHandler(capacityPerType, onChange, () -> 0, true);
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
     * Returns the effective capacity for a slot: the whole capacity when it is
     * held per type, otherwise the shared capacity less the other slots.
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
        if (capacityPerType) {
            return capacity;
        }
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
            stream.record(GooTypes.order().get(index), delta, tickSupplier.getAsLong());
        }
        onChange.run();
    }

    /**
     * Returns the goo type streaming in, or null once the stream's hold has passed.
     *
     * @param currentTick the current game tick
     * @return the streaming goo type, or null
     */
    public @Nullable ResourceKey<GooTypeDefinition> getStreamType(long currentTick) {
        return stream.typeAt(currentTick);
    }

    /**
     * Returns the volume the last landing tick carried in mB, or 0 once the stream's hold has passed.
     *
     * @param currentTick the current game tick
     * @return the stream rate, or 0
     */
    public int getStreamRate(long currentTick) {
        return stream.rateAt(currentTick);
    }

    /**
     * Returns the last tick goo landed, or -1 before any has.
     *
     * @return the last landing tick
     */
    public long getStreamTick() {
        return stream.lastTick();
    }

    // --- Bridge methods ---

    /**
     * Creates a {@link GooContents} snapshot from the current tank state.
     * Used by rendering, serialization and the GooContents-based APIs.
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
     * Returns the total volume across all tanks, a long because the tanks
     * together can pass an int's range (decision diagnose-then-fix-crucible-overflow).
     *
     * @return the total volume in mB
     */
    public long totalVolume() {
        long total = 0;
        for (int i = 0; i < size(); i++) {
            total += getAmountAsLong(i);
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
     * Updates the shared capacity. Used when the compression level changes.
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
