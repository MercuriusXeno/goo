package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.ExprNode;
import com.mercuriusxeno.goo.ability.program.ExprOperator;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalDouble;
import java.util.function.Function;
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

    private static final String NOT_A_COST = "Ability cost must be an amount of 0 or more, not %s";
    // The most decimal places a number reads exactly at; a longer decimal rounds to this many.
    private static final int DECIMAL_PLACES = 9;
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
     * Accepts a number of non-negative mB, whole or not, or an expression whose every name is an item id, and
     * refuses any other, naming what it read.
     *
     * @param amount the amount as the JSON wrote it
     * @return the amount, or an error naming it
     */
    public static DataResult<Expr> checked(Expr amount) {
        if (amount.source() == null) {
            double mb = amount.evaluate(Variables.NONE);
            return mb >= 0 && mb <= Integer.MAX_VALUE ? DataResult.success(amount)
                    : DataResult.error(() -> NOT_A_COST.formatted(mb));
        }
        return amount.variables().stream().filter(name -> !HostVariables.isNamespacedId(name)
                        && !HostVariables.isPrefab(name)).findFirst()
                .<DataResult<Expr>>map(name -> DataResult.error(() -> NAMES_NO_ITEM.formatted(name)))
                .orElseGet(() -> DataResult.success(amount));
    }

    /**
     * @param values the goo values the items read
     * @return the most whole mB a use may take, the exact price rounded up, whatever remainder the payer holds
     */
    public int in(IGooValueLookup values) {
        return exactIn(values).ceiling();
    }

    /**
     * The price as the exact fraction its numbers and items come to, each item and prefab read at its exact value,
     * a decimal reading as the fraction it writes and a function's answer as its decimal.
     * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
     *
     * @param values the goo values the items read
     * @return the exact mB the price comes to, never below zero
     */
    public GooFraction exactIn(IGooValueLookup values) {
        GooFraction exact = exact(amount.root(), name -> type == null ? GooFraction.ZERO : valueOf(name, values, type));
        return exact.isNegative() ? GooFraction.ZERO : exact;
    }

    /**
     * @param values the goo values the items read, asked for only where the price names an item
     * @return the exact mB the price comes to, never below zero
     */
    public GooFraction exactIn(Supplier<? extends IGooValueLookup> values) {
        return exactIn(type == null ? GooValueTable.EMPTY : values.get());
    }

    /**
     * @param values the goo values the items read, asked for only where the price names an item
     * @return the most whole mB a use may take, the exact price rounded up
     */
    public int in(Supplier<? extends IGooValueLookup> values) {
        return in(type == null ? GooValueTable.EMPTY : values.get());
    }

    private static GooFraction exact(ExprNode node, Function<String, GooFraction> names) {
        return switch (node) {
            case ExprNode.Literal literal -> fractionOf(literal.value());
            case ExprNode.Variable variable -> names.apply(variable.name());
            case ExprNode.Negate negate -> exact(negate.inner(), names).negated();
            case ExprNode.Binary binary -> combined(binary.op(), exact(binary.left(), names),
                    exact(binary.right(), names));
            case ExprNode.Call call -> fractionOf(call.evaluate(name -> OptionalDouble.of(names.apply(name).toDouble())));
        };
    }

    private static GooFraction combined(ExprOperator op, GooFraction left, GooFraction right) {
        return switch (op) {
            case ADD -> left.plus(right);
            case SUBTRACT -> left.minus(right);
            case MULTIPLY -> left.times(right);
            case DIVIDE -> right.isZero() ? GooFraction.ZERO : left.dividedBy(right);
        };
    }

    // A number as the fraction its decimal writes: 12.5 is 25/2.
    private static GooFraction fractionOf(double number) {
        if (!Double.isFinite(number)) {
            return GooFraction.ZERO;
        }
        BigDecimal decimal = BigDecimal.valueOf(number);
        if (decimal.scale() > DECIMAL_PLACES) {
            decimal = decimal.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
        }
        decimal = decimal.stripTrailingZeros();
        int places = Math.max(0, decimal.scale());
        return GooFraction.of(decimal.movePointRight(places).longValueExact(), BigDecimal.TEN.pow(places).longValueExact());
    }

    // An item's or a prefab's exact value in the one type, zero for a name neither holds.
    private static GooFraction valueOf(String name, IGooValueLookup values, ResourceKey<GooTypeDefinition> type) {
        if (HostVariables.isPrefab(name)) {
            GooFraction prefab = values.prefabs().in(HostVariables.prefabName(name), type);
            return prefab == null ? GooFraction.ZERO : prefab;
        }
        Identifier item = HostVariables.isNamespacedId(name) ? Identifier.tryParse(name) : null;
        GooValue value = item == null ? null : values.lookup(item);
        return value == null ? GooFraction.ZERO : value.exact(type);
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
     * @return the text the sync payload carries, the expression as written or the number, a fraction as its decimal
     */
    public String source() {
        return amount.source() != null ? amount.source()
                : BigDecimal.valueOf(amount.evaluate(Variables.NONE)).stripTrailingZeros().toPlainString();
    }
}
