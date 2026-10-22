package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * Writes config/goo_cliques.txt: each goo type's items grouped into cliques joined by the winning recipes,
 * and, per clique, how many recipes melt to a fraction when the clique's lowest item is worth 1, 2, 3, 4, 6,
 * 8, 12, 24 or 60. The values-dumps gametest writes it beside the audit in one boot
 * (decision workbench-reruns-the-audit-headless).
 */
final class GooCliqueDump {
    static final String FILE_CLIQUES = "goo_cliques.txt";
    static final String TITLE = "CLIQUES AND LOSSY RECIPES";
    private static final long[] ROOT_WORTHS = {1, 2, 3, 4, 6, 8, 12, 24, 60};
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String INDENT = "  ";
    private static final List<String> PREAMBLE = List.of(
            "A clique is a group of items linked by recipes that carry one goo type (the iron family for metal).",
            "Its lowest item holds the least of that type. Each table asks: if the lowest item is worth N, how",
            "many of the clique's recipes make an item worth a fraction, which melting rounds down and loses?",
            "The recipe list under each table is for the lowest item worth 1.",
            "Values come from the recipes alone, starting from items no recipe makes.");

    private GooCliqueDump() {
    }

    /**
     * A winning recipe as the clique walk reads it: the output count less any slot returning the output, and
     * one valued pick per remaining slot.
     */
    private record WalkedRecipe(RecipeInput recipe, int count, List<Identifier> picks) {
    }

    /**
     * Writes the clique dump from the derived values.
     *
     * @param values the server's goo value registry, derived
     */
    static void write(GooValueRegistry values) {
        Map<Identifier, GooValue> effective = values.table().getEffectiveValues();
        List<WalkedRecipe> recipes = walkedRecipes(values, effective);
        Map<Identifier, WalkedRecipe> byOutput = new HashMap<>();
        recipes.forEach(walked -> byOutput.put(walked.recipe().output(), walked));
        Map<Identifier, GooValue> recipeValues = recipeOnlyValues(recipes, byOutput, effective);
        List<String> out = new ArrayList<>();
        out.add(TITLE);
        out.add("");
        out.addAll(PREAMBLE);
        out.add("");
        out.add("");
        for (ResourceKey<GooTypeDefinition> type : GooTypes.BUNDLED) {
            writeType(type, recipes, recipeValues, out);
        }
        try {
            Files.write(FMLPaths.CONFIGDIR.get().resolve(FILE_CLIQUES), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Goo.LOGGER.error("Failed to write {}: {}", FILE_CLIQUES, e.getMessage());
        }
    }

    private static List<WalkedRecipe> walkedRecipes(GooValueRegistry values, Map<Identifier, GooValue> effective) {
        Set<Identifier> denied = values.deniedItems();
        List<WalkedRecipe> recipes = new ArrayList<>();
        for (RecipeInput recipe : values.valueSettingRecipes()) {
            if (denied.contains(recipe.output())) {
                continue;
            }
            int count = recipe.resultCount();
            List<Identifier> picks = new ArrayList<>();
            boolean everySlotValued = true;
            for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
                if (slot.contains(recipe.output())) {
                    count--;
                    continue;
                }
                Optional<Identifier> pick = slot.stream().filter(effective::containsKey).sorted().findFirst();
                if (pick.isEmpty()) {
                    everySlotValued = false;
                    break;
                }
                picks.add(pick.get());
            }
            if (everySlotValued && count > 0 && !picks.isEmpty()) {
                recipes.add(new WalkedRecipe(recipe, count, picks));
            }
        }
        return recipes;
    }

