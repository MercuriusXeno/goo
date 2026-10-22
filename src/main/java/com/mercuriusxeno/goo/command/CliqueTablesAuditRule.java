package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * The audit's clique tables: each goo type's items grouped into cliques joined by the winning recipes and,
 * per clique, how many recipes melt to a fraction when the clique's lowest item is worth 1, 2, 3, 4, 6, 8,
 * 12, 24 or 60; each clique losing goo at worth 1 is a flag (decision each-check-is-one-rule-added-alone).
 */
@AuditRule(order = 100)
public final class CliqueTablesAuditRule implements GooAuditRule {
    /**
     * The section's name, which its header leads with.
     */
    public static final String SECTION = "CLIQUES";
    /**
     * The clique dump file's own title.
     */
    public static final String TITLE = "CLIQUES AND LOSSY RECIPES";
    private static final long[] ROOT_WORTHS = {1, 2, 3, 4, 6, 8, 12, 24, 60};
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String INDENT = "  ";
    private static final String BLANK = "";
    private static final String SPACE = " ";
    private static final String GAP = "  ";
    private static final String HEADER_SUFFIX = " types)";
    private static final String HEADER_OPEN = " (";
    private static final String TYPE_UNDERLINE = "=";
    private static final String RULE_DASH = "-";
    private static final String CLIQUE_PREFIX = "Clique: ";
    private static final String CLIQUE_SIZE_ITEMS = " items, ";
    private static final String CLIQUE_SIZE_RECIPES = " recipes)";
    private static final String ROOT_PREFIX = "Root: ";
    private static final String CANDIDATE_ALL = "Candidates: every recipe melts whole at ";
    private static final String CANDIDATE_WORTH = " worth ";
    private static final String CANDIDATE_ONE_OUTPUT = "; every recipe making one item per craft at worth ";
    private static final String UNROOTED_NOTE =
            "Cliques with no root named; name one of each clique's items in _roots to work it from that item:";
    private static final String MADE_BY_NONE = ", made by no recipe";
    private static final String MADE_BY = ", made by ";
    private static final String WORTH_PREFIX = "If ";
    private static final String WORTH_SUFFIX = " is worth";
    private static final String TABLE_COLUMN = " | Recipes that lose goo on melt";
    private static final String TABLE_RULE = "-+------------------------------";
    private static final String TABLE_CELL = " | ";
    private static final String OF = " of ";
    private static final String LOSSLESS_ITEMS = " items";
    private static final String LOSSLESS_SEPARATOR = ", ";
    private static final String NO_LOSS_PREFIX = "No recipe loses goo when ";
    private static final String NO_LOSS_SUFFIX = " is worth 1.";
    private static final String LOSS_PREFIX = "Recipes that lose goo when ";
    private static final String LOSS_SUFFIX = " is worth 1:";
    private static final String COLUMN_RECIPE = "Recipe";
    private static final String COLUMN_EACH = "Each";
    private static final String COLUMN_MELTS = "Melts";
    private static final String COLUMN_LOST_EACH = "Lost each";
    private static final String COLUMN_LOST_PER_CRAFT = "  Lost per craft";
    private static final String RECIPE_JOIN = " + ";
    private static final String RECIPE_ARROW = " -> ";
    private static final int RECIPE_COLUMN_AT_LEAST = 6;
    private static final int EACH_WIDTH = 8;
    private static final int MELTS_WIDTH = 6;
    private static final int LOST_EACH_WIDTH = 10;
    private static final int LOST_PER_CRAFT_WIDTH = 14;
    private static final List<String> LOSSLESS_NOTE = List.of(
            "Cliques where no recipe loses goo with the lowest item worth 1, so none loses goo at any",
            "whole value of the lowest item either (lowest item, then the clique's size):");
    private static final List<String> PREAMBLE = List.of(
            "A clique is a group of items linked by recipes that carry one goo type (the iron family for metal).",
            "Its root is the item _roots names in it. Each table asks: if the root is worth N, how many of the",
            "clique's recipes make an item worth a fraction, which melting rounds down and loses? The candidates",
            "are the smallest root worths at which every recipe melts whole. The recipe list is for the root worth 1.",
            "Values come from the recipes alone, starting from items no recipe makes.");

