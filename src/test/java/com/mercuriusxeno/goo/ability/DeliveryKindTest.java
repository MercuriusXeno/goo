package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A delivery kind reads from the fixed vocabulary: each word parses to its
 * kind (decision mob-ability-touches-at-reach).
 */
class DeliveryKindTest {

    @ParameterizedTest
    @CsvSource({"arc, ARC", "beam, BEAM", "stream, STREAM", "self, SELF"})
    void eachVocabularyWordParsesToItsKind(String word, DeliveryKind expected) {
        assertEquals(expected, DeliveryKind.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(word)).getOrThrow());
    }
}
