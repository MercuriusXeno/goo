package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

// Arithmetic operations live in GooValueArithmetic; instance methods delegate there.

/**
 * Represents the goo composition of an item: how much goo of each type it contains.
 * Decision values-are-fractions-melt-floors-fabricate-ceils: each amount is an exact
 * fraction; melting reads the floor of each type and fabricating reads the ceiling.
 */
public class GooValue {

    public static final GooValue EMPTY = new GooValue(Collections.emptyMap());

    /**
     * Display label for empty values.
     */
    private static final String LABEL_NONE = "none";
    /**
     * Separator between types in toString.
     */
    private static final String TYPE_SEPARATOR = ", ";
    /**
     * Key-value separator in toString.
     */
    private static final String KV_SEPARATOR = ": ";

    private final Map<ResourceKey<GooTypeDefinition>, GooFraction> amounts;

    /**
     * Creates a GooValue from whole amounts per goo type. Zero entries are stripped.
     *
     * @param values whole goo type amounts (zero entries are excluded)
     */
    public GooValue(Map<ResourceKey<GooTypeDefinition>, Integer> values) {
        this.amounts = new HashMap<>();
        values.forEach((type, amount) -> {
            if (amount != 0) {
                this.amounts.put(type, GooFraction.of(amount));
            }
        });
    }

    /**
     * Creates a GooValue from exact amounts per goo type. Zero entries are stripped.
     *
     * @param exactAmounts exact goo type amounts
     * @return the value holding those amounts
     */
    public static GooValue ofExact(Map<ResourceKey<GooTypeDefinition>, GooFraction> exactAmounts) {
        GooValue value = new GooValue(Collections.emptyMap());
        exactAmounts.forEach((type, amount) -> {
            if (!amount.isZero()) {
                value.amounts.put(type, amount);
            }
        });
        return value;
    }

    /**
     * The exact amount of a type, zero when absent.
     *
     * @param type the goo type to query
     * @return the exact amount
     */
    public GooFraction exact(ResourceKey<GooTypeDefinition> type) {
        return amounts.getOrDefault(type, GooFraction.ZERO);
    }

    /**
     * An unmodifiable view of every exact amount.
     *
     * @return map of goo types to exact amounts (never null, may be empty)
     */
    public Map<ResourceKey<GooTypeDefinition>, GooFraction> exactAll() {
        return Collections.unmodifiableMap(amounts);
    }

    /**
     * What melting pays in a type: the floor of its exact amount.
     *
     * @param type the goo type to query
     * @return the floor, 0 when absent
     */
    public int meltFloor(ResourceKey<GooTypeDefinition> type) {
        return exact(type).floor();
    }

    /**
     * What melting pays per type; a type flooring to zero is absent.
     *
     * @return map of goo types to floored amounts
     */
    public Map<ResourceKey<GooTypeDefinition>, Integer> meltFloorAll() {
        return wholeView(GooFraction::floor);
    }

