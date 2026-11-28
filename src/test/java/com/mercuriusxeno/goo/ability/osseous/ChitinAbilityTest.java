package com.mercuriusxeno.goo.ability.osseous;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.program.HeartOverlayStep;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Chitin, read from the shipped abilities through the ability codec, is Osseous's and lays the chitin heart
 * overlay, and Crystal's abilities are Glimmer, Knives and Shatter alone.
 * decision chitin-is-scales-moved-from-crystal
 */
class ChitinAbilityTest {

    private static final Identifier OSSEOUS_CHITIN = Identifier.fromNamespaceAndPath("goo", "osseous_chitin");
    private static Map<Identifier, AbilityDefinition> shipped;

    @BeforeAll
    static void decodeTheShippedAbilities() {
        shipped = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(AbilityDefinition::id, Function.identity()));
    }

    @Test
    void chitinIsOsseousAndLaysChitinHearts() {
        AbilityDefinition chitin = shipped.get(OSSEOUS_CHITIN);
        assertNotNull(chitin, "osseous_chitin does not ship");
        assertEquals(GooTypes.OSSEOUS, chitin.gooType());
        assertEquals(List.of(HeartKind.CHITIN), chitin.behaviors().stream()
                .filter(HeartOverlayStep.class::isInstance)
                .map(step -> ((HeartOverlayStep) step).kind())
                .toList());
    }

    @Test
    void crystalHoldsGlimmerKnivesAndShatterAlone() {
        assertEquals(List.of("goo:crystal_glimmer", "goo:crystal_knives", "goo:crystal_shatter"),
                shipped.values().stream()
                        .filter(ability -> ability.gooType().equals(GooTypes.CRYSTAL))
                        .map(ability -> ability.id().toString())
                        .sorted()
                        .toList());
    }
}
