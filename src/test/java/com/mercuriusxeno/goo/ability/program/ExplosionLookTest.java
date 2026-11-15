package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shipped abilities whose explode step writes vanilla's look, read from
 * the goo_abilities on the classpath: Blast alone takes the new look.
 */
class ExplosionLookTest {

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(ExplosionLookTest::withDescendants));
    }

    private static boolean writesTheVanillaLook(AbilityDefinition ability) {
        return ability.behaviors().stream().flatMap(ExplosionLookTest::withDescendants)
                .anyMatch(step -> step instanceof ExplodeStep explode && explode.look() == ExplosionLook.VANILLA);
    }

    // blast-alone-takes-the-new-explosion-look
    @Test
    void blastIsTheOneShippedAbilityWritingTheVanillaLook() {
        Set<Identifier> vanilla = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ExplosionLookTest::writesTheVanillaLook).map(AbilityDefinition::id)
                .collect(Collectors.toSet());

        assertEquals(Set.of(Identifier.parse("goo:unstable_blast")), vanilla);
    }
}