    /**
     * A winning recipe as the clique walk reads it: the output count less any slot returning the output, and
     * one valued pick per remaining slot.
     */
    private record WalkedRecipe(RecipeInput recipe, int count, List<Identifier> picks) {
    }

    /**
     * One recipe losing goo with the lowest item worth 1.
     */
    private record LossyRecipe(GooFraction lostPerCraft, String text, String each, String melts, String lostEach) {
    }

    /**
     * One clique of one goo type, worked from the root _roots names in it
     * (decision audit-proposes-root-candidates-and-flags-bleed).
     *
     * @param type             the goo type the clique's recipes carry
     * @param root             the named root the clique is worked from, or null where _roots names none of it
     * @param items            the clique's items
     * @param recipes          the clique's recipe count
     * @param allWholeAt       the smallest worth of the root at which every recipe melts whole, 0 with no root
     * @param oneOutputWholeAt the smallest at which every recipe making one item per craft does, 0 with no root
     */
    public record Family(ResourceKey<GooTypeDefinition> type, @Nullable Identifier root, List<Identifier> items,
                         int recipes, long allWholeAt, long oneOutputWholeAt) {
    }

    /**
     * What one clique is worked against: its recipe-only values, its recipes and its root.
     */
    private record Clique(ResourceKey<GooTypeDefinition> type, List<Identifier> items, List<WalkedRecipe> recipes,
                          @Nullable Identifier root, Map<Identifier, GooValue> valued) {
    }

    /**
     * @param inputs what the audit reads
     * @return every clique of every goo type with its root and its candidate root values
     */
    public static List<Family> families(GooAuditInputs inputs) {
        List<Family> families = new ArrayList<>();
        for (ResourceKey<GooTypeDefinition> type : GooTypes.BUNDLED) {
            for (Clique clique : cliques(inputs, type)) {
                long all = clique.root() == null ? 0 : wholeAt(clique, clique.recipes());
                long oneOutput = clique.root() == null ? 0 : wholeAt(clique,
                        clique.recipes().stream().filter(walked -> walked.count() == 1).toList());
                families.add(new Family(type, clique.root(), clique.items(), clique.recipes().size(), all, oneOutput));
            }
        }
        return families;
    }

    private static List<Clique> cliques(GooAuditInputs inputs, ResourceKey<GooTypeDefinition> type) {
        GooValueRegistry values = inputs.values();
        Map<Identifier, GooValue> effective = values.table().getEffectiveValues();
        List<WalkedRecipe> recipes = walkedRecipes(values, effective);
        Map<Identifier, WalkedRecipe> byOutput = new HashMap<>();
        recipes.forEach(walked -> byOutput.put(walked.recipe().output(), walked));
        return cliquesOf(type, recipes, recipeOnlyValues(recipes, byOutput, effective), values.rootItems());
    }

