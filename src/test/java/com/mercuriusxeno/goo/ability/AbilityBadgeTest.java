package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An ability's badge reads only from the fixed vocabulary, and every shipped ability
 * declares mob exactly where it targets entities (decision badge-marks-the-target-kind),
 * graded from each loaded definition's own fields rather than a list of names
 * (decision tests-read-the-inventory-from-loaded-json).
 */
class AbilityBadgeTest {

    /** The name the residual fixture loads under, which its refusal names. */
    private static final String RESIDUAL_FIXTURE = "residual_fixture";
    /** A residual stream naming a prism reaction, with the badge left for the test to write. */
    private static final String STREAM_REACTING_TO_PRISMS = """
            { "gooType": "typhoon", "displayName": "goo.ability.fixture", "badge": "%s", "cost": 96,
              "delivery": { "kind": "stream" }, "behaviors": [],
              "on_prism": [ { "type": "coat" } ] }""";
    /** A stream naming a required item, with the badge left for the test to write. */
    private static final String STREAM_REQUIRING_AN_ITEM = """
            { "gooType": "blaze", "displayName": "goo.ability.fixture", "badge": "%s", "cost": 96,
              "delivery": { "kind": "stream" }, "behaviors": [],
              "requires": [ "minecraft:lava_bucket" ] }""";

    /** The delivery field a held channel writes; its codec default hides whether the JSON wrote it. */
    private static final String TICKS_PER_CHARGE_FIELD = "ticks_per_charge";

