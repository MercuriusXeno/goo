package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Single-tank block-level fluid handler for canister slots. Accepts any
 * fluid resource, a stamped goo type or a vanilla fluid. Only one resource
 * at a time, so two goo types never share a slot.
 *
 * <p>Used by canister and hub block entities for per-slot fluid storage.
 * Replaces the multi-tank ordinal-indexed GooFluidHandler for canister slots.</p>
 */
public class CanisterSlotFluidHandler extends FluidStacksResourceHandler {

    private final Runnable onChange;
    private final LongSupplier tickSupplier;

    // --- Stream tracking (transient, for rendering incoming fluid) ---

    /**
     * Resource last inserted via transfer, or null if idle.
     */
    private @Nullable FluidResource streamResource;

    /**
     * Total mB inserted this tick.
     */
    private int streamRate;

    /**
     * Game tick of the last insertion event.
     */
    private long streamTick = -1;

    /**
     * Suppresses callbacks during bulk loads.
     */
    private boolean suppressCallbacks;

    /**
     * Creates a single-tank handler with the given capacity and change callback.
     *
     * @param capacity total capacity in microblobs (mB)
     * @param onChange called when contents change
     */
    public CanisterSlotFluidHandler(int capacity, Runnable onChange) {
        this(capacity, onChange, () -> 0);
    }

