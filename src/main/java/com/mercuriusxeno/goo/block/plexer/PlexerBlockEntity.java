package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.block.ICutawayMachine;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.canister.ICanisterAttachable;
import com.mercuriusxeno.goo.block.plexer.CutawayInteractionHelper;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plexer: reconstitutes items from goo in externally-attached canisters.
 * All 9 copper fittings on the top face are always available (no slot
 * constraints).
 */
public class PlexerBlockEntity extends GooSyncedBlockEntity implements ICanisterAttachable, ICutawayMachine {

    /**
     * Total canister positions on the 3x3 grid.
     */
    private static final int SLOT_COUNT = 9;
    /**
     * NBT key for the observer target item.
     */
    private static final String TAG_TARGET_ITEM = "TargetItem";

    /**
     * NBT key for the plexer's remainders.
     */
    private static final String TAG_REMAINDERS = "Remainders";

    private ItemStack targetItem = ItemStack.EMPTY;

    /**
     * The overpayment each fabricate banks, each goo type's part under 1 mB, saved with the plexer.
     * decision every-producer-consumer-and-container-holds-a-remainder-buffer
     */
    private final RemainderLedger ledger = new RemainderLedger();

    /**
     * Creates a plexer block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public PlexerBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.PLEXER.get(), pos, state);
    }

    // --- ICanisterAttachable ---

    /**
     * Returns 9 (all copper fitting positions on the top face).
     *
     * @return the integer value
     */
    @Override
    public int maxTopAttachments() {
        return SLOT_COUNT;
    }

    /**
     * Counts the occupied canister slots in the CanisterBlock above.
     *
     * @return the integer value
     */
    @Override
    public int currentTopAttachments() {
        if (level == null) {
            return 0;
        }
        BlockPos above = worldPosition.above();
        if (!(level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe)) {
            return 0;
        }
        return countOccupiedSlots(canisterBe);
    }