    @ParameterizedTest
    @CsvSource({"world, WORLD", "mob, MOB", "instant, SELF", "channeled, CHANNELED", "durative, DURATIVE",
            "free, FREE", "prism, PRISM", "held, HELD", "residual, RESIDUAL"})
    void eachVocabularyWordParsesToItsBadge(String word, AbilityBadge expected) {
        assertEquals(expected, AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).getOrThrow());
    }

    /**
     * The network codec writes the ordinal, so every badge keeps the ordinal it had as each new
     * badge joins last.
     * decision held-badge-marks-abilities-that-prepare-and-throw
     * decision residual-badge-marks-the-four-goo-sprays
     */
    @Test
    void everyBadgeKeepsItsNetworkOrdinal() {
        assertEquals(List.of(AbilityBadge.WORLD, AbilityBadge.MOB, AbilityBadge.SELF, AbilityBadge.CHANNELED,
                AbilityBadge.DURATIVE, AbilityBadge.FREE, AbilityBadge.PRISM, AbilityBadge.HELD,
                AbilityBadge.RESIDUAL), List.of(AbilityBadge.values()));
    }

    /**
     * A held ability and a residual spray each resolve at the aimed point, as a channeled one does.
     * decision held-badge-marks-abilities-that-prepare-and-throw
     * decision residual-badge-marks-the-four-goo-sprays
     *
     * @param badge the badge aiming a point
     */
    @ParameterizedTest
    @CsvSource({"HELD", "RESIDUAL"})
    void heldAndResidualAimAPoint(AbilityBadge badge) {
        assertTrue(badge.aimsAPoint(), badge + " aims no point");
    }

    /** decision self-badge-becomes-instant-with-a-noita-trigger-star */
    @Test
    void theOneShotBadgeSpellsInstant() {
        assertEquals("instant", AbilityBadge.SELF.getSerializedName());
    }

    /**
     * Each badge holds its own tier, ranked residual, channeled, held, mob, world, free,
     * prism, self, durative, at orders that reverse that rank if order alone
     * decided; a shared badge falls back to order (decisions
     * fan-sorts-badge-then-order, held-badge-marks-abilities-that-prepare-and-throw,
     * residual-tier-leads-the-fan).
     */
    @Test
    void fanOrderRanksEachBadgeThenOrder() {
        record Ranked(String name, AbilityBadge badge, int order) {
        }
        List<Ranked> shuffled = List.of(
                new Ranked("held", AbilityBadge.HELD, 7),
                new Ranked("residual", AbilityBadge.RESIDUAL, 8),
                new Ranked("durative", AbilityBadge.DURATIVE, -3),
                new Ranked("world", AbilityBadge.WORLD, 3),
                new Ranked("self", AbilityBadge.SELF, -2),
                new Ranked("prism", AbilityBadge.PRISM, 1),
                new Ranked("mob_late", AbilityBadge.MOB, 5),
                new Ranked("free", AbilityBadge.FREE, 2),
                new Ranked("channeled", AbilityBadge.CHANNELED, 6),
                new Ranked("mob_early", AbilityBadge.MOB, 4));

        // an unranked badge answers -1 and sorts first by accident, so every badge holds its own rank
        assertEquals(IntStream.range(0, AbilityBadge.values().length).boxed().toList(),
                Stream.of(AbilityBadge.values()).map(AbilityBadge::fanRank).sorted().toList());
        assertEquals(List.of("residual", "channeled", "held", "mob_early", "mob_late", "world", "free", "prism", "self", "durative"),
                shuffled.stream().sorted(AbilityBadge.fanOrder(Ranked::badge, Ranked::order)).map(Ranked::name).toList());
    }

    /**
     * A word outside the badge vocabulary fails to load.
     * decision clock-badge-marks-durative-abilities
     */
    @ParameterizedTest
    @CsvSource({"splash", "self"})
    void aWordOutsideTheVocabularyFailsToParse(String word) {
        assertTrue(AbilityBadge.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).isError());
    }

    /**
     * The badges a shipped ability may wear, read from its own fields: channeled or held for any delivery that charges, its charge sizing no cast, or writes a hold's ticks
     * per charge, channeled or residual for a stream, save the charm-like cast, which takes a mob and wears mob, durative for a self delivery paying upkeep, instant for any other self delivery,
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
            // residual-badge-marks-the-four-goo-sprays
            case STREAM -> EnumSet.of(AbilityBadge.CHANNELED, AbilityBadge.RESIDUAL);
            // charm-like-abilities-lock-the-highlighted-mob-on-right-click: a charm-like cast takes a mob
            case ENVELOP -> EnumSet.of(AbilityBadge.MOB);
            // self-effects-trickle-until-ended
            case SELF -> definition.upkeep().in(GooValueTable.EMPTY) > AbilityDefinition.NO_UPKEEP
                    ? EnumSet.of(AbilityBadge.DURATIVE)
                    : holds ? EnumSet.of(AbilityBadge.CHANNELED, AbilityBadge.HELD) : EnumSet.of(AbilityBadge.SELF);
            // held-badge-marks-abilities-that-prepare-and-throw: a charge forming knives prepares and throws
            default -> definition.delivery().charge().stacks() ? EnumSet.of(AbilityBadge.HELD)
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
    @CsvSource({"pulse_relay, pulse_zap", "pulse_metronome, pulse_tick",
            "nether_hive, zombie_decay", "glow_reflector, glow_laser",
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

    /**
     * An ability wearing the residual badge that names a prism reaction
     * fails to load, its error naming the ability, the badge and on_prism,
     * while the same JSON wearing another badge loads.
     * no-residual-ability-carries-a-prism-reaction
     */
    @Test
    void aResidualAbilityNamingAPrismReactionFailsToLoad() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> AbilityJson.decodeText(RESIDUAL_FIXTURE, STREAM_REACTING_TO_PRISMS.formatted("residual")));
        String message = refusal.getMessage();

        assertTrue(message.contains(RESIDUAL_FIXTURE), message);
        assertTrue(message.contains("residual badge"), message);
        assertTrue(message.contains("on_prism"), message);
        assertFalse(AbilityJson.decodeText(RESIDUAL_FIXTURE, STREAM_REACTING_TO_PRISMS.formatted("free"))
                .onPrism().isEmpty(), "a free stream keeps its prism reaction");
    }

    /**
     * An ability wearing the residual badge that names a required item fails
     * to load, its error naming the ability, while the same JSON wearing
     * another badge loads with its requirement.
     * decision no-residual-is-locked-behind-recipes
     */
    @Test
    void residualNamingARequiredItemIsRefused() {
        IllegalStateException refusal = assertThrows(IllegalStateException.class,
                () -> AbilityJson.decodeText(RESIDUAL_FIXTURE, STREAM_REQUIRING_AN_ITEM.formatted("residual")));
        String message = refusal.getMessage();

        assertTrue(message.contains(RESIDUAL_FIXTURE), message);
        assertTrue(message.contains("requires"), message);
        assertEquals(List.of(Identifier.withDefaultNamespace("lava_bucket")),
                AbilityJson.decodeText(RESIDUAL_FIXTURE, STREAM_REQUIRING_AN_ITEM.formatted("free")).requires());
    }

    /**
     * Every shipped ability wearing the residual badge decodes with no prism reaction.
     * no-residual-ability-carries-a-prism-reaction
     */
    @Test
    void shippedResidualAbilitiesCarryNoPrismReaction() {
        for (Path file : AbilityJson.files()) {
            AbilityDefinition definition = AbilityJson.decode(file);
            if (definition.badge() == AbilityBadge.RESIDUAL) {
                assertTrue(definition.onPrism().isEmpty(), definition.id() + " carries an on_prism reaction");
            }
        }
    }
}