    private static List<Clique> cliquesOf(ResourceKey<GooTypeDefinition> type, List<WalkedRecipe> recipes,
                                          Map<Identifier, GooValue> valued, List<Identifier> roots) {
        Map<Identifier, Identifier> parent = new HashMap<>();
        List<WalkedRecipe> carrying = carryingRecipes(type, recipes, valued, parent);
        Map<Identifier, List<Identifier>> grouped = new HashMap<>();
        for (Identifier item : new ArrayList<>(parent.keySet())) {
            grouped.computeIfAbsent(GooItemUnionFind.find(parent, item), key -> new ArrayList<>()).add(item);
        }
        List<List<Identifier>> bySize = new ArrayList<>(grouped.values());
        // Largest first; a tie falls to the smallest item id, so the dump reads the same every run.
        bySize.sort(Comparator.comparingInt((List<Identifier> clique) -> -clique.size())
                .thenComparing(clique -> clique.stream().min(Comparator.naturalOrder()).orElseThrow()));
        List<Clique> cliques = new ArrayList<>();
        for (List<Identifier> items : bySize) {
            Identifier cliqueRoot = GooItemUnionFind.find(parent, items.getFirst());
            List<WalkedRecipe> inClique = carrying.stream()
                    .filter(walked -> GooItemUnionFind.find(parent, walked.recipe().output()).equals(cliqueRoot)).toList();
            Identifier named = roots.stream().filter(items::contains).findFirst().orElse(null);
            cliques.add(new Clique(type, items, inClique, named, valued));
        }
        return cliques;
    }

    /**
     * The smallest worth of the root at which every one of the recipes' outputs is whole: the least common
     * multiple of the denominators each output's share reads at a root worth 1.
     *
     * @param clique  the clique, its root named
     * @param recipes the recipes to make whole
     * @return the smallest whole worth of the root
     */
    private static long wholeAt(Clique clique, List<WalkedRecipe> recipes) {
        GooFraction unit = share(clique.valued(), clique.root(), clique.type());
        long whole = 1;
        for (WalkedRecipe walked : recipes) {
            GooFraction each = scaled(share(clique.valued(), walked.recipe().output(), clique.type()), unit, 1);
            whole = leastCommonMultiple(whole, each.denominator());
        }
        return whole;
    }