    /**
     * Values every recipe output from the recipes alone, seeded with the items no recipe makes and, where a
     * cycle stalls the walk, with the most valuable unvalued input.
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
        while (true) {
            boolean progressed = true;
            while (progressed) {
                progressed = false;
                for (WalkedRecipe walked : recipes) {
                    if (!valued.containsKey(walked.recipe().output()) && deriveOutput(walked, valued)) {
                        progressed = true;
                    }
                }
            }
            Identifier seed = cycleSeed(recipes, valued, effective);
            if (seed == null) {
                return valued;
            }
            valued.put(seed, effective.get(seed));
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
                if (!valued.containsKey(pick) && effective.containsKey(pick) && (seed == null
                        || effective.get(pick).totalExact().compareTo(effective.get(seed).totalExact()) > 0)) {
                    seed = pick;
                }
            }
        }
        return seed;
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

    private static void writeType(ResourceKey<GooTypeDefinition> type, List<WalkedRecipe> recipes,
                                  Map<Identifier, GooValue> valued, List<String> out) {
        Map<Identifier, Identifier> parent = new HashMap<>();
        List<WalkedRecipe> carrying = new ArrayList<>();
        for (WalkedRecipe walked : recipes) {
            Identifier output = walked.recipe().output();
            if (!share(valued, output, type).isPositive()) {
                continue;
            }
            boolean joined = false;
            for (Identifier pick : new HashSet<>(walked.picks())) {
                if (share(valued, pick, type).isPositive()) {
                    union(parent, output, pick);
                    joined = true;
                }
            }
            if (joined) {
                carrying.add(walked);
            }
        }
        Map<Identifier, List<Identifier>> cliques = new HashMap<>();
        for (Identifier item : new ArrayList<>(parent.keySet())) {
            cliques.computeIfAbsent(find(parent, item), key -> new ArrayList<>()).add(item);
        }
        if (cliques.isEmpty()) {
            return;
        }
        String title = GooTypes.id(type).toUpperCase(Locale.ROOT);
        out.add(title);
        out.add("=".repeat(title.length()));
        out.add("");
        List<List<Identifier>> bySize = new ArrayList<>(cliques.values());
        bySize.sort(Comparator.comparingInt((List<Identifier> clique) -> -clique.size()));
        List<String> lossless = new ArrayList<>();
        for (List<Identifier> clique : bySize) {
            writeClique(type, clique, carrying, valued, parent, out, lossless);
        }
        if (!lossless.isEmpty()) {
            out.add("Cliques where no recipe loses goo with the lowest item worth 1, so none loses goo at any");
            out.add("whole value of the lowest item either (lowest item, then the clique's size):");
            lossless.forEach(line -> out.add(INDENT + line));
            out.add("");
        }
        out.add("");
    }

    private static void writeClique(ResourceKey<GooTypeDefinition> type, List<Identifier> clique,
                                    List<WalkedRecipe> carrying, Map<Identifier, GooValue> valued,
                                    Map<Identifier, Identifier> parent, List<String> out, List<String> lossless) {
        Identifier lowest = clique.stream().min(Comparator.comparing((Identifier item) -> share(valued, item, type))
                .thenComparing(Comparator.naturalOrder())).orElseThrow();
        GooFraction unit = share(valued, lowest, type);
        Identifier cliqueRoot = find(parent, lowest);
        List<WalkedRecipe> inClique = carrying.stream()
                .filter(walked -> find(parent, walked.recipe().output()).equals(cliqueRoot)).toList();
        long[] lossyAt = new long[ROOT_WORTHS.length];
        for (int i = 0; i < ROOT_WORTHS.length; i++) {
            long worth = ROOT_WORTHS[i];
            lossyAt[i] = inClique.stream()
                    .filter(walked -> !scaled(share(valued, walked.recipe().output(), type), unit, worth).isWhole())
                    .count();
        }
        if (lossyAt[0] == 0) {
            lossless.add(name(lowest) + ", " + clique.size() + " items");
            return;
        }
        WalkedRecipe lowestRecipe = inClique.stream().filter(walked -> walked.recipe().output().equals(lowest))
                .findFirst().orElse(null);
        out.add("Clique: " + name(lowest) + " (" + clique.size() + " items, " + inClique.size() + " recipes)");
        out.add(INDENT + "Lowest item: " + name(lowest)
                + (lowestRecipe == null ? ", made by no recipe" : ", made by " + recipeText(lowestRecipe)));
        out.add("");
        String head = "If " + name(lowest) + " is worth";
        out.add(INDENT + head + " | Recipes that lose goo on melt");
        out.add(INDENT + "-".repeat(head.length()) + "-+------------------------------");
        for (int i = 0; i < ROOT_WORTHS.length; i++) {
            out.add(INDENT + padLeft(Long.toString(ROOT_WORTHS[i]), head.length()) + " | " + lossyAt[i] + " of "
                    + inClique.size());
        }
        out.add("");
        writeLossyRecipes(type, lowest, unit, inClique, valued, out);
        out.add("");
        out.add("");
    }

    /**
     * One recipe losing goo with the lowest item worth 1.
     */
    private record LossyRecipe(GooFraction lostPerCraft, String text, String each, String melts, String lostEach) {
    }

