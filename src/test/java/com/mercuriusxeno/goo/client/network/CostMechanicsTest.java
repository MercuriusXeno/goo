package com.mercuriusxeno.goo.client.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DistancePrice;
import com.mercuriusxeno.goo.client.ClientGooValues;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValueTable;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.PrefabValues;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each shipped ability naming cost_mechanics fills its format string from its own
 * numbers, read through a test lang map, with no placeholder left. Swaps the client
 * value source and restores it.
 * decision zero-cost-field-formats-the-cost-mechanics
 */
class CostMechanicsTest {

    private static final int MUNDANE_BLOCK_ROCK = 7;
    private static final String SPIRE_PER_BLOCK_FORMULA = MUNDANE_BLOCK_ROCK + "/m³";
    private static final int NO_HOLDINGS = 0;
    private static final int NO_REAGENTS = 0;

    private static final Map<String, String> LANG = Map.of(
            "goo.ability.cost_mechanics.upkeep", "%s a second",
            "goo.ability.ender.blink.cost_mechanics", "%s per block, %s through a wall, %s percent to an oculus",
            "goo.ability.rock.spire.cost_mechanics", "%s per block raised");

    private static final IGooValueLookup VALUES = GooValueTable.ofEffectiveValues(Map.of(), new PrefabValues(Map.of(),
            Map.of("mundane_block", new GooValue(Map.of(GooTypes.ROCK, MUNDANE_BLOCK_ROCK)))));

    private static final CostMechanicsLookup LOOKUP = new CostMechanicsLookup(
            key -> LANG.getOrDefault(key, key), () -> VALUES);

    private Supplier<IGooValueLookup> standing;

    @AfterEach
    void restoreTheClientValueSource() {
        if (standing != null) {
            ClientGooValues.readFrom(standing);
        }
    }

    static Stream<AbilityDefinition> abilitiesNamingCostMechanics() {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(definition -> definition.costMechanics().isPresent());
    }

    private static ClientAbility clientOf(AbilityDefinition definition) {
        return ClientAbility.fromEntry(
                AbilitySyncPayload.gloveEntries(definition.gooType(), List.of(definition)).getFirst());
    }

    private static String textOf(String ability) {
        return clientOf(AbilityJson.decode(ability)).costMechanics(LOOKUP).orElseThrow();
    }

    @ParameterizedTest
    @MethodSource("abilitiesNamingCostMechanics")
    void textLeavesNoPlaceholder(AbilityDefinition definition) {
        String text = clientOf(definition).costMechanics(LOOKUP).orElseThrow();

        assertFalse(text.contains("%"), definition.id() + ": " + text);
    }

    static Stream<AbilityDefinition> brews() {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(definition -> definition.upkeep().in(GooValueTable.EMPTY) > AbilityDefinition.NO_UPKEEP);
    }

    @ParameterizedTest
    @MethodSource("brews")
    void brewTextNamesItsUpkeepASecond(AbilityDefinition definition) {
        String text = clientOf(definition).costMechanics(LOOKUP).orElseThrow();

        assertTrue(text.contains(GooFormat.formatAmount(definition.upkeep().in(GooValueTable.EMPTY) * 20L) + " a second"),
                definition.id() + ": " + text);
    }

    @Test
    void blinkTextNamesItsDistancePrice() {
        AbilityDefinition blink = AbilityJson.decode("ender_blink");
        String text = textOf("ender_blink");

        assertTrue(text.contains(GooFormat.formatAmount(blink.distancePrice().perBlock()) + " per block"), text);
        assertTrue(text.contains(GooFormat.formatAmount(blink.distancePrice().wallCost()) + " through a wall"), text);
        assertTrue(text.contains(blink.distancePrice().nodePercent() + " percent"), text);
    }

    /**
     * Spire's cost is its price per block grown, so the petal and the crosshair panel read its per-block formula and
     * its cost mechanics name the per-block mB, never that mB as a flat total.
     * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
     * decision dynamic-costs-keep-the-mb-quantity
     */
    @Test
    void spireReadsItsPerBlockPriceOnThePetalThePanelAndItsCostMechanics() {
        standing = ClientGooValues.readFrom(() -> VALUES);
        ClientAbility spire = clientOf(AbilityJson.decode("rock_spire"));

        assertTrue(spire.pricedDynamically());
        assertEquals(SPIRE_PER_BLOCK_FORMULA, spire.petalLabel(NO_HOLDINGS, NO_REAGENTS));
        assertEquals(SPIRE_PER_BLOCK_FORMULA, spire.costLabel());
        assertTrue(textOf("rock_spire").contains(MUNDANE_BLOCK_ROCK + " per block raised"), textOf("rock_spire"));
    }

    /**
     * Outsider's text names the void to enter, filled from its step's enter param, and a cost a
     * second rising as the room grows, never a cost per block or per cell
     * (decision outsider-cost-text-reads-the-new-mechanics).
     */
    @Test
    void outsiderTextNamesItsEntryAndACostASecond() throws IOException {
        String format = shippedLang().get(OUTSIDER_MECHANICS).getAsString();
        CostMechanicsLookup shipped = new CostMechanicsLookup(key -> OUTSIDER_MECHANICS.equals(key) ? format : key,
                () -> VALUES);
        String json = AbilityJson.read("void_outsider");
        String retuned = json.replaceFirst("\"enter\"\\s*:\\s*\\d+", "\"enter\": 9");

        assertTrue(clientOf(AbilityJson.decodeText("void_outsider", retuned)).costMechanics(shipped).orElseThrow()
                .startsWith("9 "), format);
        assertTrue(format.contains("second"), format);
        assertFalse(format.contains("block") || format.contains("cell"), format);
    }

    private static final String OUTSIDER_MECHANICS = "goo.ability.void.outsider.cost_mechanics";

    private static JsonObject shippedLang() throws IOException {
        try (InputStream in = CostMechanicsTest.class.getResourceAsStream("/assets/goo/lang/en_us.json")) {
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static Stream<AbilityDefinition> abilitiesWithAPlainCost() {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(definition -> definition.cost().in(GooValueTable.EMPTY) > 0 && definition.distancePrice().equals(DistancePrice.NONE));
    }

    @ParameterizedTest
    @MethodSource("abilitiesWithAPlainCost")
    void abilityWithAPlainCostNamesNoCostMechanics(AbilityDefinition definition) {
        assertTrue(definition.costMechanics().isEmpty(), definition.id().toString());
    }

    // decision ability-json-names-its-reagent
    @Test
    void eraseConsumesNoItem() {
        assertTrue(AbilityJson.decode("void_erase").consumes().isEmpty());
    }

    @Test
    void abilityNamingNoCostMechanicsHasNoText() {
        assertTrue(clientOf(AbilityJson.decode("unstable_explode")).costMechanics(LOOKUP).isEmpty());
    }
}
