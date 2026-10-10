package com.mercuriusxeno.goo.ability.hex;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Spawn's pool: each type the biome lists, once, among those that
 * fit the landing cell.
 */
class NaturalSpawnsTest {

    @Test
    void aTypeListedTwiceDrawsOnce() {
        assertEquals(List.of("zombie", "cow"),
                NaturalSpawns.pool(Stream.of("zombie", "cow", "zombie"), type -> true));
    }

    @Test
    void aTypeThatDoesNotFitIsLeftOut() {
        assertEquals(List.of("cow"), NaturalSpawns.pool(Stream.of("squid", "cow"), type -> !"squid".equals(type)));
    }
}
