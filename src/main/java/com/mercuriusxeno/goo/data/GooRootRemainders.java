package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Each root's solved items and every recipe's remainder at the root: the part of a recipe's value per output,
 * read in units of the root's own goo type, that falls under one whole root. 6 glass into 16 panes leaves 3/8
 * of a glass per pane. The audit section and the workbench's Values tab both read this one computation.
 * decision workbench-shows-each-roots-remainders-and-items-solved
 */
public final class GooRootRemainders {
    /**
     * Non-dividing recipes first, the smallest remainder first, whole-dividing recipes after; a tie falls to
     * the output's id, so the order reads the same every run.
     */
    private static final Comparator<Row> ROW_ORDER = Comparator
            .comparing((Row row) -> row.remainder().isZero())
            .thenComparing(Row::remainder)
            .thenComparing(row -> row.recipe().output());

    private GooRootRemainders() {
    }

    /**
     * One recipe among a root's solved items, read at the root.
     *
     * @param recipe      the recipe, its slots narrowed to the alternatives that set the value
     * @param inputTotal  the recipe's input value in roots
     * @param outputCount the items one craft makes
     * @param perOutput   each output's value in roots
     * @param remainder   the part of each output's value under one whole root
     */
    public record Row(RecipeInput recipe, GooFraction inputTotal, int outputCount, GooFraction perOutput,
                      GooFraction remainder) {
    }

    /**
     * One root and what it solves.
     *
     * @param root   the item _roots names
     * @param solved every item whose derivation reaches the root, sorted by id
     * @param rows   every recipe that values a solved item, non-dividing first, smallest remainder first
     */
    public record RootSolution(Identifier root, List<Identifier> solved, List<Row> rows) {

        /**
         * @return the smallest nonzero remainder among the rows, zero where every recipe divides whole
         */
        public GooFraction smallestRemainder() {
            return rows.isEmpty() ? GooFraction.ZERO : rows.getFirst().remainder();
        }
    }

    /**
     * @param values a registry after derivation
     * @return each root its _roots names, in file order, with what it solves; a root holding no value is left out
     */
    public static List<RootSolution> solveEachRoot(GooValueRegistry values) {
        return solveEachRoot(values.rootItems(), values.handKeyedItems(), values.valueSettingRecipeByItem(),
                values.table().getEffectiveValues());
    }

    /**
     * @param roots     the roots, in order
     * @param handKeyed the items valued by hand, where a walk to a root stops
     * @param sources   each derived item's value-setting recipe
     * @param values    each item's effective value
     * @return each root with what it solves; a root holding no value is left out
     */
    static List<RootSolution> solveEachRoot(List<Identifier> roots, Set<Identifier> handKeyed,
                                            Map<Identifier, RecipeInput> sources, Map<Identifier, GooValue> values) {
        Map<Identifier, Set<Identifier>> solvedFrom = itemsSolvedFrom(handKeyed, sources);
        List<RootSolution> solutions = new ArrayList<>();
        for (Identifier root : roots) {
            GooValue rootValue = values.get(root);
            if (rootValue == null || rootValue.isEmpty()) {
                continue;
            }
            Set<Identifier> solved = new TreeSet<>(reachedFrom(root, solvedFrom));
            solutions.add(new RootSolution(root, List.copyOf(solved), rows(rootValue, solved, sources, values)));
        }
        return solutions;
    }

    /**
     * @param handKeyed the items valued by hand, which no recipe solves
     * @param sources   each derived item's value-setting recipe
     * @return for each item, the derived items whose value-setting recipe reads it
     */
    private static Map<Identifier, Set<Identifier>> itemsSolvedFrom(Set<Identifier> handKeyed,
                                                                    Map<Identifier, RecipeInput> sources) {
        Map<Identifier, Set<Identifier>> solvedFrom = new HashMap<>();
        sources.forEach((item, recipe) -> {
            if (handKeyed.contains(item)) {
                return;
            }
            Set<Identifier> read = new HashSet<>();
            read.add(recipe.output());
            recipe.ingredientAlternatives().forEach(read::addAll);
            read.remove(item);
            read.forEach(from -> solvedFrom.computeIfAbsent(from, key -> new HashSet<>()).add(item));
        });
        return solvedFrom;
    }

    private static Set<Identifier> reachedFrom(Identifier root, Map<Identifier, Set<Identifier>> solvedFrom) {
        Set<Identifier> reached = new HashSet<>();
        Deque<Identifier> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty()) {
            for (Identifier next : solvedFrom.getOrDefault(queue.pop(), Set.of())) {
                if (!next.equals(root) && reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        return reached;
    }

    private static List<Row> rows(GooValue rootValue, Set<Identifier> solved, Map<Identifier, RecipeInput> sources,
                                  Map<Identifier, GooValue> values) {
        ResourceKey<GooTypeDefinition> type = rootValue.largestType();
        GooFraction unit = rootValue.exact(type);
        Set<RecipeInput> recipes = new LinkedHashSet<>();
        solved.forEach(item -> recipes.add(sources.get(item)));
        List<Row> rows = new ArrayList<>();
        for (RecipeInput recipe : recipes) {
            GooValue output = values.get(recipe.output());
            GooFraction each = output == null ? GooFraction.ZERO : inRoots(output.exact(type), unit);
            rows.add(new Row(recipe, each.times(recipe.resultCount()), recipe.resultCount(), each,
                    each.minus(GooFraction.of(each.floor()))));
        }
        rows.sort(ROW_ORDER);
        return List.copyOf(rows);
    }

    private static GooFraction inRoots(GooFraction share, GooFraction unit) {
        return GooFraction.of(Math.multiplyExact(share.numerator(), unit.denominator()),
                Math.multiplyExact(share.denominator(), unit.numerator()));
    }

}