    private static void writeLossyRecipes(ResourceKey<GooTypeDefinition> type, Identifier lowest, GooFraction unit,
                                          List<WalkedRecipe> inClique, Map<Identifier, GooValue> valued,
                                          List<String> out) {
        List<LossyRecipe> lossy = new ArrayList<>();
        for (WalkedRecipe walked : inClique) {
            GooFraction each = scaled(share(valued, walked.recipe().output(), type), unit, 1);
            if (!each.isWhole()) {
                GooFraction lostEach = each.minus(GooFraction.of(each.floor()));
                lossy.add(new LossyRecipe(lostEach.times(walked.count()), recipeText(walked), each.toString(),
                        Integer.toString(each.floor()), lostEach.toString()));
            }
        }
        lossy.sort(Comparator.comparing(LossyRecipe::lostPerCraft).reversed().thenComparing(LossyRecipe::text));
        if (lossy.isEmpty()) {
            out.add(INDENT + "No recipe loses goo when " + name(lowest) + " is worth 1.");
            return;
        }
        int width = Math.max(6, lossy.stream().mapToInt(recipe -> recipe.text().length()).max().orElse(0));
        out.add(INDENT + "Recipes that lose goo when " + name(lowest) + " is worth 1:");
        out.add(INDENT + INDENT + pad("Recipe", width) + "  " + pad("Each", 8) + "  " + pad("Melts", 6) + "  "
                + pad("Lost each", 10) + "  Lost per craft");
        out.add(INDENT + INDENT + "-".repeat(width) + "  " + "-".repeat(8) + "  " + "-".repeat(6) + "  "
                + "-".repeat(10) + "  " + "-".repeat(14));
        for (LossyRecipe recipe : lossy) {
            out.add(INDENT + INDENT + pad(recipe.text(), width) + "  " + pad(recipe.each(), 8) + "  "
                    + pad(recipe.melts(), 6) + "  " + pad(recipe.lostEach(), 10) + "  " + recipe.lostPerCraft());
        }
    }

    private static String name(Identifier id) {
        return DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    private static String recipeText(WalkedRecipe walked) {
        Map<Identifier, Integer> counts = new TreeMap<>();
        walked.picks().forEach(pick -> counts.merge(pick, 1, Integer::sum));
        StringJoiner inputs = new StringJoiner(" + ");
        counts.forEach((id, count) -> inputs.add(count + " " + name(id)));
        return inputs + " -> " + walked.count() + " " + name(walked.recipe().output());
    }

    private static GooFraction scaled(GooFraction share, GooFraction unit, long worth) {
        return new GooFraction(Math.multiplyExact(Math.multiplyExact(share.numerator(), unit.denominator()), worth),
                Math.multiplyExact(share.denominator(), unit.numerator()));
    }

    private static Identifier find(Map<Identifier, Identifier> parent, Identifier item) {
        parent.putIfAbsent(item, item);
        Identifier above = parent.get(item);
        if (above.equals(item)) {
            return item;
        }
        Identifier root = find(parent, above);
        parent.put(item, root);
        return root;
    }

    private static void union(Map<Identifier, Identifier> parent, Identifier first, Identifier second) {
        Identifier firstRoot = find(parent, first);
        Identifier secondRoot = find(parent, second);
        if (!firstRoot.equals(secondRoot)) {
            parent.put(firstRoot, secondRoot);
        }
    }
}
