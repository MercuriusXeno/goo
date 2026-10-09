package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ability's badge reads only from the fixed vocabulary, and every shipped ability
 * declares mob exactly where it targets entities (decision badge-marks-the-target-kind).
 */
class AbilityBadgeTest {

    private static final int SHIPPED_MOB_BADGES = 13;
    private static final int SHIPPED_WORLD_BADGES = 7;
    private static final int SHIPPED_SELF_BADGES = 5;
    private static final int SHIPPED_BREW_BADGES = 5;
    private static final int SHIPPED_CHANNELED_BADGES = 7;
    private static final int SHIPPED_FREE_BADGES = 3;
    private static final int SHIPPED_TAP_BADGES = 3;
    /** The self + brew abilities, which wear brew on their self delivery (decision self-brew-goos-eat-before-the-effect). */
    private static final List<String> SHIPPED_BREWS = List.of("blaze_kindle", "leaf_barkskin", "rock_stoneskin",
            "vital_nourish", "shroom_sight");
    /** Blast, Crush and Colonize, the shipped free abilities (decision badge-vocabulary-gains-free-prism-tap-brew). */
    private static final List<String> SHIPPED_FREE = List.of("unstable_explode", "rock_crush", "shroom_colonize");
    /** Self deliveries wearing the channeled badge (decision flatten-disc-cursor-breaks-above-the-plane). */
    private static final List<String> SHIPPED_SELF_CHANNELS = List.of("rock_flatten");

    @ParameterizedTest
    @CsvSource({"world, WORLD", "mob, MOB", "self, SELF", "channeled, CHANNELED", "brew, BREW",
            "free, FREE", "prism, PRISM", "tap, TAP"})
    void eachVocabularyWordParsesToItsBadge(String word, AbilityBadge expected) {
        assertEquals(expected, AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).getOrThrow());
    }

    /**
     * Each badge holds its own tier, ranked channeled, mob, world, free,
     * prism, tap, self, brew, at orders that reverse that rank if order alone
     * decided; a shared badge falls back to order (decision
     * fan-sorts-badge-then-order).
     */
    @Test
    void fanOrderRanksEachBadgeThenOrder() {
        record Ranked(String name, AbilityBadge badge, int order) {
        }
        List<Ranked> shuffled = List.of(
                new Ranked("brew", AbilityBadge.BREW, -3),
                new Ranked("tap", AbilityBadge.TAP, 0),
                new Ranked("world", AbilityBadge.WORLD, 3),
                new Ranked("self", AbilityBadge.SELF, -2),
                new Ranked("prism", AbilityBadge.PRISM, 1),
                new Ranked("mob_late", AbilityBadge.MOB, 5),
                new Ranked("free", AbilityBadge.FREE, 2),
                new Ranked("channeled", AbilityBadge.CHANNELED, 6),
                new Ranked("mob_early", AbilityBadge.MOB, 4));

        assertEquals(List.of("channeled", "mob_early", "mob_late", "world", "free", "prism", "tap", "self", "brew"),
                shuffled.stream().sorted(AbilityBadge.fanOrder(Ranked::badge, Ranked::order)).map(Ranked::name).toList());
    }

    @ParameterizedTest
    @CsvSource({"splash", "punch"})
    void aWordOutsideTheVocabularyFailsToParse(String word) {
        assertTrue(AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).isError());
    }

    /**
     * The badge a shipped ability wears: its delivery's own badge for a self
     * or a stream (decision one-proving-ability-per-kind), brew for a self +
     * brew ability, free for Blast, tap for a tap's drip ability, and for any
     * other thrown ability, mob where it targets entities and world elsewhere.
     *
     * @param definition the shipped ability
     * @return the badge it should wear
     */
    private static AbilityBadge expectedBadge(AbilityDefinition definition) {
        if (SHIPPED_FREE.contains(definition.id().getPath())) {
            return AbilityBadge.FREE;
        }
        if (definition.hasTag(AbilityTags.TAP)) {
            return AbilityBadge.TAP;
        }
        return switch (definition.delivery().kind()) {
            case SELF -> selfBadge(definition.id().getPath());
            case STREAM -> AbilityBadge.CHANNELED;
            default -> definition.hasTag(AbilityTags.ENTITY) ? AbilityBadge.MOB : AbilityBadge.WORLD;
        };
    }

    private static AbilityBadge selfBadge(String path) {
        if (SHIPPED_SELF_CHANNELS.contains(path)) {
            return AbilityBadge.CHANNELED;
        }
        return SHIPPED_BREWS.contains(path) ? AbilityBadge.BREW : AbilityBadge.SELF;
    }

    @Test
    void shippedAbilitiesWearMobExactlyWhereTheyTargetEntities() {
        Map<String, AbilityDefinition> shipped = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(definition -> definition.id().getPath(), Function.identity()));

        shipped.values().forEach(definition -> assertEquals(
                expectedBadge(definition), definition.badge(), definition.id().toString()));
        assertEquals(Map.of(AbilityBadge.MOB, (long) SHIPPED_MOB_BADGES, AbilityBadge.WORLD, (long) SHIPPED_WORLD_BADGES,
                        AbilityBadge.SELF, (long) SHIPPED_SELF_BADGES, AbilityBadge.BREW, (long) SHIPPED_BREW_BADGES,
                        AbilityBadge.CHANNELED, (long) SHIPPED_CHANNELED_BADGES, AbilityBadge.FREE, (long) SHIPPED_FREE_BADGES,
                        AbilityBadge.TAP, (long) SHIPPED_TAP_BADGES),
                shipped.values().stream().collect(Collectors.groupingBy(AbilityDefinition::badge, Collectors.counting())));
    }
}
