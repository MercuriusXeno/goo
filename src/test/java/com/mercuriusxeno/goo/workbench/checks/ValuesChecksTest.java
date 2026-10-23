package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mercuriusxeno.goo.workbench.SampleDrafts;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the type-source checks over a draft: one rule per content decision plus the retired-type rule, each
 * passing on the frozen sample of the last curated file and failing with the items it wants on a draft
 * lacking a type's source (decision type-source-checks-are-rules-that-gate-promote).
 */
class ValuesChecksTest {

    private static final String OLD_RULE = "old-ships-from-scutes-diamonds-and-scrap";
    private static final String RETIRED_RULE = "no-item-resolves-a-retired-type";

    @Test
    void oneRuleStandsPerContentDecisionAndTheRetiredType() {
        assertEquals(List.of(OLD_RULE, "deep-ships-from-deepslate-and-sculk",
                        "kinetic-ships-from-slime-and-gunpowder", "astral-ships-from-endstone",
                        "opulent-ships-from-things-of-worth", "weird-ships-from-slime-and-sculk",
                        "xeno-ships-as-a-type-with-no-source-yet", "void-ships-from-astral-and-deep",
                        "quantum-ships-from-goo-ratios", "infinity-sources-whatever-shares-its-vessel",
                        "zoo-ships-from-animal-items", RETIRED_RULE),
                ValuesChecks.discovered().stream().map(ValuesCheck::title).toList());
    }

    @Test
    void frozenSamplePassesEveryRule() throws IOException {
        List<CheckResult> results = ValuesChecks.run(SampleDrafts.sample());
        assertEquals(12, results.size());
        assertEquals(List.of(), results.stream().filter(result -> !result.passed())
                .map(result -> result.title() + " wants " + result.wants()).toList());
    }

    @Test
    void diamondWithNoYoreFailsTheOldRuleNamingDiamond() throws IOException {
        CheckResult old = named(ValuesChecks.run(SampleDrafts.diamondLackingYore()), OLD_RULE);
        assertFalse(old.passed());
        assertEquals(List.of("minecraft:diamond"), old.wants().stream().map(Want::item).toList());
        assertTrue(old.wants().getFirst().lacks().contains("yore"), old.wants().toString());
    }

    /**
     * A type no bundled set holds reaches a value only through a registry that knows it, so this test builds the
     * draft's values by hand.
     */
    @Test
    void retiredTypeRuleNamesTheItemResolvingIt() {
        ResourceKey<GooTypeDefinition> retired = ResourceKey.create(GooTypes.REGISTRY,
                Identifier.fromNamespaceAndPath("goo", "blob"));
        DraftValues values = new DraftValues(Map.of(
                Identifier.parse("minecraft:dirt"), GooValue.ofExact(Map.of(GooTypes.ROCK, GooFraction.of(2),
                        retired, GooFraction.of(3))),
                Identifier.parse("minecraft:stone"), GooValue.ofExact(Map.of(GooTypes.ROCK, GooFraction.of(2)))));
        List<Want> wants = new NoItemResolvesARetiredTypeCheck().wants(values);
        assertEquals(List.of(new Want("minecraft:dirt", "resolves the retired type goo:blob")), wants);
    }

    private static CheckResult named(List<CheckResult> results, String title) {
        return results.stream().filter(result -> title.equals(result.title())).findFirst().orElseThrow();
    }
}
