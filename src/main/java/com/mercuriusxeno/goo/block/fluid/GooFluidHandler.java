package com.mercuriusxeno.goo.block.fluid;

import com.mercuriusxeno.goo.block.gasket.DemandRelay;
import com.mercuriusxeno.goo.block.gasket.GasketDemand;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooLabelRule;
import com.mercuriusxeno.goo.registry.GooFluids;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

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
public class GooFluidHandler extends FluidStacksResourceHandler implements GasketDemand {

    /**
     * This container's link in the gasket chain: its resting demand plus the demand behind it.
     */
    private final DemandRelay relay = new DemandRelay();

    private final Runnable onChange;
    private final LongSupplier tickSupplier;

    /**
     * True when each tank holds the full capacity on its own rather than
     * sharing it with the other tanks (decision crucible-refuses-past-two-billion).
     */
    private final boolean capacityPerType;

    /**
     * True when the held void grows the shared capacity, which then stands as
     * the base (decision each-void-mb-adds-n-plus-one-capacity).
     */
    private final boolean voidGrowsCapacity;

    /**
     * The tank index that holds vanilla water, or {@link #NO_WATER_TANK}
     * (decision diagnose-then-fix-waterlogged-gasket-link).
     */
    private final int waterIndex;

    /** The water index of a handler that holds goo alone. */
    private static final int NO_WATER_TANK = -1;

    /**
     * Reads the goo label type the handler's vessel wears, null where it wears none.
     */
    private Supplier<@Nullable ResourceKey<GooTypeDefinition>> gooLabel = () -> null;

    /**
     * Incoming goo, transient, for rendering the pour.
     */
    private final GooStream stream = new GooStream();

    /**
     * Suppresses all side-effect callbacks (stream tracking + onChange) during bulk loads.
     */
    private boolean suppressCallbacks;

    /**
     * Each goo type's part under 1 mB, saved beside the tank volumes.
     * decision every-producer-consumer-and-container-holds-a-remainder-buffer
     */
    private final RemainderLedger ledger = new RemainderLedger();

    /**
     * The handler's remainder ledger; a credit or debit that moves a remainder
     * notifies the owner, so the remainder saves with the tanks.
     * decision one-remainder-capability-injected-into-every-fluid-capability
     */
    private final IRemainderLedger notifyingLedger = new IRemainderLedger() {
        @Override
        public int creditExact(ResourceKey<GooTypeDefinition> type, GooFraction exact) {
            GooRemainders before = ledger.remainders();
            int whole = ledger.creditExact(type, exact);
            notifyIfMoved(before);
            return whole;
        }

        @Override
        public OptionalInt debitExact(ResourceKey<GooTypeDefinition> type, GooFraction exact, int availableWhole) {
            GooRemainders before = ledger.remainders();
            OptionalInt whole = ledger.debitExact(type, exact, availableWhole);
            notifyIfMoved(before);
            return whole;
        }

        @Override
        public GooRemainders remainders() {
            return ledger.remainders();
        }
    };

    /**
     * The handler's remainder ledger.
     *
     * @return the ledger
     */
    public IRemainderLedger ledger() {
        return notifyingLedger;
    }

    /**
     * Notifies the owner where a remainder moved.
     *
     * @param before the remainders before the exchange
     */
    private void notifyIfMoved(GooRemainders before) {
        if (!suppressCallbacks && !before.equals(ledger.remainders())) {
            onChange.run();
        }
    }

    /**
     * Creates a handler with shared capacity and a change callback.
     *
     * @param capacity total shared capacity (mB)
     * @param onChange called when contents change (e.g. markDirtyAndSync)
     */
    public GooFluidHandler(int capacity, Runnable onChange) {
        this(capacity, onChange, () -> 0);
    }

