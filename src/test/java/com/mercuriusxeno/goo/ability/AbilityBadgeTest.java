package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ability's badge reads only from the fixed vocabulary, and every shipped ability
 * declares mob exactly where it targets entities (decision badge-marks-the-target-kind),
 * graded from each loaded definition's own fields rather than a list of names
 * (decision tests-read-the-inventory-from-loaded-json).
 */
class AbilityBadgeTest {

    /** The delivery field a held channel writes; its codec default hides whether the JSON wrote it. */
    private static final String TICKS_PER_CHARGE_FIELD = "ticks_per_charge";

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
     * The badges a shipped ability may wear, read from its own fields: tap for a tap-tagged
     * drip, channeled for a stream or for any delivery that charges or writes a hold's ticks
     * per charge, brew for a self delivery paying upkeep, self for any other self delivery,
     * and for a thrown ability mob where it targets entities and world elsewhere. Free and
     * prism are carried by the badge alone, since no field marks them, so a thrown ability
     * wearing either passes on its badge.
     *
     * @param definition the shipped ability
     * @param delivery   the ability's delivery block as written in its JSON
     * @return the badges it may wear
     */
    private static Set<AbilityBadge> allowedBadges(AbilityDefinition definition, JsonObject delivery) {
        if (definition.hasTag(AbilityTags.TAP)) {
            return EnumSet.of(AbilityBadge.TAP);
        }
        boolean holds = definition.delivery().charges() || delivery.has(TICKS_PER_CHARGE_FIELD);
        return switch (definition.delivery().kind()) {
            case STREAM -> EnumSet.of(AbilityBadge.CHANNELED);
            // self-effects-trickle-until-ended
            case SELF -> EnumSet.of(definition.upkeep() > AbilityDefinition.NO_UPKEEP ? AbilityBadge.BREW
                    : holds ? AbilityBadge.CHANNELED : AbilityBadge.SELF);
            default -> holds ? EnumSet.of(AbilityBadge.CHANNELED)
                    : EnumSet.of(definition.hasTag(AbilityTags.ENTITY) ? AbilityBadge.MOB : AbilityBadge.WORLD,
                            AbilityBadge.FREE, AbilityBadge.PRISM);
        };
    }

    private static JsonObject writtenDelivery(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement delivery = JsonParser.parseReader(reader).getAsJsonObject().get("delivery");
            return delivery != null && delivery.isJsonObject() ? delivery.getAsJsonObject() : new JsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A prism card folded into its host's on_prism reaction loads no
     * definition of its own, so the wheel holds no card for it, and the host
     * carries the reaction in its place.
     * decision thrown-hosts-absorb-their-prism-combos
     *
     * @param card the folded card's file name
     * @param host the host ability's file name
     */
    @ParameterizedTest
    @CsvSource({"typhoon_lift, typhoon_updraft", "pulse_relay, pulse_zap", "pulse_metronome, pulse_pulser",
            "nether_hive, nether_decay", "glow_reflector, glow_sunbeam",
            "leaf_verdant, leaf_growth", "frost_glacial, frost_cold"})
    void aFoldedPrismCardLoadsNoDefinitionAndItsHostReacts(String card, String host) {
        assertTrue(AbilityJson.files().stream().map(AbilityJson::decode)
                .noneMatch(definition -> definition.id().getPath().equals(card)), card + " loads");
        assertFalse(AbilityJson.decode(host).onPrism().isEmpty(), host + " carries no on_prism reaction");
    }

    @Test
    void shippedAbilitiesWearMobExactlyWhereTheyTargetEntities() {
        for (Path file : AbilityJson.files()) {
            AbilityDefinition definition = AbilityJson.decode(file);
            assertTrue(allowedBadges(definition, writtenDelivery(file)).contains(definition.badge()),
                    definition.id() + " wears " + definition.badge());
        }
    }
}
