package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Pure arithmetic operations on {@link GooValue} instances, each exact on fractions.
 * Decision values-are-fractions-melt-floors-fabricate-ceils: division keeps every
 * fraction, so no derivation loses goo to a remainder.
 */
final class GooValueArithmetic {

    private GooValueArithmetic() {
        // utility class
    }

    /**
     * Returns a new GooValue that is the sum of self and other, scaled by a multiplier.
     *
     * @param self       the base value
     * @param other      the value to add
     * @param multiplier scaling factor applied to other before adding
     * @return a new GooValue with the combined amounts
     */
    static GooValue add(GooValue self, GooValue other, int multiplier) {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> result = new HashMap<>(self.exactAll());
        other.exactAll().forEach((type, amount) ->
                result.merge(type, amount.times(multiplier), GooFraction::plus));
        return GooValue.ofExact(result);
    }

    /**
     * Returns a new GooValue with per-type subtraction; a type reaching zero is excluded.
     *
     * @param self  the base value
     * @param other the value to subtract
     * @return a new GooValue with the difference
     */
    static GooValue subtract(GooValue self, GooValue other) {
        return add(self, mapEachType(other, GooFraction::negated), 1);
    }

    /**
     * Multiplies all type amounts by a factor. Symmetric to {@link #divide(GooValue, int)}.
     *
     * @param self   the value to multiply
     * @param factor the multiplier to apply to each type
     * @return a new scaled GooValue
     */
    static GooValue multiply(GooValue self, int factor) {
        if (factor <= 0) {
            return GooValue.EMPTY;
        }
        if (factor == 1) {
            return self;
        }
        return mapEachType(self, amount -> amount.times(factor));
    }

    /**
     * Divides all type amounts by divisor exactly.
     *
     * @param self    the value to divide
     * @param divisor the divisor for each type's amount
     * @return a new GooValue holding each exact share
     */
    static GooValue divide(GooValue self, int divisor) {
        if (divisor <= 1) {
            return self;
        }
        return mapEachType(self, amount -> amount.dividedBy(divisor));
    }

    /**
     * Returns a new GooValue with all type amounts scaled by the given ratio, each
     * rounded to the nearest whole. Types that round to zero are excluded.
     *
     * @param self     the value to scale
     * @param fraction the scaling factor (e.g. 0.5 for half value)
     * @return a new scaled GooValue
     */
    static GooValue scale(GooValue self, double fraction) {
        if (fraction <= 0.0) {
            return GooValue.EMPTY;
        }
        if (fraction >= 1.0) {
            return self;
        }
        return mapEachType(self, amount -> GooFraction.of(Math.round(amount.toDouble() * fraction)));
    }

    /**
     * Returns a new GooValue with all negative types clamped to zero (removed).
     * Use after applying modifiers that can introduce negatives, when the result
     * must represent a physical goo composition.
     *
     * @param self the value to floor
     * @return a new GooValue with negatives removed, or self if none exist
     */
    static GooValue floorZero(GooValue self) {
        if (!self.hasNegative()) {
            return self;
        }
        return mapEachType(self, amount -> amount.isNegative() ? GooFraction.ZERO : amount);
    }

    private static GooValue mapEachType(GooValue self, UnaryOperator<GooFraction> operation) {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> result = new HashMap<>();
        self.exactAll().forEach((type, amount) -> result.put(type, operation.apply(amount)));
        return GooValue.ofExact(result);
    }
}
