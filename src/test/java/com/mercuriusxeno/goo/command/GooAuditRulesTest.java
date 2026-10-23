package com.mercuriusxeno.goo.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the audit as a walk over its rules: the discovered rules write every section the audit dump test
 * reads, in order, and a rule added beside them adds its section to the text and the JSON with nothing
 * else changed (decision each-check-is-one-rule-added-alone).
 */
class GooAuditRulesTest {

    private static final List<String> SECTIONS_IN_ORDER = List.of(
            "EXPRESSION WARNINGS", "PHANTOM IDS", "CYCLES", "CONFLICTS",
            "LEAST VALUE PER TYPE", "NO VALUE", "FORMULAS", "REACTIONS", "ALL VALUES");
    private static final String SMALL_VALUES = """
            {
                "_constants": {"base": 4},
                "stick": {"leaf": "$base"},
                "ghost_item": {"leaf": 1}
            }
            """;

    /**
     * A rule that flags one thing, standing for any concern added later.
     */
    static final class LossyRecipeRule implements GooAuditRule {
        @Override
        public GooAuditSection run(GooAuditInputs inputs) {
            return GooAuditSection.of("LOSSY RECIPES", "LOSSY RECIPES (1 recipes)",
                    List.of("  iron_chain melts to 11/9"), List.of("iron_chain melts to 11/9"));
        }
    }

    private static GooAuditInputs smallInputs() throws IOException {
        return new GooAuditInputs(GooBaseValuesPreview.registry(SMALL_VALUES), List.of(),
                Set.of(Identifier.withDefaultNamespace("stick")));
    }

    private static int headerIndex(List<String> lines, String section, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (lines.get(i).startsWith(section + " (")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The discovered rules alone write every section the audit dump test reads, in that order, and the
     * scratch clique dump's header stands nowhere.
     */
    @Test
    void discoveredRulesWriteEverySectionInOrder() throws IOException {
        GooAuditReport.Built report = GooAuditReport.build(smallInputs(), GooAuditRules.discovered());
        int from = 0;
        for (String section : SECTIONS_IN_ORDER) {
            int found = headerIndex(report.lines(), section, from);
            assertTrue(found >= 0, section);
            from = found + 1;
        }
        assertFalse(report.lines().contains("CLIQUES AND LOSSY RECIPES"));
    }

    /**
     * The root remainders rule is discovered with no change to discovery and writes its section after TYPE
     * PROVENANCE (decision workbench-shows-each-roots-remainders-and-items-solved).
     */
    @Test
    void rootRemaindersSectionFollowsTypeProvenance() throws IOException {
        GooAuditReport.Built report = GooAuditReport.build(smallInputs(), GooAuditRules.discovered());
        int provenance = headerIndex(report.lines(), "TYPE PROVENANCE", 0);
        assertTrue(provenance >= 0);
        assertTrue(headerIndex(report.lines(), "ROOT REMAINDERS", provenance + 1) > provenance);
    }

    /**
     * A rule registered beside the discovered ones adds its header and lines after the standing sections,
     * and its section, lines and flag to the JSON.
     */
    @Test
    void addedRuleAddsItsSectionToTextAndJson() throws IOException {
        List<GooAuditRule> rules = new ArrayList<>(GooAuditRules.discovered());
        rules.add(new LossyRecipeRule());
        GooAuditReport.Built report = GooAuditReport.build(smallInputs(), rules);
        int lossy = headerIndex(report.lines(), "LOSSY RECIPES", 0);
        assertTrue(lossy > headerIndex(report.lines(), "ALL VALUES", 0));
        assertTrue(report.lines().contains("  iron_chain melts to 11/9"));
        JsonArray sections = JsonParser.parseString(report.json()).getAsJsonObject().getAsJsonArray("sections");
        JsonObject added = sections.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(section -> "LOSSY RECIPES".equals(section.get("section").getAsString())).findFirst()
                .orElseThrow();
        assertEquals(List.of("  iron_chain melts to 11/9"),
                added.getAsJsonArray("lines").asList().stream().map(JsonElement::getAsString).toList());
        assertEquals(List.of("iron_chain melts to 11/9"),
                added.getAsJsonArray("flags").asList().stream().map(JsonElement::getAsString).toList());
    }

    /**
     * A phantom id is read against the registered items handed in, so the rule needs no registry.
     */
    @Test
    void phantomIdIsFlagged() throws IOException {
        GooAuditReport.Built report = GooAuditReport.build(smallInputs(), GooAuditRules.discovered());
        JsonObject phantoms = JsonParser.parseString(report.json()).getAsJsonObject().getAsJsonArray("sections")
                .asList().stream().map(JsonElement::getAsJsonObject)
                .filter(section -> "PHANTOM IDS".equals(section.get("section").getAsString())).findFirst()
                .orElseThrow();
        assertEquals(1, phantoms.getAsJsonArray("flags").size());
        assertTrue(phantoms.getAsJsonArray("flags").get(0).getAsString().contains("minecraft:ghost_item"));
    }
}
