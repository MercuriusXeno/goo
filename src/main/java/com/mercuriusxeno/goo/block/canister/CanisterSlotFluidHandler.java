package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.block.gasket.DemandRelay;
import com.mercuriusxeno.goo.block.gasket.GasketDemand;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Block-level fluid handler for canister slots: one tank per fluid held, in
 * arrival order, plus one empty tank for a fluid not yet held, every tank on
 * one shared capacity the way the vat's GooFluidHandler shares its own
 * (capacity less the other tanks' volume). An insert or extract finds its
 * fluid's tank by resource, whichever index the caller names.
 *
 * <p>Used by canister, hub, tap, reactor, plexer and crystallizer block
 * entities for per-slot fluid storage.</p>
 *
 * decision canisters-hold-more-than-one-goo-type
 */
public class CanisterSlotFluidHandler extends SnapshotJournal<CanisterFluidContent>
        implements ResourceHandler<FluidResource>, GasketDemand {

    private final Runnable onChange;
    private final LongSupplier tickSupplier;
    private final Predicate<FluidResource> admits;

    /** The shared capacity every fluid in the slot fills together (mB). */
    private int capacity;

    /** Every fluid in the slot with its volume. */
    private CanisterFluidContent content = CanisterFluidContent.EMPTY;

    /**
     * This canister's link in the gasket chain: its resting demand plus the consumer's.
     */
    private final DemandRelay relay = new DemandRelay();

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
     * Creates a handler with the given capacity and change callback.
     *
     * @param capacity total capacity (mB)
     * @param onChange called when contents change
     */
    public CanisterSlotFluidHandler(int capacity, Runnable onChange) {
        this(capacity, onChange, () -> 0);
    }

    /**
     * Creates a handler with capacity, change callback, and tick supplier.
     *
     * @param capacity     total capacity (mB)
     * @param onChange     called when contents change
     * @param tickSupplier supplies current game tick for stream timing
     */
    public CanisterSlotFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier) {
        this(capacity, onChange, tickSupplier, incoming -> true);
    }

    /**
     * Creates a handler that takes only the goo its holder admits.
     *
     * @param capacity     total capacity (mB)
     * @param onChange     called when contents change
     * @param tickSupplier supplies current game tick for stream timing
     * @param admits       answers whether the holder takes an arriving goo
     */
    public CanisterSlotFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier,
                                    Predicate<FluidResource> admits) {
        super();
        this.capacity = capacity;
        this.onChange = onChange;
        this.tickSupplier = tickSupplier;
        this.admits = admits;
    }

    /**
     * Sets where this canister reads the demand of the consumer behind it.
     *
     * @param demand the consumer's stated demand for a resource, or empty when none stands behind it
     */
    public void setConsumerDemand(Function<FluidResource, OptionalInt> demand) {
        relay.readDemandFrom(demand);
    }

    /**
     * A canister asks the power law of its own capacity plus the demand of the consumer
     * behind it (decision relay-adds-dependent-ask-to-own).
     */
    @Override
    public OptionalInt statedDemand(FluidResource resource) {
        return relay.statedDemand(resource, () -> GasketDemand.restingDemand(resource, capacity, totalVolume()));
    }

    // --- ResourceHandler ---

    /**
     * One tank per fluid held plus one empty tank for the next.
     *
     * @return the tank count
     */
    @Override
    public int size() {
        return content.portions().size() + 1;
    }

    /**
     * The fluid in a tank.
     *
     * @param index the tank index
     * @return the fluid, or EMPTY for the trailing tank
     */
    @Override
    public FluidResource getResource(int index) {
        List<CanisterFluidContent.Portion> portions = content.portions();
        return index >= 0 && index < portions.size() ? portions.get(index).resource() : FluidResource.EMPTY;
    }

    /**
     * The volume in a tank.
     *
     * @param index the tank index
     * @return the volume in mB, 0 for the trailing tank
     */
    @Override
    public long getAmountAsLong(int index) {
        List<CanisterFluidContent.Portion> portions = content.portions();
        return index >= 0 && index < portions.size() ? portions.get(index).amount() : 0;
    }

    /**
     * The shared capacity less the volume every other tank holds.
     *
     * @param index    the tank index
     * @param resource the fluid resource
     * @return the room this tank has in mB, counting its own volume
     */
    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return capacity - content.totalVolume() + getAmountAsLong(index);
    }

    /**
     * Any fluid the held content accepts and the holder admits: goo beside goo,
     * a vanilla fluid alone.
     *
     * @param index    the tank index, which routing ignores
     * @param resource the fluid resource to validate
     * @return true if valid
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        return content.canAccept(resource) && admits.test(resource);
    }

    /**
     * Adds a fluid to the tank holding it, or to a new tank, up to the shared capacity.
     *
     * @param index       the tank index, which routing ignores
     * @param resource    the fluid resource
     * @param amount      the mB offered
     * @param transaction the caller's transaction
     * @return the mB accepted
     */
    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || !isValid(index, resource)) {
            return 0;
        }
        int accepted = content.cappedAddAmount(resource, amount, capacity);
        if (accepted > 0) {
            updateSnapshots(transaction);
            content = content.withCappedAdd(resource, accepted, capacity);
            streamResource = resource;
        }
        return accepted;
    }

    /**
     * Removes a fluid from the tank holding it, leaving every other fluid as it stands.
     *
     * @param index       the tank index, which routing ignores
     * @param resource    the fluid resource
     * @param amount      the mB requested
     * @param transaction the caller's transaction
     * @return the mB removed
     */
    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        int taken = Math.min(amount, content.volumeOf(resource));
        if (taken > 0) {
            updateSnapshots(transaction);
            content = content.withRemoved(resource, taken);
        }
        return taken;
    }

    // --- SnapshotJournal ---

    /**
     * The content as it stands, immutable, so the snapshot is the value itself.
     *
     * @return the snapshot
     */
    @Override
    protected CanisterFluidContent createSnapshot() {
        return content;
    }

    /**
     * Restores the content an aborted transaction changed.
     *
     * @param snapshot the content to restore
     */
    @Override
    protected void revertToSnapshot(CanisterFluidContent snapshot) {
        content = snapshot;
    }

    /**
     * Tracks insertions and notifies the owner once the root transaction commits.
     * Suppressed during bulk loads.
     *
     * @param original the content before the transaction
     */
    @Override
    protected void onRootCommit(CanisterFluidContent original) {
        if (suppressCallbacks || original.equals(content)) {
            return;
        }
        int delta = content.totalVolume() - original.totalVolume();
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

    // --- GooContents bridge ---

    /**
     * Returns a GooContents snapshot: every goo type in the slot with its volume.
     *
     * @return goo contents for push operations
     */
    public GooContents toGooContents() {
        Map<ResourceKey<GooTypeDefinition>, Integer> volumes = content.gooVolumes();
        return volumes.isEmpty() ? GooContents.EMPTY : new GooContents(volumes);
    }

    /**
     * Loads from a GooContents snapshot, every type it holds. Suppresses callbacks.
     *
     * @param contents the goo contents to load
     */
    public void loadFrom(GooContents contents) {
        List<CanisterFluidContent.Portion> portions = new ArrayList<>();
        contents.getAll().forEach((type, volume) -> portions.add(
                new CanisterFluidContent.Portion(GooFluids.resource(type), Math.min(volume, Integer.MAX_VALUE))));
        loadFrom(new CanisterFluidContent(portions));
    }

    // --- CanisterFluidContent bridge ---

    /**
     * Returns the slot's content.
     *
     * @return the fluid content
     */
    public CanisterFluidContent toFluidContent() {
        return content;
    }

    /**
     * Loads fluid from a CanisterFluidContent into the slot. Suppresses callbacks.
     *
     * @param loaded the content to load
     */
    public void loadFrom(CanisterFluidContent loaded) {
        suppressCallbacks = true;
        try (var tx = Transaction.openRoot()) {
            updateSnapshots(tx);
            content = loaded;
            tx.commit();
        } finally {
            suppressCallbacks = false;
        }
    }

    /**
     * Returns true if the slot holds no fluid.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return content.isEmpty();
    }

    /**
     * Returns the volume of every fluid together.
     *
     * @return volume in mB
     */
    public int totalVolume() {
        return content.totalVolume();
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
     * Updates the capacity. Used when the compression level changes.
     *
     * @param newCapacity new total capacity in mB
     */
    public void setCapacity(int newCapacity) {
        this.capacity = newCapacity;
    }
}
