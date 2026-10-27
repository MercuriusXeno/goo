package com.mercuriusxeno.goo.client.workbench;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the expression builder offers where a value is read, so every choice it shows is one the game's evaluator
 * accepts and none ends in an error (decision expression-builder-composes-every-form-without-typing). The game reads
 * a prefab before any item and each prefab only after the prefabs above it, so a prefab lists only those and no
 * item. A whole value is one of two sorts: a number, summing numbers and number prefabs, or a goo value, summing
 * items and per-type prefabs, which the game cannot add a bare number to, so the number sort is a prefab's alone.
 * A number stands unscaled, a reference offers a factor, and a factor is never 0.
 */
final class BuilderGrammar {
    /** A whole value summing numbers and number prefabs. */
    static final String NUMBER_SORT = ExpressionBuilder.NUMBER_SORT;
    /** A whole value summing items and per-type prefabs. */
    static final String GOO_SORT = "goo";
    private static final Pattern DIGITS = Pattern.compile("\\d{0,9}");
    private static final Pattern ZERO = Pattern.compile("0+");

    private final boolean prefab;
    private final List<Prefab> readable;

    /**
     * One prefab the value may read.
     *
     * @param name    its name
     * @param perType whether it holds an amount per goo type rather than one number
     */
    record Prefab(String name, boolean perType) {
    }

    /**
     * How a term's factor shows.
     */
    enum Factor {
        /** A number with no factor shows none. */
        HIDDEN,
        /** A reference or a sum with no factor draws the ratio 1:1. */
        ONE_TO_ONE,
        /** A factor other than 1 shows with its operation. */
        SHOWN
    }

    private BuilderGrammar(boolean prefab, List<Prefab> readable) {
        this.prefab = prefab;
        this.readable = List.copyOf(readable);
    }

    /**
     * @param prefabs every prefab, in the file's order
     * @return the grammar of an item's or group's value, which reads every prefab and items
     */
    static BuilderGrammar forItems(List<Prefab> prefabs) {
        return new BuilderGrammar(false, prefabs);
    }

    /**
     * @param prefabs every prefab, in the file's order
     * @param edited  the prefab whose value is edited
     * @return the grammar of that prefab's value, which reads only the prefabs above it
     */
    static BuilderGrammar forPrefab(List<Prefab> prefabs, String edited) {
        int above = prefabs.stream().map(Prefab::name).toList().indexOf(edited);
        return new BuilderGrammar(true, above < 0 ? prefabs : prefabs.subList(0, above));
    }

    /**
     * @return whether the value reads items
     */
    boolean readsItems() {
        return !prefab;
    }

    /**
     * @return the number prefabs the value may read
     */
    List<String> scalarPrefabs() {
        return readable.stream().filter(each -> !each.perType()).map(Prefab::name).toList();
    }

    /**
     * @return the per-type prefabs the value may read
     */
    List<String> perTypePrefabs() {
        return readable.stream().filter(Prefab::perType).map(Prefab::name).toList();
    }

    /**
     * @return whether the value may be one number, which only a prefab may
     */
    boolean offersNumberKind() {
        return prefab;
    }

    /**
     * @return whether the value may be a goo value summed from others, which a prefab may only where a per-type
     *         prefab stands above it
     */
    boolean offersGooKind() {
        return !prefab || !perTypePrefabs().isEmpty();
    }

    /**
     * @param builder the value
     * @return its whole terms' sort: a goo value where it reads an item or a per-type prefab, a number otherwise
     */
    String sortOf(ExpressionBuilder builder) {
        if (!prefab) {
            return GOO_SORT;
        }
        Set<String> perType = Set.copyOf(perTypePrefabs());
        return readsPerType(builder.terms(), perType) ? GOO_SORT : NUMBER_SORT;
    }

    private static boolean readsPerType(List<ExpressionBuilder.Piece> pieces, Set<String> perType) {
        return pieces.stream().anyMatch(piece -> switch (piece.operand()) {
            case ExpressionBuilder.Ref ref -> ref.kind() == SearchDropdown.Kind.ITEM || perType.contains(ref.id());
            case ExpressionBuilder.Nest nest -> readsPerType(nest.pieces(), perType);
            default -> false;
        });
    }

    /**
     * @param sort the whole terms' sort
     * @return whether a term may be a bare number
     */
    boolean termTakesNumbers(String sort) {
        return NUMBER_SORT.equals(sort);
    }

    /**
     * @param sort the whole terms' sort
     * @return the prefabs a term may relate to
     */
    List<String> termPrefabs(String sort) {
        return NUMBER_SORT.equals(sort) ? scalarPrefabs() : perTypePrefabs();
    }

    /**
     * @param sort the whole terms' sort
     * @return whether a term may relate to an item
     */
    boolean termTakesItems(String sort) {
        return GOO_SORT.equals(sort) && readsItems();
    }

    /**
     * @param sort the whole terms' sort
     * @return whether a term may take one goo type of what it relates to, which only a goo value's term may
     */
    boolean memberOptional(String sort) {
        return GOO_SORT.equals(sort);
    }

    /**
     * @param builder the value
     * @return the goo types its rows hold, which the picker hides
     */
    static Set<String> heldTypes(ExpressionBuilder builder) {
        Set<String> held = new HashSet<>();
        builder.rows().forEach(row -> held.add(row.type()));
        return Set.copyOf(held);
    }

    /**
     * @param piece a term
     * @return how its factor shows
     */
    static Factor factorOf(ExpressionBuilder.Piece piece) {
        if (!ExpressionBuilder.ONE_TEXT.equals(piece.factor())) {
            return Factor.SHOWN;
        }
        return piece.operand() instanceof ExpressionBuilder.Ref || piece.operand() instanceof ExpressionBuilder.Nest
                ? Factor.ONE_TO_ONE : Factor.HIDDEN;
    }

    /**
     * @param typed  what the number box holds
     * @param factor whether the box holds a factor, which is never 0
     * @return whether the box takes it: digits alone, and no zero factor
     */
    static boolean acceptsTyped(String typed, boolean factor) {
        return DIGITS.matcher(typed).matches() && !(factor && ZERO.matcher(typed).matches());
    }
}
