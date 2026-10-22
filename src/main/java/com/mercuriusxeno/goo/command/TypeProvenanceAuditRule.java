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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * TYPE PROVENANCE: each type on each derived item traced to the hand-keyed entry it entered from and the
 * chain of recipes it took. A type's family is its clique, the items its recipes join; a family that one
 * hand-keyed entry alone feeds is a bleed, unless _roots names that entry, to fix at that entry rather than downstream,
 * as deep reaching a glass pane through a dye plant (decision audit-proposes-root-candidates-and-flags-bleed).
 */
@AuditRule(order = 110)
public final class TypeProvenanceAuditRule implements GooAuditRule {
    /**
     * The section's name, which its header leads with.
     */
    public static final String SECTION = "TYPE PROVENANCE";
    private static final String HEADER_OPEN = " (";
    private static final String HEADER_SUFFIX = " types)";
    private static final String INDENT = "  ";
    private static final String ON = " on ";
    private static final String ENTERS_AT = ": enters at ";
    private static final String VIA = " via ";
    private static final String ARROW = " -> ";
    private static final String BLEED_PREFIX = " enters its family through ";
    private static final String BLEED_SUFFIX = " alone: ";
    private static final String DEFAULT_NAMESPACE = "minecraft";

    @Override
    public GooAuditSection run(GooAuditInputs inputs) {
        GooValueRegistry values = inputs.values();
        Map<Identifier, GooValue> effective = values.table().getEffectiveValues();
        Set<Identifier> handKeyed = values.handKeyedItems();
        Map<Identifier, RecipeInput> byOutput = new HashMap<>();
        values.valueSettingRecipes().forEach(recipe -> byOutput.put(recipe.output(), recipe));
        Set<Identifier> roots = new HashSet<>(values.rootItems());
        List<String> lines = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        int tracedTypes = 0;
        for (ResourceKey<GooTypeDefinition> type : GooTypes.BUNDLED) {
            Provenance provenance = new Provenance(type, effective, handKeyed, byOutput);
            if (provenance.trace(roots, lines, flags)) {
                tracedTypes++;
            }
        }
        return new GooAuditSection(SECTION, SECTION + HEADER_OPEN + tracedTypes + HEADER_SUFFIX, List.copyOf(lines),
                flags.size(), List.copyOf(flags));
    }

    /**
     * One type's trace: which recipe input carried it into each derived item, back to a hand-keyed entry.
     */
    private record Provenance(ResourceKey<GooTypeDefinition> type, Map<Identifier, GooValue> effective,
                              Set<Identifier> handKeyed, Map<Identifier, RecipeInput> byOutput) {

        private GooFraction share(Identifier item) {
            GooValue value = effective.get(item);
            return value == null ? GooFraction.ZERO : value.exact(type);
        }

        /**
         * @param item a derived item
         * @return the input of its valuing recipe carrying the most of the type into it, or null where none does
         */
        private @Nullable Identifier carrier(Identifier item) {
            RecipeInput recipe = byOutput.get(item);
            if (recipe == null) {
                return null;
            }
            return recipe.ingredientAlternatives().stream().flatMap(slot -> slot.stream().sorted())
                    .filter(pick -> !pick.equals(item) && share(pick).isPositive())
                    .max(Comparator.comparing(this::share)).orElse(null);
        }

        /**
         * @param item a derived item
         * @return the chain from the hand-keyed entry to the item, or empty where the trace reaches none
         */
        private List<Identifier> chainTo(Identifier item) {
            List<Identifier> path = new ArrayList<>(List.of(item));
            Set<Identifier> seen = new HashSet<>(path);
            Identifier current = item;
            while (!handKeyed.contains(current)) {
                Identifier next = carrier(current);
                if (next == null || !seen.add(next)) {
                    return List.of();
                }
                path.add(next);
                current = next;
            }
            Collections.reverse(path);
            return path;
        }

        boolean trace(Set<Identifier> roots, List<String> lines, List<String> flags) {
            Map<Identifier, List<Identifier>> chains = new TreeMap<>();
            for (Identifier item : effective.keySet()) {
                if (!handKeyed.contains(item) && share(item).isPositive()) {
                    List<Identifier> chain = chainTo(item);
                    if (!chain.isEmpty()) {
                        chains.put(item, chain);
                    }
                }
            }
            if (chains.isEmpty()) {
                return false;
            }
            chains.forEach((item, chain) -> lines.add(INDENT + GooTypes.id(type) + ON + name(item) + ENTERS_AT
                    + name(chain.getFirst()) + VIA + chainText(chain)));
            flagBleeds(chains, roots, flags);
            return true;
        }

        /**
         * Flags each of the type's families that one hand-keyed entry alone feeds, naming its longest chain; a
         * root named elsewhere in the family is another type's unit, so only the entry itself named exempts it.
         *
         * @param chains each traced item's chain
         * @param roots  the roots _roots names
         * @param flags  the flags, added to
         */
        private void flagBleeds(Map<Identifier, List<Identifier>> chains, Set<Identifier> roots, List<String> flags) {
            Map<Identifier, Family> families = families(chains);
            families.values().stream().filter(family -> family.entries().size() == 1
                            && !roots.contains(family.entries().iterator().next()) && !family.longest().isEmpty())
                    .sorted(Comparator.comparing(family -> family.longest().getLast()))
                    .forEach(family -> flags.add(GooTypes.id(type) + BLEED_PREFIX
                            + name(family.entries().iterator().next()) + BLEED_SUFFIX + chainText(family.longest())));
        }

        /**
         * @param chains each traced item's chain
         * @return the type's families, joined by the recipes carrying it, by representative
         */
        private Map<Identifier, Family> families(Map<Identifier, List<Identifier>> chains) {
            Map<Identifier, Identifier> parent = new HashMap<>();
            byOutput.forEach((output, recipe) -> {
                if (share(output).isPositive()) {
                    recipe.ingredientAlternatives().forEach(slot -> slot.stream().filter(pick -> share(pick).isPositive())
                            .forEach(pick -> GooItemUnionFind.union(parent, output, pick)));
                }
            });
            Map<Identifier, Family> families = new HashMap<>();
            for (Identifier item : new ArrayList<>(parent.keySet())) {
                Family family = families.computeIfAbsent(GooItemUnionFind.find(parent, item),
                        key -> new Family());
                family.meet(item, handKeyed.contains(item), chains.get(item));
            }
            return families;
        }
    }

    /**
     * One family of one type: its hand-keyed entries and its longest traced chain.
     */
    private static final class Family {
        private final Set<Identifier> entries = new LinkedHashSet<>();
        private final List<Identifier> longest = new ArrayList<>();

        Set<Identifier> entries() {
            return entries;
        }

        List<Identifier> longest() {
            return longest;
        }

        void meet(Identifier item, boolean handKeyed, @Nullable List<Identifier> chain) {
            if (handKeyed) {
                entries.add(item);
            }
            if (chain != null && chain.size() > longest.size()) {
                longest.clear();
                longest.addAll(chain);
            }
        }
    }

    private static String chainText(List<Identifier> chain) {
        return String.join(ARROW, chain.stream().map(TypeProvenanceAuditRule::name).toList());
    }

    private static String name(Identifier id) {
        return DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }
}