    /**
     * Creates a handler with shared capacity, change callback, and tick supplier
     * for stream tracking.
     *
     * @param capacity     total shared capacity (mB)
     * @param onChange     called when contents change (e.g. markDirtyAndSync)
     * @param tickSupplier supplies the current game tick for stream timing
     */
    public GooFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier) {
        this(capacity, onChange, tickSupplier, false, false, false);
    }

    /**
     * Creates a handler whose capacity is shared across tanks or held by each.
     *
     * @param capacity        the capacity (mB)
     * @param onChange        called when contents change
     * @param tickSupplier    supplies the current game tick for stream timing
     * @param capacityPerType   true when each tank holds the whole capacity
     * @param holdsWater        true when one tank past the goo tanks holds vanilla water
     * @param voidGrowsCapacity true when the held void grows the shared capacity
     */
    private GooFluidHandler(int capacity, Runnable onChange, LongSupplier tickSupplier,
                            boolean capacityPerType, boolean holdsWater, boolean voidGrowsCapacity) {
        super(GooTypes.order().size() + (holdsWater ? 1 : 0), capacity);
        this.onChange = onChange;
        this.tickSupplier = tickSupplier;
        this.capacityPerType = capacityPerType;
        this.voidGrowsCapacity = voidGrowsCapacity;
        this.waterIndex = holdsWater ? GooTypes.order().size() : NO_WATER_TANK;
    }

    /**
     * Creates the vat's reservoir: a shared-capacity handler with a water tank
     * past the goo tanks, which a waterlogged choral gasket fills (decision
     * diagnose-then-fix-waterlogged-gasket-link), its capacity grown by the
     * void it holds (decision each-void-mb-adds-n-plus-one-capacity).
     *
     * @param base         the shared capacity with no void, in mB
     * @param onChange     called when contents change
     * @param tickSupplier supplies the current game tick for stream timing
     * @return the handler
     */
    public static GooFluidHandler vatReservoir(int base, Runnable onChange, LongSupplier tickSupplier) {
        return new GooFluidHandler(base, onChange, tickSupplier, false, true, true);
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
        return new GooFluidHandler(capacityPerType, onChange, () -> 0, true, false, false);
    }

    /**
     * Sets where the handler reads its vessel's goo label, which then holds every
     * insert to that type (decision labeled-vessel-accepts-only-its-label-type).
     *
     * @param label reads the label type, null where the vessel wears none
     */
    public void holdTo(Supplier<@Nullable ResourceKey<GooTypeDefinition>> label) {
        this.gooLabel = label;
    }

    /**
     * Only the goo fluid matching this tank index is valid, and plain water
     * in the water tank where the handler holds one.
     * Index maps to the type's position in GooTypes.order().
     *
     * @param index    tank index
     * @param resource the fluid resource to validate
     * @return true if the resource's stamped goo type matches the tank's, or it is water at the water tank
     */
    @Override
    public boolean isValid(int index, FluidResource resource) {
        // decision labeled-vessel-accepts-only-its-label-type
        if (resource.isEmpty() || !GooLabelRule.admits(gooLabel.get(), resource)) {
            return false;
        }
        if (index == waterIndex) {
            return FluidResource.of(Fluids.WATER).equals(resource);
        }
        ResourceKey<GooTypeDefinition> type = GooFluids.keyOf(resource);
        return type != null && GooTypes.indexOf(type) == index;
    }

    /**
     * Takes goo out of a tank, save infinity, which never leaves.
     * decision infinity-sources-whatever-shares-its-vessel
     *
     * @param index       the tank index
     * @param resource    the fluid resource
     * @param amount      the mB requested
     * @param transaction the caller's transaction
     * @return the mB removed
     */
    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        ResourceKey<GooTypeDefinition> type = GooFluids.keyOf(resource);
        if (GooTypes.isUnspendable(type)) {
            return 0;
        }
        int allowed = voidGrowsCapacity && GooTypes.VOID.equals(type)
                ? Math.min(amount, ContainerCapacity.releasableVoid(capacity, totalVolume(), voidVolume()))
                : amount;
        return super.extract(index, resource, allowed, transaction);
    }

    /**
     * Returns the effective capacity for a slot: the whole capacity when it is
     * held per type, otherwise the slot's own volume plus the room the shared
     * capacity leaves it. The default insert() clamps at
     * {@code getCapacity - currentAmount}, yielding that room. Where void grows
     * the capacity, void's room reaches past the capacity it grows as it arrives.
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
        long own = getAmountAsInt(index);
        long total = totalVolume();
        if (!voidGrowsCapacity) {
            return (int) (capacity - (total - own));
        }
        boolean isVoid = index == GooTypes.indexOf(GooTypes.VOID);
        return (int) Math.min(Integer.MAX_VALUE, own + ContainerCapacity.room(isVoid, capacity, total, voidVolume()));
    }

    /**
     * The capacity every tank shares, grown by the held void where void grows it.
     *
     * @return the capacity in mB
     */
    public int currentCapacity() {
        return voidGrowsCapacity ? ContainerCapacity.capacity(capacity, voidVolume()) : capacity;
    }

    /**
     * @return the void the handler holds, in mB
     */
    private int voidVolume() {
        return getVolume(GooTypes.VOID);
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
        if (delta > 0 && index == waterIndex) {
            stream.recordWater(delta, tickSupplier.getAsLong());
        } else if (delta > 0) {
            stream.record(GooTypes.order().get(index), delta, tickSupplier.getAsLong());
        }
        onChange.run();
    }

    /**
     * Returns true while water is pouring in, until the stream's hold has passed.
     *
     * @param currentTick the current game tick
     * @return true when the stream's last landing was water
     */
    public boolean isStreamWater(long currentTick) {
        return stream.waterAt(currentTick);
    }

    /**
     * Returns the water tank's volume, 0 where the handler holds no water tank.
     *
     * @return the water volume in mB
     */
    public int waterVolume() {
        return waterIndex == NO_WATER_TANK ? 0 : getAmountAsInt(waterIndex);
    }

    /**
     * Sets the water tank's volume without stream tracking or change callbacks,
     * the load path beside {@link #loadFrom}. A handler without a water tank ignores it.
     *
     * @param volume the water volume in mB
     */
    public void loadWater(int volume) {
        if (waterIndex == NO_WATER_TANK) {
            return;
        }
        suppressCallbacks = true;
        try {
            set(waterIndex, volume > 0 ? FluidResource.of(Fluids.WATER) : FluidResource.EMPTY, Math.max(volume, 0));
        } finally {
            suppressCallbacks = false;
        }
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
     * Creates a {@link GooContents} snapshot from the current tank state and remainders.
     * Used by rendering, serialization and the GooContents-based APIs.
     *
     * @return immutable GooContents reflecting current volumes and remainders
     */
    public GooContents toGooContents() {
        Map<ResourceKey<GooTypeDefinition>, Integer> map = collectNonEmptyTanks();
        GooRemainders remainders = ledger.remainders();
        return map.isEmpty() && remainders.isEmpty() ? GooContents.EMPTY : new GooContents(map, remainders);
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
     * Loads volumes and remainders from a {@link GooContents} snapshot.
     * Clears all tanks first, then sets each type's volume. Suppresses
     * all callbacks (stream tracking and onChange) - caller should sync after loading.
     *
     * @param contents the contents to load from
     */
    public void loadFrom(GooContents contents) {
        suppressCallbacks = true;
        try {
            applyAllTanks(contents);
            ledger.load(contents.remainders());
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
     * Sets where this container reads the demand placed on it from behind.
     *
     * @param demandBehind the stated demand of what it feeds, or empty when nothing stands behind it
     */
    public void readDemandFrom(Function<FluidResource, OptionalInt> demandBehind) {
        relay.readDemandFrom(demandBehind);
    }

    /**
     * A container asks the power law of its capacity, so a bigger vat pulls harder, plus
     * the demand of what it feeds (decision relay-adds-dependent-ask-to-own).
     * A per-type handler rests against the one tank the resource fills.
     */
    @Override
    public OptionalInt statedDemand(FluidResource resource) {
        return relay.statedDemand(resource, () -> GasketDemand.restingDemand(resource, currentCapacity(),
                capacityPerType ? heldOf(resource) : totalVolume()));
    }

    /**
     * @param resource the fluid
     * @return what the tank holding that fluid holds, in mB
     */
    private long heldOf(FluidResource resource) {
        for (int i = 0; i < size(); i++) {
            if (getResource(i).equals(resource)) {
                return getAmountAsLong(i);
            }
        }
        return 0;
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