    /**
     * Counts non-empty canister slots in the given block entity.
     *
     * @param canisterBe the canister block entity to inspect
     * @return the number of occupied slots
     */
    private int countOccupiedSlots(CanisterBlockEntity canisterBe) {
        int count = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canisterBe.getCanister(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    // --- Target item ---

    /**
     * Returns the target item in the observer slot.
     *
     * @return the target item
     */
    public ItemStack getTargetItem() {
        return targetItem;
    }

    /**
     * Sets the target item for reconstitution (single-count copy, or EMPTY to clear).
     *
     * @param stack the item stack
     */
    public void setTargetItem(ItemStack stack) {
        this.targetItem = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        BlockEntitySync.markDirtyAndSync(this);
    }

    @Override
    public boolean isCutawayHit(net.minecraft.world.phys.BlockHitResult hit) {
        return CutawayInteractionHelper.isCutawayClick(getBlockState(), getBlockPos(), hit);
    }

    /**
     * Returns true if the item has a non-empty, non-restricted goo value.
     *
     * @param stack the item to check
     * @return true if valid for reconstitution targeting
     */
    public boolean isValidTarget(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        IGooValueLookup values = gooValues();
        GooValue value = values.lookup(itemId);
        if (value == null || value.isEmpty()) {
            return false;
        }
        return !values.isRestricted(itemId);
    }

    /**
     * Answers the goo values the plexer's side holds.
     *
     * @return the values, empty for a plexer in no level
     */
    private IGooValueLookup gooValues() {
        Level level = getLevel();
        return level == null ? GooValueTable.EMPTY : GooValues.of(level);
    }

    // --- Reconstitution (reads from external CanisterBlock above) ---

    /**
     * Attempts to reconstitute the target item from canister goo.
     *
     * @return true if enough goo of reconstitute item, or empty if insufficient goo
     */
    public ItemStack tryReconstitute() {
        if (targetItem.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Identifier targetId = BuiltInRegistries.ITEM.getKey(targetItem.getItem());
        return tryReconstitute(targetId, gooValues());
    }

    /**
     * Testable seam: reconstitutes by Identifier without registry coupling.
     *
     * @param targetId the target item registry ID
     * @param lookup   the goo value lookup
     * @return the external canister slots
     */
    ItemStack tryReconstitute(Identifier targetId, IGooValueLookup lookup) {
        GooValue required = resolveRequired(targetId, lookup);
        if (required == null) {
            return ItemStack.EMPTY;
        }

        List<SlotRef> slots = getExternalCanisterSlots();
        if (slots.isEmpty() || !fabricateFrom(required, new CanisterSlotSupply(slots), ledger)) {
            return ItemStack.EMPTY;
        }
        setChanged();
        return new ItemStack(targetItem.getItem());
    }

    /**
     * Debits the target's exact value through the plexer's ledger, the remainder paying
     * first, and consumes the whole mB each debit answers from the supply, the
     * overpayment banked as remainder; where the supply cannot cover any type, nothing
     * is consumed and the remainder stands.
     * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
     *
     * @param target the target's goo value
     * @param supply the goo to fabricate from
     * @param ledger the plexer's remainder ledger
     * @return true if the supply paid the cost
     */
    public static boolean fabricateFrom(GooValue target, PlexerGooSupply supply, IRemainderLedger ledger) {
        Optional<Map<ResourceKey<GooTypeDefinition>, Integer>> cost =
                ledger.debitAllExact(target.exactAll(), supply::available);
        cost.ifPresent(withdrawn -> withdrawn.forEach(supply::consume));
        return cost.isPresent();
    }

    /**
     * Looks up the goo value for a target, returning null if absent or restricted.
     *
     * @param targetId the target item registry ID
     * @param lookup   the goo value lookup
     * @return the required goo value, or null if unavailable
     */
    private @Nullable GooValue resolveRequired(Identifier targetId, IGooValueLookup lookup) {
        GooValue required = lookup.lookup(targetId);
        if (required == null || required.isEmpty()) {
            return null;
        }
        if (lookup.isRestricted(targetId)) {
            return null;
        }
        return required;
    }

    /**
     * Returns slot references for all occupied canister slots above.
     *
     * @return the update tag
     */
    private List<SlotRef> getExternalCanisterSlots() {
        if (level == null) {
            return List.of();
        }
        BlockPos above = worldPosition.above();
        if (!(level.getBlockEntity(above) instanceof CanisterBlockEntity canisterBe)) {
            return List.of();
        }
        return collectOccupiedSlotRefs(canisterBe);
    }

    /**
     * Builds slot references for all occupied positions in the canister block entity.
     *
     * @param canisterBe the canister block entity above this plexer
     * @return list of occupied slot references
     */
    private List<SlotRef> collectOccupiedSlotRefs(CanisterBlockEntity canisterBe) {
        List<SlotRef> refs = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!canisterBe.getCanister(slot).isEmpty()) {
                refs.add(new SlotRef(canisterBe, slot));
            }
        }
        return refs;
    }

    /**
     * Persists target item.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        if (!targetItem.isEmpty()) {
            output.store(TAG_TARGET_ITEM, ItemStack.CODEC, targetItem);
        }
        if (!ledger.remainders().isEmpty()) {
            output.store(TAG_REMAINDERS, GooRemainders.CODEC, ledger.remainders());
        }
    }

    // --- Serialization ---

    /**
     * Restores target item from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        targetItem = input.read(TAG_TARGET_ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
        ledger.load(input.read(TAG_REMAINDERS, GooRemainders.CODEC).orElse(GooRemainders.EMPTY));
    }

    /**
     * Reference to a canister slot in the CanisterBlock above.
     *
     * @param entity the item entity
     * @param slot   the slot index
     */
    private record SlotRef(CanisterBlockEntity entity, int slot) {
    }

    /**
     * The occupied canister slots above, read as one goo supply.
     *
     * @param slots the occupied slot references
     */
    private record CanisterSlotSupply(List<SlotRef> slots) implements PlexerGooSupply {

        @Override
        public int available(ResourceKey<GooTypeDefinition> type) {
            int total = 0;
            for (SlotRef ref : slots) {
                total += CanisterItem.getFluidContent(ref.entity.getCanister(ref.slot)).volumeOf(type);
            }
            return total;
        }

        @Override
        public void consume(ResourceKey<GooTypeDefinition> type, int amount) {
            int remaining = amount;
            for (SlotRef ref : slots) {
                if (remaining <= 0) {
                    break;
                }
                remaining -= ref.entity.extractGoo(ref.slot, type, remaining);
            }
        }
    }
}
