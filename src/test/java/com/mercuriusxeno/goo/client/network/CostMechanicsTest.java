package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each shipped ability naming cost_mechanics fills its format string from its own
 * numbers, read through a test lang map, with no placeholder left.
 * decision zero-cost-field-formats-the-cost-mechanics
 */
class CostMechanicsTest {

    private static final int STONE_ROCK_VALUE = 7;
    private static final Identifier STONE = Identifier.withDefaultNamespace("stone");

    private static final Map<String, String> LANG = Map.of(
            "goo.ability.cost_mechanics.upkeep", "%s a second",
            "goo.ability.ender.blink.cost_mechanics", "%s per block, %s through a wall, %s percent to an oculus",
            "goo.ability.rock.spire.cost_mechanics", "%s per block raised",
            "goo.ability.unstable.unmake.cost_mechanics", "Burns unstable goo for each block dissolved.",
            "goo.ability.hex.enchant.cost_mechanics", "Uses a %s",
            "goo.ability.void.erase.cost_mechanics", "Uses an %s");

    private static final Map<Identifier, String> ITEM_NAMES = Map.of(
            Identifier.withDefaultNamespace("book"), "Book",
            Identifier.withDefaultNamespace("ender_pearl"), "Ender Pearl");

    private static final CostMechanicsLookup LOOKUP = new CostMechanicsLookup(
            key -> LANG.getOrDefault(key, key),
            item -> ITEM_NAMES.getOrDefault(item, item.toString()),
            item -> STONE.equals(item) ? STONE_ROCK_VALUE : 0);

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
                .filter(definition -> definition.upkeep() > AbilityDefinition.NO_UPKEEP);
    }

    @ParameterizedTest
    @MethodSource("brews")
    void brewTextNamesItsUpkeepASecond(AbilityDefinition definition) {
        String text = clientOf(definition).costMechanics(LOOKUP).orElseThrow();

        assertTrue(text.contains(GooFormat.formatAmount(definition.upkeep() * 20L) + " a second"),
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

    @Test
    void spireTextNamesStonesRockValuePerBlock() {
        assertTrue(textOf("rock_spire").contains(STONE_ROCK_VALUE + " per block raised"), textOf("rock_spire"));
    }

    @Test
    void unmakeTextStatesItsRuleWithNoNumber() {
        assertTrue(textOf("unstable_unmake").startsWith("Burns unstable goo"), textOf("unstable_unmake"));
    }

    @Test
    void enchantTextNamesTheBookItConsumes() {
        assertTrue(textOf("hex_enchant").contains("Uses a Book"), textOf("hex_enchant"));
    }

    @Test
    void eraseTextNamesTheEnderPearlItConsumes() {
        assertTrue(textOf("void_erase").contains("Uses an Ender Pearl"), textOf("void_erase"));
    }

    @Test
    void abilityNamingNoCostMechanicsHasNoText() {
        assertTrue(clientOf(AbilityJson.decode("unstable_explode")).costMechanics(LOOKUP).isEmpty());
    }
}
