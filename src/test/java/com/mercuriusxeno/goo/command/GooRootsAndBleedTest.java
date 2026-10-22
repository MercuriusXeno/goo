package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValueRegistry;
import com.mercuriusxeno.goo.data.RecipeInput;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the audit's roots and bleed: the clique tables work a family from the root _roots names and propose
 * the smallest root values at which it melts whole, and TYPE PROVENANCE traces each type on a derived item to
 * the hand-keyed entry it entered from, flagging a family with no named root that one entry alone feeds
 * (decision audit-proposes-root-candidates-and-flags-bleed).
 */
class GooRootsAndBleedTest {

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    private static RecipeInput recipe(String output, int count, String... inputs) {
        List<Set<Identifier>> slots = new ArrayList<>();
        List<Optional<Identifier>> tags = new ArrayList<>();
        for (String input : inputs) {
            slots.add(Set.of(id(input)));
            tags.add(Optional.empty());
        }
        return new RecipeInput(id(output), count, slots, Map.of(), tags);
    }

    private static List<String> repeat(String item, int times) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            items.add(item);
        }
        return items;
    }

    private static final List<RecipeInput> IRON = List.of(
            recipe("iron_ingot", 1, repeat("iron_nugget", 9).toArray(String[]::new)),
            recipe("iron_block", 1, repeat("iron_ingot", 9).toArray(String[]::new)),
            recipe("torch", 4, "iron_nugget", "stick"));

    private static GooAuditInputs iron(String roots) {
        String json = """
                {
                    "_constants": {},
                    %s
                    "iron_nugget": {"metal": 1},
                    "stick": {"leaf": 1}
                }
                """.formatted(roots);
        GooValueRegistry values = GooBaseValuesPreview.derivedRegistry(json, IRON);
        return new GooAuditInputs(values, List.of(), Set.of());
    }

    /**
     * The named root is the family's unit, not the torch holding the least metal, and every recipe melts whole
     * once the nugget is worth 4; the loader reads _roots beside _restricted.
     */
    @Test
    void namedRootWorksTheFamily() {
        GooAuditInputs inputs = iron("\"_roots\": [\"iron_nugget\"], \"_restricted\": [],");
        CliqueTablesAuditRule.Family metal = CliqueTablesAuditRule.families(inputs).stream()
                .filter(family -> family.type().equals(GooTypes.METAL)).findFirst().orElseThrow();
        assertEquals(id("iron_nugget"), metal.root());
        assertEquals(4, metal.allWholeAt());
        assertTrue(GooBaseValuesPreview.load("""
                {"_roots": ["iron_nugget"], "_restricted": ["stick"]}
                """).roots().contains(id("iron_nugget")));
        assertTrue(new CliqueTablesAuditRule().run(inputs).lines().stream()
                .anyMatch(line -> line.startsWith("Clique: iron_nugget")));
    }

    /**
     * A family whose _roots names nothing says so rather than picking a unit.
     */
    @Test
    void unrootedFamilySaysNoRootNamed() {
        GooAuditInputs inputs = iron("");
        CliqueTablesAuditRule.Family metal = CliqueTablesAuditRule.families(inputs).stream()
                .filter(family -> family.type().equals(GooTypes.METAL)).findFirst().orElseThrow();
        assertNull(metal.root());
        assertTrue(new CliqueTablesAuditRule().run(inputs).lines().stream()
                .anyMatch(line -> line.contains("no root named")));
    }

    private static GooAuditInputs glass(String extraPlant) {
        String json = """
                {
                    "_constants": {},
                    "sea_pickle": {"deep": 4},
                    %s
                    "glass": {"rock": 1}
                }
                """.formatted(extraPlant.isEmpty() ? "" : "\"" + extraPlant + "\": {\"deep\": 2},");
        List<String> stainedInputs = new ArrayList<>(repeat("glass", 8));
        stainedInputs.add("gray_dye");
        if (!extraPlant.isEmpty()) {
            stainedInputs.add(extraPlant);
        }
        List<RecipeInput> recipes = List.of(
                recipe("gray_dye", 1, "sea_pickle"),
                recipe("gray_stained_glass", 8, stainedInputs.toArray(String[]::new)),
                recipe("gray_stained_glass_pane", 16, repeat("gray_stained_glass", 6).toArray(String[]::new)));
        return new GooAuditInputs(GooBaseValuesPreview.derivedRegistry(json, recipes), List.of(), Set.of());
    }

    /**
     * Deep on the pane traces back to the pickle through the dye and the stained glass, and the pickle, its
     * family's lone deep source, is flagged.
     */
    @Test
    void loneSourceIsTracedAndFlagged() {
        GooAuditSection provenance = new TypeProvenanceAuditRule().run(glass(""));
        assertTrue(provenance.lines().contains("  deep on gray_stained_glass_pane: enters at sea_pickle via "
                + "sea_pickle -> gray_dye -> gray_stained_glass -> gray_stained_glass_pane"), provenance.lines()
                .toString());
        assertTrue(provenance.flags().contains("deep enters its family through sea_pickle alone: sea_pickle -> "
                + "gray_dye -> gray_stained_glass -> gray_stained_glass_pane"), provenance.flags().toString());
    }

    /**
     * Two hand-keyed entries feeding deep into one family raise no flag.
     */
    @Test
    void twoSourcesRaiseNoFlag() {
        GooAuditSection provenance = new TypeProvenanceAuditRule().run(glass("kelp"));
        assertFalse(provenance.flags().stream().anyMatch(flag -> flag.startsWith("deep")), provenance.flags()
                .toString());
    }
}
