package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * No shipped ability floats its lifted blob to the center line: none carries
 * the tag that once sent it there, so Click, Orb, Drain, Reap, Zap, Glimmer and
 * Magnet rest their blob over the hand as every other ability does.
 * decision no-ability-floats-its-blob-to-the-center-line
 */
class CenteredAbilitiesTest {

    /** The tag that floated a blob to the center line. */
    private static final String CENTER_LINE_TAG = "centered";

    @Test
    void noShippedAbilityCarriesTheCenterLineTag() {
        Set<String> tagged = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.hasTag(CENTER_LINE_TAG))
                .map(ability -> ability.id().getPath()).collect(Collectors.toSet());

        assertEquals(Set.of(), tagged, "no shipped ability should float its blob to the center line");
    }
}
