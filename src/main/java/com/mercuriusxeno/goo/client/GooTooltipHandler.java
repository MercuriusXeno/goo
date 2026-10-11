package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.tooltip.GooValueTooltipComponent;
import com.mercuriusxeno.goo.client.tooltip.GooValuesKey;
import com.mercuriusxeno.goo.client.tooltip.VanillaFluidTooltipComponent;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.fluid.GooBucketItem;
import com.mercuriusxeno.goo.item.*;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import java.util.List;
import java.util.Map;

/**
 * Injects goo value tooltip components into any item tooltip that has goo values.
 * Uses RenderTooltipEvent.GatherComponents to insert custom icon+text components.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTooltipHandler {

    /**
     * One vanilla bucket in millibuckets / amount.
     */
    private static final int BUCKET_VOLUME = 1000;

    /** The hint's text before the bound key's name. */
    private static final String HINT_LEAD = "Hold [";

    /** The hint's text after the bound key's name. */
    private static final String HINT_TAIL = "] for goo values";

    /**
     * "+" separator between contents and container value rows.
     */
    private static final Component PLUS_SEPARATOR =
            Component.literal("+").withStyle(ChatFormatting.WHITE);

    private GooTooltipHandler() {
    }

    /**
     * Intercepts tooltip component gathering to inject goo value entries.
     * For goo items, shows the goo's actual volume instead of the registry base value.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        if (!GooValuesKey.isHeld()) {
            if (hasGooData(stack)) {
                event.getTooltipElements().add(Either.left(
                        revealHint(GooValuesKey.MAPPING.getTranslatedKeyMessage())));
            }
            return;
        }
        handleTooltipOrchestration(event, stack);
    }

    private static void handleTooltipOrchestration(RenderTooltipEvent.GatherComponents event, ItemStack stack) {
        if (handleGooTooltip(event.getTooltipElements(), stack)) {
            return;
        }
        GooContents chrysmValue = chrysmValue(stack);
        if (chrysmValue != null) {
            appendGooRows(event.getTooltipElements(), chrysmValue.getAll());
            return;
        }
        if (handleContainerTooltip(event.getTooltipElements(), stack)) {
            return;
        }
        handleItemTooltip(event.getTooltipElements(), stack);
    }

    /**
     * A chrysm's goo value, read from its tier and type rather than the item registry:
     * its tier volume of its type and the crystal spent on it (decision chrysm-melts-back-to-its-goo).
     *
     * @param stack the item stack
     * @return the value, or null when the stack is no typed chrysm
     */
    static @org.jspecify.annotations.Nullable GooContents chrysmValue(ItemStack stack) {
        ResourceKey<GooTypeDefinition> type = stack.get(GooDataComponents.GOO_TYPE.get());
        return stack.getItem() instanceof ChrysmItem chrysm && type != null ? chrysm.tier().contentsOf(type) : null;
    }

    /**
     * The hint under an item with goo data while the Goo values key is up,
     * naming the key bound to it.
     * decision tooltip-key-is-its-own-g-binding
     *
     * @param keyName the bound key's display name
     * @return the hint line
     */
    static Component revealHint(Component keyName) {
        return Component.literal(HINT_LEAD).append(keyName).append(HINT_TAIL)
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * Returns true if the item has any goo-relevant data worth showing: goo
     * it carries, or a value the player has learned.
     *
     * @param stack the item stack
     * @return true if goo tooltip would be non-empty
     */
    private static boolean hasGooData(ItemStack stack) {
        if (carriesGoo(stack)) {
            return true;
        }
        GooValue value = knownValue(stack);
        return value != null && !value.isEmpty();
    }

    /**
     * Returns true if the stack holds goo of its own: a goo, a chrysm, goo
     * fluid, goo contents or canister fluid.
     *
     * @param stack the item stack to inspect
     * @return true if the stack carries goo
     */
    private static boolean carriesGoo(ItemStack stack) {
        if (stack.getItem() instanceof GooItem || chrysmValue(stack) != null || getGooContentType(stack) != null) {
            return true;
        }
        return isEmptyContents(stack.get(GooDataComponents.GOO_CONTENTS.get()))
                || isEmptyCanister(stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get()));
    }

    /**
     * Whether the tooltip shows an item's goo lines: the goo a stack carries
     * always shows, and an item's own value shows only once the player has
     * learned the item (decision goo-tooltip-shows-only-known-values).
     *
     * @param carriesGoo whether the lines read goo the stack holds rather than the item's value
     * @param item       the item id
     * @param known      the items the player knows
     * @return true when the lines show
     */
    static boolean revealsGooLines(boolean carriesGoo, Identifier item, KnownItems known) {
        return carriesGoo || known.contains(item);
    }

    /**
     * The item's own goo value, when the player has learned the item.
     *
     * @param stack the item stack
     * @return the value, or null when unknown to the player or valueless
     */
    private static @org.jspecify.annotations.Nullable GooValue knownValue(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return revealsGooLines(false, itemId, ClientKnownItems.current()) ? lookupValue(stack) : null;
    }

    private static boolean isEmptyCanister(CanisterFluidContent canister) {
        return canister != null && !canister.isEmpty();
    }

    private static boolean isEmptyContents(GooContents contents) {
        return contents != null && !contents.isEmpty();
    }

    /**
     * Handles goo items, returning true if a goo tooltip was appended.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     * @return true if the stack was a goo
     */
    private static boolean handleGooTooltip(List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        if (stack.getItem() instanceof GooItem) {
            appendGooComponent(elements, GooStacks.keyOf(stack), GooItem.getVolume(stack));
            return true;
        }
        return false;
    }

    /**
     * Handles fluid containers (goo buckets, canisters with goo fluid):
     * single column with contents rows, a "+" separator, and container
     * value rows. Shows contents alone if the container has no goo value.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     * @return true if a container tooltip was appended
     */
    private static boolean handleContainerTooltip(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        ResourceKey<GooTypeDefinition> contentType = getGooContentType(stack);
        int contentAmount = getGooContentAmount(stack);
        if (contentType == null || contentAmount <= 0) {
            return false;
        }

        appendContainerRows(elements, contentType, contentAmount, lookupContainerValue(stack));
        return true;
    }

    /**
     * Appends a container's contents row, then, when the container has a goo
     * value of its own, the "+" separator and the container's rows.
     *
     * @param elements       the tooltip element list
     * @param contentType    the contents' goo type
     * @param contentAmount  the contents' amount
     * @param containerValue the container's own value, or null
     */
    static void appendContainerRows(List<Either<FormattedText, TooltipComponent>> elements,
            ResourceKey<GooTypeDefinition> contentType, int contentAmount,
            @org.jspecify.annotations.Nullable GooValue containerValue) {
        appendGooRows(elements, Map.of(contentType, contentAmount));
        if (containerValue == null || containerValue.isEmpty()) {
            return;
        }
        elements.add(Either.left(PLUS_SEPARATOR));
        appendGooRows(elements, containerValue.getAll());
    }

    /**
     * Appends one goo row per type, directly under the line above them.
     * decision tooltip-key-is-its-own-g-binding
     *
     * @param elements the tooltip element list
     * @param rows     each goo type's amount
     */
    static void appendGooRows(List<Either<FormattedText, TooltipComponent>> elements,
            Map<ResourceKey<GooTypeDefinition>, Integer> rows) {
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> row : rows.entrySet()) {
            elements.add(Either.right(new GooValueTooltipComponent(row.getKey(), row.getValue())));
        }
    }

    /**
     * Returns the goo type of the fluid contents, or null.
     *
     * @param stack the item stack
     * @return the goo type, or null
     */
    private static @org.jspecify.annotations.Nullable ResourceKey<GooTypeDefinition> getGooContentType(ItemStack stack) {
        if (stack.getItem() instanceof GooBucketItem) {
            return GooBucketItem.keyOf(stack);
        }
        if (stack.getItem() instanceof BucketItem) {
            return null;
        }
        CanisterFluidContent content = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        if (isEmptyCanister(content)) {
            return content.dominantGooType();
        }
        return null;
    }

    /**
     * Returns the fluid amount, or 0.
     *
     * @param stack the item stack
     * @return the amount
     */
    private static int getGooContentAmount(ItemStack stack) {
        if (stack.getItem() instanceof BucketItem) {
            return BUCKET_VOLUME;
        }
        CanisterFluidContent content = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        if (isEmptyCanister(content)) {
            return content.volumeOf(content.dominantResource());
        }
        return 0;
    }

    /**
     * Looks up the goo value of the container itself (not its contents).
     *
     * @param stack the item stack
     * @return the container's decomposition value, or null
     */
    private static @org.jspecify.annotations.Nullable GooValue lookupContainerValue(ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        IGooValueLookup values = ClientGooValues.current();
        GooValue value = values.lookup(itemId);
        if ((value == null || value.isEmpty()) && stack.getItem() instanceof BucketItem) {
            itemId = BuiltInRegistries.ITEM.getKey(Items.BUCKET);
            value = values.lookup(itemId);
        }
        return revealsGooLines(false, itemId, ClientKnownItems.current()) ? value : null;
    }

    /**
     * Handles non-goo items: goo contents, upgrades, and base registry values.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void handleItemTooltip(List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        boolean appendedFluid = appendFluidComponents(elements, stack);
        appendUpgradeComponents(elements, stack);
        if (!appendedFluid) {
            appendBaseValueTooltip(elements, stack);
        }
    }

    /**
     * Appends goo contents or canister fluid rows, returning true if either was present.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack to inspect
     * @return true if fluid rows were appended
     */
    private static boolean appendFluidComponents(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        GooContents gooContents = stack.get(GooDataComponents.GOO_CONTENTS.get());
        if (isEmptyContents(gooContents)) {
            appendGooRows(elements, gooContents.getAll());
            return true;
        }
        CanisterFluidContent canisterContent = stack.get(GooDataComponents.CANISTER_FLUID_CONTENT.get());
        if (isEmptyCanister(canisterContent)) {
            appendCanisterRows(elements, canisterContent,
                    ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(stack)));
            return true;
        }
        return false;
    }

    /**
     * Appends registry base value tooltip lines for items without explicit goo contents.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void appendBaseValueTooltip(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        GooValue value = knownValue(stack);
        if (value != null && !value.isEmpty()) {
            appendGooRows(elements, value.getAll());
        }
    }

    /**
     * Looks up the effective goo value for an item stack, including component-aware values.
     *
     * @param stack the item stack
     * @return the value, or null if not found
     */
    private static GooValue lookupValue(ItemStack stack) {
        return ClientGooValues.current().lookup(stack);
    }

    /**
     * Appends a goo's row, or nothing when it holds no typed volume.
     *
     * @param elements the tooltip element list
     * @param type     the goo type
     * @param volume   the volume
     */
    private static void appendGooComponent(
            List<Either<FormattedText, TooltipComponent>> elements,
            @org.jspecify.annotations.Nullable ResourceKey<GooTypeDefinition> type, int volume) {
        if (volume <= 0 || type == null) {
            return;
        }
        appendGooRows(elements, Map.of(type, volume));
    }

    /**
     * Appends the fluid tooltip lines for canister items: a row per goo type
     * (icon + amount), dominant first, or the vanilla fluid's text label +
     * amount, then the total against capacity
     * (decision canisters-hold-more-than-one-goo-type).
     *
     * @param elements the tooltip element list
     * @param content  the canister fluid content
     * @param capacity the canister's capacity in mB
     */
    static void appendCanisterRows(List<Either<FormattedText, TooltipComponent>> elements,
            CanisterFluidContent content, int capacity) {
        Map<ResourceKey<GooTypeDefinition>, Integer> gooVolumes = content.gooVolumesDominantFirst();
        if (gooVolumes.isEmpty()) {
            appendVanillaFluidRow(elements, content.dominantFluid(), content.totalVolume());
        } else {
            appendGooRows(elements, gooVolumes);
        }
        elements.add(Either.left(Component.literal(GooFormat.formatFill(content.totalVolume(), capacity))
                .withStyle(ChatFormatting.GRAY)));
    }

    /**
     * Appends a bucket icon + mB amount tooltip for vanilla fluids.
     *
     * @param elements the tooltip element list
     * @param fluid    the vanilla fluid
     * @param amount   the amount in millibuckets
     */
    static void appendVanillaFluidRow(
            List<Either<FormattedText, TooltipComponent>> elements, Fluid fluid, int amount) {
        elements.add(Either.right(new VanillaFluidTooltipComponent(fluid, amount)));
    }

    /**
     * Appends canister label from canister metadata.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void appendUpgradeComponents(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        appendCanisterUpgrades(elements, stack);
    }

    /**
     * Appends canister label from canister metadata.
     *
     * @param elements the tooltip element list
     * @param stack    the item stack
     */
    private static void appendCanisterUpgrades(
            List<Either<FormattedText, TooltipComponent>> elements, ItemStack stack) {
        CanisterMetadata meta = stack.get(GooDataComponents.CANISTER_METADATA.get());
        if (meta == null || !meta.hasData()) {
            return;
        }
        if (meta.label() != null && !meta.label().isEmpty()) {
            elements.add(Either.left(
                    Component.literal(meta.label()).withStyle(ChatFormatting.GOLD)));
        }
    }
}
