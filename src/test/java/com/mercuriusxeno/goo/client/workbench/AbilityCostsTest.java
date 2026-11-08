package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An ability's cost shows in the builder as a by-type value of the ability's own goo type, its items and per-type
 * prefabs reading that type, and the builder's text writes back as the expression the ability's JSON reads; an item
 * holding none of the type reads a red line (decision ability-cost-builds-as-a-one-type-by-type-value).
 */
class AbilityCostsTest {

    @Nested
    class Shown {
        @Test
        void aNumberIsOneRowOfTheType() {
            assertEquals("{\"rock\":\"1000\"}", AbilityCosts.shown("1000", "rock", Set.of()));
        }

        @Test
        void itemsAndPerTypePrefabsReadTheType() {
            assertEquals("{\"rock\":\"3 * minecraft:cobblestone.rock + $stick.rock + 2 * $base\"}",
                    AbilityCosts.shown("3 * minecraft:cobblestone + $stick + 2 * $base", "rock", Set.of("stick")));
        }

        @Test
        void aDecimalCostShowsAsTheFractionTheBuilderReads() {
            assertEquals("{\"rock\":\"25/2\"}", AbilityCosts.shown("12.5", "rock", Set.of()));
        }

        @Test
        void aDecimalCostWritesBackAsItsFraction() {
            assertEquals(Optional.of("25 / 2"), AbilityCosts.written(AbilityCosts.shown("12.5", "rock", Set.of()),
                    "rock"));
        }
    }

    @Nested
    class Written {
        @Test
        void aCountWritesAnExplicitProductAndTheTypeDrops() {
            assertEquals(Optional.of("3 * minecraft:cobblestone + 2 * $base"),
                    AbilityCosts.written("{\"rock\": \"3 minecraft:cobblestone.rock + 2 $base\"}", "rock"));
        }

        @Test
        void aShownCostWritesBackAsItWasRead() {
            String source = "3 * minecraft:cobblestone / 2 - $stick";
            assertEquals(Optional.of(source),
                    AbilityCosts.written(AbilityCosts.shown(source, "rock", Set.of("stick")), "rock"));
        }

        @Test
        void aRowOfAnotherTypeWritesNothing() {
            assertEquals(Optional.empty(), AbilityCosts.written("{\"leaf\": \"3\"}", "rock"));
        }
    }

    @Nested
    class Lacking {
        private final Map<String, Map<String, String>> worths = Map.of("minecraft:cobblestone", Map.of("rock", "250"));

        @Test
        void anItemHoldingNoneOfTheTypeNamesBoth() {
            assertEquals(List.of("minecraft:cobblestone holds no unstable"), AbilityCosts.lacking(
                    "3 * minecraft:cobblestone", "unstable", ref -> worths.getOrDefault(ref.id(), Map.of())));
        }

        @Test
        void anItemHoldingTheTypeReadsNoLine() {
            assertEquals(List.of(), AbilityCosts.lacking("3 * minecraft:cobblestone", "rock",
                    ref -> worths.getOrDefault(ref.id(), Map.of())));
        }
    }
}
