package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.ContainerEvaluator;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Item and goo insertion logic for CrucibleBlockEntity. Handles meltable items,
 * containers, direct goo contents, and PMI pool merging.
 */
final class CrucibleInsertion {

    private CrucibleInsertion() {
    }

    /**
     * Returns true if the item has a non-empty goo value.
     *
     * @param stack the item stack to check
     * @return true if the item can be inserted
     */
    static boolean canInsertItem(ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return canInsertItem(id, Goo.GOO_VALUES);
    }

    /**
     * Testable seam: checks goo value via Identifier without registry coupling.
     *
     * @param itemId the item registry ID
     * @param lookup the goo value lookup
     * @return true if the item has goo value
     */
    static boolean canInsertItem(Identifier itemId, IGooValueLookup lookup) {
        GooValue value = lookup.lookup(itemId);
        return value != null && !value.isEmpty();
    }

    /**
     * Inserts the whole items of a stack whose goo fits the shared PMI pool.
     *
     * @param be    the crucible block entity
     * @param stack the item stack to insert
     * @param count the number of items offered
     * @return the number of items melted in, from zero to {@code count}
     */
    static int insertItem(CrucibleBlockEntity be, ItemStack stack, int count) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return insertItem(be, id, count, Goo.GOO_VALUES);
    }

    /**
     * Testable seam: melts in the whole items whose goo fits the pool, by
     * Identifier without registry coupling (decision crucible-refuses-past-two-billion).
     *
     * @param be     the crucible block entity
     * @param itemId the item registry ID
     * @param count  the number of items offered
     * @param lookup the goo value lookup
     * @return the number of items melted in, from zero to {@code count}
     */
    static int insertItem(CrucibleBlockEntity be, Identifier itemId, int count, IGooValueLookup lookup) {
        GooValue value = lookup.lookup(itemId);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int fitting = CrucibleCapacity.wholeUnitsThatFit(poolContents(be), value.toGooContents(), count);
        if (fitting > 0 && mergeIntoPool(be, value.toGooContents(fitting))) {
            be.syncToClients();
        }
        return fitting;
    }

    /**
     * Merges goo contents into the PMI pool only when every type fits under
     * the cap, creating a new PMI if needed.
     *
     * @param be       the crucible block entity
     * @param contents the goo contents
     * @return true if the contents merged, false if refused at the cap
     */
    static boolean mergeIntoPool(CrucibleBlockEntity be, GooContents contents) {
        GooContents merged = CrucibleCapacity.mergedWithinCap(poolContents(be), contents);
        if (merged == null) {
            return false;
        }
        if (be.meltingItem.isEmpty()) {
            be.meltingItem = PartiallyMeltedItem.createWith(merged);
        } else {
            PartiallyMeltedItem.setContents(be.meltingItem, merged);
        }
        return true;
    }

    /**
     * Returns the PMI pool's contents, or EMPTY when nothing is melting.
     *
     * @param be the crucible block entity
     * @return the pool's contents
     */
    static GooContents poolContents(CrucibleBlockEntity be) {
        return be.meltingItem.isEmpty() ? GooContents.EMPTY : PartiallyMeltedItem.getContents(be.meltingItem);
    }

    /**
     * Counts the whole units of one goo type that fit the reservoir under the cap.
     *
     * @param be      the crucible block entity
     * @param type    the goo type
     * @param perUnit the volume one unit carries, in mB
     * @param offered the units offered
     * @return the units that fit, from zero to {@code offered}
     */
    static int reservoirUnitsThatFit(CrucibleBlockEntity be, ResourceKey<GooTypeDefinition> type,
                                     int perUnit, int offered) {
        if (perUnit <= 0) {
            return 0;
        }
        return CrucibleCapacity.wholeUnitsThatFit(be.getReservoir(),
            GooContents.EMPTY.withAdded(type, perUnit), offered);
    }

    /**
     * Inserts goo directly into the reservoir. Bypasses melting pipeline.
     *
     * @param be     the crucible block entity
     * @param type   the goo type
     * @param volume volume in microblobs
     * @return the amount actually inserted
     */
    static int insertGoo(CrucibleBlockEntity be, ResourceKey<GooTypeDefinition> type, int volume) {
        if (volume <= 0) {
            return 0;
        }
        int clamped = Math.min(volume, Integer.MAX_VALUE);
        int inserted = be.reservoir.insertGoo(type, clamped, false);
        be.syncToClients();
        return inserted;
    }

    /**
     * Inserts a container item's evaluated contents into the melt pool.
     *
     * @param be        the crucible block entity
     * @param container the container item stack
     * @return list of items to eject, or null if the container had no content
     */
    static @Nullable List<ItemStack> insertContainer(CrucibleBlockEntity be, ItemStack container) {
        Identifier containerId = BuiltInRegistries.ITEM.getKey(container.getItem());
        return insertContainer(be, containerId, container, Goo.GOO_VALUES);
    }

    /**
     * Testable seam: evaluates a container via Identifier without registry coupling.
     *
     * @param be          the crucible block entity
     * @param containerId the container registry ID
     * @param container   the container item stack
     * @param lookup      the goo value lookup
     * @return the eject list, or null when the container had no content or its goo does not fit whole
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull") // null = nothing happened; empty = eject nothing
    static @Nullable List<ItemStack> insertContainer(
            CrucibleBlockEntity be, Identifier containerId, ItemStack container, IGooValueLookup lookup) {
        ContainerEvaluator.ContainerEvaluation eval = be.containerEvaluator.evaluate(containerId, container, lookup);
        if (eval.goo().isEmpty() && eval.ejects().isEmpty()) {
            return null;
        }
        if (!eval.goo().isEmpty() && !mergeIntoPool(be, eval.goo())) {
            return null;
        }
        be.syncToClients();
        return eval.ejects();
    }
}
