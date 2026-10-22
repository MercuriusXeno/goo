package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/** Scratch: clique roots and lossy recipes per goo type. Not for commit. */
@RegistersGameTests
public final class CliqueDumpTests {
    private static final long[] SCALES = {1, 2, 3, 4, 6, 8, 12, 24, 60};

    private CliqueDumpTests() {
    }

    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, "clique_dump", CliqueDumpTests::dump);
    }

    private record Rec(RecipeInput recipe, int count, List<Identifier> picks) {
    }

    public static void dump(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GooValueRegistry values = GooValues.registryOf(server);
        values.loadBaseValuesFromPacks(server);
        values.deriveFromRecipes(server);
        Map<Identifier, GooValue> eff = values.table().getEffectiveValues();
        Set<Identifier> denied = values.deniedItems();
        List<Rec> recs = new ArrayList<>();
        for (RecipeInput r : values.valueSettingRecipes()) {
            if (denied.contains(r.output())) {
                continue;
            }
            int count = r.resultCount();
            List<Identifier> picks = new ArrayList<>();
            boolean ok = true;
            for (Set<Identifier> slot : r.ingredientAlternatives()) {
                if (slot.contains(r.output())) {
                    count--;
                    continue;
                }
                Optional<Identifier> pick = slot.stream().filter(eff::containsKey).sorted().findFirst();
                if (pick.isEmpty()) {
                    ok = false;
                    break;
                }
                picks.add(pick.get());
            }
            if (ok && count > 0 && !picks.isEmpty()) {
                recs.add(new Rec(r, count, picks));
            }
        }
        Map<Identifier, Rec> byOutput = new HashMap<>();
        recs.forEach(r -> byOutput.put(r.recipe().output(), r));
        Map<Identifier, GooValue> vac = vacuumValues(recs, byOutput, eff);

        List<String> out = new ArrayList<>();
        out.add("CLIQUES AND LOSSY RECIPES");
        out.add("");
        out.add("A clique is a group of items linked by recipes that carry one goo type (the iron family for metal).");
        out.add("Its lowest item holds the least of that type. Each table asks: if the lowest item is worth N, how");
        out.add("many of the clique's recipes make an item worth a fraction, which melting rounds down and loses?");
        out.add("The recipe list under each table is for the lowest item worth 1.");
        out.add("Values come from the recipes alone, starting from items no recipe makes.");
        out.add("");
        out.add("");
        for (ResourceKey<GooTypeDefinition> type : GooTypes.BUNDLED) {
            dumpType(type, recs, vac, out);
        }
        try {
            Files.write(FMLPaths.CONFIGDIR.get().resolve("goo_cliques.txt"), out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            helper.fail(e.getMessage());
            return;
        }
        helper.succeed();
    }

    private static Map<Identifier, GooValue> vacuumValues(List<Rec> recs, Map<Identifier, Rec> byOutput,
                                                          Map<Identifier, GooValue> eff) {
        Map<Identifier, GooValue> vac = new HashMap<>();
        Set<Identifier> starting = new HashSet<>();
        recs.forEach(r -> {
            starting.addAll(r.picks());
            starting.addAll(r.recipe().containerItems().values());
        });
        starting.removeAll(byOutput.keySet());
        starting.stream().filter(eff::containsKey).forEach(i -> vac.put(i, eff.get(i)));
        while (true) {
            boolean resolved = true;
            while (resolved) {
                resolved = false;
                for (Rec r : recs) {
                    if (!vac.containsKey(r.recipe().output()) && derive(r, vac)) {
                        resolved = true;
                    }
                }
            }
            Identifier seed = null;
            for (Rec r : recs) {
                if (vac.containsKey(r.recipe().output())) {
                    continue;
                }
                for (Identifier p : r.picks()) {
                    if (!vac.containsKey(p) && eff.containsKey(p) && (seed == null
                            || eff.get(p).totalExact().compareTo(eff.get(seed).totalExact()) > 0)) {
                        seed = p;
                    }
                }
            }
            if (seed == null) {
                return vac;
            }
            vac.put(seed, eff.get(seed));
        }
    }

    private static boolean derive(Rec r, Map<Identifier, GooValue> vac) {
        GooValue total = GooValue.EMPTY;
        for (Identifier p : r.picks()) {
            GooValue v = vac.get(p);
            if (v == null) {
                return false;
            }
            Identifier container = r.recipe().containerItems().get(p);
            GooValue cv = container == null ? null : vac.get(container);
            total = total.add(cv == null ? v : v.subtract(cv), 1);
        }
        vac.put(r.recipe().output(), total.divide(r.count()));
        return true;
    }

    private static GooFraction share(Map<Identifier, GooValue> vac, Identifier item,
                                     ResourceKey<GooTypeDefinition> type) {
        GooValue v = vac.get(item);
        return v == null ? GooFraction.ZERO : v.exact(type);
    }

    private static void dumpType(ResourceKey<GooTypeDefinition> type, List<Rec> recs, Map<Identifier, GooValue> vac,
                                 List<String> out) {
        Map<Identifier, Identifier> parent = new HashMap<>();
        List<Rec> carrying = new ArrayList<>();
        for (Rec r : recs) {
            Identifier o = r.recipe().output();
            if (!share(vac, o, type).isPositive()) {
                continue;
            }
            boolean joined = false;
            for (Identifier p : new HashSet<>(r.picks())) {
                if (share(vac, p, type).isPositive()) {
                    union(parent, o, p);
                    joined = true;
                }
            }
            if (joined) {
                carrying.add(r);
            }
        }
        Map<Identifier, List<Identifier>> cliques = new HashMap<>();
        for (Identifier i : new ArrayList<>(parent.keySet())) {
            cliques.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        if (cliques.isEmpty()) {
            return;
        }
        String title = GooTypes.id(type).toUpperCase(Locale.ROOT);
        out.add(title);
        out.add("=".repeat(title.length()));
        out.add("");
        List<List<Identifier>> ordered = new ArrayList<>(cliques.values());
        ordered.sort(Comparator.comparingInt((List<Identifier> c) -> -c.size()));
        List<String> clean = new ArrayList<>();
        for (List<Identifier> clique : ordered) {
            dumpClique(type, clique, carrying, vac, parent, out, clean);
        }
        if (!clean.isEmpty()) {
            out.add("Cliques where no recipe loses goo with the lowest item worth 1, so none loses goo at any");
            out.add("whole value of the lowest item either (lowest item, then the clique's size):");
            clean.forEach(c -> out.add("  " + c));
            out.add("");
        }
        out.add("");
    }

    private static String name(Identifier id) {
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
    }

    private static void dumpClique(ResourceKey<GooTypeDefinition> type, List<Identifier> clique, List<Rec> carrying,
                                   Map<Identifier, GooValue> vac, Map<Identifier, Identifier> parent,
                                   List<String> out, List<String> clean) {
        Identifier root = clique.stream().min(Comparator.comparing((Identifier i) -> share(vac, i, type))
                .thenComparing(Comparator.naturalOrder())).orElseThrow();
        GooFraction unit = share(vac, root, type);
        Identifier rootId = find(parent, root);
        List<Rec> inClique = carrying.stream().filter(r -> find(parent, r.recipe().output()).equals(rootId)).toList();
        long[] lossyAt = new long[SCALES.length];
        for (int i = 0; i < SCALES.length; i++) {
            long s = SCALES[i];
            lossyAt[i] = inClique.stream().filter(r -> !scaled(share(vac, r.recipe().output(), type), unit, s)
                    .isWhole()).count();
        }
        if (lossyAt[0] == 0) {
            clean.add(name(root) + ", " + clique.size() + " items");
            return;
        }
        Rec rootRecipe = inClique.stream().filter(r -> r.recipe().output().equals(root)).findFirst().orElse(null);
        out.add("Clique: " + name(root) + " (" + clique.size() + " items, " + inClique.size() + " recipes)");
        out.add("  Lowest item: " + name(root)
                + (rootRecipe == null ? ", made by no recipe" : ", made by " + textOf(rootRecipe)));
        out.add("");
        String head = "If " + name(root) + " is worth";
        out.add("  " + head + " | Recipes that lose goo on melt");
        out.add("  " + "-".repeat(head.length()) + "-+------------------------------");
        for (int i = 0; i < SCALES.length; i++) {
            out.add("  " + padLeft(Long.toString(SCALES[i]), head.length()) + " | "
                    + lossyAt[i] + " of " + inClique.size());
        }
        out.add("");
        List<Object[]> rows = new ArrayList<>();
        for (Rec r : inClique) {
            GooFraction v = scaled(share(vac, r.recipe().output(), type), unit, 1);
            if (!v.isWhole()) {
                GooFraction each = v.minus(GooFraction.of(v.floor()));
                GooFraction perCraft = each.times(r.count());
                rows.add(new Object[]{perCraft, textOf(r), v.toString(), Integer.toString(v.floor()),
                        each.toString(), perCraft.toString()});
            }
        }
        rows.sort(Comparator.comparing((Object[] row) -> (GooFraction) row[0]).reversed()
                .thenComparing(row -> (String) row[1]));
        if (rows.isEmpty()) {
            out.add("  No recipe loses goo when " + name(root) + " is worth 1.");
        } else {
            int width = Math.max(6, rows.stream().mapToInt(row -> ((String) row[1]).length()).max().orElse(0));
            out.add("  Recipes that lose goo when " + name(root) + " is worth 1:");
            out.add("    " + pad("Recipe", width) + "  " + pad("Each", 8) + "  " + pad("Melts", 6) + "  "
                    + pad("Lost each", 10) + "  Lost per craft");
            out.add("    " + "-".repeat(width) + "  " + "-".repeat(8) + "  " + "-".repeat(6) + "  "
                    + "-".repeat(10) + "  " + "-".repeat(14));
            for (Object[] row : rows) {
                out.add("    " + pad((String) row[1], width) + "  " + pad((String) row[2], 8) + "  "
                        + pad((String) row[3], 6) + "  " + pad((String) row[4], 10) + "  " + row[5]);
            }
        }
        out.add("");
        out.add("");
    }

    private static String textOf(Rec r) {
        Map<Identifier, Integer> counts = new TreeMap<>();
        r.picks().forEach(p -> counts.merge(p, 1, Integer::sum));
        StringJoiner in = new StringJoiner(" + ");
        counts.forEach((id, n) -> in.add(n + " " + name(id)));
        return in + " -> " + r.count() + " " + name(r.recipe().output());
    }

    private static GooFraction scaled(GooFraction share, GooFraction unit, long scale) {
        return new GooFraction(Math.multiplyExact(Math.multiplyExact(share.numerator(), unit.denominator()), scale),
                Math.multiplyExact(share.denominator(), unit.numerator()));
    }

    private static Identifier find(Map<Identifier, Identifier> parent, Identifier i) {
        parent.putIfAbsent(i, i);
        Identifier p = parent.get(i);
        if (p.equals(i)) {
            return i;
        }
        Identifier r = find(parent, p);
        parent.put(i, r);
        return r;
    }

    private static void union(Map<Identifier, Identifier> parent, Identifier a, Identifier b) {
        Identifier ra = find(parent, a);
        Identifier rb = find(parent, b);
        if (!ra.equals(rb)) {
            parent.put(ra, rb);
        }
    }
}
