package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * A cost or an upkeep as an ability's JSON writes it: a number of mB, or an expression in which each item id and each
 * $prefab, spelled as base_values spells them, reads its value in the ability's own goo type alone. The price is read
 * when it is charged or shown, since goo values attach after the abilities load.
 * ability-cost-builds-as-a-one-type-by-type-value
 *
 * @param amount the number or the expression
 * @param type   the goo type the expression's items price in, null for an amount naming no item
 */
public record AbilityPrice(Expr amount, @Nullable ResourceKey<GooTypeDefinition> type) {

    /** A price of nothing. */
    public static final AbilityPrice FREE = flat(0);

    private static final String NOT_A_FLAT_COST = "Ability cost must be one whole amount, not %s";
    private static final String NAMES_NO_ITEM = "Ability cost reads '%s', which names no item or $prefab";
    private static final String UNTYPED_ITEMS = "A price naming items needs the goo type they price in";

    /**
     * Refuses an amount naming no type where it reads an item.
     */
    public AbilityPrice {
        if (type == null && !amount.variables().isEmpty()) {
            throw new IllegalArgumentException(UNTYPED_ITEMS);
        }
    }

    /**
     * @param mb the whole mB
     * @return the price of that many mB
     */
    public static AbilityPrice flat(int mb) {
        return new AbilityPrice(Expr.literal(mb), null);
    }

    /**
     * @param amount the number or the expression the JSON wrote
     * @param type   the ability's goo type
     * @return the price, typed only where the amount names an item
     */
    public static AbilityPrice of(Expr amount, ResourceKey<GooTypeDefinition> type) {
        return new AbilityPrice(amount, amount.variables().isEmpty() ? null : type);
    }

    /**
     * Accepts a number that is one whole, non-negative mB, or an expression whose every name is an item id, and
     * refuses any other, naming what it read.
     *
     * @param amount the amount as the JSON wrote it
     * @return the amount, or an error naming it
     */
    public static DataResult<Expr> checked(Expr amount) {
        if (amount.source() == null) {
            double mb = amount.evaluate(Variables.NONE);
            return mb >= 0 && mb <= Integer.MAX_VALUE && mb == Math.rint(mb) ? DataResult.success(amount)
                    : DataResult.error(() -> NOT_A_FLAT_COST.formatted(mb));
        }
        return amount.variables().stream().filter(name -> !HostVariables.isNamespacedId(name)
                        && !HostVariables.isPrefab(name)).findFirst()
                .<DataResult<Expr>>map(name -> DataResult.error(() -> NAMES_NO_ITEM.formatted(name)))
                .orElseGet(() -> DataResult.success(amount));
    }

    /**
     * @param values the goo values the items read
     * @return the whole mB the price comes to, nearest, never below zero
     */
    public int in(IGooValueLookup values) {
        double mb = amount.evaluate(type == null ? Variables.NONE : itemValues(values, type));
        return (int) Math.max(0, Math.round(mb));
    }

    /**
     * @param values the goo values the items read, asked for only where the price names an item
     * @return the whole mB the price comes to, nearest, never below zero
     */
    public int in(Supplier<? extends IGooValueLookup> values) {
        return in(type == null ? GooValueTable.EMPTY : values.get());
    }

    /**
     * @return whether the price is a number of zero, costing nothing whatever the values
     */
    public boolean isFree() {
        return isNothing(amount);
    }

    /**
     * @param amount a number or an expression
     * @return whether it is a number of zero
     */
    static boolean isNothing(Expr amount) {
        return amount.variables().isEmpty() && amount.evaluate(Variables.NONE) == 0;
    }

    /**
     * @return the text the sync payload carries, the expression as written or the whole number
     */
    public String source() {
        return amount.source() != null ? amount.source() : Long.toString(Math.round(amount.evaluate(Variables.NONE)));
    }

    /**
     * Reads each namespaced name as an item's value in one goo type, zero for an item valued in none of it, and each
     * $name as a prefab's: a scalar's number, a per-type prefab's amount of the type, zero for a name no prefab holds.
     *
     * @param values the goo values
     * @param type   the one type
     * @return the scope
     */
    static Variables itemValues(IGooValueLookup values, ResourceKey<GooTypeDefinition> type) {
        return name -> {
            if (HostVariables.isPrefab(name)) {
                GooFraction prefab = values.prefabs().in(HostVariables.prefabName(name), type);
                return OptionalDouble.of(prefab == null ? 0 : prefab.toDouble());
            }
            Identifier item = HostVariables.isNamespacedId(name) ? Identifier.tryParse(name) : null;
            if (item == null) {
                return OptionalDouble.empty();
            }
            GooValue value = values.lookup(item);
            return OptionalDouble.of(value == null ? 0 : value.exact(type).toDouble());
        };
    }
}
