package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonObject;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import org.jspecify.annotations.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An ability's cost between the ability's JSON and the builder: the JSON's expression, each item id and $prefab read
 * at its value in the ability's own goo type, shows as a by-type value of that one type, its items and per-type
 * prefabs naming the type as base_values spells it; the builder's text writes back as the expression, each product
 * spelled with its * and the type dropped (decision ability-cost-builds-as-a-one-type-by-type-value).
 */
final class AbilityCosts {
    // An item id or a $prefab, as the cost expression spells it, or a decimal number, which the builder reads as the
    // fraction it writes.
    private static final Pattern REFERENT = Pattern.compile(
            "\\$[A-Za-z0-9_]+|[A-Za-z_][A-Za-z0-9_]*:[A-Za-z0-9_/]+|\\d+\\.\\d+");
    private static final String FRACTION_BAR = "/";
    private static final char PREFAB_MARK = '$';
    private static final String TYPE_MARK = ".";
    private static final String TIMES = " * ";
    private static final String DIVIDE = " / ";
    private static final String OPEN = "(";
    private static final String CLOSE = ")";
    private static final String MINUS = "-";
    private static final String HOLDS_NO = " holds no ";
    private static final String NO_TEXT = "";

    private AbilityCosts() {
    }

    /**
     * @param source         the cost as the ability's JSON writes it, a number or an expression
     * @param type           the ability's goo type id
     * @param perTypePrefabs the prefabs holding an amount per goo type, which name the type; a scalar prefab reads
     *                       its one number
     * @return the cost as the builder reads it, one row of the type
     */
    static String shown(String source, String type, Set<String> perTypePrefabs) {
        Matcher referents = REFERENT.matcher(source);
        StringBuilder typed = new StringBuilder();
        while (referents.find()) {
            String referent = referents.group();
            referents.appendReplacement(typed, Matcher.quoteReplacement(typedReferent(referent, type, perTypePrefabs)));
        }
        referents.appendTail(typed);
        JsonObject row = new JsonObject();
        row.addProperty(type, typed.toString());
        return row.toString();
    }

    // A referent naming the type, a scalar prefab as it is, and a decimal as its fraction: 12.5 reads 25/2.
    private static String typedReferent(String referent, String type, Set<String> perTypePrefabs) {
        if (Character.isDigit(referent.charAt(0))) {
            BigDecimal decimal = new BigDecimal(referent).stripTrailingZeros();
            int places = Math.max(0, decimal.scale());
            BigInteger numerator = decimal.movePointRight(places).toBigIntegerExact();
            BigInteger denominator = BigInteger.TEN.pow(places);
            BigInteger common = numerator.gcd(denominator);
            BigInteger reducedDenominator = denominator.divide(common);
            return reducedDenominator.equals(BigInteger.ONE) ? numerator.divide(common).toString()
                    : numerator.divide(common) + FRACTION_BAR + reducedDenominator;
        }
        boolean prefab = referent.charAt(0) == PREFAB_MARK;
        return prefab && !perTypePrefabs.contains(referent.substring(1)) ? referent : referent + TYPE_MARK + type;
    }

    /**
     * @param written what the builder wrote
     * @param type    the ability's goo type id
     * @return the cost expression the ability's JSON reads, empty where the builder holds no row of the type alone
     *     or a referent of another type
     */
    static Optional<String> written(String written, String type) {
        Optional<ExpressionTerms.Value> value = ExpressionTerms.parseShown(written);
        if (value.isEmpty() || !(value.get() instanceof ExpressionTerms.PerType perType)
                || perType.rows().size() != 1 || !perType.rows().getFirst().type().equals(type)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sumText(perType.rows().getFirst().sum(), type));
    }

    // The sum as the cost expression spells it, null where a referent reads another type.
    private static @Nullable String sumText(ExpressionTerms.Sum sum, String type) {
        StringBuilder text = new StringBuilder();
        for (ExpressionTerms.Term term : sum.terms()) {
            text.append(term.join().gap());
            for (ExpressionTerms.Factor factor : term.factors()) {
                String atom = atomText(factor.atom(), type);
                if (atom == null) {
                    return null;
                }
                text.append(gapOf(factor.scale())).append(atom);
            }
        }
        return text.toString();
    }

    private static String gapOf(ExpressionTerms.Scale scale) {
        return switch (scale) {
            case FIRST -> NO_TEXT;
            case IMPLICIT, TIMES -> TIMES;
            case DIVIDE -> DIVIDE;
        };
    }

    private static @Nullable String atomText(ExpressionTerms.Atom atom, String type) {
        return switch (atom) {
            case ExpressionTerms.Number number -> number.digits();
            case ExpressionTerms.Item item -> referentText(item.id(), item.type(), type);
            case ExpressionTerms.Prefab prefab -> referentText(PREFAB_MARK + prefab.name(), prefab.type(), type);
            case ExpressionTerms.Group group -> around(OPEN, sumText(group.sum(), type), CLOSE);
            case ExpressionTerms.Negated negated -> around(MINUS, atomText(negated.atom(), type), NO_TEXT);
        };
    }

    // A referent reads the ability's type alone: where it names another, the cost writes nothing.
    private static @Nullable String referentText(String spelled, @Nullable String read, String type) {
        return read == null || read.equals(type) ? spelled : null;
    }

    private static @Nullable String around(String before, @Nullable String inner, String after) {
        return inner == null ? null : before + inner + after;
    }

    /**
     * @param source the cost expression
     * @param type   the ability's goo type id
     * @param worth  each item's or prefab's resolved amounts by goo type, empty where none resolves
     * @return one line for each item or per-type prefab the cost reads that holds none of the type, as "stem holds no
     *     leaf" reads where a value reads it; a referent resolving to nothing names nothing the manager can know
     */
    static List<String> lacking(String source, String type, Function<ExpressionBuilder.Ref, Map<String, String>> worth) {
        List<String> lines = new ArrayList<>();
        Expr.parse(source).result().ifPresent(cost -> {
            for (String name : new TreeSet<>(cost.variables())) {
                ExpressionBuilder.Ref ref = HostVariables.isPrefab(name)
                        ? new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, HostVariables.prefabName(name), type)
                        : new ExpressionBuilder.Ref(SearchDropdown.Kind.ITEM, name, type);
                Map<String, String> held = worth.apply(ref);
                if (!held.isEmpty() && !held.containsKey(type)) {
                    lines.add(name + HOLDS_NO + type);
                }
            }
        });
        return lines;
    }
}
