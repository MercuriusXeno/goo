package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.data.GooValueTable;
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
    @CsvSource({"world, WORLD", "mob, MOB", "instant, SELF", "channeled, CHANNELED", "durative, DURATIVE",
            "free, FREE", "prism, PRISM", "held, HELD"})
    void eachVocabularyWordParsesToItsBadge(String word, AbilityBadge expected) {
        assertEquals(expected, AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).getOrThrow());
    }

    /**
     * The network codec writes the ordinal, so every badge that stood before held keeps its own,
     * and held takes the next.
     * decision held-badge-marks-abilities-that-prepare-and-throw
     */
    @Test
    void everyBadgeKeepsItsNetworkOrdinal() {
        assertEquals(List.of(AbilityBadge.WORLD, AbilityBadge.MOB, AbilityBadge.SELF, AbilityBadge.CHANNELED,
                AbilityBadge.DURATIVE, AbilityBadge.FREE, AbilityBadge.PRISM, AbilityBadge.HELD),
                List.of(AbilityBadge.values()));
    }

    /**
     * A held ability resolves at the aimed point, as a channeled one does.
     * decision held-badge-marks-abilities-that-prepare-and-throw
     */
    @Test
    void heldAimsAPoint() {
        assertTrue(AbilityBadge.HELD.aimsAPoint());
    }

    /**
     * Grab and Gouge prepare on right click and throw on left click, so they wear held; the
     * channels stay channeled.
     * decision held-badge-marks-abilities-that-prepare-and-throw
     *
     * @param ability the shipped ability's file name
     * @param badge   the badge it wears
     */
    @ParameterizedTest
    @CsvSource({"kinetic_grab, HELD", "rock_gouge, HELD", "blaze_flames, CHANNELED", "typhoon_gust, CHANNELED",
            "frost_cold, CHANNELED", "weird_goop, CHANNELED", "kinetic_slick, CHANNELED"})
    void shippedAbilityWearsItsControlBadge(String ability, AbilityBadge badge) {
        assertEquals(badge, AbilityJson.decode(ability).badge(), ability);
    }

    /** decision self-badge-becomes-instant-with-a-noita-trigger-star */
    @Test
    void theOneShotBadgeSpellsInstant() {
        assertEquals("instant", AbilityBadge.SELF.getSerializedName());
    }

    /**
     * Each badge holds its own tier, ranked channeled, held, mob, world, free,
     * prism, self, durative, at orders that reverse that rank if order alone
     * decided; a shared badge falls back to order (decisions
     * fan-sorts-badge-then-order, held-badge-marks-abilities-that-prepare-and-throw).
     */
    @Test
    void fanOrderRanksEachBadgeThenOrder() {
        record Ranked(String name, AbilityBadge badge, int order) {
        }
        List<Ranked> shuffled = List.of(
                new Ranked("held", AbilityBadge.HELD, 7),
                new Ranked("durative", AbilityBadge.DURATIVE, -3),
                new Ranked("world", AbilityBadge.WORLD, 3),
                new Ranked("self", AbilityBadge.SELF, -2),
                new Ranked("prism", AbilityBadge.PRISM, 1),
                new Ranked("mob_late", AbilityBadge.MOB, 5),
                new Ranked("free", AbilityBadge.FREE, 2),
                new Ranked("channeled", AbilityBadge.CHANNELED, 6),
                new Ranked("mob_early", AbilityBadge.MOB, 4));

        assertEquals(List.of("channeled", "held", "mob_early", "mob_late", "world", "free", "prism", "self", "durative"),
                shuffled.stream().sorted(AbilityBadge.fanOrder(Ranked::badge, Ranked::order)).map(Ranked::name).toList());
    }

    /**
     * The durative badge left the vocabulary for durative, so a JSON still spelling brew fails to load.
     * decision clock-badge-marks-durative-abilities
     */
    @ParameterizedTest
    @CsvSource({"splash", "punch", "self", "brew"})
    void aWordOutsideTheVocabularyFailsToParse(String word) {
        assertTrue(AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).isError());
    }

    /**
     * The badges a shipped ability may wear, read from its own fields: channeled or held for any delivery that charges, its charge sizing no cast, or writes a hold's ticks
     * per charge, channeled for a stream, save the charm-like cast, which takes a mob and wears mob, durative for a self delivery paying upkeep, instant for any other self delivery,
     * and for a thrown ability mob where it targets entities and world elsewhere. Free and
     * prism are carried by the badge alone, since no field marks them, so a thrown ability
     * wearing either passes on its badge.
     *
     * @param definition the shipped ability
     * @param delivery   the ability's delivery block as written in its JSON
     * @return the badges it may wear
     */
    private static Set<AbilityBadge> allowedBadges(AbilityDefinition definition, JsonObject delivery) {
        // spontaneous-abilities-cast-at-the-target-with-no-thrown-blob: a charge sizing a cast still aims it
        boolean holds = definition.delivery().charges() && !definition.delivery().sizesByCharge()
                || delivery.has(TICKS_PER_CHARGE_FIELD);
        return switch (definition.delivery().kind()) {
            case STREAM -> EnumSet.of(AbilityBadge.CHANNELED);
            // charm-like-abilities-lock-the-highlighted-mob-on-right-click: a charm-like cast takes a mob
            case ENVELOP -> EnumSet.of(AbilityBadge.MOB);
            // self-effects-trickle-until-ended
            case SELF -> definition.upkeep().in(GooValueTable.EMPTY) > AbilityDefinition.NO_UPKEEP
                    ? EnumSet.of(AbilityBadge.DURATIVE)
                    : holds ? EnumSet.of(AbilityBadge.CHANNELED, AbilityBadge.HELD) : EnumSet.of(AbilityBadge.SELF);
            // knives-wears-the-free-badge: a charge slinging knives is a free cast
            // held-badge-marks-abilities-that-prepare-and-throw
            default -> definition.delivery().charge().slings() ? EnumSet.of(AbilityBadge.FREE)
                    : holds ? EnumSet.of(AbilityBadge.CHANNELED, AbilityBadge.HELD) : releasedBadges(definition);
        };
    }

    /**
     * The badges an ability thrown at its aim may wear: mob for one taking
     * entities, which an arc may wear as held, formed on right click and
     * thrown on left, and world for one taking blocks.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param definition the ability
     * @return the badges it may wear
     */
    private static Set<AbilityBadge> releasedBadges(AbilityDefinition definition) {
        Set<AbilityBadge> allowed = EnumSet.of(AbilityBadge.FREE, AbilityBadge.PRISM);
        if (!definition.hasTag(AbilityTags.ENTITY)) {
            allowed.add(AbilityBadge.WORLD);
            return allowed;
        }
        allowed.add(AbilityBadge.MOB);
        if (definition.delivery().kind() == DeliveryKind.ARC) {
            allowed.add(AbilityBadge.HELD);
        }
        return allowed;
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
    @CsvSource({"typhoon_lift, typhoon_updraft", "pulse_relay, pulse_zap", "pulse_metronome, pulse_tick",
            "nether_hive, nether_decay", "glow_reflector, glow_laser",
            "leaf_verdant, leaf_growth", "frost_glacial, frost_cold", "ender_oculus, ender_blink",
            "blaze_heater, blaze_flames"})
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