    /**
     * The sum of what melting pays across every type.
     *
     * @return the total of each type's floor
     */
    public int meltFloorTotal() {
        return meltFloorAll().values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * What fabricating costs in a type: the ceiling of its exact amount.
     *
     * @param type the goo type to query
     * @return the ceiling, 0 when absent
     */
    public int fabricateCeiling(ResourceKey<GooTypeDefinition> type) {
        return exact(type).ceiling();
    }

    /**
     * What fabricating costs per type.
     *
     * @return map of goo types to ceiled amounts
     */
    public Map<ResourceKey<GooTypeDefinition>, Integer> fabricateCeilingAll() {
        return wholeView(GooFraction::ceiling);
    }

    private Map<ResourceKey<GooTypeDefinition>, Integer> wholeView(ToIntFunction<GooFraction> rounding) {
        Map<ResourceKey<GooTypeDefinition>, Integer> view = new HashMap<>();
        amounts.forEach((type, amount) -> {
            int whole = rounding.applyAsInt(amount);
            if (whole != 0) {
                view.put(type, whole);
            }
        });
        return Collections.unmodifiableMap(view);
    }

    /**
     * Returns true if this value has no goo types.
     *
     * @return true if the value map is empty
     */
    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /**
     * True if any goo type has a negative amount.
     *
     * @return true if any type's amount is below zero
     */
    public boolean hasNegative() {
        return amounts.values().stream().anyMatch(GooFraction::isNegative);
    }

    /**
     * Number of distinct goo types with non-zero amounts.
     *
     * @return the count of goo types present
     */
    public int typeCount() {
        return amounts.size();
    }

    /**
     * The exact sum of every type's amount.
     *
     * @return total goo across all types
     */
    public GooFraction totalExact() {
        GooFraction total = GooFraction.ZERO;
        for (GooFraction amount : amounts.values()) {
            total = total.plus(amount);
        }
        return total;
    }

    /**
     * Returns the goo type with the highest amount, or null if empty.
     * Ties break by iteration order of the contents map.
     *
     * @return the dominant goo type, or null if empty
     */
    public ResourceKey<GooTypeDefinition> largestType() {
        ResourceKey<GooTypeDefinition> largest = null;
        GooFraction highest = GooFraction.ZERO;
        for (Map.Entry<ResourceKey<GooTypeDefinition>, GooFraction> entry : amounts.entrySet()) {
            if (entry.getValue().compareTo(highest) > 0) {
                highest = entry.getValue();
                largest = entry.getKey();
            }
        }
        return largest;
    }

    /**
     * Returns a new GooValue that is the sum of this and another, scaled by a multiplier.
     *
     * @param other      the value to add
     * @param multiplier scaling factor applied to other before adding
     * @return a new GooValue with the combined amounts
     */
    public GooValue add(GooValue other, int multiplier) {
        return GooValueArithmetic.add(this, other, multiplier);
    }

    /**
     * Returns a new GooValue with per-type subtraction; a type reaching zero is excluded.
     *
     * @param other the value to subtract
     * @return a new GooValue with the difference
     */
    public GooValue subtract(GooValue other) {
        return GooValueArithmetic.subtract(this, other);
    }

    /**
     * Returns a new GooValue with all negative types clamped to zero (removed).
     * Use after applying modifiers that can introduce negatives, when the result
     * must represent a physical goo composition.
     *
     * @return a new GooValue with negatives removed, or this if none exist
     */
    public GooValue floorZero() {
        return GooValueArithmetic.floorZero(this);
    }

    /**
     * Returns a new GooValue with all type amounts scaled by the given ratio, each
     * rounded to the nearest whole. Types that round to zero are excluded.
     *
     * @param fraction the scaling factor (e.g. 0.5 for half value)
     * @return a new scaled GooValue
     */
    public GooValue scale(double fraction) {
        return GooValueArithmetic.scale(this, fraction);
    }

    /**
     * Multiplies all type amounts by a factor. Symmetric to {@link #divide(int)}.
     *
     * @param factor the multiplier to apply to each type
     * @return a new scaled GooValue
     */
    public GooValue multiply(int factor) {
        return GooValueArithmetic.multiply(this, factor);
    }

    /**
     * Divides all type amounts by divisor exactly, keeping every fraction.
     *
     * @param divisor the divisor for each type's amount
     * @return a new GooValue with the exact shares
     */
    public GooValue divide(int divisor) {
        return GooValueArithmetic.divide(this, divisor);
    }

    /**
     * What melting one item pays, as goo contents.
     *
     * @return the melt floor of each type
     */
    public GooContents toGooContents() {
        return toGooContents(1);
    }

    /**
     * What melting count items pays, each item paying its own melt floor.
     *
     * @param count number of items melted
     * @return the melt floor of each type, times count
     */
    public GooContents toGooContents(int count) {
        if (isEmpty() || count <= 0) {
            return GooContents.EMPTY;
        }
        Map<ResourceKey<GooTypeDefinition>, Integer> paid = new HashMap<>();
        meltFloorAll().forEach((type, amount) -> paid.put(type, amount * count));
        return new GooContents(paid);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof GooValue value && amounts.equals(value.amounts);
    }

    @Override
    public int hashCode() {
        return amounts.hashCode();
    }

    /**
     * Returns a human-readable summary like "metal: 480, leaf: 64/3", or "none" if empty.
     */
    @Override
    public String toString() {
        if (isEmpty()) {
            return LABEL_NONE;
        }
        StringBuilder sb = new StringBuilder();
        amounts.forEach((type, amount) -> {
            if (sb.length() > 0) {
                sb.append(TYPE_SEPARATOR);
            }
            sb.append(GooTypes.id(type)).append(KV_SEPARATOR).append(amount);
        });
        return sb.toString();
    }
}