    private static long leastCommonMultiple(long first, long second) {
        long a = first;
        long b = second;
        while (b != 0) {
            long rest = a % b;
            a = b;
            b = rest;
        }
        return Math.multiplyExact(first / a, second);
    }

    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        List<String> lines = new ArrayList<>(PREAMBLE);
        lines.add(BLANK);
        int typesWithCliques = 0;
        for (ResourceKey<GooTypeDefinition> type : GooTypes.BUNDLED) {
            if (writeType(cliques(inputs, type), lines)) {
                typesWithCliques++;
            }
        }
        List<String> flags = lines.stream().filter(line -> line.startsWith(CLIQUE_PREFIX)).toList();
        return new GooAuditSection(SECTION, SECTION + HEADER_OPEN + typesWithCliques + HEADER_SUFFIX,
                List.copyOf(lines), 0, flags);
    }

    private static List<WalkedRecipe> walkedRecipes(GooValueRegistry values, Map<Identifier, GooValue> effective) {
        Set<Identifier> denied = values.deniedItems();
        List<WalkedRecipe> recipes = new ArrayList<>();
        for (RecipeInput recipe : values.valueSettingRecipes()) {
            if (!denied.contains(recipe.output())) {
                walk(recipe, effective).ifPresent(recipes::add);
            }
        }
        return recipes;
    }

    private static Optional<WalkedRecipe> walk(RecipeInput recipe, Map<Identifier, GooValue> effective) {
        int count = recipe.resultCount();
        List<Identifier> picks = new ArrayList<>();
        for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
            if (slot.contains(recipe.output())) {
                count--;
                continue;
            }
            Optional<Identifier> pick = slot.stream().filter(effective::containsKey).sorted().findFirst();
            if (pick.isEmpty()) {
                return Optional.empty();
            }
            picks.add(pick.get());
        }
        return count > 0 && !picks.isEmpty() ? Optional.of(new WalkedRecipe(recipe, count, picks)) : Optional.empty();
    }

    /**
     * Values every recipe output from the recipes alone, seeded with the items no recipe makes and, where a
     * cycle stalls the walk, with the most valuable unvalued input.
     *
     * @param recipes   the walked recipes
     * @param byOutput  each output's walked recipe
     * @param effective the values the server derived, which seed the walk
     * @return each item's value from the recipes alone
     */
    private static Map<Identifier, GooValue> recipeOnlyValues(List<WalkedRecipe> recipes,
                                                              Map<Identifier, WalkedRecipe> byOutput,
                                                              Map<Identifier, GooValue> effective) {
        Map<Identifier, GooValue> valued = new HashMap<>();
        Set<Identifier> starting = new HashSet<>();
        recipes.forEach(walked -> {
            starting.addAll(walked.picks());
            starting.addAll(walked.recipe().containerItems().values());
        });
        starting.removeAll(byOutput.keySet());
        starting.stream().filter(effective::containsKey).forEach(item -> valued.put(item, effective.get(item)));
        Identifier seed;
        do {
            deriveUntilStalled(recipes, valued);
            seed = cycleSeed(recipes, valued, effective);
            if (seed != null) {
                valued.put(seed, effective.get(seed));
            }
        } while (seed != null);
        return valued;
    }

    private static void deriveUntilStalled(List<WalkedRecipe> recipes, Map<Identifier, GooValue> valued) {
        boolean progressed = true;
        while (progressed) {
            progressed = false;
            for (WalkedRecipe walked : recipes) {
                if (!valued.containsKey(walked.recipe().output()) && deriveOutput(walked, valued)) {
                    progressed = true;
                }
            }
        }
    }

    private static Identifier cycleSeed(List<WalkedRecipe> recipes, Map<Identifier, GooValue> valued,
                                        Map<Identifier, GooValue> effective) {
        Identifier seed = null;
        for (WalkedRecipe walked : recipes) {
            if (valued.containsKey(walked.recipe().output())) {
                continue;
            }
            for (Identifier pick : walked.picks()) {
                if (!valued.containsKey(pick) && outweighs(pick, seed, effective)) {
                    seed = pick;
                }
            }
        }
        return seed;
    }

    private static boolean outweighs(Identifier pick, Identifier seed, Map<Identifier, GooValue> effective) {
        return effective.containsKey(pick)
                && (seed == null || effective.get(pick).totalExact().compareTo(effective.get(seed).totalExact()) > 0);
    }

    private static boolean deriveOutput(WalkedRecipe walked, Map<Identifier, GooValue> valued) {
        GooValue total = GooValue.EMPTY;
        for (Identifier pick : walked.picks()) {
            GooValue pickValue = valued.get(pick);
            if (pickValue == null) {
                return false;
            }
            Identifier container = walked.recipe().containerItems().get(pick);
            GooValue left = container == null ? null : valued.get(container);
            total = total.add(left == null ? pickValue : pickValue.subtract(left), 1);
        }
        valued.put(walked.recipe().output(), total.divide(walked.count()));
        return true;
    }

    private static GooFraction share(Map<Identifier, GooValue> valued, Identifier item,
                                     ResourceKey<GooTypeDefinition> type) {
        GooValue value = valued.get(item);
        return value == null ? GooFraction.ZERO : value.exact(type);
    }

    private static boolean writeType(List<Clique> cliques, List<String> out) {
        if (cliques.isEmpty()) {
            return false;
        }
        String title = GooTypes.id(cliques.getFirst().type()).toUpperCase(Locale.ROOT);
        out.add(title);
        out.add(TYPE_UNDERLINE.repeat(title.length()));
        out.add(BLANK);
        List<String> lossless = new ArrayList<>();
        List<String> unrooted = new ArrayList<>();
        for (Clique clique : cliques) {
            if (clique.root() == null) {
                unrooted.add(name(clique.items().stream().min(Comparator.naturalOrder()).orElseThrow())
                        + LOSSLESS_SEPARATOR + clique.items().size() + LOSSLESS_ITEMS);
            } else {
                writeClique(clique, out, lossless);
            }
        }
        writeLossless(lossless, out);
        writeUnrooted(unrooted, out);
        out.add(BLANK);
        return true;
    }

    private static void writeUnrooted(List<String> unrooted, List<String> out) {
        if (unrooted.isEmpty()) {
            return;
        }
        out.add(UNROOTED_NOTE);
        unrooted.forEach(line -> out.add(INDENT + line));
        out.add(BLANK);
    }

    private static void writeLossless(List<String> lossless, List<String> out) {
        if (lossless.isEmpty()) {
            return;
        }
        out.addAll(LOSSLESS_NOTE);
        lossless.forEach(line -> out.add(INDENT + line));
        out.add(BLANK);
    }

    private static List<WalkedRecipe> carryingRecipes(ResourceKey<GooTypeDefinition> type, List<WalkedRecipe> recipes,
                                                      Map<Identifier, GooValue> valued,
                                                      Map<Identifier, Identifier> parent) {
        List<WalkedRecipe> carrying = new ArrayList<>();
        for (WalkedRecipe walked : recipes) {
            Identifier output = walked.recipe().output();
            if (!share(valued, output, type).isPositive()) {
                continue;
            }
            boolean joined = false;
            for (Identifier pick : new HashSet<>(walked.picks())) {
                if (share(valued, pick, type).isPositive()) {
                    GooItemUnionFind.union(parent, output, pick);
                    joined = true;
                }
            }
            if (joined) {
                carrying.add(walked);
            }
        }
        return carrying;
    }

    private static void writeClique(Clique clique, List<String> out, List<String> lossless) {
        Identifier root = clique.root();
        GooFraction unit = share(clique.valued(), root, clique.type());
        long[] lossyAt = lossyCounts(clique, unit);
        if (lossyAt[0] == 0) {
            lossless.add(name(root) + LOSSLESS_SEPARATOR + clique.items().size() + LOSSLESS_ITEMS);
            return;
        }
        WalkedRecipe rootRecipe = clique.recipes().stream().filter(walked -> walked.recipe().output().equals(root))
                .findFirst().orElse(null);
        out.add(CLIQUE_PREFIX + name(root) + HEADER_OPEN + clique.items().size() + CLIQUE_SIZE_ITEMS
                + clique.recipes().size() + CLIQUE_SIZE_RECIPES);
        out.add(INDENT + ROOT_PREFIX + name(root)
                + (rootRecipe == null ? MADE_BY_NONE : MADE_BY + recipeText(rootRecipe)));
        out.add(INDENT + CANDIDATE_ALL + name(root) + CANDIDATE_WORTH + wholeAt(clique, clique.recipes())
                + CANDIDATE_ONE_OUTPUT + wholeAt(clique, clique.recipes().stream()
                .filter(walked -> walked.count() == 1).toList()));
        out.add(BLANK);
        writeWorthTable(name(root), lossyAt, clique.recipes().size(), out);
        writeLossyRecipes(clique, root, unit, out);
        out.add(BLANK);
        out.add(BLANK);
    }

    /**
     * How many of the clique's recipes melt to a fraction at each worth of the lowest item.
     *
     * @param clique the clique
     * @param unit   the root's share of the type
     * @return the lossy recipe count at each of ROOT_WORTHS
     */
    private static long[] lossyCounts(Clique clique, GooFraction unit) {
        long[] lossyAt = new long[ROOT_WORTHS.length];
        for (int i = 0; i < ROOT_WORTHS.length; i++) {
            long worth = ROOT_WORTHS[i];
            lossyAt[i] = clique.recipes().stream().filter(walked -> !scaled(share(clique.valued(),
                    walked.recipe().output(), clique.type()), unit, worth).isWhole()).count();
        }
        return lossyAt;
    }

    private static void writeWorthTable(String lowest, long[] lossyAt, int recipes, List<String> out) {
        String head = WORTH_PREFIX + lowest + WORTH_SUFFIX;
        out.add(INDENT + head + TABLE_COLUMN);
        out.add(INDENT + RULE_DASH.repeat(head.length()) + TABLE_RULE);
        for (int i = 0; i < ROOT_WORTHS.length; i++) {
            out.add(INDENT + padLeft(Long.toString(ROOT_WORTHS[i]), head.length()) + TABLE_CELL + lossyAt[i] + OF
                    + recipes);
        }
        out.add(BLANK);
    }

    private static List<LossyRecipe> lossyRecipes(Clique clique, GooFraction unit) {
        List<LossyRecipe> lossy = new ArrayList<>();
        for (WalkedRecipe walked : clique.recipes()) {
            GooFraction each = scaled(share(clique.valued(), walked.recipe().output(), clique.type()), unit, 1);
            if (!each.isWhole()) {
                GooFraction lostEach = each.minus(GooFraction.of(each.floor()));
                lossy.add(new LossyRecipe(lostEach.times(walked.count()), recipeText(walked), each.toString(),
                        Integer.toString(each.floor()), lostEach.toString()));
            }
        }
        lossy.sort(Comparator.comparing(LossyRecipe::lostPerCraft).reversed().thenComparing(LossyRecipe::text));
        return lossy;
    }

    private static void writeLossyRecipes(Clique clique, Identifier lowest, GooFraction unit, List<String> out) {
        List<LossyRecipe> lossy = lossyRecipes(clique, unit);
        if (lossy.isEmpty()) {
            out.add(INDENT + NO_LOSS_PREFIX + name(lowest) + NO_LOSS_SUFFIX);
            return;
        }
        int width = Math.max(RECIPE_COLUMN_AT_LEAST, lossy.stream().mapToInt(recipe -> recipe.text().length())
                .max().orElse(0));
        out.add(INDENT + LOSS_PREFIX + name(lowest) + LOSS_SUFFIX);
        out.add(INDENT + INDENT + pad(COLUMN_RECIPE, width) + GAP + pad(COLUMN_EACH, EACH_WIDTH) + GAP
                + pad(COLUMN_MELTS, MELTS_WIDTH) + GAP + pad(COLUMN_LOST_EACH, LOST_EACH_WIDTH) + COLUMN_LOST_PER_CRAFT);
        out.add(INDENT + INDENT + RULE_DASH.repeat(width) + GAP + RULE_DASH.repeat(EACH_WIDTH) + GAP
                + RULE_DASH.repeat(MELTS_WIDTH) + GAP + RULE_DASH.repeat(LOST_EACH_WIDTH) + GAP
                + RULE_DASH.repeat(LOST_PER_CRAFT_WIDTH));
        for (LossyRecipe recipe : lossy) {
            out.add(INDENT + INDENT + pad(recipe.text(), width) + GAP + pad(recipe.each(), EACH_WIDTH) + GAP
                    + pad(recipe.melts(), MELTS_WIDTH) + GAP + pad(recipe.lostEach(), LOST_EACH_WIDTH) + GAP
                    + recipe.lostPerCraft());
        }
    }

    private static String name(Identifier id) {
        return DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + SPACE.repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return text.length() >= width ? text : SPACE.repeat(width - text.length()) + text;
    }

    private static String recipeText(WalkedRecipe walked) {
        Map<Identifier, Integer> counts = new TreeMap<>();
        walked.picks().forEach(pick -> counts.merge(pick, 1, Integer::sum));
        StringJoiner inputs = new StringJoiner(RECIPE_JOIN);
        counts.forEach((id, count) -> inputs.add(count + SPACE + name(id)));
        return inputs + RECIPE_ARROW + walked.count() + SPACE + name(walked.recipe().output());
    }

    private static GooFraction scaled(GooFraction share, GooFraction unit, long worth) {
        return new GooFraction(Math.multiplyExact(Math.multiplyExact(share.numerator(), unit.denominator()), worth),
                Math.multiplyExact(share.denominator(), unit.numerator()));
    }
}
