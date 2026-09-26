package com.mercuriusxeno.goo.block.canister;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.PlayerUtils;
import com.mercuriusxeno.goo.block.GooLightEntry;
import com.mercuriusxeno.goo.block.GooMachineBlockEntity;
import com.mercuriusxeno.goo.block.gasket.GasketPusher;
import com.mercuriusxeno.goo.block.gasket.SlotGasketPusher;
import com.mercuriusxeno.goo.block.gasket.SlotGasketRegistration;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Behavioral component that owns the slot grid of every machine holding
 * canisters in slots: the canister block, the hub, the reactor's output hollow
 * and the tap. Each machine holds one instance bound to itself and exposes its
 * slots via {@link #slots}. Per-slot operations live on {@link CanisterSlot};
 * this class owns the cross-slot work: composite shape (re-ORed on slot
 * mutation, not access), fluid routing, pushers, and the whole lifecycle of a
 * canister entering and leaving a slot, together with its save and load
 * (decision machine-base-owns-the-lifecycle).
 */
public class SlottedCanisterData {

    /** Per-slot state. Index range is {@code [0, maxSlots)}. */
    public final CanisterSlot[] slots;

    private final GooMachineBlockEntity owner;
    private final int maxSlots;
    private final Runnable syncCallback;
    private final Function<CanisterSlot[], VoxelShape> shapeBuilder;
    private final IntPredicate slotAllowed;
    private VoxelShape compositeShape;

    /**
     * Creates the slot grid for a machine that takes a canister in any slot.
     *
     * @param owner        the machine holding the slots
     * @param maxSlots     number of slots
     * @param slotShapeFor function from slot index to its filled voxel shape
     * @param shapeBuilder builds the composite voxel shape from the slot array;
     *                     called on each structural change. The machine supplies
     *                     this so it can include block-level geometry (e.g.,
     *                     hub frame) and pick a fallback when no slot is occupied.
     */
    public SlottedCanisterData(GooMachineBlockEntity owner, int maxSlots,
            IntFunction<VoxelShape> slotShapeFor,
            Function<CanisterSlot[], VoxelShape> shapeBuilder) {
        this(owner, maxSlots, slotShapeFor, shapeBuilder, index -> true);
    }

    /**
     * Creates the slot grid for a machine whose placement rules close some slots.
     *
     * @param owner        the machine holding the slots
     * @param maxSlots     number of slots
     * @param slotShapeFor function from slot index to its filled voxel shape
     * @param shapeBuilder builds the composite voxel shape from the slot array
     * @param slotAllowed  answers whether a slot takes a canister where the machine stands
     */
    public SlottedCanisterData(GooMachineBlockEntity owner, int maxSlots,
            IntFunction<VoxelShape> slotShapeFor,
            Function<CanisterSlot[], VoxelShape> shapeBuilder,
            IntPredicate slotAllowed) {
        this.owner = owner;
        this.maxSlots = maxSlots;
        this.syncCallback = owner.gasketSyncCallback();
        this.shapeBuilder = shapeBuilder;
        this.slotAllowed = slotAllowed;
        this.slots = new CanisterSlot[maxSlots];
        for (int i = 0; i < maxSlots; i++) {
            slots[i] = new CanisterSlot(i, slotShapeFor.apply(i),
                    syncCallback, this::onStructureChanged);
        }
        this.compositeShape = shapeBuilder.apply(this.slots);
    }
    /** @return the configured slot count */
    public int maxSlots() {
        return maxSlots;
    }

    /**
     * @param index the slot index
     * @return true if {@code index} is within the slot grid
     */
    public boolean inRange(int index) {
        return index >= 0 && index < maxSlots;
    }

    /** @return true if any slot is occupied */
    public boolean hasAnyCanister() {
        for (CanisterSlot slot : slots) {
            if (!slot.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** @return the eagerly-maintained composite voxel shape across all slots */
    public VoxelShape compositeShape() {
        return compositeShape;
    }

    /** Triggers the BE's sync callback. Used by callers that mutate slot
     *  state outside the slot's own setters and need to signal sync. */
    public void markChanged() {
        syncCallback.run();
    }

    // --- Slot-keyed accessors (route through slot) ---

    /** @param index the slot index
     *  @return the canister stack at the given slot, or EMPTY if out of range */
    public ItemStack getCanister(int index) {
        return inRange(index) ? slots[index].canister() : ItemStack.EMPTY;
    }

    /** @param index the slot index
     *  @return the slot's fluid content, or EMPTY */
    public CanisterFluidContent getSlotFluidContent(int index) {
        return inRange(index) ? slots[index].fluidContent() : CanisterFluidContent.EMPTY;
    }

    /** @param index the slot index
     *  @return the live fluid handler for the slot, or null */
    public @Nullable CanisterSlotFluidHandler getSlotFluidHandler(int index) {
        return inRange(index) ? slots[index].handler() : null;
    }

    /** @param index the slot index
     *  @return true if the slot has a canister with remaining capacity */
    public boolean canAccept(int index) {
        return inRange(index) && slots[index].canAccept();
    }

    /**
     * Inserts fluid into the slot's canister.
     *
     * @param index  the slot index
     * @param fluid  the fluid resource to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertFluid(int index, FluidResource fluid, int volume) {
        return inRange(index) ? slots[index].insertFluid(fluid, volume) : 0;
    }

    /**
     * Extracts fluid from the slot's canister.
     *
     * @param index     the slot index
     * @param fluid     the fluid resource to extract
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractFluid(int index, FluidResource fluid, int requested) {
        return inRange(index) ? slots[index].extractFluid(fluid, requested) : 0;
    }

    /**
     * Convenience: insert goo by type.
     *
     * @param index  the slot index
     * @param type   the goo type to insert
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    public int insertGoo(int index, ResourceKey<GooTypeDefinition> type, int volume) {
        return inRange(index) ? slots[index].insertGoo(type, volume) : 0;
    }

    /**
     * Convenience: extract goo by type.
     *
     * @param index     the slot index
     * @param type      the goo type
     * @param requested volume in microblobs
     * @return the amount actually extracted
     */
    public int extractGoo(int index, ResourceKey<GooTypeDefinition> type, int requested) {
        return inRange(index) ? slots[index].extractGoo(type, requested) : 0;
    }

    /**
     * @param index       the slot index
     * @param currentTick the current game tick
     * @return the slot's snapshot stream goo type if fresh, else null
     */
    public @Nullable ResourceKey<GooTypeDefinition> getSlotStreamType(int index, long currentTick) {
        return inRange(index) ? slots[index].getStreamType(currentTick) : null;
    }

    /**
     * @param index       the slot index
     * @param currentTick the current game tick
     * @return the slot's snapshot stream fluid if fresh, else null
     */
    public @Nullable Fluid getSlotStreamFluid(int index, long currentTick) {
        return inRange(index) ? slots[index].getStreamFluid(currentTick) : null;
    }

    /**
     * @param index       the slot index
     * @param currentTick the current game tick
     * @return the slot's snapshot stream rate in mB/tick if fresh, else 0
     */
    public int getSlotStreamRate(int index, long currentTick) {
        return inRange(index) ? slots[index].getStreamRate(currentTick) : 0;
    }

    /**
     * Recomputes {@link #compositeShape} and signals sync. Invoked by each
     * slot's structure-changed callback so the cache stays current at
     * mutation time, not at access time.
     */
    private void onStructureChanged() {
        compositeShape = shapeBuilder.apply(slots);
        syncCallback.run();
    }

    /**
     * Recomputes {@link #compositeShape} from current slot state without
     * firing the sync callback. Call after a batch slot mutation that
     * bypasses {@link CanisterSlot#setCanister(net.minecraft.world.item.ItemStack)}
     * (e.g., NBT load), since {@link CanisterSlot#load} writes the slot's
     * canister and shape fields directly without triggering the structure-
     * changed callback.
     */
    public void rebuildCompositeShape() {
        compositeShape = shapeBuilder.apply(slots);
    }

    // --- Cross-slot fluid routing (used by Hub intake) ---

    /**
     * Distributes fluid across slots: matching slots first, then empty slots.
     *
     * @param fluid  the fluid resource to route
     * @param amount volume in microblobs
     * @return total volume accepted across all slots
     */
    public int routeFluid(FluidResource fluid, int amount) {
        int routed = distributeAcrossSlots(fluid, amount);
        if (routed > 0) {
            syncCallback.run();
        }
        return routed;
    }

    /**
     * Convenience: route goo by type.
     *
     * @param type   the goo type
     * @param amount volume in microblobs
     * @return total volume accepted across all slots
     */
    public int routeGoo(ResourceKey<GooTypeDefinition> type, int amount) {
        return routeFluid(GooFluids.resource(type), amount);
    }

    private int distributeAcrossSlots(FluidResource fluid, int amount) {
        int remaining = distributePass(fluid, amount, true);
        remaining = distributePass(fluid, remaining, false);
        return amount - remaining;
    }

    private int distributePass(FluidResource fluid, int remaining, boolean existing) {
        int left = remaining;
        for (CanisterSlot slot : slots) {
            if (left <= 0) {
                break;
            }
            CanisterSlotFluidHandler handler = slot.handler();
            if (handler == null) {
                continue;
            }
            if (!isEligibleFluidHolder(fluid, existing, handler)) {
                continue;
            }
            left -= handler.insertFluid(fluid, left, false);
        }
        return left;
    }

    private static boolean isEligibleFluidHolder(FluidResource fluid, boolean existing,
            CanisterSlotFluidHandler handler) {
        return handler.isEmpty()
                || (existing && !handler.isEmpty() && handler.getFluidResource().equals(fluid));
    }

    // --- Pusher lifecycle (cross-slot) ---

    /** Ticks all active slot pushers. */
    public void tickPushers() {
        for (CanisterSlot slot : slots) {
            GasketPusher pusher = slot.pusher();
            if (pusher != null) {
                pusher.tick();
            }
        }
    }

    /** Disposes all active slot pushers (used during block removal). */
    public void disposeAllPushers() {
        for (CanisterSlot slot : slots) {
            slot.disposePusher();
        }
    }

    // --- Slot lifecycle (decision machine-base-owns-the-lifecycle) ---

    /**
     * Puts a canister in a slot and runs every step a slotted canister needs:
     * the placement check, the creative-duplicate gasket strip, the fluid
     * handler, the pusher, the capability refresh, the gasket registration, and
     * one sync once the handler stands, so the synced emission reads the canister.
     *
     * @param index        the slot index
     * @param stack        the canister stack; one is copied from it
     * @param stripGaskets true to clear gasket ids (creative duplication)
     * @return true if the canister went in
     */
    public boolean insert(int index, ItemStack stack, boolean stripGaskets) {
        if (!inRange(index) || !(stack.getItem() instanceof CanisterItem)
                || !slots[index].isEmpty() || !slotAllowed.test(index)) {
            return false;
        }
        CanisterSlot slot = slots[index];
        slot.setCanister(stack.copyWithCount(1));
        if (stripGaskets) {
            slot.stripGaskets();
        }
        slot.buildHandler(this::gameTime);
        rebuildPusher(index);
        invalidateCapabilities();
        SlotGasketRegistration.register(registryAccess(), owner.getLevel(), owner.getBlockPos(),
                index, CanisterItem.getMetadata(slot.canister()));
        syncCallback.run();
        return true;
    }

    /**
     * Takes the canister out of a slot: stops its pusher, writes its fluid onto
     * the stack, clears its gasket registration, vacates the slot (which syncs)
     * and refreshes capabilities.
     *
     * @param index the slot index
     * @return the removed canister, or EMPTY when the slot holds none
     */
    public ItemStack remove(int index) {
        if (!inRange(index) || slots[index].isEmpty()) {
            return ItemStack.EMPTY;
        }
        CanisterSlot slot = slots[index];
        slot.disposePusher();
        slot.syncHandlerToStack();
        ItemStack removed = slot.canister().copy();
        SlotGasketRegistration.deregister(registryAccess(), CanisterItem.getMetadata(removed));
        slot.clear();
        invalidateCapabilities();
        return removed;
    }

    /**
     * Stands or drops a slot's pusher from its canister's bottom gasket state.
     *
     * @param index the slot index
     */
    public void rebuildPusher(int index) {
        if (inRange(index)) {
            SlotGasketPusher.rebuild(slots[index], owner, registryAccess());
        }
    }

    /** Stands or drops every slot's pusher. */
    public void rebuildAllPushers() {
        for (int i = 0; i < maxSlots; i++) {
            rebuildPusher(i);
        }
    }

    /** Registers the gaskets of every occupied slot's canister, once the owner stands in its level. */
    public void registerSlotGaskets() {
        for (CanisterSlot slot : slots) {
            if (!slot.isEmpty()) {
                SlotGasketRegistration.register(registryAccess(), owner.getLevel(), owner.getBlockPos(),
                        slot.index(), CanisterItem.getMetadata(slot.canister()));
            }
        }
    }

    /**
     * Disposes every slot pusher and clears the registry location of every
     * occupied slot's canister gaskets, as the owner leaves the level.
     */
    public void releaseSlotGaskets() {
        disposeAllPushers();
        for (CanisterSlot slot : slots) {
            if (!slot.isEmpty()) {
                SlotGasketRegistration.deregister(registryAccess(), CanisterItem.getMetadata(slot.canister()));
            }
        }
    }

    /**
     * Hands a canister taken from a slot to the player, with the pot-hit sound.
     *
     * @param removed the canister taken out
     * @param player  the player receiving it
     * @param level   the level
     * @param pos     the machine's position
     * @return SUCCESS when a canister was handed over, PASS when none was
     */
    public static InteractionResult handToPlayer(ItemStack removed, Player player, Level level, BlockPos pos) {
        if (removed.isEmpty()) {
            return InteractionResult.PASS;
        }
        PlayerUtils.addOrDrop(player, removed);
        level.playSound(null, pos, SoundEvents.DECORATED_POT_HIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.SUCCESS;
    }

    private @Nullable Supplier<GasketRegistry> registryAccess() {
        return owner.gasket().registryAccess();
    }

    private long gameTime() {
        Level level = owner.getLevel();
        return level != null ? level.getGameTime() : 0L;
    }

    private void invalidateCapabilities() {
        Level level = owner.getLevel();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(owner.getBlockPos());
        }
    }

    // --- Save and load ---

    /**
     * Writes the slots under one key. A grid of slots writes a compound keyed
     * by slot index; a single slot writes its canister stack alone, the format
     * the reactor and the tap have always saved. Each handler's fluid is
     * written onto its stack first.
     *
     * @param output the value output
     * @param key    the tag key the slots live under
     */
    public void save(ValueOutput output, String key) {
        for (CanisterSlot slot : slots) {
            slot.syncHandlerToStack();
        }
        if (maxSlots == 1) {
            saveLoneCanister(output, key);
        } else {
            saveGrid(output, key);
        }
    }

    private void saveLoneCanister(ValueOutput output, String key) {
        ItemStack canister = slots[0].canister();
        if (!canister.isEmpty()) {
            output.store(key, ItemStack.CODEC, canister);
        }
    }

    private void saveGrid(ValueOutput output, String key) {
        CompoundTag root = new CompoundTag();
        for (CanisterSlot slot : slots) {
            CompoundTag slotTag = new CompoundTag();
            slot.save(slotTag);
            if (!slotTag.isEmpty()) {
                root.put(String.valueOf(slot.index()), slotTag);
            }
        }
        if (!root.isEmpty()) {
            output.store(key, CompoundTag.CODEC, root);
        }
    }

    /**
     * Reads the slots {@link #save} wrote, then rebuilds each fluid handler and
     * the composite shape. Loading fires no structure callback, so no sync runs.
     *
     * @param input the value input
     * @param key   the tag key the slots live under
     */
    public void load(ValueInput input, String key) {
        if (maxSlots == 1) {
            slots[0].load(input.read(key, ItemStack.CODEC).orElse(ItemStack.EMPTY));
        } else {
            CompoundTag root = input.read(key, CompoundTag.CODEC).orElseGet(CompoundTag::new);
            for (CanisterSlot slot : slots) {
                slot.load(root.getCompoundOrEmpty(String.valueOf(slot.index())));
            }
        }
        rebuildCompositeShape();
        for (CanisterSlot slot : slots) {
            slot.buildHandler(this::gameTime);
        }
    }

    // --- Light ---

    /**
     * @return one light entry per slot holding goo, measured against the slot's capacity
     */
    public List<GooLightEntry> lightEntries() {
        List<GooLightEntry> entries = new ArrayList<>(maxSlots);
        for (CanisterSlot slot : slots) {
            CanisterFluidContent content = slot.fluidContent();
            ResourceKey<GooTypeDefinition> type = content.getGooType();
            if (!content.isEmpty() && type != null) {
                entries.add(new GooLightEntry(type, content.amount(), slot.capacity()));
            }
        }
        return entries;
    }
}