    /**
     * Creates a single-tank handler with capacity, change callback, and tick supplier.
     *
     * @param capacity     total capacity in microblobs (mB)
     * @param onChange     called when contents change
     * @param tickSupplier supplies current game tick for stream timing
     */
    public CanisterSlotFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier) {
        super(1, capacity);
        this.onChange = onChange;
        this.tickSupplier = tickSupplier;
    }

    /**
     * Accepts any non-empty resource if the slot is empty or already holds the same resource.
     *
     * @param index    always 0
     * @param resource the fluid resource to validate
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        FluidResource current = getResource(0);
        return current.isEmpty() || current.equals(resource);
    }

    /**
     * Full capacity for the single tank.
     *
     * @param index    always 0
     * @param resource the fluid resource
     * @return capacity in mB
     */
    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return capacity;
    }

    /**
     * Tracks insertions and notifies owner. Suppressed during bulk loads.
     */
    @Override
    protected void onContentsChanged(int index, FluidStack previousContents) {
        if (suppressCallbacks) {
            return;
        }
        int delta = (int) getAmountAsLong(0) - previousContents.getAmount();
        if (delta > 0) {
            trackInsertion(delta);
        }
        onChange.run();
    }

    /**
     * Records an insertion event for stream rendering.
     *
     * @param delta the amount of fluid inserted this tick in mB
     */
    private void trackInsertion(int delta) {
        long now = tickSupplier.getAsLong();
        if (now != streamTick) {
            streamRate = 0;
            streamTick = now;
        }
        FluidResource res = getResource(0);
        streamResource = res.isEmpty() ? null : res;
        streamRate += delta;
    }

    // --- Stream getters ---

    /**
     * Returns the resource currently streaming in, or null if no active stream.
     *
     * @param currentTick the current game tick
     * @return the streaming resource, or null
     */
    public @Nullable FluidResource getStreamResource(long currentTick) {
        return (currentTick - streamTick <= 1) ? streamResource : null;
    }

    /**
     * Returns the fluid currently streaming in without its components, for
     * render code that tells vanilla fluids apart, or null if no active stream.
     *
     * @param currentTick the current game tick
     * @return the streaming fluid, or null
     */
    public @Nullable Fluid getStreamFluid(long currentTick) {
        FluidResource res = getStreamResource(currentTick);
        return res == null ? null : res.getFluid();
    }

    /**
     * Returns the goo type of the active stream, or null if non-goo or inactive.
     *
     * @param currentTick the current game tick
     * @return the streaming goo type, or null
     */
    public @Nullable ResourceKey<GooTypeDefinition> getStreamGooType(long currentTick) {
        FluidResource res = getStreamResource(currentTick);
        return res != null ? GooFluids.keyOf(res) : null;
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

    /**
     * Returns a GooContents snapshot. Single goo entry if holding goo, empty otherwise.
     *
     * @return goo contents for push operations
     */
    public GooContents toGooContents() {
        ResourceKey<GooTypeDefinition> type = getGooType();
        if (type == null) {
            return GooContents.EMPTY;
        }
        return new GooContents(Map.of(type, getAmount()));
    }

    /**
     * Loads from a GooContents snapshot. Reads the first (only) entry.
     *
     * @param contents the goo contents to load
     */
    public void loadFrom(GooContents contents) {
        suppressCallbacks = true;
        try {
            if (contents.isEmpty()) {
                set(0, FluidResource.EMPTY, 0);
            } else {
                var entry = contents.getAll().entrySet().iterator().next();
                set(0, GooFluids.resource(entry.getKey()),
                        Math.min(entry.getValue(), Integer.MAX_VALUE));
            }
        } finally {
            suppressCallbacks = false;
        }
    }

    // --- CanisterFluidContent bridge ---

    /**
     * Creates a CanisterFluidContent snapshot from the current tank state.
     *
     * @return the fluid content
     */
    public CanisterFluidContent toFluidContent() {
        FluidResource res = getResource(0);
        if (res.isEmpty()) {
            return CanisterFluidContent.EMPTY;
        }
        return new CanisterFluidContent(res, (int) getAmountAsLong(0));
    }

    /**
     * Loads fluid from a CanisterFluidContent into the tank. Suppresses callbacks.
     *
     * @param content the content to load
     */
    public void loadFrom(CanisterFluidContent content) {
        suppressCallbacks = true;
        try {
            if (content.isEmpty()) {
                set(0, FluidResource.EMPTY, 0);
            } else {
                set(0, content.resource(),
                        Math.min(content.amount(), Integer.MAX_VALUE));
            }
        } finally {
            suppressCallbacks = false;
        }
    }

    /**
     * Returns the resource stored in this slot, or FluidResource.EMPTY.
     *
     * @return the stored resource
     */
    public FluidResource getFluidResource() {
        return getResource(0);
    }

    /**
     * Returns the fluid stored in this slot without its components, for
     * render code that tells vanilla fluids apart.
     *
     * @return the stored fluid, or the empty fluid
     */
    public Fluid getFluid() {
        return getResource(0).getFluid();
    }

    /**
     * Returns the goo type stored in this slot, or null for non-goo/empty.
     *
     * @return the goo type, or null
     */
    @Nullable
    public ResourceKey<GooTypeDefinition> getGooType() {
        return GooFluids.keyOf(getResource(0));
    }

    /**
     * Returns the stored amount.
     *
     * @return volume in mB
     */
    public int getAmount() {
        return (int) getAmountAsLong(0);
    }

    /**
     * Returns true if the tank is empty.
     *
     * @return true if the tank holds no fluid
     */
    public boolean isEmpty() {
        return getAmountAsLong(0) == 0;
    }

    /**
     * Returns the total volume (same as getAmount for single-tank).
     *
     * @return volume in mB
     */
    public int totalVolume() {
        return (int) getAmountAsLong(0);
    }

    /**
     * Inserts fluid, handling transaction lifecycle internally.
     *
     * @param resource the fluid resource to insert
     * @param amount   volume in mB
     * @param simulate if true, returns how much would be accepted without mutating
     * @return the amount actually inserted
     */
    public int insertFluid(FluidResource resource, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        try (var tx = Transaction.openRoot()) {
            int inserted = insert(0, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return inserted;
        }
    }

    /**
     * Convenience: insert goo by type.
     *
     * @param type     the goo type
     * @param amount   volume in mB
     * @param simulate if true, dry run
     * @return the amount actually inserted
     */
    public int insertGoo(ResourceKey<GooTypeDefinition> type, int amount, boolean simulate) {
        return insertFluid(GooFluids.resource(type), amount, simulate);
    }

    /**
     * Extracts fluid, handling transaction lifecycle internally.
     *
     * @param resource the fluid resource to extract
     * @param amount   volume in mB
     * @param simulate if true, dry run
     * @return the amount actually extracted
     */
    public int extractFluid(FluidResource resource, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        try (var tx = Transaction.openRoot()) {
            int extracted = extract(0, resource, amount, tx);
            if (!simulate) {
                tx.commit();
            }
            return extracted;
        }
    }

    /**
     * Convenience: extract goo by type.
     *
     * @param type     the goo type
     * @param amount   volume in mB
     * @param simulate if true, dry run
     * @return the amount actually extracted
     */
    public int extractGoo(ResourceKey<GooTypeDefinition> type, int amount, boolean simulate) {
        return extractFluid(GooFluids.resource(type), amount, simulate);
    }

    /**
     * Updates the capacity. Used when matrix upgrades change.
     *
     * @param newCapacity new total capacity in mB
     */
    public void setCapacity(int newCapacity) {
        this.capacity = newCapacity;
    }
}
