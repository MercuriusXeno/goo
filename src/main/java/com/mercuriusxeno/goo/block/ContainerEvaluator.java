package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates container items by recursively summing goo values
 * and collecting valueless items for ejection.
 */
public class ContainerEvaluator {

    /**
     * Result of recursively evaluating a container item's contents.
     *
     * @param goo    the summed goo of every valued item, nested containers included
     * @param ejects the items with no goo value, to spawn as entities
     * @param valued every valued stack in the order the walk summed it, the shell last
     */
    public record ContainerEvaluation(GooContents goo, List<ItemStack> ejects, List<ValuedStack> valued) {
    }

    /**
     * Checks whether the stack has container or bundle data components.
     *
     * @param stack the item stack to test
     * @return true if the stack is a recognized container type
     */
    public boolean isContainer(ItemStack stack) {
        return stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    /**
     * Recursively evaluates a container's contents, summing goo values for
     * items with known mappings and collecting valueless items for ejection.
     *
     * @param containerId the registry ID of the container item (for shell value lookup)
     * @param container   the container item stack to evaluate
     * @param lookup      goo value lookup for resolving item values
     * @return evaluation result containing aggregated goo and ejected items
     */
    public ContainerEvaluation evaluate(Identifier containerId, ItemStack container, IGooValueLookup lookup) {
        List<ItemStack> contents = collectContainerContents(container);
        List<ItemStack> ejects = new ArrayList<>();
        List<ValuedStack> valued = new ArrayList<>();
        for (ItemStack item : contents) {
            accumulateItem(item, lookup, valued, ejects);
        }
        addValued(valuedStack(containerId, 1, lookup), valued);
        return new ContainerEvaluation(sumOf(valued, lookup), ejects, valued);
    }

    /**
     * Values a stack by its item id, the walk's seam free of the item registry.
     *
     * @param itemId the item's registry id
     * @param count  the number of items
     * @param lookup the goo value lookup
     * @return the valued stack, or null when the item has no goo value
     */
    public static @Nullable ValuedStack valuedStack(Identifier itemId, int count, IGooValueLookup lookup) {
        GooValue value = lookup.lookup(itemId);
        if (value == null || value.isEmpty()) {
            return null;
        }
        return new ValuedStack(itemId, count, value.toGooContents(count).totalVolume());
    }

    /**
     * Sums the goo the valued stacks carry, typed by each item's goo value.
     *
     * @param valued the valued stacks
     * @param lookup the goo value lookup
     * @return the summed goo
     */
    private static GooContents sumOf(List<ValuedStack> valued, IGooValueLookup lookup) {
        GooContents goo = GooContents.EMPTY;
        for (ValuedStack stack : valued) {
            GooValue value = lookup.lookup(stack.item());
            if (value != null) {
                goo = goo.mergeWith(value.toGooContents(stack.count()));
            }
        }
        return goo;
    }

    /**
     * Adds a valued stack to the walk's list when it carries goo.
     *
     * @param stack  the valued stack, or null
     * @param valued the walk's valued stacks
     */
    private static void addValued(@Nullable ValuedStack stack, List<ValuedStack> valued) {
        if (stack != null) {
            valued.add(stack);
        }
    }

    /**
     * Processes one item: recurse into nested containers or evaluate directly.
     *
     * @param item   the item stack
     * @param lookup goo value lookup
     * @param valued accumulator for valued stacks, in walk order
     * @param ejects accumulator for ejected items
     */
    private void accumulateItem(ItemStack item, IGooValueLookup lookup,
                                List<ValuedStack> valued, List<ItemStack> ejects) {
        if (isContainer(item)) {
            Identifier nestedId = BuiltInRegistries.ITEM.getKey(item.getItem());
            ContainerEvaluation nested = evaluate(nestedId, item, lookup);
            ejects.addAll(nested.ejects());
            valued.addAll(nested.valued());
            return;
        }
        evaluateItemOrEject(item, lookup, valued, ejects);
    }

    /**
     * Collects all non-empty item stacks from a container's data components.
     *
     * @param container the container item stack
     * @return the list
     */
    private List<ItemStack> collectContainerContents(ItemStack container) {
        List<ItemStack> items = new ArrayList<>();
        collectFromComponent(container, DataComponents.CONTAINER, items);
        collectFromBundle(container, items);
        return items;
    }

    /**
     * Adds non-empty items from a CONTAINER data component.
     *
     * @param container the container item stack
     * @param component the data component key
     * @param items     accumulator for collected items
     */
    private void collectFromComponent(ItemStack container, net.minecraft.core.component.DataComponentType<ItemContainerContents> component, List<ItemStack> items) {
        ItemContainerContents data = container.get(component);
        if (data != null) {
            data.nonEmptyItemCopyStream().forEach(items::add);
        }
    }

    /**
     * Adds items from a BUNDLE_CONTENTS data component.
     *
     * @param container the container item stack
     * @param items     accumulator for collected items
     */
    private void collectFromBundle(ItemStack container, List<ItemStack> items) {
        BundleContents data = container.get(DataComponents.BUNDLE_CONTENTS);
        if (data != null) {
            data.itemCopyStream().forEach(items::add);
        }
    }

    /**
     * Resolves ID from the item stack, then values it or ejects it.
     *
     * @param item   the item stack to evaluate
     * @param lookup the goo value lookup
     * @param valued accumulator for valued stacks, in walk order
     * @param ejects the list of items to eject
     */
    private void evaluateItemOrEject(ItemStack item, IGooValueLookup lookup,
                                     List<ValuedStack> valued, List<ItemStack> ejects) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
        ValuedStack stack = valuedStack(itemId, item.getCount(), lookup);
        if (stack == null) {
            ejects.add(item.copy());
        } else {
            valued.add(stack);
        }
    }
}
