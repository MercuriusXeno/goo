package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unified immutable data component for multi-type goo volume storage.
 * Unified data component for all goo containers:
 * canisters, vats, crucible reservoirs, and partially melted items.
 *
 * <p>Each entry maps a goo type key to a volume in microblobs (mB).
 * Mutation methods return new instances; this record is never modified in place.</p>
 *
 * @param contents the map of goo types to volumes in microblobs
 */
public record GooContents(Map<ResourceKey<GooTypeDefinition>, Integer> contents) implements TooltipProvider {

    /**
     * Empty container with no goo.
     */
    public static final GooContents EMPTY = new GooContents(Map.of());

    /**
     * Persistent codec: goo type ids to volumes, a bundled type by its bare
     * id and a datapack type by its namespaced id (decision datapack-goo-registry).
     */
    public static final Codec<GooContents> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(GooTypes.ID_CODEC, Codec.INT)
                            .fieldOf("contents").forGetter(GooContents::contents)
            ).apply(instance, GooContents::new)
    );

    /**
     * Network codec: each entry as its type key then its volume.
     */
    public static final StreamCodec<ByteBuf, GooContents> STREAM_CODEC = ByteBufCodecs
            .<ByteBuf, ResourceKey<GooTypeDefinition>, Integer, Map<ResourceKey<GooTypeDefinition>, Integer>>map(
                    HashMap::new, GooTypes.KEY_STREAM_CODEC, ByteBufCodecs.VAR_INT)
            .map(GooContents::new, GooContents::contents);

    /**
     * Defensive copy constructor: filters non-positive values, wraps in an unmodifiable map.
     */
    public GooContents {
        contents = filterPositive(contents);
    }

    /**
     * Retains only entries with positive volume, wrapping the result as unmodifiable.
     *
     * @param input the raw contents map
     * @return filtered, unmodifiable map (or Map.of() if empty)
     */
    private static Map<ResourceKey<GooTypeDefinition>, Integer> filterPositive(Map<ResourceKey<GooTypeDefinition>, Integer> input) {
        if (input.isEmpty()) {
            return Map.of();
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> filtered = new HashMap<>();
        input.forEach((type, vol) -> {
            if (vol > 0) {
                filtered.put(type, vol);
            }
        });
        return filtered.isEmpty() ? Map.of() : Collections.unmodifiableMap(filtered);
    }

    /**
     * Returns true if the candidate volume/type beats the current leader.
     *
     * @param vol       the candidate volume
     * @param candidate the candidate goo type
     * @param highest   the current highest volume
     * @param leader    the current leader type
     * @return true if the candidate should replace the leader
     */
    private static boolean beatsCurrentLeader(int vol, ResourceKey<GooTypeDefinition> candidate,
                                              int highest, ResourceKey<GooTypeDefinition> leader) {
        return vol > highest || (vol == highest && winsOrdinalTie(candidate, leader));
    }

    /**
     * Returns true if the candidate wins a tie against the current leader by
     * key order, so the dominant type is the same on every read.
     *
     * @param candidate the challenger goo type
     * @param current   the current leader, or null
     * @return true if the candidate should replace the leader
     */
    private static boolean winsOrdinalTie(ResourceKey<GooTypeDefinition> candidate,
                                          @Nullable ResourceKey<GooTypeDefinition> current) {
        return current == null || GooTypes.ORDER.compare(candidate, current) < 0;
    }

    /**
     * Returns true if no goo of any type is stored.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /**
     * Returns the total volume across all goo types.
     *
     * @return total volume in microblobs
     */
    public int totalVolume() {
        int total = 0;
        for (int v : contents.values()) {
            total += v;
        }
        return total;
    }

    /**
     * Returns how many distinct goo types are present.
     *
     * @return the number of goo types stored
     */
    public int typeCount() {
        return contents.size();
    }

    /**
     * Returns true if exactly one goo type is present.
     *
     * @return true if exactly one type is stored
     */
    public boolean isSingleType() {
        return contents.size() == 1;
    }

    /**
     * Returns the single goo type if exactly one is present, or null otherwise.
     *
     * @return the single goo type, or null
     */
    @Nullable
    public ResourceKey<GooTypeDefinition> getSingleType() {
        if (contents.size() != 1) {
            return null;
        }
        return contents.keySet().iterator().next();
    }

    /**
     * Returns the goo type with the highest volume, or null if empty. Ties break by key order.
     *
     * @return the dominant goo type, or null if empty
     */
    @Nullable
    public ResourceKey<GooTypeDefinition> largestType() {
        ResourceKey<GooTypeDefinition> largest = null;
        int highest = 0;
        for (var e : contents.entrySet()) {
            if (beatsCurrentLeader(e.getValue(), e.getKey(), highest, largest)) {
                highest = e.getValue();
                largest = e.getKey();
            }
        }
        return largest;
    }

    /**
     * Returns the volume of a specific goo type, or 0 if absent.
     *
     * @param type the goo type to query
     * @return volume in microblobs
     */
    public int getVolume(ResourceKey<GooTypeDefinition> type) {
        return contents.getOrDefault(type, 0);
    }

    /**
     * Returns an unmodifiable view of all contents.
     *
     * @return map of goo type to volume in microblobs
     */
    public Map<ResourceKey<GooTypeDefinition>, Integer> getAll() {
        return contents;
    }

    /**
     * Returns a new GooContents with the given volume added to the specified type.
     *
     * @param type   the goo type to add to
     * @param amount the volume to add in microblobs
     * @return new contents with the addition applied
     */
    public GooContents withAdded(ResourceKey<GooTypeDefinition> type, int amount) {
        if (amount <= 0) {
            return this;
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> newMap = new HashMap<>();
        newMap.putAll(contents);
        newMap.merge(type, amount, Integer::sum);
        return new GooContents(newMap);
    }

    /**
     * Returns a new GooContents with all entries from the other contents merged in.
     * Each type's volume is summed.
     *
     * @param other the contents to merge in
     * @return new contents with both sets combined
     */
    public GooContents mergeWith(GooContents other) {
        if (other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }
        GooContents result = this;
        for (Map.Entry<ResourceKey<GooTypeDefinition>, Integer> entry : other.contents.entrySet()) {
            result = result.withAdded(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Returns a new GooContents with the given volume removed from the specified type.
     *
     * @param type   the goo type to remove from
     * @param amount the volume to remove in microblobs
     * @return new contents with the removal applied
     */
    public GooContents withRemoved(ResourceKey<GooTypeDefinition> type, int amount) {
        if (amount <= 0 || !contents.containsKey(type)) {
            return this;
        }
        return new GooContents(removeFromMap(type, amount));
    }

    /**
     * Creates a copy of the contents map with the given volume removed from one type.
     *
     * @param type   the goo type to reduce
     * @param amount the volume to subtract
     * @return the new map (may have the type removed entirely if depleted)
     */
    private Map<ResourceKey<GooTypeDefinition>, Integer> removeFromMap(ResourceKey<GooTypeDefinition> type, int amount) {
        Map<ResourceKey<GooTypeDefinition>, Integer> newMap = new HashMap<>();
        newMap.putAll(contents);
        int remaining = newMap.getOrDefault(type, 0) - amount;
        if (remaining <= 0) {
            newMap.remove(type);
        } else {
            newMap.put(type, remaining);
        }
        return newMap;
    }

    /**
     * Adds up to the remaining capacity of goo, returning new contents.
     * The caller provides the total capacity externally; this method only
     * adds what fits, capping at that capacity.
     *
     * @param type     the goo type to add
     * @param amount   the amount requested to add
     * @param capacity the total capacity of the container
     * @return new contents with the capped addition
     */
    public GooContents withCappedAdd(ResourceKey<GooTypeDefinition> type, int amount, int capacity) {
        if (amount <= 0) {
            return this;
        }
        int space = capacity - totalVolume();
        if (space <= 0) {
            return this;
        }
        int accepted = Math.min(amount, space);
        return withAdded(type, accepted);
    }

    /**
     * Returns how much of the requested amount was actually accepted by
     * {@link #withCappedAdd}. Useful for callers that need to know the delta.
     *
     * @param amount   the amount requested to add
     * @param capacity the total capacity of the container
     * @return the amount that would be accepted (0 if full)
     */
    public int cappedAddAmount(int amount, int capacity) {
        if (amount <= 0) {
            return 0;
        }
        int space = capacity - totalVolume();
        if (space <= 0) {
            return 0;
        }
        return Math.min(amount, space);
    }

    /**
     * No-op: icon tooltips are handled by GooTooltipHandler for all container types.
     *
     * @param context         the tooltip context
     * @param tooltip         the tooltip line consumer
     * @param flag            the tooltip flag (normal or advanced)
     * @param componentGetter the component getter
     */
    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip,
                             TooltipFlag flag, DataComponentGetter componentGetter) {
        // Intentionally empty: GooTooltipHandler renders icon+volume lines
    }
}
