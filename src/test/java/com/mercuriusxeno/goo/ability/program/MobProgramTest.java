package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Each mob ability JSON is a program that loads for the struck-entity host.
 * An effect step acts on the entity the host hands over, which no unit test
 * can build, so the struck mob's reaction is graded by MobEffectTests
 * through the throw's impact (decision world-tests-assert-one-observation).
 */
class MobProgramTest {

    /** Every shipped ability wearing the mob badge (decision tests-read-the-inventory-from-loaded-json). */
    static Stream<AbilityDefinition> shippedMobAbilities() {
        return AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(definition -> definition.badge() == AbilityBadge.MOB);
    }

    @ParameterizedTest
    @MethodSource("shippedMobAbilities")
    void everyMobAbilityIsAProgramThatLoadsForTheStruckEntityHost(AbilityDefinition definition) {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(definition.behaviors(), HostKind.ENTITY),
                definition.id().toString());
    }
}
